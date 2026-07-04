package com.lingua.english.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Base64
import com.lingua.english.data.prefs.SettingsPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gemini 云端语音: generateContent + AUDIO 模态, 返回 PCM 16bit 24kHz 单声道。
 * 真人感远好于系统 TTS; 英式口音靠提示词前缀控制。走「AI 对话服务」里的
 * Gemini Key 按量计费; 没 Key / 请求失败时自动退回系统 TTS 兜底。
 * 同一句话的音频做进程内缓存, 复习反复朗读不重复扣费。
 */
class GeminiTtsProvider(
    private val prefs: SettingsPreferences,
    private val scope: CoroutineScope,
    private val fallback: TtsProvider,
) : TtsProvider {

    private val _status = MutableStateFlow<TtsStatus>(
        TtsStatus.Ready(britishVoice = true, voiceName = "Gemini 云端语音"),
    )
    override val status: StateFlow<TtsStatus> = _status

    /** 最近一次云端合成失败的原因 (429 限额/400 等), 设置页展示, 成功后清空。 */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var rate = 0.9f
    private var job: Job? = null

    @Volatile
    private var track: AudioTrack? = null

    private class AudioClip(val pcm: ByteArray, val sampleRate: Int)

    /** text+voice → 音频, 简单 LRU (访问序) 控制内存。 */
    private val cache = object : LinkedHashMap<String, AudioClip>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioClip>?) = size > 24
    }

    override fun speak(text: String, flush: Boolean) {
        val spoken = text.trim().take(MAX_CHARS)
        if (spoken.isEmpty()) return
        if (flush) stop()
        job = scope.launch(Dispatchers.IO) {
            val key = prefs.aiApiKey.first().trim()
            if (key.isEmpty()) {
                fallback.speak(spoken, flush)
                return@launch
            }
            val voice = prefs.ttsVoice.first().trim().ifEmpty { "Kore" }
            val model = prefs.ttsModel.first().trim().ifEmpty { "gemini-2.5-flash-preview-tts" }
            val cacheKey = "$voice|$model|$spoken"
            val clip = synchronized(cache) { cache[cacheKey] }
                ?: runCatching { fetchAudio(key, model, voice, spoken) }
                    .onFailure { _lastError.value = it.message?.take(160) ?: "未知错误" }
                    .getOrNull()
                    ?.also {
                        synchronized(cache) { cache[cacheKey] = it }
                        _lastError.value = null
                    }
            if (clip == null) {
                // 云端失败不打断学习流, 退回系统音; 原因记在 lastError 供设置页查看
                fallback.speak(spoken, flush)
                return@launch
            }
            playPcm(clip)
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        releaseTrack()
        fallback.stop()
    }

    override fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 1.2f)
        fallback.setRate(rate)
    }

    override fun release() {
        stop()
    }

    // ─── 请求 ────────────────────────────────────────────────

    private fun fetchAudio(key: String, model: String, voice: String, text: String): AudioClip {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", "$STYLE_PREFIX$text") }
                    }
                }
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") {
                        putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val message = runCatching {
                    json.parseToJsonElement(bodyText).jsonObject["error"]
                        ?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                error("HTTP ${resp.code}${message?.let { ": ${it.take(120)}" }.orEmpty()}")
            }
            val root = json.parseToJsonElement(bodyText).jsonObject
            // 长文本的音频可能分多个 inlineData part 返回, 全部拼起来
            val inlineParts = root["candidates"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                .mapNotNull { it.jsonObject["inlineData"]?.jsonObject }
            val chunks = inlineParts.mapNotNull { it["data"]?.jsonPrimitive?.content }
                .map { Base64.decode(it, Base64.DEFAULT) }
            if (chunks.isEmpty()) error("模型没有返回音频 (文本可能过长被截断)")
            val pcm = if (chunks.size == 1) chunks[0] else ByteArray(chunks.sumOf { it.size }).also { out ->
                var pos = 0
                chunks.forEach { it.copyInto(out, pos); pos += it.size }
            }
            // 采样率以 mimeType 里声明的为准 (audio/L16;codec=pcm;rate=24000)
            val mime = inlineParts.firstOrNull()?.get("mimeType")?.jsonPrimitive?.content.orEmpty()
            val sampleRate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: SAMPLE_RATE
            return AudioClip(pcm, sampleRate)
        }
    }

    // ─── 播放 ────────────────────────────────────────────────

    private suspend fun playPcm(clip: AudioClip) {
        releaseTrack()
        val pcm = clip.pcm
        val minBuf = AudioTrack.getMinBufferSize(
            clip.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val newTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(clip.sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBuf, 8192),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track = newTrack
        runCatching { newTrack.playbackParams = PlaybackParams().setSpeed(rate) }
        try {
            newTrack.play()
            var offset = 0
            while (offset < pcm.size) {
                currentCoroutineContext().ensureActive()
                val written = newTrack.write(pcm, offset, minOf(8192, pcm.size - offset))
                if (written <= 0) break
                offset += written
            }
        } finally {
            if (track === newTrack) track = null
            runCatching { newTrack.stop() }
            runCatching { newTrack.release() }
        }
    }

    private fun releaseTrack() {
        track?.let { t ->
            track = null
            runCatching { t.pause() }
            runCatching { t.flush() }
            runCatching { t.release() }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 24_000
        private const val MAX_CHARS = 1_500

        /** 口音/风格指令, TTS 模型只照做不朗读。注意别写 relaxed/slow 之类的词, 模型真会放慢。 */
        private const val STYLE_PREFIX =
            "Read the following aloud in a natural British RP (Received Pronunciation) accent, " +
                "with lively intonation, at the brisk, natural pace of everyday conversation: "
    }
}
