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

    /** WebView 只负责拿到字幕轨地址, 下载由 OkHttp 带 cookie 完成 (页内 fetch 易被代理掐)。 */
    @Serializable
    data class RawCaptions(val title: String = "", val baseUrl: String = "")

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
                webView.settings.domStorageEnabled = true
                // 桌面 UA: 避免被重定向到 m.youtube.com (页面结构不同, 播放器数据出得晚)
                webView.settings.userAgentString =
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                webView.settings.useWideViewPort = true
                runCatching {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                }
                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onResult(payload: String) = finish {
                        val parsed = runCatching {
                            json.decodeFromString(RawCaptions.serializer(), payload)
                        }.getOrNull()
                        if (parsed == null || parsed.baseUrl.isBlank()) {
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
                        view.evaluateJavascript(EXTRACT_JS.replace("%VID%", videoId), null)
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
        /**
         * 页内脚本, 两条路: ① 页面自带的 ytInitialPlayerResponse;
         * ② 等不到就用页面自己的 ytcfg 密钥+上下文调站内 player API
         * (等于网站自己发请求, 带全套 botguard/cookie)。报错附 host/标题便于远程诊断。
         */
        private val EXTRACT_JS = """
            (function(){
              var VIDEO = '%VID%';
              var tries = 0;
              var apiTried = false;
              function fail(msg){
                try { CapBridge.onError(msg + ' [' + location.host + ' · ' + (document.title||'').slice(0,40) + ']'); } catch(e) {}
              }
              function cfgGet(name){
                try { return (window.ytcfg && ytcfg.get) ? ytcfg.get(name) : null; } catch(e) { return null; }
              }
              function pickAndFetch(tracks, title){
                var en = [];
                for (var i = 0; i < tracks.length; i++) {
                  if (((tracks[i].languageCode)||'').indexOf('en') === 0) en.push(tracks[i]);
                }
                var pick = null;
                for (var j = 0; j < en.length; j++) { if (en[j].kind !== 'asr') { pick = en[j]; break; } }
                if (!pick && en.length) pick = en[0];
                if (!pick) { fail('没有英文字幕'); return; }
                CapBridge.onResult(JSON.stringify({title: title || document.title || '', baseUrl: pick.baseUrl}));
              }
              function viaSiteApi(){
                var key = cfgGet('INNERTUBE_API_KEY');
                var ctx = cfgGet('INNERTUBE_CONTEXT');
                if (!key || !ctx) return false;
                apiTried = true;
                fetch('/youtubei/v1/player?key=' + key, {
                  method: 'POST', credentials: 'include',
                  headers: {'Content-Type': 'application/json'},
                  body: JSON.stringify({context: ctx, videoId: VIDEO})
                }).then(function(r){ return r.json(); }).then(function(pr){
                  var rr = pr.captions && pr.captions.playerCaptionsTracklistRenderer;
                  if (rr && rr.captionTracks && rr.captionTracks.length) {
                    pickAndFetch(rr.captionTracks, pr.videoDetails && pr.videoDetails.title);
                  } else {
                    var st = pr.playabilityStatus && pr.playabilityStatus.status;
                    fail('站内API没给字幕 (status=' + st + ')');
                  }
                }).catch(function(e){ fail('站内API失败: ' + e); });
                return true;
              }
              function grab(){
                tries++;
                var pr = window.ytInitialPlayerResponse;
                var rr = pr && pr.captions && pr.captions.playerCaptionsTracklistRenderer;
                if (rr && rr.captionTracks && rr.captionTracks.length) {
                  pickAndFetch(rr.captionTracks, pr.videoDetails && pr.videoDetails.title);
                  return;
                }
                if (pr && pr.videoDetails && tries > 3 && !(rr && rr.captionTracks)) {
                  // 播放器数据在了但没字幕轨 → 用站内 API 再确认一次
                  if (!apiTried && viaSiteApi()) return;
                  fail('这个视频没有字幕轨');
                  return;
                }
                if (tries > 12 && !apiTried && viaSiteApi()) return;
                if (tries < 100) { setTimeout(grab, 300); return; }
                fail('页面没加载出播放器数据');
              }
              grab();
            })();
        """.trimIndent()
    }
}
