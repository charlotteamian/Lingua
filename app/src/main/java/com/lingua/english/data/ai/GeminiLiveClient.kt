package com.lingua.english.data.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
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
 *
 * 外放回声消除 (公放时 Poppy 的声音别被当成用户说话打断自己): 通话期间把整机切成
 * VoIP 通信模式 (MODE_IN_COMMUNICATION) + 播放走 USAGE_VOICE_COMMUNICATION + 显式选内置
 * 扬声器路由——这样系统回声消除器 (来自 VOICE_COMMUNICATION 录音源) 才有「扬声器正在放
 * 什么」的参考信号去抵消; 再显式挂上 AEC/降噪/AGC 效果器兜底。挂断时恢复原音频模式。
 */
class GeminiLiveClient(
    private val context: Context,
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

    // 通话期间的 VoIP 音频路由 (挂断恢复) + 录音链上的回声消除/降噪/增益效果器
    private var audioManager: AudioManager? = null
    private var prevAudioMode = AudioManager.MODE_NORMAL
    private var prevSpeakerOn = false
    private var aec: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    private val userAcc = StringBuilder()
    private val modelAcc = StringBuilder()

    /** 开始通话。onExchange 在每轮结束时回调 (用户转写, Poppy 转写) 供落库。 */
    fun start(system: String, onExchange: (String, String) -> Unit) {
        if (_state.value.phase != LivePhase.IDLE) return
        this.onExchange = onExchange
        _state.value = LiveUiState(phase = LivePhase.CONNECTING)
        // 麦克风型前台服务必须趁 Activity 仍可见时启动。ChatScreen 的用户点击
        // 路径已保证 RECORD_AUDIO 授权；配置检查失败时 fail() 会立即停止服务。
        runCatching { LiveCallForegroundService.start(context) }
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
                        // 学英语时句中会停下来想词：降低结束敏感度，并连续静默 2 秒后才算说完。
                        putJsonObject("realtimeInputConfig") {
                            putJsonObject("automaticActivityDetection") {
                                put("disabled", false)
                                put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                                put("silenceDurationMs", LEARNER_SILENCE_MS)
                            }
                        }
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
        // 先把整机切进 VoIP 通信模式 + 外放, 回声消除才有扬声器输出作参考
        enterCommunicationRoute()

        // 播放: 24kHz PCM16 单声道流式。走 VOICE_COMMUNICATION 而非 MEDIA——回声消除只抵消
        // 通信路径上放出去的声音, 走 MEDIA 的话公放时 Poppy 的声音会漏回麦克风被当成插话。
        val outMin = AudioTrack.getMinBufferSize(
            OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
        // 显式挂回声消除/降噪/增益: VOICE_COMMUNICATION 通常自带, 但部分机型要手动开才生效
        attachAudioEffects(record.audioSessionId)
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

    /** 通话开始: 切 VoIP 通信模式; 无耳机时强制外放 (让回声消除拿到扬声器输出作参考)。 */
    private fun enterCommunicationRoute() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        prevAudioMode = am.mode
        @Suppress("DEPRECATION")
        prevSpeakerOn = am.isSpeakerphoneOn
        runCatching { am.mode = AudioManager.MODE_IN_COMMUNICATION }
        // 接了耳机 (有线/蓝牙) 就别抢外放——耳机本就没回声问题, 强开公放反而吵到用户
        if (hasHeadset(am)) return
        // 免提: 通信模式默认可能走听筒, 显式选内置扬声器保持公放, 回声消除也以它为参考
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { am.setCommunicationDevice(it) }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.isSpeakerphoneOn = true }
        }
    }

    private fun hasHeadset(am: AudioManager): Boolean = runCatching {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    }.getOrDefault(false)

    /** 挂断: 还原音频模式与外放开关, 别影响后续系统 TTS / 其它 app。 */
    private fun restoreCommunicationRoute() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { am.clearCommunicationDevice() }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.isSpeakerphoneOn = prevSpeakerOn }
        }
        runCatching { am.mode = prevAudioMode }
        audioManager = null
    }

    private fun attachAudioEffects(sessionId: Int) {
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
        }
        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        }
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
        }
    }

    private fun releaseAudioEffects() {
        runCatching { aec?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { agc?.release() }
        aec = null
        noiseSuppressor = null
        agc = null
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
        releaseAudioEffects()
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        playbackQueue?.close()
        playbackQueue = null
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        restoreCommunicationRoute()
        _state.value = LiveUiState(error = error)
        LiveCallForegroundService.stop(context)
    }

    companion object {
        /** Live 模型出新版在设置页改, 这里只是空值兜底。 */
        const val DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"
        private const val WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val IN_RATE = 16_000
        private const val OUT_RATE = 24_000
        private const val LEARNER_SILENCE_MS = 2_000
    }
}
