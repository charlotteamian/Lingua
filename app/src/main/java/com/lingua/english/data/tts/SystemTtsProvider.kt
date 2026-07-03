package com.lingua.english.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 系统 TTS + en-GB。免费离线; Google TTS 的英音质量可用。
 * 设备没装英音语音包时退回默认英语并在 status 里标记, 设置页提示用户去装。
 */
class SystemTtsProvider(context: Context) : TtsProvider {

    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Initialising)
    override val status: StateFlow<TtsStatus> = _status

    private var rate: Float = 0.9f
    private var utteranceSeq = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { result ->
        if (result != TextToSpeech.SUCCESS) {
            _status.value = TtsStatus.Unavailable("设备上没有可用的语音引擎")
            return@TextToSpeech
        }
        applyBritishVoice()
    }

    private fun applyBritishVoice() {
        val langResult = runCatching { tts.setLanguage(Locale.UK) }.getOrDefault(TextToSpeech.LANG_MISSING_DATA)
        val british = langResult != TextToSpeech.LANG_MISSING_DATA &&
            langResult != TextToSpeech.LANG_NOT_SUPPORTED
        if (!british) {
            runCatching { tts.setLanguage(Locale.ENGLISH) }
        }
        // 有多个英音 voice 时挑一个非网络依赖的
        val voice = runCatching {
            tts.voices?.filter { it.locale == Locale.UK && !it.isNetworkConnectionRequired }
                ?.minByOrNull { it.name.length }
        }.getOrNull()
        voice?.let { runCatching { tts.setVoice(it) } }
        tts.setSpeechRate(rate)
        _status.value = TtsStatus.Ready(
            britishVoice = british,
            voiceName = voice?.name ?: runCatching { tts.voice?.name }.getOrNull(),
        )
    }

    override fun speak(text: String, flush: Boolean) {
        if (_status.value !is TtsStatus.Ready) return
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching { tts.speak(text, mode, null, "lingua-${utteranceSeq++}") }
    }

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 1.2f)
        runCatching { tts.setSpeechRate(this.rate) }
    }

    override fun release() {
        runCatching { tts.shutdown() }
    }
}
