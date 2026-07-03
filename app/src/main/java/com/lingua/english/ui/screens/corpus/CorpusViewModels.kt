package com.lingua.english.ui.screens.corpus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.local.entity.MaterialEntity
import com.lingua.english.data.prefs.SettingsPreferences
import com.lingua.english.data.repository.ConvertedChunk
import com.lingua.english.data.repository.ConvertedMaterial
import com.lingua.english.data.repository.MaterialRepository
import com.lingua.english.data.tts.TtsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 语料列表页。 */
class CorpusViewModel(materialRepo: MaterialRepository) : ViewModel() {
    val materials: StateFlow<List<MaterialEntity>> = materialRepo.materials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** 随手记 → 转写 → 保存。 */
data class ComposeEntryState(
    val converting: Boolean = false,
    val converted: ConvertedMaterial? = null,
    val keptChunkIndices: Set<Int> = emptySet(),
    val error: String? = null,
    val saved: Boolean = false,
)

class ComposeEntryViewModel(
    private val materialRepo: MaterialRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeEntryState())
    val state: StateFlow<ComposeEntryState> = _state

    fun convert(sourceText: String) {
        if (sourceText.isBlank() || _state.value.converting) return
        _state.value = ComposeEntryState(converting = true)
        viewModelScope.launch {
            runCatching { materialRepo.convert(sourceText) }
                .onSuccess { converted ->
                    _state.value = ComposeEntryState(
                        converted = converted,
                        keptChunkIndices = converted.chunks.indices.toSet(),
                    )
                }
                .onFailure { e ->
                    _state.value = ComposeEntryState(error = e.message ?: "转写失败, 重试一次试试")
                }
        }
    }

    fun toggleChunk(index: Int) {
        val kept = _state.value.keptChunkIndices
        _state.value = _state.value.copy(
            keptChunkIndices = if (index in kept) kept - index else kept + index,
        )
    }

    fun save(sourceText: String) {
        val converted = _state.value.converted ?: return
        val kept = _state.value.keptChunkIndices.sorted().map { converted.chunks[it] }
        viewModelScope.launch {
            materialRepo.save(sourceText, converted, kept)
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun reset() {
        _state.value = ComposeEntryState()
    }
}

/** 语料详情页 (按 materialId 参数化)。 */
class MaterialDetailViewModel(
    private val materialRepo: MaterialRepository,
    private val prefs: SettingsPreferences,
    private val tts: TtsProvider,
    materialId: String,
) : ViewModel() {

    val material: StateFlow<MaterialEntity?> = materialRepo.material(materialId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chunks: StateFlow<List<ChunkEntity>> = materialRepo.chunksOf(materialId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ttsRate: StateFlow<Float> = prefs.ttsRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.9f)

    /** 播放整条语料并计一次「听」。 */
    fun playAll(text: String) {
        tts.speak(text)
        viewModelScope.launch { materialRepo.recordListen() }
    }

    fun speak(text: String) = tts.speak(text)

    fun stop() = tts.stop()

    fun setRate(rate: Float) {
        tts.setRate(rate)
        viewModelScope.launch { prefs.setTtsRate(rate) }
    }

    fun delete(materialId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            materialRepo.delete(materialId)
            onDone()
        }
    }
}
