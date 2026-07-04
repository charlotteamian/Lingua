package com.lingua.english.data

import android.content.Context
import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.dict.DictDatabaseHelper
import com.lingua.english.data.dict.DictRepository
import com.lingua.english.data.dict.OnlineDictClient
import com.lingua.english.data.local.LinguaDatabase
import com.lingua.english.data.prefs.SettingsPreferences
import com.lingua.english.data.repository.ChatRepository
import com.lingua.english.data.repository.MaterialRepository
import com.lingua.english.data.repository.PlanRepository
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.data.tts.GeminiTtsProvider
import com.lingua.english.data.tts.RoutingTtsProvider
import com.lingua.english.data.tts.SystemTtsProvider
import com.lingua.english.data.tts.TtsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 手摇 DI (和 Vita 同风格, 不引 Hilt)。 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** app 级作用域: 引擎路由、云端语音等跟随进程生命周期的工作。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LinguaDatabase by lazy { LinguaDatabase.get(appContext) }
    val prefs: SettingsPreferences by lazy { SettingsPreferences(appContext) }

    val chatApiClient: ChatApiClient by lazy { ChatApiClient(prefs) }

    val dictRepository: DictRepository by lazy {
        DictRepository(DictDatabaseHelper(appContext), OnlineDictClient())
    }

    private val systemTts: TtsProvider by lazy { SystemTtsProvider(appContext) }
    private val geminiTts: TtsProvider by lazy { GeminiTtsProvider(prefs, appScope, systemTts) }

    /** 全 app 唯一发音入口, 按设置在系统/Gemini 云端之间路由。 */
    val tts: TtsProvider by lazy { RoutingTtsProvider(appScope, prefs, systemTts, geminiTts) }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(database.chatDao(), database.dailyLogDao(), chatApiClient, prefs)
    }

    val materialRepository: MaterialRepository by lazy {
        MaterialRepository(database.materialDao(), database.chunkDao(), database.dailyLogDao(), chatApiClient, prefs)
    }

    val reviewRepository: ReviewRepository by lazy {
        ReviewRepository(database.chunkDao(), database.dailyLogDao())
    }

    val planRepository: PlanRepository by lazy {
        PlanRepository(database.dailyLogDao())
    }
}
