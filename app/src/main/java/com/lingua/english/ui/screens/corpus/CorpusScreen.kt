package com.lingua.english.ui.screens.corpus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted

@Composable
fun CorpusScreen(
    vm: CorpusViewModel,
    onOpenMaterial: (String) -> Unit,
    onCompose: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    val materials by vm.materials.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("语料", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "你的生活, 你的英文素材",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                FilledTonalButton(onClick = onCompose) { Text("＋ 记一条") }
            }
        }

        item {
            LinguaCard(modifier = Modifier.clickable(onClick = onOpenMedia)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("听读 · 视频/播客", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "YouTube 免费字幕 · 播客 AI 转写 · 边听边查",
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

        if (materials.isEmpty()) {
            item {
                LinguaCard {
                    Text("还没有语料", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "OIO 的第一步: 把今天做的事、情绪、想法随手记下来 (中文就行), AI 会转成「你自己会说的英文」。这些才是你真正用得上的素材, 比课本例句管用。",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        items(materials, key = { it.id }) { material ->
            LinguaCard(modifier = Modifier.clickable { onOpenMaterial(material.id) }) {
                Text(material.date, color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.labelMedium)
                Text(
                    material.english,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
