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
import com.lingua.english.data.tts.SystemTtsProvider
import com.lingua.english.data.tts.TtsProvider

/** 手摇 DI (和 Vita 同风格, 不引 Hilt)。 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: LinguaDatabase by lazy { LinguaDatabase.get(appContext) }
    val prefs: SettingsPreferences by lazy { SettingsPreferences(appContext) }

    val chatApiClient: ChatApiClient by lazy { ChatApiClient(prefs) }

    val dictRepository: DictRepository by lazy {
        DictRepository(DictDatabaseHelper(appContext), OnlineDictClient())
    }

    val tts: TtsProvider by lazy { SystemTtsProvider(appContext) }

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
