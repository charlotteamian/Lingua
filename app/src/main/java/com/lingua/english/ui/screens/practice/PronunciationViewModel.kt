package com.lingua.english.ui.screens.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.MaterialEntity
import com.lingua.english.data.practice.PronIssue
import com.lingua.english.data.practice.PronunciationCoach
import com.lingua.english.data.repository.MaterialRepository
import com.lingua.english.data.tts.TtsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PronunciationUiState(
    val recording: Boolean = false,
    val assessing: Boolean = false,
    val assessedOnce: Boolean = false,
    val overall: String? = null,
    val issues: List<PronIssue> = emptyList(),
    val playingOwn: Boolean = false,
    val error: String? = null,
)

class PronunciationViewModel(
    materialRepo: MaterialRepository,
    private val coach: PronunciationCoach,
    private val tts: TtsProvider,
    materialId: String,
) : ViewModel() {

    val material: StateFlow<MaterialEntity?> = materialRepo.material(materialId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(PronunciationUiState())
    val state: StateFlow<PronunciationUiState> = _state

    /** 开始/停止朗读。停止后自动送 AI 评估, 新结果整体替换旧标记 (读对了标记自然消失)。 */
    fun toggleRecording(targetText: String) {
        if (_state.value.assessing) return
        if (_state.value.recording) {
            coach.stopRecording()
            _state.value = _state.value.copy(recording = false, assessing = true, error = null)
            viewModelScope.launch {
                runCatching { coach.assess(targetText) }
                    .onSuccess { result ->
                        _state.value = _state.value.copy(
                            assessing = false,
                            assessedOnce = true,
                            overall = result.overall.takeIf { it.isNotBlank() },
                            issues = result.words,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            assessing = false,
                            error = e.message ?: "评估失败, 再试一次",
                        )
                    }
            }
        } else {
            tts.stop()
            coach.stopPlayback()
            runCatching { coach.startRecording() }
                .onSuccess { _state.value = _state.value.copy(recording = true, error = null, playingOwn = false) }
                .onFailure { _state.value = _state.value.copy(error = "启动录音失败, 检查麦克风权限") }
        }
    }

    /** 回放自己的录音。 */
    fun playMyRecording() {
        if (_state.value.recording) return
        tts.stop()
        _state.value = _state.value.copy(playingOwn = true)
        coach.playRecording {
            _state.value = _state.value.copy(playingOwn = false)
        }
    }

    fun speakWord(word: String) {
        coach.stopPlayback()
        tts.speak(word)
    }

    fun speakAll(text: String) {
        coach.stopPlayback()
        tts.speak(text)
    }

    override fun onCleared() {
        coach.stopRecording()
        coach.stopPlayback()
    }
}
