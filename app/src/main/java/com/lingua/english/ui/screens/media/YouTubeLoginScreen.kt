package com.lingua.english.ui.screens.media

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted

/**
 * 在 app 内登录 YouTube: 风控 IP 上未登录访问一律被拒 (LOGIN_REQUIRED),
 * 登录 cookie 存进 app 的 WebView 后, 字幕通道即可带登录态放行。
 * 登录动作完全由用户自己在 Google 页面完成, app 不经手账号密码。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("登录 YouTube", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点右上角头像登录, 完成后点「完成」回去重试",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onBack) { Text("完成") }
        }

        DisposableEffect(Unit) {
            onDispose {
                // 确保登录 cookie 落盘, 字幕通道立刻可用
                runCatching { CookieManager.getInstance().flush() }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 去掉 UA 里的 "; wv" 标记: Google 登录页会拒绝内嵌 WebView
                    settings.userAgentString = settings.userAgentString
                        .replace("; wv", "")
                        .replace(" Version/4.0", "")
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl("https://m.youtube.com")
                }
            },
        )
    }
}
