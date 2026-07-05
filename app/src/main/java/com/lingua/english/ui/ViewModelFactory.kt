package com.lingua.english.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lingua.english.data.AppContainer
import com.lingua.english.ui.screens.chat.ChatViewModel
import com.lingua.english.ui.screens.corpus.ComposeEntryViewModel
import com.lingua.english.ui.screens.corpus.CorpusViewModel
import com.lingua.english.ui.screens.corpus.MaterialDetailViewModel
import com.lingua.english.ui.screens.media.MediaLibraryViewModel
import com.lingua.english.ui.screens.media.MediaPlayerViewModel
import com.lingua.english.ui.screens.practice.PronunciationViewModel
import com.lingua.english.ui.screens.review.ReviewViewModel
import com.lingua.english.ui.screens.settings.SettingsViewModel
import com.lingua.english.ui.screens.today.TodayViewModel

/** 手摇 VM 工厂 (和 Vita 同风格)。参数化的 MaterialDetailViewModel 用 [materialDetail] 单独造。 */
class LinguaViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(TodayViewModel::class.java) ->
            TodayViewModel(container.planRepository, container.reviewRepository, container.prefs) as T

        modelClass.isAssignableFrom(ChatViewModel::class.java) ->
            ChatViewModel(
                container.chatRepository, container.reviewRepository, container.tts,
                container.prefs, container.voiceRecorder, container.geminiLive,
            ) as T

        modelClass.isAssignableFrom(CorpusViewModel::class.java) ->
            CorpusViewModel(container.materialRepository) as T

        modelClass.isAssignableFrom(ComposeEntryViewModel::class.java) ->
            ComposeEntryViewModel(container.materialRepository) as T

        modelClass.isAssignableFrom(ReviewViewModel::class.java) ->
            ReviewViewModel(container.reviewRepository, container.tts, container.pronunciationCoach) as T

        modelClass.isAssignableFrom(MediaLibraryViewModel::class.java) ->
            MediaLibraryViewModel(container.mediaRepository) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(
                container.prefs, container.chatApiClient, container.chatRepository,
                container.dictRepository, container.tts, container.geminiTts.lastError,
            ) as T

        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }

    companion object {
        /** 参数化工厂: 语料详情页按 materialId 建 VM。 */
        fun materialDetail(container: AppContainer, materialId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MaterialDetailViewModel(
                        container.materialRepository, container.reviewRepository,
                        container.prefs, container.tts, materialId,
                    ) as T
            }

        /** 参数化工厂: 跟读练习页。 */
        fun pronunciation(container: AppContainer, materialId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PronunciationViewModel(
                        container.materialRepository, container.pronunciationCoach, container.tts, materialId,
                    ) as T
            }

        /** 参数化工厂: 听读播放页。 */
        fun mediaPlayer(container: AppContainer, mediaId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MediaPlayerViewModel(
                        container.appContext, container.mediaRepository, container.reviewRepository,
                        container.chatApiClient, mediaId,
                    ) as T
            }
    }
}
