package com.lingua.english.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted

/**
 * 收藏库: 全部句子/词块/生词, 按类型筛选, 最近收藏在前。
 * 复习页只留入口, 不平铺 (Anki 牌组 / 墨墨词库同款思路)。
 */
@Composable
fun LibraryScreen(vm: ReviewViewModel, onBack: () -> Unit) {
    val all by vm.allChunks.collectAsStateWithLifecycle()
    var kindFilter by remember { mutableStateOf<String?>(null) } // null = 全部

    val shown = remember(all, kindFilter) {
        all.filter { kindFilter == null || it.kind == kindFilter }
            .sortedByDescending { it.createdAtEpochMs }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column {
                    Text("收藏库", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "共 ${all.size} 条 · 最近收藏在前",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kindFilter == null,
                    onClick = { kindFilter = null },
                    label = { Text("全部 ${all.size}", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
                KIND_OPTIONS.forEach { (kind, label) ->
                    FilterChip(
                        selected = kindFilter == kind,
                        onClick = { kindFilter = kind },
                        label = { Text("$label ${all.count { it.kind == kind }}", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    "这一类还没有收藏。对话里点书签收句子, 点单词加生词, ▸ 词块点 ＋。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        items(shown, key = { it.id }) { chunk ->
            ChunkRow(chunk, onSpeak = vm::speak, onDelete = vm::delete)
        }
    }
}
