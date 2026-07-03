package com.lingua.english.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface SpeechInputState {
    data object Idle : SpeechInputState
    data object Listening : SpeechInputState
    data class Error(val message: String) : SpeechInputState
}

/**
 * 语音输入 (系统 SpeechRecognizer, 不引第三方 SDK)。
 * 对话页用 en-GB 听英文, 随手记用 zh-CN 听中文。
 * 引擎在说话停顿后自动结束一段并回调 onFinal, 想继续说再点一次麦克风。
 * 设备缺识别服务 (无 Google app 的国产 ROM 可能没有) 时给友好提示, 不崩。
 */
class SpeechInputController(private val context: Context) {

    private val _state = MutableStateFlow<SpeechInputState>(SpeechInputState.Idle)
    val state: StateFlow<SpeechInputState> = _state

    /** 由 UI 每次重组时赋值, 回调都在主线程。 */
    var onPartial: (String) -> Unit = {}
    var onFinal: (String) -> Unit = {}

    private var recognizer: SpeechRecognizer? = null

    fun toggle(languageTag: String) {
        if (_state.value is SpeechInputState.Listening) stop() else start(languageTag)
    }

    private fun start(languageTag: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechInputState.Error("此设备没有可用的语音识别服务 (通常由 Google app 提供)")
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        _state.value = SpeechInputState.Listening
        runCatching { r.startListening(intent) }.onFailure {
            _state.value = SpeechInputState.Error("启动语音识别失败, 再试一次")
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = SpeechInputState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            firstResult(results)?.let(onFinal)
            _state.value = SpeechInputState.Idle
        }

        override fun onError(error: Int) {
            _state.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> SpeechInputState.Error("没听清, 再试一次")

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                -> SpeechInputState.Error("语音识别网络出错")

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    SpeechInputState.Error("没有麦克风权限")

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    SpeechInputState.Error("识别器忙, 稍等再试")

                else -> SpeechInputState.Error("语音识别出错 (code $error)")
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
}
