package com.lingua.english.ui.screens.corpus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
        modifier = Modifier.fillMaxSize(),
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
                    .heightIn(min = 140.dp),
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
                    Text("你的英文版", style = MaterialTheme.typography.titleMedium)
                    TappableText(converted.english, onWordTap = lookup, style = MaterialTheme.typography.bodyLarge)
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
            item {
                Button(onClick = { vm.save(input) }, modifier = Modifier.fillMaxWidth()) {
                    Text("保存进语料库 (词块进复习)")
                }
            }
        }
    }
}
