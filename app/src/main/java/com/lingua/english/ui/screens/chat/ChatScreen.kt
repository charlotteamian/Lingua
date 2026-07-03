package com.lingua.english.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaAmber
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSecondary
import com.lingua.english.ui.theme.LinguaSurfaceVariant

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sendState by vm.sendState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, sendState.sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("对话", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Poppy · 伦敦腔陪练 · 点任何单词查词典",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = vm::markNewTopic, enabled = !sendState.pendingNewTopic) {
                Text(if (sendState.pendingNewTopic) "已开新话题" else "新话题")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyChatHint() }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message, onSpeak = vm::speak, onSaveChunk = vm::saveChunk)
            }
            if (sendState.sending) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Poppy 正在输入…", color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        sendState.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("用英文聊聊你正在做的事…", color = LinguaOnSurfaceMuted) },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )
            IconButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !sendState.sending,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else LinguaOnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun EmptyChatHint() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("和 Poppy 用英文聊你真实的生活", style = MaterialTheme.typography.titleMedium)
        Text(
            "她只说英式英语, 会顺手纠正你的表达 (✎ 开头), 偶尔递给你一个值得记住的词块 (▸ 开头, 点 ＋ 收进复习)。写错没关系, 写中文也可以, 她会教你怎么用英文说。",
            color = LinguaOnSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 消息气泡。助手消息逐行渲染: ✎ 纠正行 / ▸ 词块行 (带收藏) / 普通行 (可点查词)。 */
@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    onSpeak: (String) -> Unit,
    onSaveChunk: (String, String) -> Unit,
) {
    val isUser = message.role == "user"
    val lookup = LocalWordLookup.current

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else LinguaSurfaceVariant.copy(alpha = 0.6f)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isUser) {
                TappableText(message.content, onWordTap = lookup)
            } else {
                AssistantContent(message.content, lookup, onSaveChunk)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSpeak(message.content) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "朗读",
                            tint = LinguaOnSurfaceMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantContent(
    content: String,
    lookup: (String) -> Unit,
    onSaveChunk: (String, String) -> Unit,
) {
    content.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> {}

            trimmed.startsWith("✎") -> Text(
                trimmed,
                color = LinguaAmber,
                style = MaterialTheme.typography.bodyMedium,
            )

            trimmed.startsWith("▸") -> {
                // "▸ chunk: phrase — 中文" → 可一键收进复习
                val body = trimmed.removePrefix("▸").removePrefix(" chunk:").trim()
                val phrase = body.substringBefore("—").trim().trim('*')
                val zh = body.substringAfter("—", "").trim()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        trimmed,
                        color = LinguaSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (phrase.isNotEmpty()) {
                        IconButton(
                            onClick = { onSaveChunk(phrase, zh) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "收进复习",
                                tint = LinguaSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            else -> TappableText(trimmed, onWordTap = lookup)
        }
    }
}
