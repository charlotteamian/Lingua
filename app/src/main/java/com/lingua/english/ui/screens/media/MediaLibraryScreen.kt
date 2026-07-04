package com.lingua.english.ui.screens.media

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.local.entity.MediaItemEntity
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaTertiary

/**
 * 听读素材库: YouTube 链接 (免费字幕) / 本地音频·播客 (AI 转写)。
 * 系统分享 YouTube 链接给 Lingua 会自动填进输入框。
 */
@Composable
fun MediaLibraryScreen(
    vm: MediaLibraryViewModel,
    sharedUrl: String?,
    onConsumeSharedUrl: () -> Unit,
    onOpen: (String) -> Unit,
    onLoginYouTube: () -> Unit,
    onBack: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }

    LaunchedEffect(sharedUrl) {
        sharedUrl?.let {
            urlInput = it
            onConsumeSharedUrl()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }.orEmpty()
        vm.importAudio(uri, name) { id -> onOpen(id) }
    }

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
                Column {
                    Text("听读", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "视频/播客边听边读 · 点词查词 · 收藏句子",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        item {
            LinguaCard {
                Text("YouTube 视频", style = MaterialTheme.typography.titleMedium)
                Text(
                    "粘贴链接或在 YouTube 里「分享 → Lingua」。字幕免费, 不花 token。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://youtube.com/watch?v=…", color = LinguaOnSurfaceMuted) },
                    singleLine = true,
                )
                Button(
                    onClick = { vm.importYouTube(urlInput) { id -> urlInput = ""; onOpen(id) } },
                    enabled = urlInput.isNotBlank() && !importState.importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (importState.importing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("拉取字幕并打开")
                    }
                }
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("＋ 本地音频 / 播客文件") }
                importState.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if ("LOGIN_REQUIRED" in error || "风控" in error || "登录" in error) {
                        Text(
                            "这个网络出口被 YouTube 要求登录验证。在 app 里登录一次 YouTube 即可解决 (账号密码只经过 Google 页面):",
                            color = LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = onLoginYouTube, modifier = Modifier.fillMaxWidth()) {
                            Text("登录 YouTube 后重试")
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                Text(
                    "还没有素材。刷到好视频就分享进来, 播客下载后从文件导入。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        items(items, key = { it.id }) { media ->
            LinguaCard(modifier = Modifier.clickable { onOpen(media.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (media.type == MediaItemEntity.TYPE_YOUTUBE) Icons.Filled.SmartDisplay
                        else Icons.Filled.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            media.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            buildString {
                                append(if (media.type == MediaItemEntity.TYPE_YOUTUBE) "YouTube" else "音频")
                                append(" · ")
                                append(if (media.transcribed) "字幕就绪" else "待转写")
                            },
                            color = if (media.transcribed) LinguaTertiary else LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    IconButton(onClick = { vm.delete(media) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline, contentDescription = "删除",
                            tint = LinguaOnSurfaceMuted, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
