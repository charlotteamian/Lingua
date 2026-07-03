package com.lingua.english.data.ai

import com.lingua.english.data.prefs.SettingsPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** API 协议方言: 市面绝大多数服务商兼容 OpenAI Chat Completions, Claude 官方 API 用 Anthropic Messages。 */
enum class AiApiDialect { OPENAI, ANTHROPIC }

/** 服务商预设: 选中后自动填 base URL / 协议 / 默认模型, 三者都允许手动覆盖。(与 Vita 同款) */
data class AiApiPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val dialect: AiApiDialect,
    val defaultModel: String,
)

object AiApiPresets {
    val ALL = listOf(
        AiApiPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", AiApiDialect.OPENAI, "deepseek-chat"),
        AiApiPreset("anthropic", "Claude", "https://api.anthropic.com", AiApiDialect.ANTHROPIC, "claude-sonnet-4-6"),
        AiApiPreset("openai", "OpenAI", "https://api.openai.com/v1", AiApiDialect.OPENAI, "gpt-5.1"),
        AiApiPreset("moonshot", "Kimi", "https://api.moonshot.cn/v1", AiApiDialect.OPENAI, "kimi-latest"),
        AiApiPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", AiApiDialect.OPENAI, "qwen-plus"),
        AiApiPreset("zhipu", "智谱", "https://open.bigmodel.cn/api/paas/v4", AiApiDialect.OPENAI, "glm-4.6"),
        AiApiPreset("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", AiApiDialect.OPENAI, "gemini-2.5-flash"),
        AiApiPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", AiApiDialect.OPENAI, "anthropic/claude-sonnet-4.5"),
        AiApiPreset("custom", "自定义", "", AiApiDialect.OPENAI, ""),
    )

    fun byId(id: String?): AiApiPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 对话的一轮, role = user / assistant。 */
data class ChatTurn(val role: String, val content: String)

/**
 * API 直连聊天客户端 (BYOK): 手机直接调大模型 API, 费用走用户自己的 Key。
 * 双协议移植自 Vita 的 DirectApiInsightProvider, 扩展成多轮 + system 提示。
 */
class ChatApiClient(private val prefs: SettingsPreferences) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class ApiConfig(
        val dialect: AiApiDialect,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    )

    /** 多轮对话, 返回助手回复文本。 */
    suspend fun chat(
        system: String,
        turns: List<ChatTurn>,
        maxTokens: Int = CHAT_MAX_TOKENS,
    ): String = withContext(Dispatchers.IO) {
        val cfg = resolveConfig()
        when (cfg.dialect) {
            AiApiDialect.OPENAI -> chatOpenAi(cfg, system, turns)
            AiApiDialect.ANTHROPIC -> chatAnthropic(cfg, system, turns, maxTokens)
        }
    }

    /** 设置页「测试连接」。 */
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val cfg = resolveConfig()
        when (cfg.dialect) {
            AiApiDialect.OPENAI -> chatOpenAi(cfg, "", listOf(ChatTurn("user", "请只回复两个字: 正常")))
            AiApiDialect.ANTHROPIC -> chatAnthropic(cfg, "", listOf(ChatTurn("user", "请只回复两个字: 正常")), 64)
        }
        "连接正常 · ${cfg.model}"
    }

    suspend fun isConfigured(): Boolean = prefs.aiApiKey.first().isNotBlank()

    // ─── 配置 ────────────────────────────────────────────────

    private suspend fun resolveConfig(): ApiConfig {
        val preset = AiApiPresets.byId(prefs.aiApiPreset.first())
        val baseUrl = prefs.aiApiBaseUrl.first().trim().ifEmpty { preset.baseUrl }
        val apiKey = prefs.aiApiKey.first().trim()
        val model = prefs.aiApiModel.first().trim().ifEmpty { preset.defaultModel }
        if (baseUrl.isEmpty()) throw AiException("请先在设置页填写 API 地址。")
        if (apiKey.isEmpty()) throw AiException("请先在设置页填写 API Key。")
        if (model.isEmpty()) throw AiException("请先在设置页填写模型名称。")
        return ApiConfig(preset.dialect, normalizeBaseUrl(baseUrl), apiKey, model)
    }

    private fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url
    }

    // ─── 请求 ────────────────────────────────────────────────

    /** OpenAI Chat Completions (DeepSeek/Kimi/通义/智谱/Gemini 兼容端点/OpenRouter 通用)。 */
    private fun chatOpenAi(cfg: ApiConfig, system: String, turns: List<ChatTurn>): String {
        // 不带 temperature / max_tokens: 各家可选参数名并不统一, 省略最兼容
        val body = buildJsonObject {
            put("model", cfg.model)
            putJsonArray("messages") {
                if (system.isNotBlank()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                }
                turns.forEach { turn ->
                    addJsonObject {
                        put("role", turn.role)
                        put("content", turn.content)
                    }
                }
            }
        }
        val root = execute(
            cfg,
            url = "${cfg.baseUrl}/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${cfg.apiKey}"),
            bodyJson = body.toString(),
        )
        val content = runCatching {
            root.jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }.getOrNull()
        if (content.isNullOrBlank()) throw AiException("模型没有返回内容, 换个模型或重试一次。")
        return content
    }

    /** Anthropic Messages (Claude 官方 API)。 */
    private fun chatAnthropic(cfg: ApiConfig, system: String, turns: List<ChatTurn>, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", maxTokens)
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                turns.forEach { turn ->
                    addJsonObject {
                        put("role", turn.role)
                        put("content", turn.content)
                    }
                }
            }
        }
        val root = execute(
            cfg,
            url = "${cfg.baseUrl}/v1/messages",
            headers = mapOf(
                "x-api-key" to cfg.apiKey,
                "anthropic-version" to "2023-06-01",
            ),
            bodyJson = body.toString(),
        )
        val content = runCatching {
            root.jsonObject["content"]!!.jsonArray
                .map { it.jsonObject }
                .filter { it["type"]?.jsonPrimitive?.content == "text" }
                .joinToString("") { it["text"]?.jsonPrimitive?.content.orEmpty() }
        }.getOrNull()
        if (content.isNullOrBlank()) throw AiException("模型没有返回内容, 换个模型或重试一次。")
        return content
    }

    private fun execute(
        cfg: ApiConfig,
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
    ): kotlinx.serialization.json.JsonElement {
        val builder = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = runCatching { client.newCall(builder.build()).execute() }.getOrElse {
            throw AiException("连不上 ${cfg.baseUrl}，检查网络或 API 地址。", it)
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AiException("API 返回错误: ${describeError(resp.code, text)}")
            }
            return runCatching { json.parseToJsonElement(text) }.getOrElse {
                throw AiException("API 返回了无法解析的内容 (HTTP ${resp.code})。", it)
            }
        }
    }

    /** 从各家不同形状的错误体里抠出人话 ({"error":{"message":..}} / {"error":".."} / {"message":..})。 */
    private fun describeError(code: Int, body: String): String {
        val message = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]
            when {
                error is JsonObject -> error["message"]?.jsonPrimitive?.content
                error != null -> error.jsonPrimitive.content
                else -> root["message"]?.jsonPrimitive?.content
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return message?.take(200) ?: "HTTP $code"
    }

    companion object {
        private const val CHAT_MAX_TOKENS = 2048

        /** 从模型输出里抠出 JSON 对象 (容忍代码围栏和前后杂质)。 */
        fun extractJsonObject(text: String): String {
            val cleaned = text.trim()
                .replace(Regex("^```[a-zA-Z]*\\s*"), "")
                .replace(Regex("\\s*```$"), "")
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start == -1 || end <= start) {
                throw AiException("模型输出里没有 JSON 对象。")
            }
            return cleaned.substring(start, end + 1)
        }
    }
}
