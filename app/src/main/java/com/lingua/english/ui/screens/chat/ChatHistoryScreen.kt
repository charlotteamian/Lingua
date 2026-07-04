package com.lingua.english.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.SentenceCollectSheet
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SESSION_DATE = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/** 往期话题列表: 开新话题后旧内容归档到这里, 想复习随时翻。 */
@Composable
fun ChatHistoryScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val sessions by vm.pastSessions.collectAsStateWithLifecycle()

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
                Text("往期话题", style = MaterialTheme.typography.headlineMedium)
            }
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    "还没有归档的话题。在对话页点「新话题」后, 之前的内容会收进这里。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        items(sessions, key = { it.anchorId }) { session ->
            LinguaCard(modifier = Modifier.clickable { onOpenSession(session.anchorId) }) {
                Text(
                    Instant.ofEpochMilli(session.startEpochMs)
                        .atZone(ZoneId.systemDefault()).format(SESSION_DATE) + " · ${session.count} 条",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    session.preview,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 单个往期话题回看: 气泡渲染和对话页一致 (点词/朗读/收藏都能用), 只是不能继续发消息。 */
@Composable
fun ChatSessionScreen(
    vm: ChatViewModel,
    anchorId: String,
    onBack: () -> Unit,
) {
    val messages by remember(anchorId) { vm.sessionMessages(anchorId) }.collectAsStateWithLifecycle()
    val playingVoice by vm.playingVoice.collectAsStateWithLifecycle()
    val savedTexts by vm.savedTexts.collectAsStateWithLifecycle()
    var collectSource by remember { mutableStateOf<String?>(null) }

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
                    Text("往期话题", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "只读回看 · 点词查词典, 收藏照常",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        items(messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                hidden = false,
                playingVoice = playingVoice,
                savedTexts = savedTexts,
                onReveal = {},
                onSpeak = vm::speak,
                onSaveChunk = vm::saveChunk,
                onToggleVoice = vm::toggleVoicePlayback,
                onCollect = { collectSource = message.content },
            )
        }
    }

    SentenceCollectSheet(
        sourceText = collectSource,
        collected = savedTexts,
        onCollect = vm::collectSentence,
        onDismiss = { collectSource = null },
    )
}
