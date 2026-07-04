package com.lingua.english.data.media

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.lingua.english.data.ai.AiApiPresets
import com.lingua.english.data.ai.AiException
import com.lingua.english.data.local.dao.MediaDao
import com.lingua.english.data.local.entity.MediaItemEntity
import com.lingua.english.data.prefs.SettingsPreferences
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 听读素材: YouTube (免费字幕) / 本地音频·播客 (Gemini 转写, 花 token)。
 * 字幕存 filesDir/transcripts/<id>.json; 本地音频只存 SAF uri 不复制文件。
 */
class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val prefs: SettingsPreferences,
    private val captions: YouTubeCaptionClient,
) {

    private val webFetcher by lazy { WebViewCaptionFetcher(context) }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)   // 长音频转写可能要几分钟
        .build()

    val items: Flow<List<MediaItemEntity>> = mediaDao.observeAll()

    fun item(id: String): Flow<MediaItemEntity?> = mediaDao.observeById(id)

    private fun transcriptFile(id: String): File =
        File(context.filesDir, "transcripts/$id.json").also { it.parentFile?.mkdirs() }

    fun loadTranscript(id: String): List<TranscriptLine> = runCatching {
        json.decodeFromString(
            ListSerializer(TranscriptLine.serializer()),
            transcriptFile(id).readText(),
        )
    }.getOrDefault(emptyList())

    private fun saveTranscript(id: String, lines: List<TranscriptLine>) {
        transcriptFile(id).writeText(
            json.encodeToString(ListSerializer(TranscriptLine.serializer()), lines),
        )
    }

    /** 导入 YouTube: 拉字幕即完成, 0 token。裸 HTTP 被风控时用 WebView 真浏览器通道兜底。 */
    suspend fun importYouTube(url: String): String {
        val videoId = captions.extractVideoId(url)
            ?: throw AiException("这不像是 YouTube 链接, 检查一下。")
        val result = runCatching { captions.fetch(videoId) }.getOrElse { direct ->
            val raw = runCatching { webFetcher.fetch(videoId) }.getOrElse { web ->
                throw AiException(
                    "字幕拉取失败——直连: ${direct.message?.take(80)}; 网页通道: ${web.message?.take(80)}"
                )
            }
            captions.fetchFromBaseUrl(videoId, raw.title.ifBlank { videoId }, raw.baseUrl)
        }
        val id = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) { saveTranscript(id, result.lines) }
        mediaDao.insert(
            MediaItemEntity(
                id = id,
                type = MediaItemEntity.TYPE_YOUTUBE,
                title = result.title,
                source = videoId,
                createdAtEpochMs = System.currentTimeMillis(),
                transcribed = true,
            )
        )
        return id
    }

    /** 导入本地音频 (播客等): 先入库, 转写由用户在播放页确认后再花 token。 */
    suspend fun importAudio(uri: Uri, displayName: String): String {
        val id = UUID.randomUUID().toString()
        mediaDao.insert(
            MediaItemEntity(
                id = id,
                type = MediaItemEntity.TYPE_AUDIO,
                title = displayName.ifBlank { "音频 ${id.take(6)}" },
                source = uri.toString(),
                createdAtEpochMs = System.currentTimeMillis(),
                transcribed = false,
            )
        )
        return id
    }

    /** 音频大小 (MB), 供转写前展示预估。读不到返回 null。 */
    fun audioSizeMb(item: MediaItemEntity): Double? = runCatching {
        context.contentResolver.openAssetFileDescriptor(Uri.parse(item.source), "r")
            ?.use { it.length / 1024.0 / 1024.0 }
    }.getOrNull()

    /** Gemini 转写本地音频成逐句字幕。仅 Gemini 预设可用; 限 ≤18MB (inline 上限)。 */
    suspend fun transcribe(item: MediaItemEntity): Unit = withContext(Dispatchers.IO) {
        val preset = AiApiPresets.byId(prefs.aiApiPreset.first())
        if (preset.id != "gemini") throw AiException("音频转写要用 Gemini, 请在设置里切换。")
        val key = prefs.aiApiKey.first().trim()
        if (key.isEmpty()) throw AiException("请先在设置页填写 API Key。")
        val model = prefs.aiApiModel.first().trim().ifEmpty { "gemini-3.5-flash" }

        val uri = Uri.parse(item.source)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AiException("读不到这个音频文件了 (可能被移动/删除), 重新导入试试。")
        if (bytes.size > 18 * 1024 * 1024) {
            throw AiException(
                "文件 ${"%.0f".format(bytes.size / 1024.0 / 1024.0)}MB 超过 18MB 上限。" +
                    "播客可以先用其它工具裁剪/压缩 (64kbps 单声道约可容纳 35 分钟)。"
            )
        }
        val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"

        val prompt = """
把这段英语音频完整转写成逐句字幕。要求:
1. 按听到的原文转写英文, 不翻译、不纠正说话人。
2. 每句 5-25 词, 在自然停顿处断句。
3. s 是该句在音频里的起始秒数 (数字, 尽量准确)。
只输出 JSON 数组, 不要其它文字: [{"s":0.0,"t":"..."},{"s":4.2,"t":"..."}]
""".trim()

        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", mime)
                                put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                            }
                        }
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", 65_536)
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val text = client.newCall(request).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val message = runCatching {
                    json.parseToJsonElement(bodyText).jsonObject["error"]
                        ?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw AiException("转写失败: HTTP ${resp.code}${message?.let { " · ${it.take(120)}" }.orEmpty()}")
            }
            runCatching {
                json.parseToJsonElement(bodyText).jsonObject["candidates"]!!.jsonArray[0]
                    .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                    .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                    .joinToString("")
            }.getOrElse { throw AiException("模型没有返回转写内容, 再试一次。", it) }
        }

        val lines = runCatching {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            json.decodeFromString(
                ListSerializer(TranscriptLine.serializer()),
                text.substring(start, end + 1),
            )
        }.getOrElse { throw AiException("转写结果解析失败, 再试一次。", it) }
        if (lines.isEmpty()) throw AiException("转写结果是空的, 再试一次。")

        saveTranscript(item.id, lines)
        mediaDao.markTranscribed(item.id)
    }

    suspend fun delete(item: MediaItemEntity) {
        mediaDao.deleteById(item.id)
        runCatching { transcriptFile(item.id).delete() }
    }
}
