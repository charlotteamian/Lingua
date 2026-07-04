package com.lingua.english.ui.screens.review

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.domain.ReviewGrade
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaGradients
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSurfaceVariant
import com.lingua.english.ui.theme.LinguaTertiary
import java.time.LocalDate

/** 复习: 中文意思 → 想出英文说法 → 自评。输出导向, 呼应 OIO 的内化环节。 */
@Composable
fun ReviewScreen(vm: ReviewViewModel, onOpenLibrary: () -> Unit) {
    val due by vm.dueCards.collectAsStateWithLifecycle()
    val all by vm.allChunks.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val speak by vm.speakCompare.collectAsStateWithLifecycle()
    val lookup = LocalWordLookup.current
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.sessionQueue.value.firstOrNull()?.let { vm.toggleSpeakCompare(it) }
    }

    val sessionActive by vm.sessionActive.collectAsStateWithLifecycle()
    val queue by vm.sessionQueue.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("复习", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when {
                            sessionActive -> "本次还剩 ${queue.size} 张 · 已完成 ${session.doneThisSession}"
                            due.isNotEmpty() -> "今日到期 ${due.size} 张"
                            else -> "今日到期清零"
                        },
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (sessionActive) {
                    TextButton(onClick = vm::endSession) { Text("结束") }
                }
            }
        }

        // 未开始: 选范围/类型/数量再开始 (今日到期 = SRS 安排; 全部收藏 = 提前加练)
        if (!sessionActive) {
            item {
                LinguaCard {
                    Text("这次复习什么", style = MaterialTheme.typography.titleMedium)
                    val source = if (filter.scope == ReviewScope.DUE) due else all
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter.scope == ReviewScope.DUE,
                            onClick = { vm.setScope(ReviewScope.DUE) },
                            label = { Text("今日到期 ${due.size}", maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = filter.scope == ReviewScope.ALL,
                            onClick = { vm.setScope(ReviewScope.ALL) },
                            label = { Text("全部收藏 ${all.size}", maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KIND_OPTIONS.forEach { (kind, label) ->
                            val count = source.count { it.kind == kind }
                            FilterChip(
                                selected = kind in filter.kinds,
                                onClick = { vm.toggleKind(kind) },
                                label = { Text("$label $count", maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LIMIT_OPTIONS.forEach { (limit, label) ->
                            FilterChip(
                                selected = filter.limit == limit,
                                onClick = { vm.setLimit(limit) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    val available = source.count { it.kind in filter.kinds }
                        .let { if (filter.limit != null) minOf(it, filter.limit!!) else it }
                    Button(
                        onClick = vm::startSession,
                        enabled = available > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (available > 0) "开始复习 ($available 张)"
                            else if (filter.scope == ReviewScope.DUE) "所选类型今天没有到期卡, 试试「全部收藏」"
                            else "所选类型还没有收藏",
                        )
                    }
                }
            }

            // 收藏库入口 (专门页面, 不平铺)
            item {
                LinguaCard(modifier = Modifier.clickable(onClick = onOpenLibrary)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("收藏库", style = MaterialTheme.typography.titleMedium)
                            Text(
                                KIND_OPTIONS.joinToString(" · ") { (kind, label) ->
                                    "$label ${all.count { it.kind == kind }}"
                                },
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
        }

        val card = if (sessionActive) queue.firstOrNull() else null
        if (card != null) {
            item {
                LinguaCard(background = LinguaGradients.reviewAccent) {
                    Text(
                        when (card.kind) {
                            ChunkEntity.KIND_WORD -> "生词"
                            ChunkEntity.KIND_SENTENCE -> "句子 · 出中文说英文"
                            else -> "词块"
                        },
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(card.meaningZh.ifEmpty { "(没有中文释义, 直接想它的用法)" }, style = MaterialTheme.typography.titleLarge)
                    if (card.note.isNotEmpty() && !session.showAnswer) {
                        Text("提示: ${card.note}", color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                    }

    if (!session.showAnswer) {
                        Text(
                            if (card.kind == ChunkEntity.KIND_SENTENCE)
                                "看着中文, 直接开口说英文——说完 AI 对比你和原句的差别。"
                            else "先在脑子里 (最好出声) 用英文说出来, 再看答案。",
                            color = LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (card.kind == ChunkEntity.KIND_SENTENCE) {
                            Button(
                                onClick = {
                                    if (speak.recording) {
                                        vm.toggleSpeakCompare(card)
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) vm.toggleSpeakCompare(card)
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                enabled = !speak.assessing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    when {
                                        speak.recording -> "说完了, 对比差别"
                                        speak.assessing -> "AI 正在对比…"
                                        else -> "开口说英文 (录音对比)"
                                    }
                                )
                            }
                            speak.error?.let { error ->
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        OutlinedButton(onClick = { vm.showAnswer(card) }, modifier = Modifier.fillMaxWidth()) {
                            Text("直接看答案")
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
                        // 句子卡说完英文后的对比结果
                        if (speak.done) {
                            speak.overall?.let { overall ->
                                Text(overall, style = MaterialTheme.typography.bodyMedium)
                            }
                            speak.issues.forEach { issue ->
                                Text(
                                    "· ${issue.w} — ${issue.issue}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (speak.issues.isEmpty() && speak.overall == null) {
                                Text("和原句基本一致 ✓", color = LinguaTertiary, style = MaterialTheme.typography.bodyMedium)
                            }
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
        } else if (sessionActive) {
            // 本次会话完成
            item {
                LinguaCard(background = LinguaGradients.reviewAccent) {
                    Text("本次完成 ✓", style = MaterialTheme.typography.titleLarge, color = LinguaTertiary)
                    Text(
                        "复习了 ${session.doneThisSession} 张。" +
                            if (due.isNotEmpty()) "今日还有 ${due.size} 张到期, 可以再来一轮。"
                            else "今日到期全部清零。",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = vm::endSession, modifier = Modifier.fillMaxWidth()) {
                        Text("好")
                    }
                }
            }
        }

    }
}

internal val KIND_OPTIONS = listOf(
    ChunkEntity.KIND_SENTENCE to "句子",
    ChunkEntity.KIND_CHUNK to "词块",
    ChunkEntity.KIND_WORD to "生词",
)

private val LIMIT_OPTIONS = listOf<Pair<Int?, String>>(
    10 to "10 张",
    20 to "20 张",
    50 to "50 张",
    null to "全部",
)

@Composable
internal fun ChunkRow(chunk: ChunkEntity, onSpeak: (String) -> Unit, onDelete: (String) -> Unit) {
    val today = LocalDate.now().toEpochDay()
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
            Text(
                buildString {
                    append(
                        when (chunk.kind) {
                            ChunkEntity.KIND_WORD -> "生词"
                            ChunkEntity.KIND_SENTENCE -> "句子"
                            else -> "词块"
                        }
                    )
                    append(" · ")
                    val days = chunk.dueEpochDay - today
                    append(if (days <= 0) "今天到期" else "${days} 天后到期")
                },
                color = LinguaOnSurfaceMuted.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        IconButton(onClick = { onSpeak(chunk.text) }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "朗读",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = { onDelete(chunk.id) }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.DeleteOutline, contentDescription = "删除",
                tint = LinguaOnSurfaceMuted, modifier = Modifier.size(18.dp),
            )
        }
    }
}
