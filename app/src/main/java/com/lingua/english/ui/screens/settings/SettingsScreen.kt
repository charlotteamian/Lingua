package com.lingua.english.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.ai.AiApiPresets
import com.lingua.english.data.ai.Prompts
import com.lingua.english.data.practice.PronunciationCoach
import com.lingua.english.data.tts.GeminiTtsProvider
import com.lingua.english.data.tts.TtsStatus
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaTertiary

/** Gemini TTS 精选声音 (30 个里挑 4 个, 口音统一由提示词控制成英式)。 */
private val GEMINI_VOICES = listOf(
    "Kore" to "Kore·沉稳",
    "Aoede" to "Aoede·轻快",
    "Leda" to "Leda·年轻",
    "Puck" to "Puck·男声",
)

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val preset by vm.aiPreset.collectAsStateWithLifecycle()
    val baseUrl by vm.aiBaseUrl.collectAsStateWithLifecycle()
    val key by vm.aiKey.collectAsStateWithLifecycle()
    val model by vm.aiModel.collectAsStateWithLifecycle()
    val apiTest by vm.apiTest.collectAsStateWithLifecycle()
    val ttsRate by vm.ttsRate.collectAsStateWithLifecycle()
    val ttsEngine by vm.ttsEngine.collectAsStateWithLifecycle()
    val ttsVoice by vm.ttsVoice.collectAsStateWithLifecycle()
    val ttsModel by vm.ttsModel.collectAsStateWithLifecycle()
    val liveModel by vm.liveModel.collectAsStateWithLifecycle()
    val ttsStyle by vm.ttsStyle.collectAsStateWithLifecycle()
    val autoRead by vm.autoRead.collectAsStateWithLifecycle()
    val hideChatText by vm.hideChatText.collectAsStateWithLifecycle()
    val ttsStatus by vm.ttsStatus.collectAsStateWithLifecycle()
    val cloudTtsError by vm.cloudTtsError.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val pronRules by vm.pronRules.collectAsStateWithLifecycle()
    val dictWords by vm.dictWords.collectAsStateWithLifecycle()

    // 输入框用本地 state, 失焦/变更即写 DataStore (本地存储, 无需保存按钮)
    var baseUrlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
    var keyInput by remember(key) { mutableStateOf(key) }
    var modelInput by remember(model) { mutableStateOf(model) }
    var ttsModelInput by remember(ttsModel) { mutableStateOf(ttsModel) }
    var liveModelInput by remember(liveModel) { mutableStateOf(liveModel) }
    var profileInput by remember(profile) { mutableStateOf(profile) }
    // 空 = 用默认; 展示时预填默认文本, 在此基础上改
    var ttsStyleInput by remember(ttsStyle) {
        mutableStateOf(ttsStyle.ifEmpty { GeminiTtsProvider.DEFAULT_STYLE })
    }
    // 空 = 用默认; 展示时预填默认文本, 学习者在此基础上改
    var pronRulesInput by remember(pronRules) {
        mutableStateOf(pronRules.ifEmpty { PronunciationCoach.DEFAULT_RULES })
    }
    var showKey by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val selectedPreset = AiApiPresets.byId(preset)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("设置", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            LinguaCard {
                Text("AI 对话服务", style = MaterialTheme.typography.titleMedium)
                Text(
                    "自带 Key (BYOK): 手机直接调大模型 API, 费用走你自己的账号。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiApiPresets.ALL) { p ->
                        FilterChip(
                            selected = p.id == preset,
                            onClick = { vm.setPreset(p.id) },
                            label = { Text(p.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it; vm.setBaseUrl(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API 地址") },
                    placeholder = { Text(selectedPreset.baseUrl.ifEmpty { "https://…" }) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; vm.setKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "隐藏" else "显示", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                )
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it; vm.setModel(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型") },
                    placeholder = { Text(selectedPreset.defaultModel.ifEmpty { "模型名称" }) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::testConnection, enabled = !apiTest.testing) {
                        Text(if (apiTest.testing) "测试中…" else "测试连接")
                    }
                    apiTest.result?.let { result ->
                        Text(
                            result,
                            color = if (apiTest.ok) LinguaTertiary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            LinguaCard {
                Text("英音发音", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ttsEngine == "system",
                        onClick = { vm.setTtsEngine("system") },
                        label = { Text("系统 TTS", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = ttsEngine == "gemini",
                        onClick = { vm.setTtsEngine("gemini") },
                        label = { Text("Gemini 语音", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (ttsEngine == "gemini") {
                    Text(
                        "真人感语音, 英式口音。用上面「AI 对话服务」的 Gemini Key 按量计费; 失败自动退回系统音。",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    cloudTtsError?.let { err ->
                        Text(
                            "上次云端合成失败: $err (那次已退回系统音)",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GEMINI_VOICES.forEach { (id, label) ->
                            FilterChip(
                                selected = ttsVoice == id,
                                onClick = { vm.setTtsVoice(id) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = ttsModelInput,
                        onValueChange = { ttsModelInput = it; vm.setTtsModel(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("语音模型 (出新版可改)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = ttsStyleInput,
                        onValueChange = { ttsStyleInput = it; vm.setTtsStyle(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        label = { Text("发音风格指令 (口音/语速在这里改)") },
                    )
                    OutlinedButton(onClick = {
                        vm.setTtsStyle("")
                        ttsStyleInput = GeminiTtsProvider.DEFAULT_STYLE
                    }) { Text("恢复默认 (中性 RP)") }
                } else {
                    Text(
                        when (val s = ttsStatus) {
                            is TtsStatus.Initialising -> "语音引擎初始化中…"
                            is TtsStatus.Unavailable -> "语音不可用: ${s.reason}"
                            is TtsStatus.Ready ->
                                if (s.britishVoice) "英音 (en-GB) 已就绪 · 免费离线, 但机械感明显"
                                else "设备缺英音语音包, 暂用默认英语。建议安装「Google 文字转语音」并下载英式英语语音。"
                        },
                        color = if ((ttsStatus as? TtsStatus.Ready)?.britishVoice == true)
                            LinguaTertiary else LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("语速 ${"%.1f".format(ttsRate)}×", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = ttsRate,
                    onValueChange = { vm.setTtsRate(it) },
                    valueRange = 0.5f..1.2f,
                    steps = 6,
                )
                OutlinedButton(onClick = vm::tryVoice) { Text("试听") }
            }
        }

        item {
            LinguaCard {
                Text("对话", style = MaterialTheme.typography.titleMedium)
                Text(
                    "模型分工: 文字/讲解用上面「AI 对话服务」的模型; 「通话」按钮用下面的 Live 模型端到端说话 (不走转文字再合成, 延迟低); 朗读用「英音发音」卡里的语音模型。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = liveModelInput,
                    onValueChange = { liveModelInput = it; vm.setLiveModel(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("实时通话模型 (Gemini Live)") },
                    singleLine = true,
                )
                Text(
                    "通话接不通且报模型名错误时, 把官方文档里的 Live 模型名贴到这里。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动朗读 Poppy 的回复", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "回复一到就读出来, 接近实时通话",
                            color = LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = autoRead, onCheckedChange = { vm.setAutoRead(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("通话模式: 隐藏回复文字", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "只听声音练听力, 点气泡才显示文字",
                            color = LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = hideChatText,
                        onCheckedChange = { vm.setHideChatText(it) },
                        enabled = autoRead,
                    )
                }
            }
        }

        item {
            LinguaCard {
                Text("学习偏好", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prompts.LEVELS.forEach { (id, label) ->
                        FilterChip(
                            selected = level == id,
                            onClick = { vm.setLevel(id) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = profileInput,
                    onValueChange = { profileInput = it; vm.setProfile(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp),
                    label = { Text("自我介绍 (说给 Poppy 听)") },
                    placeholder = { Text("职业、兴趣、最近在忙什么…对话会围绕你的生活展开") },
                )
            }
        }

        item {
            LinguaCard {
                Text("跟读点评标准", style = MaterialTheme.typography.titleMedium)
                Text(
                    "跟读练习和句子对比时给 AI 的评判要求, 可按自己的偏好改 (严一点/松一点/只盯重音…)。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = pronRulesInput,
                    onValueChange = { pronRulesInput = it; vm.setPronRules(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                )
                OutlinedButton(onClick = {
                    vm.setPronRules("")
                    pronRulesInput = PronunciationCoach.DEFAULT_RULES
                }) { Text("恢复默认") }
            }
        }

        item {
            LinguaCard {
                Text("数据", style = MaterialTheme.typography.titleMedium)
                Text(
                    "离线词典: $dictWords 词 · ECDICT (MIT) · 查不到时自动走在线词典",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { confirmClear = true }) {
                    Text("清空全部对话", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部对话?") },
            text = { Text("聊天记录会全部删除, 收进复习的词块不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.clearChat(); confirmClear = false }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}
