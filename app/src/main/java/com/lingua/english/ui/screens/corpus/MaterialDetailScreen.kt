package com.lingua.english.ui.screens.corpus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted

private val RATES = listOf(0.7f, 0.9f, 1.0f, 1.1f)

/** 语料详情: 听自己的素材 (OIO 的 Input 环节)。 */
@Composable
fun MaterialDetailScreen(vm: MaterialDetailViewModel, materialId: String, onBack: () -> Unit) {
    val material by vm.material.collectAsStateWithLifecycle()
    val chunks by vm.chunks.collectAsStateWithLifecycle()
    val rate by vm.ttsRate.collectAsStateWithLifecycle()
    val lookup = LocalWordLookup.current
    var confirmDelete by remember { mutableStateOf(false) }

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
                Text(
                    material?.date.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = LinguaOnSurfaceMuted)
                }
            }
        }

        material?.let { m ->
            item {
                LinguaCard {
                    TappableText(m.english, onWordTap = lookup, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { vm.playAll(m.english) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "朗读全文",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = vm::stop) {
                            Icon(Icons.Filled.Stop, contentDescription = "停止", tint = LinguaOnSurfaceMuted)
                        }
                        Spacer(Modifier.weight(1f))
                        RATES.forEach { r ->
                            FilterChip(
                                selected = rate == r,
                                onClick = { vm.setRate(r) },
                                label = { Text("${r}×") },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "原文: ${m.sourceText}",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (chunks.isNotEmpty()) {
            item {
                LinguaCard {
                    Text("这条语料的词块 (点击可朗读)", style = MaterialTheme.typography.titleMedium)
                    chunks.forEach { chunk ->
                        Column(modifier = Modifier.clickable { vm.speak(chunk.text) }) {
                            Text("${chunk.text} — ${chunk.meaningZh}", style = MaterialTheme.typography.bodyLarge)
                            if (chunk.note.isNotEmpty()) {
                                Text(chunk.note, color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条语料?") },
            text = { Text("它的词块也会一并从复习里移除。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(materialId) { onBack() } }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
