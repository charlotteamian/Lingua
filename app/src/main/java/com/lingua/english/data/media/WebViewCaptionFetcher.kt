package com.lingua.english.data.media

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lingua.english.data.ai.AiException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 终极兜底: 裸 HTTP 被 YouTube 风控 (LOGIN_REQUIRED) 时, 用真实 WebView
 * 加载 watch 页——完整执行 YouTube 的反机器人 JS, 和平时浏览器看视频同一通道——
 * 再从页面里取 ytInitialPlayerResponse 的字幕轨并在页内 fetch 字幕全文。
 */
class WebViewCaptionFetcher(private val context: Context) {

    @Serializable
    data class RawCaptions(val title: String = "", val captions: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(videoId: String): RawCaptions = withContext(Dispatchers.Main) {
        withTimeout(45_000) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                // 预置同意 cookie, 避开欧盟出口 IP 的 consent 拦截页
                runCatching {
                    CookieManager.getInstance().setCookie("https://www.youtube.com", "SOCS=CAI")
                }
                val webView = WebView(context)

                fun finish(block: () -> Unit) {
                    if (done.compareAndSet(false, true)) {
                        block()
                        webView.post { runCatching { webView.destroy() } }
                    }
                }

                webView.settings.javaScriptEnabled = true
                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onResult(payload: String) = finish {
                        val parsed = runCatching {
                            json.decodeFromString(RawCaptions.serializer(), payload)
                        }.getOrNull()
                        if (parsed == null || parsed.captions.isBlank()) {
                            cont.resumeWithException(AiException("字幕数据解析失败"))
                        } else {
                            cont.resume(parsed)
                        }
                    }

                    @JavascriptInterface
                    fun onError(message: String) = finish {
                        cont.resumeWithException(AiException(message.take(120)))
                    }
                }, "CapBridge")

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(EXTRACT_JS, null)
                    }
                }
                cont.invokeOnCancellation {
                    webView.post { runCatching { webView.destroy() } }
                }
                webView.loadUrl("https://www.youtube.com/watch?v=$videoId&hl=en")
            }
        }
    }

    companion object {
        /** 页内脚本: 等 playerResponse → 选英文轨 (人工优先) → 页内 fetch json3 → 回传。 */
        private val EXTRACT_JS = """
            (function(){
              var tries = 0;
              function grab(){
                tries++;
                var pr = window.ytInitialPlayerResponse;
                if ((!pr || !pr.videoDetails) && tries < 60) { setTimeout(grab, 300); return; }
                if (!pr || !pr.videoDetails) { CapBridge.onError('页面没加载出播放器数据 (网络或风控问题)'); return; }
                var title = (pr.videoDetails && pr.videoDetails.title) || '';
                var r = pr.captions && pr.captions.playerCaptionsTracklistRenderer;
                var tracks = r && r.captionTracks;
                if (!tracks || !tracks.length) { CapBridge.onError('这个视频没有字幕轨'); return; }
                var en = [];
                for (var i = 0; i < tracks.length; i++) {
                  var lc = tracks[i].languageCode || '';
                  if (lc.indexOf('en') === 0) en.push(tracks[i]);
                }
                var pick = null;
                for (var j = 0; j < en.length; j++) { if (en[j].kind !== 'asr') { pick = en[j]; break; } }
                if (!pick && en.length) pick = en[0];
                if (!pick) { CapBridge.onError('没有英文字幕 (其它语言的没法用来学英文)'); return; }
                fetch(pick.baseUrl + '&fmt=json3', {credentials: 'include'})
                  .then(function(resp){ return resp.text(); })
                  .then(function(text){ CapBridge.onResult(JSON.stringify({title: title, captions: text})); })
                  .catch(function(e){ CapBridge.onError('字幕下载失败: ' + e); });
              }
              grab();
            })();
        """.trimIndent()
    }
}
