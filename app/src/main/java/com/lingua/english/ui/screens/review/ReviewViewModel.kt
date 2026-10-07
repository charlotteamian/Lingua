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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewSessionState(
    val showAnswer: Boolean = false,
    val doneThisSession: Int = 0,
    /** 本次会话里答过「忘了」、还没重学过关的卡 id (Anki 式重学循环)。 */
    val relearnIds: Set<String> = emptySet(),
    /** 本次会话里忘过的卡总数 (完成卡文案用)。 */
    val lapsedCount: Int = 0,
)

/** 复习范围: 只刷今日到期 (SRS 安排的) 还是全部收藏 (提前加练)。 */
enum class ReviewScope { DUE, ALL }

/** 本次复习的范围设置: 到期范围 + 类型多选 + 数量上限。 */
data class ReviewFilter(
    val scope: ReviewScope = ReviewScope.DUE,
    val kinds: Set<String> = setOf(
        ChunkEntity.KIND_CHUNK, ChunkEntity.KIND_WORD, ChunkEntity.KIND_SENTENCE,
    ),
    val limit: Int? = 20,   // null = 全部
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

    /** 到期队列是响应式的: 评完一张自动缩短。 */
    val dueCards: StateFlow<List<ChunkEntity>> = reviewRepo.dueCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allChunks: StateFlow<List<ChunkEntity>> = reviewRepo.allChunks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _session = MutableStateFlow(ReviewSessionState())
    val session: StateFlow<ReviewSessionState> = _session

    // ─── 复习会话: 先选范围/数量/类型再开始 ──────────────────

    private val _filter = MutableStateFlow(ReviewFilter())
    val filter: StateFlow<ReviewFilter> = _filter

    /** 本次会话的卡片 id 快照 (有序); null = 未开始。评完一张移除一个。 */
    private val _sessionIds = MutableStateFlow<List<String>?>(null)

    val sessionActive: StateFlow<Boolean> = _sessionIds
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 会话队列: 按快照顺序取卡 (支持「全部收藏」加练未到期的卡)。 */
    val sessionQueue: StateFlow<List<ChunkEntity>> =
        combine(allChunks, _sessionIds) { all, ids ->
            if (ids == null) emptyList()
            else {
                val byId = all.associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessionTotal: Int get() = _sessionIds.value?.size ?: 0

    fun setScope(scope: ReviewScope) {
        _filter.value = _filter.value.copy(scope = scope)
    }

    fun toggleKind(kind: String) {
        val kinds = _filter.value.kinds
        val next = if (kind in kinds) kinds - kind else kinds + kind
        if (next.isNotEmpty()) _filter.value = _filter.value.copy(kinds = next)
    }

    fun setLimit(limit: Int?) {
        _filter.value = _filter.value.copy(limit = limit)
    }

    /** 按当前筛选快照一次会话。到期范围取 SRS 队列; 全部收藏按到期先后排 (到期/快到期的先来)。 */
    fun startSession() {
        val f = _filter.value
        val source = when (f.scope) {
            ReviewScope.DUE -> dueCards.value
            ReviewScope.ALL -> allChunks.value.sortedBy { it.dueEpochDay }
        }
        val picked = source
            .filter { it.kind in f.kinds }
            .let { if (f.limit != null) it.take(f.limit) else it }
        if (picked.isEmpty()) return
        _sessionIds.value = picked.map { it.id }
        _session.value = ReviewSessionState()
        _speak.value = SpeakCompareState()
    }

    fun endSession() {
        _sessionIds.value = null
        _session.value = ReviewSessionState()
        _speak.value = SpeakCompareState()
        coach.stopRecording()
    }

    fun showAnswer(card: ChunkEntity) {
        _session.value = _session.value.copy(showAnswer = true)
        tts.speak(card.text)
    }

    fun grade(card: ChunkEntity, grade: ReviewGrade) {
        viewModelScope.launch {
            reviewRepo.grade(card, grade)
            val s = _session.value
            if (grade == ReviewGrade.FORGOT) {
                // 重学循环: 忘了的卡不出队, 挪到队尾, 本次会话里答对一次才算过
                _sessionIds.value = _sessionIds.value?.let { it - card.id + card.id }
                _session.value = s.copy(
                    showAnswer = false,
                    relearnIds = s.relearnIds + card.id,
                    lapsedCount = if (card.id in s.relearnIds) s.lapsedCount else s.lapsedCount + 1,
                )
            } else {
                _sessionIds.value = _sessionIds.value?.minus(card.id)
                _session.value = s.copy(
                    showAnswer = false,
                    doneThisSession = s.doneThisSession + 1,
                    relearnIds = s.relearnIds - card.id,
                )
            }
            _speak.value = SpeakCompareState()
        }
    }

    fun speak(text: String) = tts.speak(text)

    fun delete(chunkId: String) {
        viewModelScope.launch { reviewRepo.delete(chunkId) }
    }

    fun updateCollection(
        chunk: ChunkEntity,
        text: String,
        meaningZh: String,
        note: String,
        kind: String,
    ) {
        viewModelScope.launch {
            reviewRepo.updateCollection(chunk, text, meaningZh, note, kind)
        }
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
