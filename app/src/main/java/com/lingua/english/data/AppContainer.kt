package com.lingua.english.data

import android.content.Context
import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.dict.DictDatabaseHelper
import com.lingua.english.data.dict.DictRepository
import com.lingua.english.data.dict.OnlineDictClient
import com.lingua.english.data.local.LinguaDatabase
import com.lingua.english.data.media.MediaRepository
import com.lingua.english.data.media.YouTubeCaptionClient
import com.lingua.english.data.practice.PronunciationCoach
import com.lingua.english.data.prefs.SettingsPreferences
import com.lingua.english.data.repository.ChatRepository
import com.lingua.english.data.repository.MaterialRepository
import com.lingua.english.data.repository.PlanRepository
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.data.speech.VoiceRecorder
import com.lingua.english.data.tts.GeminiTtsProvider
import com.lingua.english.data.tts.RoutingTtsProvider
import com.lingua.english.data.tts.SystemTtsProvider
import com.lingua.english.data.tts.TtsProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 手摇 DI (和 Vita 同风格, 不引 Hilt)。 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** app 级作用域: 引擎路由、云端语音等跟随进程生命周期的工作。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LinguaDatabase by lazy { LinguaDatabase.get(appContext) }
    val prefs: SettingsPreferences by lazy { SettingsPreferences(appContext) }

    val chatApiClient: ChatApiClient by lazy { ChatApiClient(prefs) }

    val dictRepository: DictRepository by lazy {
        DictRepository(DictDatabaseHelper(appContext), OnlineDictClient())
    }

    private val systemTts: TtsProvider by lazy { SystemTtsProvider(appContext) }

    /** 保留具体类型: 设置页要读 lastError 展示云端合成失败原因。 */
    val geminiTts: GeminiTtsProvider by lazy { GeminiTtsProvider(prefs, appScope, systemTts) }

    /** 全 app 唯一发音入口, 按设置在系统/Gemini 云端之间路由。 */
    val tts: TtsProvider by lazy { RoutingTtsProvider(appScope, prefs, systemTts, geminiTts) }

    /** 跟读教练 (录音 + Gemini 音频点评)。 */
    val pronunciationCoach: PronunciationCoach by lazy { PronunciationCoach(appContext, prefs) }

    /** 听读素材 (YouTube 字幕 / 播客转写)。 */
    val mediaRepository: MediaRepository by lazy {
        MediaRepository(appContext, database.mediaDao(), prefs, YouTubeCaptionClient())
    }

    /** 系统分享进来的链接 (YouTube 等), 听读页消费后置空。 */
    val pendingSharedUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            database.chatDao(), database.dailyLogDao(), chatApiClient, prefs,
            audioDir = File(appContext.filesDir, "chat_audio"),
        )
    }

    /** 对话页语音消息录音器。 */
    val voiceRecorder: VoiceRecorder by lazy { VoiceRecorder(appContext) }

    val materialRepository: MaterialRepository by lazy {
        MaterialRepository(database.materialDao(), database.chunkDao(), database.dailyLogDao(), chatApiClient, prefs)
    }

    val reviewRepository: ReviewRepository by lazy {
        ReviewRepository(database.chunkDao(), database.dailyLogDao(), chatApiClient)
    }

    val planRepository: PlanRepository by lazy {
        PlanRepository(database.dailyLogDao())
    }
}
