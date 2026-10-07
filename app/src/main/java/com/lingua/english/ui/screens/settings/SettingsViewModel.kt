package com.lingua.english.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.dict.DictRepository
import com.lingua.english.data.prefs.SettingsPreferences
import com.lingua.english.data.repository.ChatRepository
import com.lingua.english.data.tts.TtsProvider
import com.lingua.english.data.tts.TtsStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ApiTestState(val testing: Boolean = false, val result: String? = null, val ok: Boolean = false)

class SettingsViewModel(
    private val prefs: SettingsPreferences,
    private val client: ChatApiClient,
    private val chatRepo: ChatRepository,
    private val dict: DictRepository,
    val tts: TtsProvider,
    val cloudTtsError: StateFlow<String?>,
) : ViewModel() {

    private fun <T> flowOf(source: kotlinx.coroutines.flow.Flow<T>, default: T): StateFlow<T> =
        source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)

    val aiPreset = flowOf(prefs.aiApiPreset, "deepseek")
    val appFont = flowOf(prefs.appFont, "comfortable")
    val aiBaseUrl = flowOf(prefs.aiApiBaseUrl, "")
    val aiKey = flowOf(prefs.aiApiKey, "")
    val aiModel = flowOf(prefs.aiApiModel, "")
    val ttsRate = flowOf(prefs.ttsRate, 0.9f)
    val ttsEngine = flowOf(prefs.ttsEngine, "system")
    val ttsVoice = flowOf(prefs.ttsVoice, "Kore")
    val ttsModel = flowOf(prefs.ttsModel, SettingsPreferences.DEFAULT_TTS_MODEL)
    val liveModel = flowOf(prefs.liveModel, "gemini-3.1-flash-live-preview")
    val ttsStyle = flowOf(prefs.ttsStyle, "")
    val autoRead = flowOf(prefs.autoReadReplies, false)
    val hideChatText = flowOf(prefs.hideChatText, false)
    val profile = flowOf(prefs.learnerProfile, "")
    val level = flowOf(prefs.learnerLevel, "intermediate")
    val pronRules = flowOf(prefs.pronRules, "")

    val ttsStatus: StateFlow<TtsStatus> = tts.status

    private val _apiTest = MutableStateFlow(ApiTestState())
    val apiTest: StateFlow<ApiTestState> = _apiTest

    private val _dictWords = MutableStateFlow(0)
    val dictWords: StateFlow<Int> = _dictWords

    init {
        viewModelScope.launch {
            _dictWords.value = withContext(Dispatchers.IO) {
                runCatching { dict.offlineWordCount() }.getOrDefault(0)
            }
        }
    }

    fun setPreset(id: String) = viewModelScope.launch { prefs.setAiApiPreset(id) }
    fun setAppFont(id: String) = viewModelScope.launch { prefs.setAppFont(id) }
    fun setBaseUrl(url: String) = viewModelScope.launch { prefs.setAiApiBaseUrl(url) }
    fun setKey(key: String) = viewModelScope.launch { prefs.setAiApiKey(key) }
    fun setModel(model: String) = viewModelScope.launch { prefs.setAiApiModel(model) }
    fun setProfile(text: String) = viewModelScope.launch { prefs.setLearnerProfile(text) }
    fun setLevel(level: String) = viewModelScope.launch { prefs.setLearnerLevel(level) }
    fun setPronRules(rules: String) = viewModelScope.launch { prefs.setPronRules(rules) }

    fun setTtsRate(rate: Float) {
        tts.setRate(rate)
        viewModelScope.launch { prefs.setTtsRate(rate) }
    }

    fun setTtsEngine(engine: String) = viewModelScope.launch { prefs.setTtsEngine(engine) }
    fun setTtsVoice(voice: String) = viewModelScope.launch { prefs.setTtsVoice(voice) }
    fun setTtsModel(model: String) = viewModelScope.launch { prefs.setTtsModel(model) }
    fun setLiveModel(model: String) = viewModelScope.launch { prefs.setLiveModel(model) }
    fun setTtsStyle(style: String) = viewModelScope.launch { prefs.setTtsStyle(style) }
    fun setAutoRead(enabled: Boolean) = viewModelScope.launch { prefs.setAutoReadReplies(enabled) }
    fun setHideChatText(enabled: Boolean) = viewModelScope.launch { prefs.setHideChatText(enabled) }

    fun tryVoice() = tts.speak("Right then, shall we get started?")

    fun testConnection() {
        if (_apiTest.value.testing) return
        _apiTest.value = ApiTestState(testing = true)
        viewModelScope.launch {
            runCatching { client.testConnection() }
                .onSuccess { _apiTest.value = ApiTestState(result = it, ok = true) }
                .onFailure { _apiTest.value = ApiTestState(result = it.message ?: "连接失败", ok = false) }
        }
    }

    fun clearChat() {
        viewModelScope.launch { chatRepo.clearAll() }
    }
}
