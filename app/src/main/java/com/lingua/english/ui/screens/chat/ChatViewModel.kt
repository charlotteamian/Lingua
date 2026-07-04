package com.lingua.english.ui.screens.chat

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.prefs.SettingsPreferences
import com.lingua.english.data.repository.ChatRepository
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.data.speech.VoiceRecorder
import com.lingua.english.data.tts.TtsProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatSendState(
    val sending: Boolean = false,
    val error: String? = null,
    val pendingNewTopic: Boolean = false,
)

data class VoiceRecordState(
    val recording: Boolean = false,
    val seconds: Int = 0,
)

class ChatViewModel(
    private val chatRepo: ChatRepository,
    private val reviewRepo: ReviewRepository,
    private val tts: TtsProvider,
    private val prefs: SettingsPreferences,
    private val recorder: VoiceRecorder,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessageEntity>> = chatRepo.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 通话模式: 隐藏 Poppy 回复文字, 点按显示。只在自动朗读开着时生效, 避免「文字藏了又没声音」。 */
    val hideChatText: StateFlow<Boolean> =
        combine(prefs.hideChatText, prefs.autoReadReplies) { hide, autoRead -> hide && autoRead }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _sendState = MutableStateFlow(ChatSendState())
    val sendState: StateFlow<ChatSendState> = _sendState

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sendState.value.sending) return
        val newTopic = _sendState.value.pendingNewTopic
        _sendState.value = _sendState.value.copy(sending = true, error = null)
        viewModelScope.launch {
            runCatching { chatRepo.send(trimmed, newTopic) }
                .onSuccess { reply ->
                    _sendState.value = ChatSendState()
                    if (prefs.autoReadReplies.first()) {
                        tts.speak(stripMarkup(reply))
                    }
                }
                .onFailure { e ->
                    _sendState.value = ChatSendState(error = e.message ?: "发送失败, 重试一次试试")
                }
        }
    }

    /** 下一条消息作为新话题 (清空模型上下文, 历史仍保留展示)。 */
    fun markNewTopic() {
        _sendState.value = _sendState.value.copy(pendingNewTopic = true)
    }

    fun speak(text: String) = tts.speak(stripMarkup(text))

    /** 把助手回复里的 ▸ chunk 行收进复习。 */
    fun saveChunk(text: String, zh: String) {
        viewModelScope.launch {
            reviewRepo.addStandalone(text, zh, "来自对话", ChunkEntity.KIND_CHUNK)
        }
    }

    /** 本次会话里已收藏的句子 (底部卡显示 ✓)。 */
    private val _collectedSentences = MutableStateFlow<Set<String>>(emptySet())
    val collectedSentences: StateFlow<Set<String>> = _collectedSentences

    /** 收藏一句进复习 (出中文说英文)。翻译在后台跑, 不阻塞。 */
    fun collectSentence(sentence: String) {
        _collectedSentences.value += sentence
        viewModelScope.launch {
            runCatching { reviewRepo.addSentence(sentence) }
        }
    }

    // ─── 语音消息 ────────────────────────────────────────────

    private val _voice = MutableStateFlow(VoiceRecordState())
    val voice: StateFlow<VoiceRecordState> = _voice

    /** 正在播放的用户语音文件路径 (气泡显示播放态)。 */
    private val _playingVoice = MutableStateFlow<String?>(null)
    val playingVoice: StateFlow<String?> = _playingVoice

    private var ticker: Job? = null
    private var player: MediaPlayer? = null

    fun startVoice() {
        if (_voice.value.recording || _sendState.value.sending) return
        tts.stop()
        stopVoicePlayback()
        runCatching { recorder.start() }
            .onSuccess {
                _voice.value = VoiceRecordState(recording = true)
                ticker = viewModelScope.launch {
                    while (_voice.value.recording) {
                        delay(1_000)
                        val seconds = _voice.value.seconds + 1
                        _voice.value = _voice.value.copy(seconds = seconds)
                        if (seconds >= MAX_VOICE_SECONDS) {
                            stopAndSendVoice()
                            break
                        }
                    }
                }
            }
            .onFailure {
                _sendState.value = _sendState.value.copy(error = "启动录音失败, 检查麦克风权限")
            }
    }

    fun cancelVoice() {
        ticker?.cancel()
        recorder.cancel()
        _voice.value = VoiceRecordState()
    }

    fun stopAndSendVoice() {
        if (!_voice.value.recording) return
        ticker?.cancel()
        _voice.value = VoiceRecordState()
        val file = recorder.stop()
        if (file == null) {
            _sendState.value = _sendState.value.copy(error = "没录到声音, 再试一次")
            return
        }
        val newTopic = _sendState.value.pendingNewTopic
        _sendState.value = _sendState.value.copy(sending = true, error = null)
        viewModelScope.launch {
            runCatching { chatRepo.sendVoice(file, newTopic) }
                .onSuccess { reply ->
                    _sendState.value = ChatSendState()
                    if (prefs.autoReadReplies.first()) {
                        tts.speak(stripMarkup(reply))
                    }
                }
                .onFailure { e ->
                    _sendState.value = ChatSendState(error = e.message ?: "发送失败, 重试一次试试")
                }
        }
    }

    /** 点自己的语音气泡: 播放/停止切换。 */
    fun toggleVoicePlayback(path: String) {
        if (_playingVoice.value == path) {
            stopVoicePlayback()
            return
        }
        stopVoicePlayback()
        tts.stop()
        val p = MediaPlayer()
        player = p
        runCatching {
            p.setDataSource(path)
            p.setOnCompletionListener { stopVoicePlayback() }
            p.prepare()
            p.start()
            _playingVoice.value = path
        }.onFailure { stopVoicePlayback() }
    }

    private fun stopVoicePlayback() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        _playingVoice.value = null
    }

    override fun onCleared() {
        ticker?.cancel()
        recorder.cancel()
        stopVoicePlayback()
    }

    companion object {
        private const val MAX_VOICE_SECONDS = 90
    }

    /** 朗读时去掉 ✎/▸ 标记行的符号部分, 读起来自然。 */
    private fun stripMarkup(text: String): String =
        text.lines().joinToString(" ") { line ->
            line.removePrefix("✎ better:").substringBefore("▸ chunk:").trim()
        }.trim()
}
