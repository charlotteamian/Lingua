package com.lingua.english.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.domain.ReviewGrade
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaGradients
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaTertiary
import java.time.LocalDate

/** 复习: 中文意思 → 想出英文说法 → 自评。输出导向, 呼应 OIO 的内化环节。 */
@Composable
fun ReviewScreen(vm: ReviewViewModel) {
    val due by vm.dueCards.collectAsStateWithLifecycle()
    val all by vm.allChunks.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val lookup = LocalWordLookup.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("复习", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (due.isNotEmpty()) "还剩 ${due.size} 张 · 本次已完成 ${session.doneThisSession}"
                    else "今日到期清零",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        val card = due.firstOrNull()
        if (card != null) {
            item {
                LinguaCard(background = LinguaGradients.reviewAccent) {
                    Text(
                        if (card.kind == ChunkEntity.KIND_WORD) "生词" else "词块",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(card.meaningZh.ifEmpty { "(没有中文释义, 直接想它的用法)" }, style = MaterialTheme.typography.titleLarge)
                    if (card.note.isNotEmpty() && !session.showAnswer) {
                        Text("提示: ${card.note}", color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (!session.showAnswer) {
                        Text(
                            "先在脑子里 (最好出声) 用英文说出来, 再看答案。",
                            color = LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { vm.showAnswer(card) }, modifier = Modifier.fillMaxWidth()) {
                            Text("显示答案")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TappableText(
                                card.text,
                                onWordTap = lookup,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.speak(card.text) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "朗读",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (card.note.isNotEmpty()) {
                            Text(card.note, color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.grade(card, ReviewGrade.FORGOT) },
                                modifier = Modifier.weight(1f),
                            ) { Text("忘了", color = MaterialTheme.colorScheme.error) }
                            OutlinedButton(
                                onClick = { vm.grade(card, ReviewGrade.FUZZY) },
                                modifier = Modifier.weight(1f),
                            ) { Text("模糊") }
                            Button(
                                onClick = { vm.grade(card, ReviewGrade.GOOD) },
                                modifier = Modifier.weight(1f),
                            ) { Text("记得") }
                        }
                    }
                }
            }
        } else if (session.doneThisSession > 0) {
            item {
                LinguaCard(background = LinguaGradients.reviewAccent) {
                    Text("清零了 ✓", style = MaterialTheme.typography.titleLarge, color = LinguaTertiary)
                    Text(
                        "本次复习 ${session.doneThisSession} 张。想多吃进一点, 去语料页听一条自己的素材。",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (all.isNotEmpty()) {
            item {
                Text(
                    "全部词块 · ${all.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(all, key = { it.id }) { chunk ->
                ChunkRow(chunk, onSpeak = vm::speak, onDelete = vm::delete)
            }
        }
    }
}

@Composable
private fun ChunkRow(chunk: ChunkEntity, onSpeak: (String) -> Unit, onDelete: (String) -> Unit) {
    val today = LocalDate.now().toEpochDay()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${chunk.text} — ${chunk.meaningZh}", style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(if (chunk.kind == ChunkEntity.KIND_WORD) "生词" else "词块")
                    append(" · ")
                    val days = chunk.dueEpochDay - today
                    append(if (days <= 0) "今天到期" else "${days} 天后到期")
                },
                color = LinguaOnSurfaceMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(onClick = { onSpeak(chunk.text) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "朗读",
                tint = LinguaOnSurfaceMuted, modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = { onDelete(chunk.id) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.DeleteOutline, contentDescription = "删除",
                tint = LinguaOnSurfaceMuted, modifier = Modifier.size(18.dp),
            )
        }
    }
}
