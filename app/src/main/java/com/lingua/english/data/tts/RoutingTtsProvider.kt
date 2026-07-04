package com.lingua.english.data.tts

import com.lingua.english.data.prefs.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * 按「发音引擎」偏好路由到系统 TTS 或 Gemini 云端语音, 切换即时生效,
 * 调用方 (词典卡/对话/复习/语料) 零感知。(和 Vita 的 ModeRoutingInsightProvider 同思路)
 */
class RoutingTtsProvider(
    scope: CoroutineScope,
    prefs: SettingsPreferences,
    private val system: TtsProvider,
    private val gemini: TtsProvider,
) : TtsProvider {

    @Volatile
    private var useGemini = false

    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Initialising)
    override val status: StateFlow<TtsStatus> = _status

    init {
        scope.launch {
            prefs.ttsEngine.collect { useGemini = it == "gemini" }
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            prefs.ttsEngine
                .flatMapLatest { engine -> if (engine == "gemini") gemini.status else system.status }
                .collect { _status.value = it }
        }
    }

    private val active: TtsProvider get() = if (useGemini) gemini else system

    override fun speak(text: String, flush: Boolean) = active.speak(text, flush)

    override fun stop() {
        system.stop()
        gemini.stop()
    }

    override fun setRate(rate: Float) {
        system.setRate(rate)
        gemini.setRate(rate)
    }

    override fun release() {
        system.release()
        gemini.release()
    }
}
