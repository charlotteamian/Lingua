package com.lingua.english.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.ChunkEntity
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

class ReviewViewModel(
    private val reviewRepo: ReviewRepository,
    private val tts: TtsProvider,
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
        }
    }

    fun speak(text: String) = tts.speak(text)

    fun delete(chunkId: String) {
        viewModelScope.launch { reviewRepo.delete(chunkId) }
    }
}
