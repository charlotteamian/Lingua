package com.lingua.english.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.repository.ChatRepository
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.data.tts.TtsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatSendState(
    val sending: Boolean = false,
    val error: String? = null,
    val pendingNewTopic: Boolean = false,
)

class ChatViewModel(
    private val chatRepo: ChatRepository,
    private val reviewRepo: ReviewRepository,
    private val tts: TtsProvider,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessageEntity>> = chatRepo.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sendState = MutableStateFlow(ChatSendState())
    val sendState: StateFlow<ChatSendState> = _sendState

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sendState.value.sending) return
        val newTopic = _sendState.value.pendingNewTopic
        _sendState.value = _sendState.value.copy(sending = true, error = null)
        viewModelScope.launch {
            runCatching { chatRepo.send(trimmed, newTopic) }
                .onSuccess {
                    _sendState.value = ChatSendState()
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

    /** 朗读时去掉 ✎/▸ 标记行的符号部分, 读起来自然。 */
    private fun stripMarkup(text: String): String =
        text.lines().joinToString(" ") { line ->
            line.removePrefix("✎ better:").substringBefore("▸ chunk:").trim()
        }.trim()
}
