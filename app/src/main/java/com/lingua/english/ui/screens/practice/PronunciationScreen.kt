package com.lingua.english.ui.screens.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.practice.PronIssue
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.theme.LinguaError
import com.lingua.english.ui.theme.LinguaGradients
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSecondary
import com.lingua.english.ui.theme.LinguaTertiary

/**
 * 跟读练习: 朗读语料 → AI 按 RP 标准标出问题词 (红色下划线) →
 * 点标记词听 AI 读; 问题卡里可回放自己的录音; 再读一遍重新评估, 读对即消标。
 */
@Composable
fun PronunciationScreen(vm: PronunciationViewModel, onBack: () -> Unit) {
    val material by vm.material.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val lookup = LocalWordLookup.current
    val context = LocalContext.current

    val text = material?.english.orEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.toggleRecording(text)
    }

    fun onRecordTap() {
        if (state.recording) {
            vm.toggleRecording(text)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.toggleRecording(text) else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                    Text("跟读练习", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "红色标记 = 发音要改的词, 点它听 AI 读",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        item {
            LinguaCard {
                Text(
                    text = remember(text, state.issues) {
                        buildPracticeText(
                            text = text,
                            issues = state.issues,
                            onIssueTap = vm::speakWord,
                            onWordTap = lookup,
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.speakAll(text) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("  听 AI 读整段")
                    }
                }
            }
        }

        item {
            Button(
                onClick = ::onRecordTap,
                enabled = text.isNotEmpty() && !state.assessing,
                modifier = Modifier.fillMaxWidth(),
                colors = if (state.recording) ButtonDefaults.buttonColors(containerColor = LinguaSecondary)
                else ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    when {
                        state.recording -> "  读完了, 交给 AI 点评"
                        state.assessedOnce -> "  再读一遍"
                        else -> "  开始朗读这段"
                    }
                )
            }
        }

        if (state.assessing) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("AI 正在听你的录音…", color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        state.overall?.let { overall ->
            item {
                Text(overall, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (state.assessedOnce && state.issues.isEmpty() && !state.assessing && state.error == null) {
            item {
                LinguaCard(background = LinguaGradients.reviewAccent) {
                    Text("这一遍没有需要标记的发音 ✓", style = MaterialTheme.typography.titleMedium, color = LinguaTertiary)
                }
            }
        }

        items(state.issues, key = { it.i }) { issue ->
            IssueCard(
                issue = issue,
                playingOwn = state.playingOwn,
                onSpeakAi = { vm.speakWord(issue.w) },
                onPlayMine = vm::playMyRecording,
            )
        }
    }
}

@Composable
private fun IssueCard(
    issue: PronIssue,
    playingOwn: Boolean,
    onSpeakAi: () -> Unit,
    onPlayMine: () -> Unit,
) {
    LinguaCard {
        Text(issue.w, style = MaterialTheme.typography.titleLarge, color = LinguaError)
        if (issue.issue.isNotEmpty()) {
            Text(issue.issue, style = MaterialTheme.typography.bodyLarge)
        }
        if (issue.tip.isNotEmpty()) {
            Text(issue.tip, color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSpeakAi, modifier = Modifier.weight(1f)) {
                Text("听 AI 读", maxLines = 1)
            }
            OutlinedButton(onClick = onPlayMine, modifier = Modifier.weight(1f), enabled = !playingOwn) {
                Text(if (playingOwn) "播放中…" else "听我的录音", maxLines = 1)
            }
        }
    }
}

/** 原文渲染: 问题词红色下划线+可点 (听 AI 读), 其余词照常点词查词典。 */
private fun buildPracticeText(
    text: String,
    issues: List<PronIssue>,
    onIssueTap: (String) -> Unit,
    onWordTap: (String) -> Unit,
): AnnotatedString {
    val flagged = issues.associateBy { it.i }
    return buildAnnotatedString {
        var cursor = 0
        var wordIndex = 0
        for (match in Regex("[A-Za-z][A-Za-z'’-]*").findAll(text)) {
            if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
            val word = match.value
            val issue = flagged[wordIndex]
            if (issue != null) {
                withLink(
                    LinkAnnotation.Clickable(tag = word, linkInteractionListener = { onIssueTap(word) })
                ) {
                    withStyle(
                        SpanStyle(
                            color = LinguaError,
                            textDecoration = TextDecoration.Underline,
                            background = LinguaError.copy(alpha = 0.12f),
                        )
                    ) {
                        append(word)
                    }
                }
            } else {
                withLink(
                    LinkAnnotation.Clickable(tag = word, linkInteractionListener = { onWordTap(word) })
                ) {
                    append(word)
                }
            }
            wordIndex++
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
