package com.lingua.english.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.lingua.english.data.prefs.SettingsPreferences
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
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
            val model = prefs.ttsModel.first().trim().ifEmpty { SettingsPreferences.DEFAULT_TTS_MODEL }
            val style = prefs.ttsStyle.first().trim().ifEmpty { DEFAULT_STYLE }

            // 3.1+ 的首段边生成边播放；后续段在当前段播放期间提前合成。
            // 既降低首音延迟，也避免逐段「读完才请求」造成的长停顿。
            val segments = segment(spoken)
            playSegments(key, model, voice, style, segments)
        }
    }

    private suspend fun playSegments(
        key: String,
        model: String,
        voice: String,
        style: String,
        segments: List<String>,
    ) = coroutineScope {
        val cacheKeys = segments.map { "$voice|$model|${style.hashCode()}|$it" }
        val pending = MutableList<Deferred<Result<AudioClip>>?>(segments.size) { null }

        fun cached(index: Int): AudioClip? = synchronized(cache) { cache[cacheKeys[index]] }

        fun prefetch(index: Int) {
            if (index !in segments.indices || cached(index) != null || pending[index] != null) return
            pending[index] = async(Dispatchers.IO) {
                fetchCatching { fetchAudio(key, model, voice, style, segments[index]) }
            }
        }

        suspend fun load(index: Int): AudioClip? {
            cached(index)?.let { return it }
            val result = pending[index]?.await()
                ?: fetchCatching { fetchAudio(key, model, voice, style, segments[index]) }
            return result
                .onFailure(::recordFailure)
                .getOrNull()
                ?.also { clip ->
                    synchronized(cache) { cache[cacheKeys[index]] = clip }
                    _lastError.value = null
                }
        }

        var startIndex = 0
        val firstCached = cached(0)
        if (firstCached == null && supportsStreaming(model)) {
            // 第二段与首段流式请求并行；一般能在第一段读完前准备好。
            prefetch(1)
            try {
                val clip = fetchAndPlayStreaming(key, model, voice, style, segments[0])
                synchronized(cache) { cache[cacheKeys[0]] = clip }
                _lastError.value = null
                startIndex = 1
            } catch (error: StreamingAudioException) {
                recordFailure(error)
                if (error.playbackStarted) {
                    // 已经播出部分内容时不从头回退，避免把开头重复读一遍。
                    pending.forEach { it?.cancel() }
                    return@coroutineScope
                }
                // 自定义/旧端点若拒绝流式，自动沿用原来的非流式请求。
            }
        }

        for (index in startIndex until segments.size) {
            val clip = load(index)
            if (clip == null) {
                pending.forEach { it?.cancel() }
                // 剩余部分退系统音，不打断学习流；原因留在 lastError 供设置页查看。
                fallback.speak(segments.drop(index).joinToString(" "), index == 0)
                return@coroutineScope
            }
            prefetch(index + 1)
            playPcm(clip)
        }
    }

    private suspend fun fetchCatching(block: suspend () -> AudioClip): Result<AudioClip> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun recordFailure(error: Throwable) {
        _lastError.value = error.message?.take(160) ?: "未知错误"
    }

    /** 按句聚合成 ≤SEGMENT_CHARS 的段。 */
    private fun segment(text: String): List<String> {
        if (text.length <= SEGMENT_CHARS) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in sentences) {
            if (sb.isNotEmpty() && sb.length + sentence.length + 1 > SEGMENT_CHARS) {
                out.add(sb.toString())
                sb.clear()
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(sentence)
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
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

    private suspend fun fetchAudio(
        key: String,
        model: String,
        voice: String,
        style: String,
        text: String,
    ): AudioClip {
        val request = buildRequest(key, model, voice, style, text, streaming = false)
        val call = client.newCall(request)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            call.execute().use { resp ->
                val bodyText = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw responseError(resp.code, bodyText)
                return parseCompleteAudio(bodyText)
            }
        } finally {
            cancellation.dispose()
        }
    }

    /** Gemini 3.1+ TTS 的 SSE 流：拿到第一个 PCM 块就创建 AudioTrack 开播。 */
    private suspend fun fetchAndPlayStreaming(
        key: String,
        model: String,
        voice: String,
        style: String,
        text: String,
    ): AudioClip {
        val request = buildRequest(key, model, voice, style, text, streaming = true)
        val call = client.newCall(request)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        val requestStartedAt = SystemClock.elapsedRealtime()
        val audio = ByteArrayOutputStream()
        var sampleRate = SAMPLE_RATE
        var streamingTrack: AudioTrack? = null
        var playbackStarted = false
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw responseError(resp.code, resp.body?.string().orEmpty())
                }
                val source = resp.body?.source() ?: error("模型没有返回响应体")
                val eventData = StringBuilder()

                suspend fun consumeEvent() {
                    if (eventData.isEmpty()) return
                    val payload = eventData.toString()
                    eventData.clear()
                    if (payload == "[DONE]") return
                    for (chunk in parseStreamAudio(payload)) {
                        currentCoroutineContext().ensureActive()
                        if (streamingTrack == null) {
                            sampleRate = chunk.sampleRate
                            streamingTrack = startTrack(sampleRate)
                            playbackStarted = true
                            Log.d(TAG, "Gemini TTS first audio: ${SystemClock.elapsedRealtime() - requestStartedAt} ms")
                        } else if (chunk.sampleRate != sampleRate) {
                            error("流式音频采样率发生变化: $sampleRate → ${chunk.sampleRate}")
                        }
                        audio.write(chunk.pcm)
                        writePcm(streamingTrack!!, chunk.pcm)
                    }
                }

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> consumeEvent()
                        line.startsWith("data:") -> {
                            if (eventData.isNotEmpty()) eventData.append('\n')
                            eventData.append(line.removePrefix("data:").trimStart())
                        }
                    }
                }
                consumeEvent()
            }
            if (audio.size() == 0) error("模型没有返回流式音频")
            streamingTrack?.let { awaitPlaybackComplete(it, audio.size()) }
            return AudioClip(audio.toByteArray(), sampleRate)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw StreamingAudioException(
                message = error.message ?: "流式语音失败",
                cause = error,
                playbackStarted = playbackStarted,
            )
        } finally {
            cancellation.dispose()
            streamingTrack?.let(::closeTrack)
        }
    }

    private fun buildRequest(
        key: String,
        model: String,
        voice: String,
        style: String,
        text: String,
        streaming: Boolean,
    ): Request {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", speechPrompt(style, text)) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                putJsonObject("speechConfig") {
                    // 明确锁定英国英语。口音细节仍由导演说明控制, voice 只负责声线。
                    put("languageCode", "en-GB")
                    putJsonObject("voiceConfig") {
                        putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) }
                    }
                }
            }
        }
        val method = if (streaming) "streamGenerateContent?alt=sse" else "generateContent"
        return Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:$method")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun parseCompleteAudio(bodyText: String): AudioClip {
        val chunks = audioChunks(json.parseToJsonElement(bodyText).jsonObject)
        if (chunks.isEmpty()) error("模型没有返回音频 (文本可能过长被截断)")
        val sampleRate = chunks.first().sampleRate
        if (chunks.any { it.sampleRate != sampleRate }) error("音频采样率不一致")
        val pcm = ByteArray(chunks.sumOf { it.pcm.size }).also { out ->
            var pos = 0
            chunks.forEach { chunk ->
                chunk.pcm.copyInto(out, pos)
                pos += chunk.pcm.size
            }
        }
        return AudioClip(pcm, sampleRate)
    }

    private fun parseStreamAudio(payload: String): List<AudioChunk> =
        audioChunks(json.parseToJsonElement(payload).jsonObject)

    private fun audioChunks(root: kotlinx.serialization.json.JsonObject): List<AudioChunk> {
        val parts = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: return emptyList()
        return parts.mapNotNull { part ->
            val inline = part.jsonObject["inlineData"]?.jsonObject ?: return@mapNotNull null
            val encoded = inline["data"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val mime = inline["mimeType"]?.jsonPrimitive?.content.orEmpty()
            val sampleRate = Regex("rate=(\\d+)").find(mime)
                ?.groupValues?.get(1)?.toIntOrNull() ?: SAMPLE_RATE
            AudioChunk(Base64.decode(encoded, Base64.DEFAULT), sampleRate)
        }
    }

    private fun responseError(code: Int, bodyText: String): IllegalStateException {
        val message = runCatching {
            json.parseToJsonElement(bodyText).jsonObject["error"]
                ?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return IllegalStateException("HTTP $code${message?.let { ": ${it.take(120)}" }.orEmpty()}")
    }

    /**
     * TTS 模型对结构化导演说明的服从度高于一句自然语言前缀。
     * 明确标出 transcript 也避免模型把口音说明本身读出来。
     */
    private fun speechPrompt(style: String, text: String): String = buildString {
        append("Synthesize the transcript below as speech. Follow the director's notes, but never read the notes aloud.\n\n")
        append("### DIRECTOR'S NOTES\n")
        append(style.trim())
        append("\n\n### TRANSCRIPT\n")
        append(text)
    }

    // ─── 播放 ────────────────────────────────────────────────

    private suspend fun playPcm(clip: AudioClip) {
        releaseTrack()
        val pcm = clip.pcm
        val newTrack = startTrack(clip.sampleRate)
        try {
            writePcm(newTrack, pcm)
            awaitPlaybackComplete(newTrack, pcm.size)
        } finally {
            closeTrack(newTrack)
        }
    }

    private fun startTrack(sampleRate: Int): AudioTrack {
        releaseTrack()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val newTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBuf, 8192),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track = newTrack
        runCatching { newTrack.playbackParams = PlaybackParams().setSpeed(rate) }
        newTrack.play()
        return newTrack
    }

    private suspend fun writePcm(target: AudioTrack, pcm: ByteArray) {
        var offset = 0
        while (offset < pcm.size) {
            currentCoroutineContext().ensureActive()
            val written = target.write(pcm, offset, minOf(AUDIO_WRITE_CHUNK_BYTES, pcm.size - offset))
            if (written <= 0) error("AudioTrack 写入失败: $written")
            offset += written
        }
    }

    /** AudioTrack.write 只保证写入缓冲区；等播放头追上，避免段尾被 stop 截掉。 */
    private suspend fun awaitPlaybackComplete(target: AudioTrack, pcmBytes: Int) {
        val targetFrames = pcmBytes / PCM_BYTES_PER_FRAME
        while (true) {
            currentCoroutineContext().ensureActive()
            val playedFrames = runCatching { target.playbackHeadPosition.toLong() }.getOrElse { return }
            if (playedFrames >= targetFrames) return
            delay(PLAYBACK_DRAIN_POLL_MS)
        }
    }

    private fun closeTrack(target: AudioTrack) {
        if (track === target) track = null
        try {
            target.stop()
        } catch (_: Throwable) {
        }
        runCatching { target.release() }
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
        private const val TAG = "GeminiTtsProvider"
        private const val SAMPLE_RATE = 24_000
        private const val MAX_CHARS = 1_500
        private const val SEGMENT_CHARS = 220
        private const val AUDIO_WRITE_CHUNK_BYTES = 8_192
        private const val PCM_BYTES_PER_FRAME = 2
        private const val PLAYBACK_DRAIN_POLL_MS = 20L

        private val STREAMING_MODEL = Regex("(?:^|-)gemini-(?:[4-9]|3\\.(?:[1-9]|\\d{2,}))", RegexOption.IGNORE_CASE)

        /** 官方流式 TTS 从 Gemini 3.1 起支持；未知自定义模型保守走原来的端点。 */
        internal fun supportsStreaming(model: String): Boolean = STREAMING_MODEL.containsMatchIn(model)

        /** 默认口音/风格导演说明; voice 只换声线, 不参与口音选择。 */
        const val DEFAULT_STYLE =
            "Accent: Neutral, contemporary Received Pronunciation (RP), the non-regional standard " +
                "British pronunciation used by a trained national newsreader and pronunciation teacher. " +
                "Use a non-rhotic accent and clearly articulated consonants.\n" +
                "Avoid: Any London regional colouring, including Cockney, Estuary English or Multicultural " +
                "London English. Do not use glottal-T, TH-fronting or L-vocalisation.\n" +
                "Delivery: Warm, natural and conversational, with lively but controlled intonation and a " +
                "natural everyday pace. Do not sound aristocratic, theatrical or exaggerated."
    }

    private data class AudioChunk(val pcm: ByteArray, val sampleRate: Int)

    private class StreamingAudioException(
        message: String,
        cause: Throwable,
        val playbackStarted: Boolean,
    ) : RuntimeException(message, cause)
}
