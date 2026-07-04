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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.local.entity.ChatMessageEntity
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.SentenceCollectSheet
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaAmber
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSecondary
import com.lingua.english.ui.theme.LinguaSurfaceVariant

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sendState by vm.sendState.collectAsStateWithLifecycle()
    val hideText by vm.hideChatText.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val playingVoice by vm.playingVoice.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    // 通话模式下被点开显示文字的消息 id
    val revealedIds = remember { mutableStateListOf<String>() }

    // 收藏句子底部卡: 非空 = 打开, 值为来源消息全文
    var collectSource by remember { mutableStateOf<String?>(null) }
    val collectedSentences by vm.collectedSentences.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startVoice()
    }

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
                    "Poppy · 伦敦腔陪练 · 点单词查词",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                MessageBubble(
                    message = message,
                    hidden = hideText && message.role == "assistant" && message.id !in revealedIds,
                    playingVoice = playingVoice,
                    onReveal = { revealedIds.add(message.id) },
                    onSpeak = vm::speak,
                    onSaveChunk = vm::saveChunk,
                    onToggleVoice = vm::toggleVoicePlayback,
                    onCollect = { collectSource = message.content },
                )
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
        if (voice.recording) {
            // 录音条: 想说多久说多久, 停顿不会被掐断
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = LinguaSecondary)
                Text(
                    "  正在录音 %d:%02d".format(voice.seconds / 60, voice.seconds % 60),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                TextButton(onClick = vm::cancelVoice) { Text("取消") }
                Button(onClick = vm::stopAndSendVoice) { Text("发送") }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.startVoice()
                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !sendState.sending,
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "按一下开始录语音",
                        tint = LinguaOnSurfaceMuted,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("写英文, 或点麦克风说…", color = LinguaOnSurfaceMuted, maxLines = 1)
                    },
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

    SentenceCollectSheet(
        sourceText = collectSource,
        collected = collectedSentences,
        onCollect = vm::collectSentence,
        onDismiss = { collectSource = null },
    )
}

@Composable
private fun EmptyChatHint() {
    LinguaCard {
        Text("和 Poppy 用英文聊你真实的生活", style = MaterialTheme.typography.titleMedium)
        Text(
            "她只说英式英语, 会顺手纠正你的表达 (✎ 开头), 偶尔递一个值得记住的词块 (▸ 开头, 点 ＋ 收进复习)。写错没关系, 写中文也可以, 她会教你怎么用英文说。",
            color = LinguaOnSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 消息气泡。助手消息逐行渲染: ✎ 纠正行 / ▸ 词块行 (带收藏) / 普通行 (可点查词)。 */
@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    hidden: Boolean,
    playingVoice: String?,
    onReveal: () -> Unit,
    onSpeak: (String) -> Unit,
    onSaveChunk: (String, String) -> Unit,
    onToggleVoice: (String) -> Unit,
    onCollect: () -> Unit,
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
            when {
                isUser -> {
                    message.audioPath?.let { path ->
                        Row(
                            modifier = Modifier.clickable { onToggleVoice(path) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (playingVoice == path) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = "播放语音",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                if (playingVoice == path) " 播放中…" else " 我的语音",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (message.content.isNotBlank()) {
                        TappableText(message.content, onWordTap = lookup)
                        Row {
                            IconButton(onClick = onCollect, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.BookmarkAdd,
                                    contentDescription = "收藏句子",
                                    tint = LinguaOnSurfaceMuted,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // 通话模式: 只给声音, 点按显示文字
                hidden -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSpeak(message.content) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "重听",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "语音回复 · 点按显示文字",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clickable(onClick = onReveal)
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    )
                }

                else -> {
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
                        if (message.content.isNotBlank()) {
                            IconButton(onClick = onCollect, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Outlined.BookmarkAdd,
                                    contentDescription = "收藏句子",
                                    tint = LinguaOnSurfaceMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
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
