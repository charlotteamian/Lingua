package com.lingua.english.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.linguaDataStore: DataStore<Preferences> by preferencesDataStore(name = "lingua_settings")

/** App 级用户偏好, 纯本地。API Key 明文存本机 DataStore (和 Vita 同策略)。 */
class SettingsPreferences(context: Context) {

    private val ds = context.applicationContext.linguaDataStore

    // ─── AI API (BYOK) ───────────────────────────────────────

    val aiApiPreset: Flow<String> = ds.data.map { it[KEY_AI_PRESET] ?: "deepseek" }
    suspend fun setAiApiPreset(id: String) = ds.edit {
        it[KEY_AI_PRESET] = id
        // 切预设清掉 base/model 覆盖, 保留 Key (和 Vita 同逻辑)
        it.remove(KEY_AI_BASE_URL)
        it.remove(KEY_AI_MODEL)
    }

    val aiApiBaseUrl: Flow<String> = ds.data.map { it[KEY_AI_BASE_URL] ?: "" }
    suspend fun setAiApiBaseUrl(url: String) = ds.edit { it[KEY_AI_BASE_URL] = url }

    val aiApiKey: Flow<String> = ds.data.map { it[KEY_AI_KEY] ?: "" }
    suspend fun setAiApiKey(key: String) = ds.edit { it[KEY_AI_KEY] = key }

    val aiApiModel: Flow<String> = ds.data.map { it[KEY_AI_MODEL] ?: "" }
    suspend fun setAiApiModel(model: String) = ds.edit { it[KEY_AI_MODEL] = model }

    // ─── 发音 ────────────────────────────────────────────────

    val ttsRate: Flow<Float> = ds.data.map { it[KEY_TTS_RATE] ?: 0.9f }
    suspend fun setTtsRate(rate: Float) = ds.edit { it[KEY_TTS_RATE] = rate }

    // ─── 学习偏好 ────────────────────────────────────────────

    /** 自我介绍, 拼进陪练提示词让对话贴近生活。 */
    val learnerProfile: Flow<String> = ds.data.map { it[KEY_PROFILE] ?: "" }
    suspend fun setLearnerProfile(text: String) = ds.edit { it[KEY_PROFILE] = text }

    /** starter / intermediate / advanced */
    val learnerLevel: Flow<String> = ds.data.map { it[KEY_LEVEL] ?: "intermediate" }
    suspend fun setLearnerLevel(level: String) = ds.edit { it[KEY_LEVEL] = level }

    companion object {
        private val KEY_AI_PRESET = stringPreferencesKey("ai_api_preset")
        private val KEY_AI_BASE_URL = stringPreferencesKey("ai_api_base_url")
        private val KEY_AI_KEY = stringPreferencesKey("ai_api_key")
        private val KEY_AI_MODEL = stringPreferencesKey("ai_api_model")
        private val KEY_TTS_RATE = floatPreferencesKey("tts_rate")
        private val KEY_PROFILE = stringPreferencesKey("learner_profile")
        private val KEY_LEVEL = stringPreferencesKey("learner_level")
    }
}
