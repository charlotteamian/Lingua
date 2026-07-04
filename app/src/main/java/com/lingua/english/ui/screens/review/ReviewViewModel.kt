package com.lingua.english.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.practice.PronIssue
import com.lingua.english.data.practice.PronunciationCoach
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.data.tts.TtsProvider
import com.lingua.english.domain.ReviewGrade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewSessionState(
    val showAnswer: Boolean = false,
    val doneThisSession: Int = 0,
)

/** 句子卡「先说英文再对比」的状态。 */
data class SpeakCompareState(
    val recording: Boolean = false,
    val assessing: Boolean = false,
    val done: Boolean = false,
    val overall: String? = null,
    val issues: List<PronIssue> = emptyList(),
    val error: String? = null,
)

class ReviewViewModel(
    private val reviewRepo: ReviewRepository,
    private val tts: TtsProvider,
    private val coach: PronunciationCoach,
) : ViewModel() {

    /** 到期队列是响应式的: 评完一张自动缩短, 永远显示队首。 */
    val dueCards: StateFlow<List<ChunkEntity>> = reviewRepo.dueCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allChunks: StateFlow<List<ChunkEntity>> = reviewRepo.allChunks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _session = MutableStateFlow(ReviewSessionState())
    val session: StateFlow<ReviewSessionState> = _session

    fun showAnswer(card: ChunkEntity) {
        _session.value = _session.value.copy(showAnswer = true)
        tts.speak(card.text)
    }

    fun grade(card: ChunkEntity, grade: ReviewGrade) {
        viewModelScope.launch {
            reviewRepo.grade(card, grade)
            _session.value = ReviewSessionState(doneThisSession = _session.value.doneThisSession + 1)
            _speak.value = SpeakCompareState()
        }
    }

    fun speak(text: String) = tts.speak(text)

    fun delete(chunkId: String) {
        viewModelScope.launch { reviewRepo.delete(chunkId) }
    }

    // ─── 句子卡: 先说英文再对比 ──────────────────────────────

    private val _speak = MutableStateFlow(SpeakCompareState())
    val speakCompare: StateFlow<SpeakCompareState> = _speak

    /** 看着中文先把英文说出来 → 停止后 AI 对照原句找差异, 并自动亮出答案。 */
    fun toggleSpeakCompare(card: ChunkEntity) {
        if (_speak.value.assessing) return
        if (_speak.value.recording) {
            coach.stopRecording()
            _speak.value = _speak.value.copy(recording = false, assessing = true, error = null)
            viewModelScope.launch {
                runCatching { coach.assess(card.text) }
                    .onSuccess { result ->
                        _speak.value = SpeakCompareState(
                            done = true,
                            overall = result.overall.takeIf { it.isNotBlank() },
                            issues = result.words,
                        )
                        // 对比完自动亮答案并朗读原句
                        showAnswer(card)
                    }
                    .onFailure { e ->
                        _speak.value = SpeakCompareState(error = e.message ?: "对比失败, 再试一次")
                    }
            }
        } else {
            tts.stop()
            runCatching { coach.startRecording() }
                .onSuccess { _speak.value = SpeakCompareState(recording = true) }
                .onFailure { _speak.value = SpeakCompareState(error = "启动录音失败, 检查麦克风权限") }
        }
    }

    override fun onCleared() {
        coach.stopRecording()
        coach.stopPlayback()
    }
}
