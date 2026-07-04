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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaTertiary

/**
 * 在 app 内登录 YouTube: 风控 IP 上未登录访问一律被拒 (LOGIN_REQUIRED),
 * 登录 cookie 存进 app 的 WebView 后, 字幕通道即可带登录态放行。
 * 「去登录」直达 Google 标准登录流程并强制跳回 YouTube (确保凭据交接完成);
 * 顶栏实时检测 youtube.com 的登录 cookie, 变绿才算真的成了。
 * 登录动作完全由用户自己在 Google 页面完成, app 不经手账号密码。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(onBack: () -> Unit) {
    var loggedIn by remember { mutableStateOf(isYouTubeLoggedIn()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("登录 YouTube", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (loggedIn) "已登录 ✓ 返回重试拉字幕" else "未登录 · 点右边「去登录」",
                    color = if (loggedIn) LinguaTertiary else LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            if (!loggedIn) {
                TextButton(onClick = { webViewRef?.loadUrl(LOGIN_URL) }) { Text("去登录") }
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
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val ok = isYouTubeLoggedIn()
                            if (ok && !loggedIn) {
                                runCatching { CookieManager.getInstance().flush() }
                            }
                            loggedIn = ok
                        }
                    }
                    webViewRef = this
                    loadUrl(LOGIN_URL)
                }
            },
        )
    }
}

/** youtube.com 域下有 SAPISID/SID 会话 cookie = 真的登录到了 YouTube。 */
private fun isYouTubeLoggedIn(): Boolean = runCatching {
    val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
    "SAPISID" in cookies || "__Secure-3PAPISID" in cookies
}.getOrDefault(false)

/** Google 标准登录, 完成后强制跳回 YouTube 完成凭据交接。 */
private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"
