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

    /** text+voice → PCM, 简单 LRU (访问序) 控制内存。 */
    private val cache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 24
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
            val pcm = synchronized(cache) { cache[cacheKey] }
                ?: runCatching { fetchAudio(key, model, voice, spoken) }.getOrNull()?.also {
                    synchronized(cache) { cache[cacheKey] = it }
                }
            if (pcm == null) {
                // 云端失败不打断学习流, 静默退回系统音
                fallback.speak(spoken, flush)
                return@launch
            }
            playPcm(pcm)
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

    private fun fetchAudio(key: String, model: String, voice: String, text: String): ByteArray {
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
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Gemini TTS HTTP ${resp.code}")
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["candidates"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                .map { it.jsonObject }
                .firstNotNullOf { it["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content }
            return Base64.decode(data, Base64.DEFAULT)
        }
    }

    // ─── 播放 ────────────────────────────────────────────────

    private suspend fun playPcm(pcm: ByteArray) {
        releaseTrack()
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val newTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
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

        /** 口音/风格指令, TTS 模型只照做不朗读。 */
        private const val STYLE_PREFIX =
            "Read the following aloud in a natural, warm British RP (Received Pronunciation) accent, " +
                "at a relaxed conversational pace, like a friendly Londoner chatting: "
    }
}
