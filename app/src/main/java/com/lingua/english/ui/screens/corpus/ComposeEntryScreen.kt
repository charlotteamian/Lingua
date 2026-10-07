package com.lingua.english.ui.screens.corpus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.speech.SpeechInputState
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.ui.components.CollectionEditorSheet
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.MicButton
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.components.appendDictation
import com.lingua.english.ui.components.rememberSpeechInput
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSecondary

/** 随手记 → AI 转专属语料 → 勾选词块 → 保存。OIO 第一环。 */
@Composable
fun ComposeEntryScreen(vm: ComposeEntryViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showCollectionEditor by remember { mutableStateOf(false) }
    var editingCustomIndex by remember { mutableStateOf<Int?>(null) }
    val lookup = LocalWordLookup.current

    // 语音输入 (中文): 懒得打字时直接说今天干了什么
    var dictationBase by remember { mutableStateOf("") }
    val speech = rememberSpeechInput(
        onPartial = { partial -> input = appendDictation(dictationBase, partial) },
        onFinal = { final -> input = appendDictation(dictationBase, final) },
    )
    val speechState by speech.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) {
            vm.reset()
            onBack()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("随手记", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                MicButton(
                    controller = speech,
                    languageTag = "zh-CN",
                    onStartDictation = { dictationBase = input },
                )
            }
        }

        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    // 限制可视高度后 TextField 会在框内跟随光标滚动, 不再向键盘后面无限长高。
                    .heightIn(min = 180.dp, max = 300.dp),
                minLines = 6,
                maxLines = 10,
                placeholder = {
                    Text(
                        if (speechState is SpeechInputState.Listening) "正在听, 用中文说就行…"
                        else "今天做了什么? 在想什么? 什么心情?\n说 (右上麦克风) 或写, 中文、英文、混着都行。",
                        color = LinguaOnSurfaceMuted,
                    )
                },
            )
        }

        (speechState as? SpeechInputState.Error)?.let { error ->
            item { Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        item {
            Button(
                onClick = { vm.convert(input) },
                enabled = input.isNotBlank() && !state.converting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.converting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.converted == null) "转成我的英文语料" else "重新转一次")
                }
            }
        }

        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }

        state.converted?.let { converted ->
            item {
                LinguaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "你的英文版",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                editingCustomIndex = null
                                showCollectionEditor = true
                            },
                        ) {
                            Text("＋ 自己挖")
                        }
                    }
                    TappableText(converted.english, onWordTap = lookup, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "推荐里没有的句子或短语，点“自己挖”再长按选取。",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            item {
                LinguaCard {
                    Text("值得记住的词块 (点击取消不想要的)", style = MaterialTheme.typography.titleMedium)
                    converted.chunks.forEachIndexed { index, chunk ->
                        val kept = index in state.keptChunkIndices
                        Row(
                            modifier = Modifier.clickable { vm.toggleChunk(index) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (kept) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (kept) LinguaSecondary else LinguaOnSurfaceMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text("${chunk.text} — ${chunk.zh}", style = MaterialTheme.typography.bodyLarge)
                                if (chunk.note.isNotEmpty()) {
                                    Text(chunk.note, color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            if (state.customCollections.isNotEmpty()) {
                item {
                    LinguaCard {
                        Text("自己挖的收藏 · ${state.customCollections.size}", style = MaterialTheme.typography.titleMedium)
                        state.customCollections.forEachIndexed { index, draft ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(draft.text, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        draft.meaningZh.ifEmpty { "中文待补，可保存后在收藏库编辑" },
                                        color = LinguaOnSurfaceMuted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        if (draft.kind == ChunkEntity.KIND_SENTENCE) "句子" else "词块",
                                        color = LinguaSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingCustomIndex = index
                                        showCollectionEditor = true
                                    },
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                                }
                                IconButton(onClick = { vm.removeCustomCollection(index) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "移除")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { vm.save(input) }, modifier = Modifier.fillMaxWidth()) {
                    Text("保存进语料库 (推荐和自选进复习)")
                }
            }
        }
    }

    if (showCollectionEditor) {
        val draft = editingCustomIndex?.let { state.customCollections.getOrNull(it) }
        CollectionEditorSheet(
            title = if (draft == null) "自己挖句子 / 词块" else "编辑自选收藏",
            initialText = draft?.text.orEmpty(),
            initialMeaningZh = draft?.meaningZh.orEmpty(),
            initialNote = draft?.note.orEmpty(),
            initialKind = draft?.kind ?: ChunkEntity.KIND_SENTENCE,
            allowedKinds = listOf(ChunkEntity.KIND_SENTENCE, ChunkEntity.KIND_CHUNK),
            sourceText = state.converted?.english,
            saveLabel = "加入自选",
            onSave = { text, meaningZh, note, kind ->
                vm.upsertCustomCollection(editingCustomIndex, text, meaningZh, note, kind)
                showCollectionEditor = false
            },
            onDismiss = { showCollectionEditor = false },
        )
    }
}
