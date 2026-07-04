package com.lingua.english.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSecondary
import com.lingua.english.ui.theme.LinguaTertiary

/**
 * 把一段文本按句切开供逐句收藏。✎ 纠正行的英文说法放最前面 (最值得收);
 * ▸ 词块行跳过 (有自己的 ＋ 收藏)。
 */
fun splitSentences(text: String): List<String> {
    val lines = text.lines().map { it.trim() }
    val corrections = lines.filter { it.startsWith("✎") }.mapNotNull { line ->
        val body = line.removePrefix("✎").trim()
        body.substringAfter(":", body).trim()
            .takeIf { it.length >= 8 && it.any { ch -> ch.isLetter() } }
    }
    val prose = lines
        .filter { it.isNotEmpty() && !it.startsWith("✎") && !it.startsWith("▸") }
        .joinToString(" ")
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.length >= 8 && it.any { ch -> ch.isLetter() } }
    return (corrections + prose).distinct()
}

/**
 * 收藏句子底部卡: 列出 [sourceText] 的每一句, 点一句收一句。
 * 收藏的句子进复习队列 (出中文 → 说英文 → 跟读对比)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentenceCollectSheet(
    sourceText: String?,
    collected: Set<String>,
    onCollect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (sourceText == null) return
    val sentences = remember(sourceText) { splitSentences(sourceText) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 句子多时超出弹层高度, 必须可滚
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text("收藏句子进复习", style = MaterialTheme.typography.titleMedium)
            Text(
                "点一句收一句 · 复习时出中文, 你说英文再对比",
                color = LinguaOnSurfaceMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            if (sentences.isEmpty()) {
                Text(
                    "这条消息里没有可收藏的完整句子",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            sentences.forEach { sentence ->
                val done = sentence.trim().lowercase() in collected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !done) { onCollect(sentence) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        sentence,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done) LinguaOnSurfaceMuted else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (done) "✓ 已收" else "＋ 收藏",
                        color = if (done) LinguaTertiary else LinguaSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
