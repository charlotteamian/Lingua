package com.lingua.english.ui.screens.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.ai.ChatTurn
import com.lingua.english.data.local.entity.MediaItemEntity
import com.lingua.english.data.media.MediaRepository
import com.lingua.english.data.media.TranscriptLine
import com.lingua.english.data.repository.ReviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── 素材库 ─────────────────────────────────────────────────

data class MediaImportState(
    val importing: Boolean = false,
    val error: String? = null,
)

class MediaLibraryViewModel(private val mediaRepo: MediaRepository) : ViewModel() {

    val items: StateFlow<List<MediaItemEntity>> = mediaRepo.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow(MediaImportState())
    val importState: StateFlow<MediaImportState> = _importState

    fun importYouTube(url: String, onDone: (String) -> Unit) {
        if (url.isBlank() || _importState.value.importing) return
        _importState.value = MediaImportState(importing = true)
        viewModelScope.launch {
            runCatching { mediaRepo.importYouTube(url) }
                .onSuccess { id ->
                    _importState.value = MediaImportState()
                    onDone(id)
                }
                .onFailure { e ->
                    _importState.value = MediaImportState(error = e.message ?: "导入失败 (${e.javaClass.simpleName})")
                }
        }
    }

    fun importAudio(uri: Uri, displayName: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { mediaRepo.importAudio(uri, displayName) }
                .onSuccess(onDone)
                .onFailure { e ->
                    _importState.value = MediaImportState(error = e.message ?: "导入失败 (${e.javaClass.simpleName})")
                }
        }
    }

    fun delete(item: MediaItemEntity) {
        viewModelScope.launch { mediaRepo.delete(item) }
    }
}

// ─── 播放页 ─────────────────────────────────────────────────

data class TranscribeState(
    val running: Boolean = false,
    val error: String? = null,
)

class MediaPlayerViewModel(
    private val context: Context,
    private val mediaRepo: MediaRepository,
    private val reviewRepo: ReviewRepository,
    private val client: ChatApiClient,
    private val mediaId: String,
) : ViewModel() {

    val item: StateFlow<MediaItemEntity?> = mediaRepo.item(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _lines = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val lines: StateFlow<List<TranscriptLine>> = _lines

    /** 当前播放位置 (秒), YouTube 由 JS 桥更新, 音频由 ticker 更新。 */
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position

    val savedTexts: StateFlow<Set<String>> = reviewRepo.savedTexts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 行号 → 解释文本 ("…" = 加载中)。 */
    private val _explanations = MutableStateFlow<Map<Int, String>>(emptyMap())
    val explanations: StateFlow<Map<Int, String>> = _explanations

    private val _transcribe = MutableStateFlow(TranscribeState())
    val transcribe: StateFlow<TranscribeState> = _transcribe

    // 本地音频播放
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    init {
        reloadTranscript()
    }

    private fun reloadTranscript() {
        viewModelScope.launch(Dispatchers.IO) {
            _lines.value = mediaRepo.loadTranscript(mediaId)
        }
    }

    fun updatePosition(sec: Double) {
        _position.value = sec
    }

    // ─── 本地音频控制 ───

    fun toggleAudio() {
        val current = player
        if (current == null) {
            val source = item.value?.source ?: return
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val p = MediaPlayer()
                    p.setDataSource(context, Uri.parse(source))
                    p.prepare()
                    p.setOnCompletionListener { _playing.value = false }
                    player = p
                    p.start()
                    _playing.value = true
                    startTicker()
                }.onFailure {
                    _transcribe.value = _transcribe.value.copy(error = "播放失败: 文件可能已被移动")
                }
            }
        } else if (current.isPlaying) {
            runCatching { current.pause() }
            _playing.value = false
        } else {
            runCatching { current.start() }
            _playing.value = true
        }
    }

    fun seekBy(deltaSec: Int) {
        player?.let { p ->
            runCatching {
                val target = (p.currentPosition + deltaSec * 1000).coerceAtLeast(0)
                p.seekTo(target)
                _position.value = target / 1000.0
            }
        }
    }

    fun seekAudioTo(sec: Double) {
        player?.let { p ->
            runCatching {
                p.seekTo((sec * 1000).toInt())
                if (!p.isPlaying) {
                    p.start()
                    _playing.value = true
                    startTicker()
                }
                _position.value = sec
            }
        } ?: run {
            // 还没开始播: 先启动再定位
            toggleAudio()
            viewModelScope.launch {
                delay(600)
                player?.let { runCatching { it.seekTo((sec * 1000).toInt()) } }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(400)
                player?.let { p ->
                    runCatching { if (p.isPlaying) _position.value = p.currentPosition / 1000.0 }
                }
            }
        }
    }

    // ─── 转写 / 学习动作 ───

    fun audioSizeMb(): Double? = item.value?.let { mediaRepo.audioSizeMb(it) }

    fun startTranscribe() {
        val current = item.value ?: return
        if (_transcribe.value.running) return
        _transcribe.value = TranscribeState(running = true)
        viewModelScope.launch {
            runCatching { mediaRepo.transcribe(current) }
                .onSuccess {
                    _transcribe.value = TranscribeState()
                    reloadTranscript()
                }
                .onFailure { e ->
                    _transcribe.value = TranscribeState(error = e.message ?: "转写失败, 再试一次")
                }
        }
    }

    fun collect(line: TranscriptLine) {
        viewModelScope.launch { runCatching { reviewRepo.addSentence(line.t) } }
    }

    fun explain(index: Int, line: TranscriptLine) {
        if (_explanations.value[index] != null) return
        _explanations.value += index to "…"
        viewModelScope.launch {
            val result = runCatching {
                client.chat(
                    system = "",
                    turns = listOf(
                        ChatTurn(
                            "user",
                            "用中文简洁解释这句英文的意思和值得学的表达 (2-3 句话, 别啰嗦):\n${line.t}",
                        )
                    ),
                )
            }.getOrElse { it.message ?: "解释失败, 再点一次重试" }
            _explanations.value += index to result
        }
    }

    fun retryExplain(index: Int, line: TranscriptLine) {
        _explanations.value -= index
        explain(index, line)
    }

    override fun onCleared() {
        ticker?.cancel()
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
    }
}
