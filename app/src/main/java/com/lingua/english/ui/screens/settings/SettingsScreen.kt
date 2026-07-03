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
import com.lingua.english.data.tts.TtsStatus
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaTertiary

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val preset by vm.aiPreset.collectAsStateWithLifecycle()
    val baseUrl by vm.aiBaseUrl.collectAsStateWithLifecycle()
    val key by vm.aiKey.collectAsStateWithLifecycle()
    val model by vm.aiModel.collectAsStateWithLifecycle()
    val apiTest by vm.apiTest.collectAsStateWithLifecycle()
    val ttsRate by vm.ttsRate.collectAsStateWithLifecycle()
    val ttsStatus by vm.ttsStatus.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val dictWords by vm.dictWords.collectAsStateWithLifecycle()

    // 输入框用本地 state, 失焦/变更即写 DataStore (本地存储, 无需保存按钮)
    var baseUrlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
    var keyInput by remember(key) { mutableStateOf(key) }
    var modelInput by remember(model) { mutableStateOf(model) }
    var profileInput by remember(profile) { mutableStateOf(profile) }
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
                Text(
                    when (val s = ttsStatus) {
                        is TtsStatus.Initialising -> "语音引擎初始化中…"
                        is TtsStatus.Unavailable -> "语音不可用: ${s.reason}"
                        is TtsStatus.Ready ->
                            if (s.britishVoice) "英音 (en-GB) 已就绪 · 系统 TTS"
                            else "设备缺英音语音包, 暂用默认英语。建议安装「Google 文字转语音」并下载英式英语语音。"
                    },
                    color = if (ttsStatus is TtsStatus.Ready && (ttsStatus as TtsStatus.Ready).britishVoice)
                        LinguaTertiary else LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
