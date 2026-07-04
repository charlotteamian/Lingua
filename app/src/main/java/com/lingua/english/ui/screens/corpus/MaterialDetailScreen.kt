package com.lingua.english.ui.screens.corpus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkAdd
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.SentenceCollectSheet
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaGradients
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSurfaceVariant

private val RATES = listOf(0.7f, 0.9f, 1.0f, 1.1f)

/** 语料详情: 听自己的素材 (OIO 的 Input 环节)。 */
@Composable
fun MaterialDetailScreen(
    vm: MaterialDetailViewModel,
    materialId: String,
    onBack: () -> Unit,
    onPractice: () -> Unit,
) {
    val material by vm.material.collectAsStateWithLifecycle()
    val chunks by vm.chunks.collectAsStateWithLifecycle()
    val rate by vm.ttsRate.collectAsStateWithLifecycle()
    val savedTexts by vm.savedTexts.collectAsStateWithLifecycle()
    val lookup = LocalWordLookup.current
    var confirmDelete by remember { mutableStateOf(false) }
    var collectSource by remember { mutableStateOf<String?>(null) }

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
                        IconButton(onClick = { collectSource = m.english }) {
                            Icon(
                                Icons.Outlined.BookmarkAdd,
                                contentDescription = "收藏句子",
                                tint = LinguaOnSurfaceMuted,
                            )
                        }
                        Text(
                            "语速",
                            color = LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    // 语速档独占一行, 不再被播放按钮挤出屏幕
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RATES.forEach { r ->
                            FilterChip(
                                selected = rate == r,
                                onClick = { vm.setRate(r) },
                                label = { Text("${r}×", maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                LinguaCard(
                    background = LinguaGradients.primaryAccent,
                    modifier = Modifier.clickable(onClick = onPractice),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("跟读练习", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "读给 AI 听, 标出发音要改的词",
                                color = LinguaOnSurfaceMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
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
                Text("这条语料的词块 · ${chunks.size}", style = MaterialTheme.typography.titleMedium)
            }
            items(chunks, key = { it.id }) { chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LinguaSurfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            chunk.text,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (chunk.meaningZh.isNotEmpty()) {
                            Text(
                                chunk.meaningZh,
                                color = LinguaOnSurfaceMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (chunk.note.isNotEmpty()) {
                            Text(
                                chunk.note,
                                color = LinguaOnSurfaceMuted.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { vm.speak(chunk.text) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "朗读",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    SentenceCollectSheet(
        sourceText = collectSource,
        collected = savedTexts,
        onCollect = vm::collectSentence,
        onDismiss = { collectSource = null },
    )

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
