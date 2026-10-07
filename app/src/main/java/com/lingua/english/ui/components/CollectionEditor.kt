package com.lingua.english.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted

/**
 * 新增自选收藏、编辑已有收藏共用的表单。
 * [sourceText] 不为空时可在原文里长按选词, 再把选中内容送进可编辑输入框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionEditorSheet(
    title: String,
    initialText: String,
    initialMeaningZh: String,
    initialNote: String,
    initialKind: String,
    allowedKinds: List<String>,
    sourceText: String? = null,
    saveLabel: String = "保存收藏",
    onSave: (text: String, meaningZh: String, note: String, kind: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var meaningZh by remember(initialMeaningZh) { mutableStateOf(initialMeaningZh) }
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var kind by remember(initialKind, allowedKinds) {
        mutableStateOf(initialKind.takeIf { it in allowedKinds } ?: allowedKinds.first())
    }
    var sourceValue by remember(sourceText) { mutableStateOf(TextFieldValue(sourceText.orEmpty())) }

    val selectionStart = minOf(sourceValue.selection.start, sourceValue.selection.end)
        .coerceIn(0, sourceValue.text.length)
    val selectionEnd = maxOf(sourceValue.selection.start, sourceValue.selection.end)
        .coerceIn(selectionStart, sourceValue.text.length)
    val selectedText = sourceValue.text.substring(selectionStart, selectionEnd).trim()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            if (sourceText != null) {
                Text(
                    "在下面英文里长按并拖动选择句子或短语，然后点“使用选中内容”。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = sourceValue,
                    onValueChange = { sourceValue = it },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("英文原文") },
                )
                OutlinedButton(
                    onClick = { text = selectedText },
                    enabled = selectedText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selectedText.isBlank()) "先长按选择一段文字" else "使用选中内容")
                }
            }

            if (allowedKinds.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedKinds.forEach { option ->
                        LinguaChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = kindLabel(option),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                label = { Text("英文内容") },
                placeholder = { Text("也可以直接输入想收藏的英文") },
            )
            OutlinedTextField(
                value = meaningZh,
                onValueChange = { meaningZh = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                label = { Text("中文意思") },
                placeholder = { Text("复习时会先看到这里") },
            )
            Text(
                "中文可以先留空，保存后随时在收藏库补上或修改。",
                color = LinguaOnSurfaceMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                label = { Text("备注（可选）") },
            )
            Button(
                onClick = {
                    onSave(text.trim(), meaningZh.trim(), note.trim(), kind)
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(saveLabel)
            }
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    ChunkEntity.KIND_SENTENCE -> "句子"
    ChunkEntity.KIND_WORD -> "生词"
    else -> "词块"
}
