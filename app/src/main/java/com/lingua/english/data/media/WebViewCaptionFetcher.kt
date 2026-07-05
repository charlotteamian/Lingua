package com.lingua.english.data.media

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import org.json.JSONObject

/**
 * 终极兜底: 裸 HTTP 被 YouTube 风控 (LOGIN_REQUIRED) 时, 用真实 WebView
 * 加载 watch 页——完整执行 YouTube 的反机器人 JS, 和平时浏览器看视频同一通道。
 *
 * 字幕下载分三层 (YouTube 对裸下载字幕地址会返回 200 空正文, 要 PO token):
 * ① 页内 XHR 直接下载字幕全文;
 * ② 同时触发页面里的真播放器加载字幕, shouldInterceptRequest 截获它自己发的
 *    带 pot 校验参数的字幕请求, 用这个地址页内再下一次;
 * ③ 都空手而归就把地址 (截获的优先) 交回 OkHttp 带 cookie 下载兜底。
 */
class WebViewCaptionFetcher(private val context: Context) {

    /** WebView 通道结果: 页内下载到了就带全文, 否则带地址交给 OkHttp 兜底。 */
    data class RawCaptions(
        val title: String = "",
        val baseUrl: String = "",
        /** 页内已下载到的字幕 json3 全文 (空 = 页内没下载成) */
        val captionJson: String = "",
        /** 截获的播放器自己发出的字幕请求地址 (带 pot 校验参数, 最接近真浏览器) */
        val pottedUrl: String = "",
    )

    @Serializable
    private data class Track(val title: String = "", val baseUrl: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(videoId: String): RawCaptions = withContext(Dispatchers.Main) {
        withTimeout(60_000) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                val state = object {
                    @Volatile var title = ""
                    @Volatile var baseUrl = ""
                    @Volatile var pottedUrl = ""
                    @Volatile var pottedTried = false
                }
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
                // 触发播放器加载字幕需要 JS 能自主起播
                webView.settings.mediaPlaybackRequiresUserGesture = false
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
                    fun onTrack(payload: String) {
                        val parsed = runCatching {
                            json.decodeFromString(Track.serializer(), payload)
                        }.getOrNull()
                        if (parsed == null || parsed.baseUrl.isBlank()) {
                            finish { cont.resumeWithException(AiException("字幕数据解析失败")) }
                        } else {
                            state.title = parsed.title
                            state.baseUrl = parsed.baseUrl
                        }
                    }

                    @JavascriptInterface
                    fun onCaptions(text: String) {
                        if (text.isNotBlank()) {
                            finish {
                                cont.resume(
                                    RawCaptions(state.title, state.baseUrl, text, state.pottedUrl)
                                )
                            }
                            return
                        }
                        if (state.pottedTried) {
                            // 截获地址页内也下载失败, 把两个地址都交回 OkHttp 兜底
                            finish {
                                cont.resume(RawCaptions(state.title, state.baseUrl, "", state.pottedUrl))
                            }
                            return
                        }
                        // 裸地址页内下载空手而归 → 等播放器自己发字幕请求 (带 pot), 截到就再下一次
                        fun poll(attempt: Int) {
                            if (done.get()) return
                            val potted = state.pottedUrl
                            when {
                                potted.isNotEmpty() -> {
                                    state.pottedTried = true
                                    runCatching {
                                        webView.evaluateJavascript(
                                            "window.__linguaDownload && __linguaDownload(${JSONObject.quote(potted)});",
                                            null,
                                        )
                                    }
                                    // 它的结果由下一次 onCaptions 收尾; 20s 没回音就强制收尾
                                    webView.postDelayed({
                                        if (!done.get()) finish {
                                            cont.resume(RawCaptions(state.title, state.baseUrl, "", potted))
                                        }
                                    }, 20_000)
                                }
                                attempt < 16 -> webView.postDelayed({ poll(attempt + 1) }, 500)
                                else -> finish {
                                    cont.resume(RawCaptions(state.title, state.baseUrl, "", ""))
                                }
                            }
                        }
                        webView.post { poll(0) }
                    }

                    @JavascriptInterface
                    fun onError(message: String) = finish {
                        cont.resumeWithException(AiException(message.take(160)))
                    }
                }, "CapBridge")

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(EXTRACT_JS.replace("%VID%", videoId), null)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val u = request.url?.toString().orEmpty()
                        // 播放器自己发的字幕请求带 pot 参数 (我们页内 XHR 的没有), 只截它的
                        if (u.contains("/api/timedtext") && u.contains("pot=") && state.pottedUrl.isEmpty()) {
                            state.pottedUrl = u
                        }
                        return null
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
         * 页内脚本: ① 取 ytInitialPlayerResponse 的字幕轨 (等不到就用页面自己的
         * ytcfg 密钥调站内 player API); ② 选英文轨后先回传地址 (onTrack), 再触发
         * 真播放器加载字幕 (让它发出带 pot 的请求供 Kotlin 截获), 同时页内 XHR
         * 直接下载字幕全文 (onCaptions)。报错附 host/标题便于远程诊断。
         */
        private val EXTRACT_JS = """
            (function(){
              var VIDEO = '%VID%';
              if (window.__linguaRan) return;
              window.__linguaRan = true;
              var tries = 0;
              var apiTried = false;
              function fail(msg){
                try { CapBridge.onError(msg + ' [' + location.host + ' · ' + (document.title||'').slice(0,40) + ']'); } catch(e) {}
              }
              function cfgGet(name){
                try { return (window.ytcfg && ytcfg.get) ? ytcfg.get(name) : null; } catch(e) { return null; }
              }
              function download(u){
                u = u.replace(/([?&])fmt=[^&]*/, function(m, p1){ return p1 + 'fmt=json3'; });
                if (u.indexOf('fmt=') < 0) u += '&fmt=json3';
                var xhr = new XMLHttpRequest();
                xhr.open('GET', u, true);
                xhr.timeout = 12000;
                xhr.onload = function(){ try { CapBridge.onCaptions(xhr.status === 200 ? (xhr.responseText||'') : ''); } catch(e){} };
                xhr.onerror = function(){ try { CapBridge.onCaptions(''); } catch(e){} };
                xhr.ontimeout = xhr.onerror;
                try { xhr.send(); } catch(e){ xhr.onerror(); }
              }
              window.__linguaDownload = download;
              function triggerPlayer(){
                try {
                  var p = document.getElementById('movie_player');
                  if (!p) return;
                  try { p.mute && p.mute(); } catch(e){}
                  try { p.loadModule && p.loadModule('captions'); } catch(e){}
                  try { p.setOption && p.setOption('captions','track',{languageCode:'en'}); } catch(e){}
                  try { p.playVideo && p.playVideo(); } catch(e){}
                } catch(e){}
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
                try { CapBridge.onTrack(JSON.stringify({title: title || document.title || '', baseUrl: pick.baseUrl})); } catch(e){}
                triggerPlayer();
                download(pick.baseUrl);
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
