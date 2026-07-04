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
import kotlinx.coroutines.flow.map
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

/** 往期话题摘要 (对话页只显示当前话题, 旧话题归档到「往期」)。 */
data class ChatSessionSummary(
    val anchorId: String,
    val startEpochMs: Long,
    val count: Int,
    val preview: String,
)

/** 按「新话题」分界把全部消息切成话题段, 最后一段 = 当前话题。 */
internal fun sessionsOf(all: List<ChatMessageEntity>): List<List<ChatMessageEntity>> {
    if (all.isEmpty()) return emptyList()
    val sessions = mutableListOf(mutableListOf<ChatMessageEntity>())
    all.forEach { message ->
        if (message.contextBreak && sessions.last().isNotEmpty()) {
            sessions.add(mutableListOf())
        }
        sessions.last().add(message)
    }
    return sessions
}

class ChatViewModel(
    private val chatRepo: ChatRepository,
    private val reviewRepo: ReviewRepository,
    private val tts: TtsProvider,
    private val prefs: SettingsPreferences,
    private val recorder: VoiceRecorder,
) : ViewModel() {

    /** 当前话题的消息 (最后一个「新话题」分界之后)。对话页只显示这些。 */
    val currentMessages: StateFlow<List<ChatMessageEntity>> = chatRepo.messages
        .map { all -> sessionsOf(all).lastOrNull() ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 往期话题摘要 (新→旧), 不含当前话题。 */
    val pastSessions: StateFlow<List<ChatSessionSummary>> = chatRepo.messages
        .map { all ->
            sessionsOf(all).dropLast(1).map { session ->
                ChatSessionSummary(
                    anchorId = session.first().id,
                    startEpochMs = session.first().createdAtEpochMs,
                    count = session.size,
                    preview = session.firstOrNull { it.content.isNotBlank() }?.content?.take(60)
                        ?: "(语音消息)",
                )
            }.reversed()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 某个往期话题的完整消息 (anchorId = 该话题第一条消息)。 */
    fun sessionMessages(anchorId: String): StateFlow<List<ChatMessageEntity>> =
        chatRepo.messages
            .map { all -> sessionsOf(all).firstOrNull { it.first().id == anchorId } ?: emptyList() }
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

    /** 已收藏文本 (归一化小写, DB 驱动)——句子/词块在任何入口重开都显示已收。 */
    val savedTexts: StateFlow<Set<String>> = reviewRepo.savedTexts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 收藏一句进复习 (出中文说英文)。先落库立刻见 ✓, 翻译后台补。 */
    fun collectSentence(sentence: String) {
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

    /**
     * 朗读预处理: ✎ 纠正行改成自然人的口吻 ("By the way, a more natural way…"),
     * ▸ 词块行不读 (是给眼睛看的)。
     */
    private fun stripMarkup(text: String): String =
        text.lines().mapNotNull { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> null
                line.startsWith("▸") -> null
                line.startsWith("✎") -> {
                    val body = line.removePrefix("✎").trim().let { it.substringAfter(":", it) }.trim()
                    body.takeIf { it.isNotEmpty() }
                        ?.let { "By the way, a more natural way to say that is: $it" }
                }
                else -> line
            }
        }.joinToString(" ")
}
