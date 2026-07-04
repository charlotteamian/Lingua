package com.lingua.english.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lingua.english.data.speech.SpeechInputController
import com.lingua.english.data.speech.SpeechInputState
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaSecondary

/** 建一个跟随组合生命周期的语音输入控制器; 回调每次重组重新赋值, 不会拿旧闭包。 */
@Composable
fun rememberSpeechInput(
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
): SpeechInputController {
    val context = LocalContext.current
    val controller = remember { SpeechInputController(context) }
    controller.onPartial = onPartial
    controller.onFinal = onFinal
    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }
    return controller
}

/**
 * 麦克风按钮: 处理权限申请 → 开始/停止听。
 * [onStartDictation] 在真正开始听之前回调, 供调用方记下当前输入框内容作拼接基底。
 */
@Composable
fun MicButton(
    controller: SpeechInputController,
    languageTag: String,
    onStartDictation: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsStateWithLifecycle()
    val listening = state is SpeechInputState.Listening

    // 兜底: 静默识别连续失败后改用系统听写弹窗 (识别器 app 自己的界面, 兼容性最好)
    val dialogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { controller.onFinal(it) }
    }

    fun beginListening() {
        onStartDictation()
        if (controller.shouldUseDialog) {
            runCatching { dialogLauncher.launch(SpeechInputController.dialogIntent(languageTag)) }
                .onFailure { controller.reportExternalError("设备上没有可用的语音输入界面") }
        } else {
            controller.toggle(languageTag)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 刚授权完麦克风可能还被权限弹窗占着, 立刻启动会报 ERROR_CLIENT, 缓一拍
            scope.launch {
                delay(400)
                beginListening()
            }
        }
    }

    IconButton(onClick = {
        if (listening) {
            controller.stop()
            return@IconButton
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            beginListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }) {
        Icon(
            if (listening) Icons.Filled.Mic else Icons.Outlined.Mic,
            contentDescription = if (listening) "停止语音输入" else "语音输入",
            tint = if (listening) LinguaSecondary else LinguaOnSurfaceMuted,
        )
    }
}

/** 听写文本拼接: 中日韩字符之间不加空格, 英文之间加空格。 */
fun appendDictation(base: String, text: String): String {
    if (base.isBlank()) return text
    val trimmed = base.trimEnd()
    val cjkJoin = trimmed.lastOrNull()?.let { it.code > 0x2E7F } == true &&
        text.firstOrNull()?.let { it.code > 0x2E7F } == true
    return if (cjkJoin) trimmed + text else "$trimmed $text"
}
