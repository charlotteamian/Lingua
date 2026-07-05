package com.lingua.english.data.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import com.lingua.english.data.prefs.SettingsPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

enum class LivePhase { IDLE, CONNECTING, ACTIVE }

data class LiveUiState(
    val phase: LivePhase = LivePhase.IDLE,
    /** Poppy 出声中 (可直接插话打断)。 */
    val speaking: Boolean = false,
    val userPartial: String = "",
    val modelPartial: String = "",
    val error: String? = null,
)

/**
 * Gemini Live API (BidiGenerateContent over WebSocket): 端到端实时语音对话。
 * 麦克风 16kHz PCM 直传, 模型 24kHz PCM 直出——不走 STT/TTS 拼接 (官方不推荐且延迟高)。
 * 双方转写由服务端顺带返回 (input/outputAudioTranscription), 每轮结束回调落库。
 * 录音源用 VOICE_COMMUNICATION: 自带回声消除, 外放时不会把 Poppy 的声音又录进去。
 */
class GeminiLiveClient(
    private val prefs: SettingsPreferences,
    private val scope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket 长连接不设读超时
        .build()

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state

    private var ws: WebSocket? = null
    private var recordJob: Job? = null
    private var playJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var playbackQueue: Channel<ByteArray>? = null
    private var onExchange: ((String, String) -> Unit)? = null

    private val userAcc = StringBuilder()
    private val modelAcc = StringBuilder()

    /** 开始通话。onExchange 在每轮结束时回调 (用户转写, Poppy 转写) 供落库。 */
    fun start(system: String, onExchange: (String, String) -> Unit) {
        if (_state.value.phase != LivePhase.IDLE) return
        this.onExchange = onExchange
        _state.value = LiveUiState(phase = LivePhase.CONNECTING)
        scope.launch {
            val preset = AiApiPresets.byId(prefs.aiApiPreset.first())
            if (preset.id != "gemini") {
                fail("实时语音要用 Gemini, 请在设置里把 AI 对话服务切到 Gemini。")
                return@launch
            }
            val key = prefs.aiApiKey.first().trim()
            if (key.isEmpty()) {
                fail("请先在设置页填写 API Key。")
                return@launch
            }
            val model = prefs.liveModel.first().trim().ifEmpty { DEFAULT_LIVE_MODEL }
            val voice = prefs.ttsVoice.first()
            connect(key, model, voice, system)
        }
    }

    fun stop() = stopInternal(null)

    private fun connect(key: String, model: String, voice: String, system: String) {
        val request = Request.Builder()
            .url("$WS_ENDPOINT?key=$key")
            .build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setup = buildJsonObject {
                    putJsonObject("setup") {
                        put("model", "models/$model")
                        putJsonObject("generationConfig") {
                            putJsonArray("responseModalities") { add(JsonPrimitive("AUDIO")) }
                            putJsonObject("speechConfig") {
                                putJsonObject("voiceConfig") {
                                    putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) }
                                }
                            }
                        }
                        putJsonObject("systemInstruction") {
                            putJsonArray("parts") { addJsonObject { put("text", system) } }
                        }
                        putJsonObject("inputAudioTranscription") {}
                        putJsonObject("outputAudioTranscription") {}
                    }
                }
                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val detail = runCatching { response?.body?.string()?.take(160) }.getOrNull()
                    ?: t.message ?: "连接失败"
                fail("通话断了: $detail")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (_state.value.phase != LivePhase.IDLE) stopInternal(null)
            }
        })
    }

    private fun handleMessage(text: String) {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when {
            root.containsKey("setupComplete") -> {
                _state.value = _state.value.copy(phase = LivePhase.ACTIVE)
                startAudioIo()
            }

            root.containsKey("serverContent") -> {
                val sc = root["serverContent"]?.jsonObject ?: return
                sc["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let {
                    userAcc.append(it)
                    _state.value = _state.value.copy(userPartial = userAcc.toString().takeLast(80))
                }
                sc["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let {
                    modelAcc.append(it)
                    _state.value = _state.value.copy(modelPartial = modelAcc.toString().takeLast(80))
                }
                if (sc["interrupted"]?.jsonPrimitive?.booleanOrNull == true) flushPlayback()
                sc["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                    part.jsonObject["inlineData"]?.jsonObject?.get("data")
                        ?.jsonPrimitive?.contentOrNull?.let { b64 ->
                            runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()?.let { pcm ->
                                _state.value = _state.value.copy(speaking = true)
                                playbackQueue?.trySend(pcm)
                            }
                        }
                }
                if (sc["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) {
                    commitExchange()
                    _state.value = _state.value.copy(
                        speaking = false, userPartial = "", modelPartial = "",
                    )
                }
            }

            root.containsKey("goAway") -> fail("服务端结束了本次通话 (时长上限), 重新拨打即可")
        }
    }

    /** 每轮 (或挂断时的半轮) 转写交给调用方落库。 */
    private fun commitExchange() {
        val user = userAcc.toString().trim()
        val model = modelAcc.toString().trim()
        userAcc.clear()
        modelAcc.clear()
        if (user.isNotEmpty() || model.isNotEmpty()) onExchange?.invoke(user, model)
    }

    @SuppressLint("MissingPermission") // UI 层拿到 RECORD_AUDIO 后才会 start
    private fun startAudioIo() {
        // 播放: 24kHz PCM16 单声道流式
        val outMin = AudioTrack.getMinBufferSize(
            OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(OUT_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            outMin * 4,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        audioTrack = track
        val queue = Channel<ByteArray>(Channel.UNLIMITED)
        playbackQueue = queue
        runCatching { track.play() }
        playJob = scope.launch(Dispatchers.IO) {
            for (chunk in queue) {
                runCatching { track.write(chunk, 0, chunk.size) }
            }
        }

        // 录音: 16kHz PCM16 单声道, 100ms 一片直传
        val inMin = AudioRecord.getMinBufferSize(
            IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(inMin, 6_400),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            fail("麦克风初始化失败, 检查权限或是否被其它 app 占用")
            return
        }
        audioRecord = record
        recordJob = scope.launch(Dispatchers.IO) {
            runCatching {
                record.startRecording()
                val buf = ByteArray(3_200) // 100ms @16kHz/16bit
                while (_state.value.phase == LivePhase.ACTIVE) {
                    val n = record.read(buf, 0, buf.size)
                    if (n > 0) {
                        val payload = buildJsonObject {
                            putJsonObject("realtimeInput") {
                                putJsonObject("audio") {
                                    put("mimeType", "audio/pcm;rate=$IN_RATE")
                                    put("data", Base64.encodeToString(buf.copyOf(n), Base64.NO_WRAP))
                                }
                            }
                        }
                        ws?.send(payload.toString())
                    }
                }
            }.onFailure { fail("麦克风读取失败: ${it.message ?: "未知"}") }
        }
    }

    /** 用户插话: 丢掉没播完的音频, 立即安静。 */
    private fun flushPlayback() {
        playbackQueue?.let { queue ->
            while (queue.tryReceive().isSuccess) {
                // 清空积压
            }
        }
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        }
        _state.value = _state.value.copy(speaking = false)
    }

    private fun fail(message: String) = stopInternal(message)

    private fun stopInternal(error: String?) {
        commitExchange() // 挂断时的半轮对话也落库
        runCatching { ws?.close(1000, null) }
        ws = null
        recordJob?.cancel()
        recordJob = null
        playJob?.cancel()
        playJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        playbackQueue?.close()
        playbackQueue = null
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        _state.value = LiveUiState(error = error)
    }

    companion object {
        /** Live 模型出新版在设置页改, 这里只是空值兜底。 */
        const val DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"
        private const val WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val IN_RATE = 16_000
        private const val OUT_RATE = 24_000
    }
}
