package com.lingua.english.data.practice

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import com.lingua.english.data.ai.AiApiPresets
import com.lingua.english.data.ai.AiException
import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.prefs.SettingsPreferences
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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

/** AI 标记的一个发音问题词。i = 词在原文按 [wordsOf] 切分后的下标。 */
@Serializable
data class PronIssue(
    val i: Int = -1,
    val w: String = "",
    val issue: String = "",
    val tip: String = "",
)

@Serializable
data class PronAssessment(
    val overall: String = "",
    val words: List<PronIssue> = emptyList(),
)

/**
 * 跟读教练: 录下用户朗读 → 音频直接发给 Gemini (原生音频理解) 按 RP 标准点评 →
 * 返回逐词问题标记。录音留在本地 cache, 供「听我自己的发音」回放。
 * 只支持 Gemini (要音频输入); 其它服务商会抛可读错误。
 */
class PronunciationCoach(
    private val context: Context,
    private val prefs: SettingsPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    val recordingFile: File get() = File(context.cacheDir, "pron_take.aac")

    // ─── 录音 / 回放 ─────────────────────────────────────────

    fun startRecording() {
        stopPlayback()
        stopRecording()
        recordingFile.delete()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(48_000)
        r.setOutputFile(recordingFile.path)
        r.prepare()
        r.start()
        recorder = r
    }

    fun stopRecording() {
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
    }

    /** 回放自己的录音; 播完或失败都回调 onDone。 */
    fun playRecording(onDone: () -> Unit) {
        stopPlayback()
        if (!recordingFile.exists()) {
            onDone()
            return
        }
        val p = MediaPlayer()
        player = p
        runCatching {
            p.setDataSource(recordingFile.path)
            p.setOnCompletionListener {
                stopPlayback()
                onDone()
            }
            p.prepare()
            p.start()
        }.onFailure {
            stopPlayback()
            onDone()
        }
    }

    fun stopPlayback() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
    }

    // ─── AI 评估 ─────────────────────────────────────────────

    suspend fun assess(targetText: String): PronAssessment = withContext(Dispatchers.IO) {
        val preset = AiApiPresets.byId(prefs.aiApiPreset.first())
        if (preset.id != "gemini") {
            throw AiException("跟读评估要用 Gemini 的音频理解, 请在设置里把 AI 对话服务切到 Gemini。")
        }
        val key = prefs.aiApiKey.first().trim()
        if (key.isEmpty()) throw AiException("请先在设置页填写 API Key。")
        val model = prefs.aiApiModel.first().trim().ifEmpty { "gemini-3.5-flash" }
        if (!recordingFile.exists() || recordingFile.length() < 2_000) {
            throw AiException("没录到声音, 再读一次试试。")
        }

        val words = wordsOf(targetText)
        val indexed = words.mapIndexed { i, w -> "$i:$w" }.joinToString(" ")
        val prompt = """
你是英式英语 (RP, Received Pronunciation) 发音教练。用户朗读了下面这段文字, 录音已附上。
目标文本: $targetText
带编号的单词表: $indexed

对照录音逐词判断: 哪些单词发音明显有问题 (漏读、读成别的词、重音位置错、元音/辅音明显偏)。参考 RP 标准, 但不要吹毛求疵——能听懂、只是口音轻微的不要标; 录音里没读到的结尾部分只标最先漏掉的一两个词。
只输出 JSON, 不要其它文字:
{"overall":"一句中文总评(不超过40字)","words":[{"i":单词编号,"w":"单词原样","issue":"哪里不对(中文,不超过20字)","tip":"怎么读对(中文,可含音标,不超过40字)"}]}
读得好就让 words 是空数组。
""".trim()

        val audioB64 = Base64.encodeToString(recordingFile.readBytes(), Base64.NO_WRAP)
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "audio/aac")
                                put("data", audioB64)
                            }
                        }
                    }
                }
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
                throw AiException("评估请求失败: HTTP ${resp.code}${message?.let { " · ${it.take(120)}" }.orEmpty()}")
            }
            runCatching {
                json.parseToJsonElement(bodyText).jsonObject["candidates"]!!.jsonArray[0]
                    .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                    .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                    .joinToString("")
            }.getOrElse { throw AiException("模型没有返回点评, 再试一次。", it) }
        }

        val parsed = runCatching {
            json.decodeFromString(PronAssessment.serializer(), ChatApiClient.extractJsonObject(text))
        }.getOrElse { throw AiException("点评结果无法解析, 再试一次。", it) }

        // 下标消毒: 编号对不上时按单词文本找回, 找不到就丢弃
        val sane = parsed.words.mapNotNull { issue ->
            val byIndex = issue.i in words.indices &&
                words[issue.i].equals(issue.w.trim(), ignoreCase = true)
            val index = if (byIndex) issue.i
            else words.indexOfFirst { it.equals(issue.w.trim(), ignoreCase = true) }.takeIf { it >= 0 }
            index?.let { issue.copy(i = it) }
        }.distinctBy { it.i }
        parsed.copy(words = sane)
    }

    companion object {
        private val WORD_REGEX = Regex("[A-Za-z][A-Za-z'’-]*")

        /** 与跟读标记共用的分词规则 (和 TappableText 一致)。 */
        fun wordsOf(text: String): List<String> = WORD_REGEX.findAll(text).map { it.value }.toList()
    }
}
