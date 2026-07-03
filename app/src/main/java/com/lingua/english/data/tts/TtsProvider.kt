package com.lingua.english.data.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * 发音引擎抽象。v1 只有系统 TTS 实现; 将来想升级音质 (API TTS / 端侧模型)
 * 只换实现, UI 和调用方不动。(和 Vita 的 AiInsightProvider 同思路)
 */
interface TtsProvider {
    val status: StateFlow<TtsStatus>

    /** 用英音朗读; flush = 打断当前朗读。 */
    fun speak(text: String, flush: Boolean = true)

    fun stop()

    /** 语速 0.5..1.2, 1.0 为正常。 */
    fun setRate(rate: Float)

    fun release()
}

sealed interface TtsStatus {
    data object Initialising : TtsStatus
    /** 就绪; britishVoice=false 表示设备没有英音语音包, 用了默认英语。 */
    data class Ready(val britishVoice: Boolean, val voiceName: String?) : TtsStatus
    data class Unavailable(val reason: String) : TtsStatus
}
