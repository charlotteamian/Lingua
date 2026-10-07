package com.lingua.english.ui.screens.media

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lingua.english.data.local.entity.MediaItemEntity
import com.lingua.english.data.media.TranscriptLine
import com.lingua.english.data.media.WebViewCaptionFetcher
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaAmber
import com.lingua.english.ui.theme.LinguaBackground
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaPrimary
import com.lingua.english.ui.theme.LinguaSurfaceVariant
import com.lingua.english.ui.theme.LinguaTertiary

/**
 * 听读播放页: 上半 YouTube 播放器 / 音频控制条, 下半同步高亮字幕。
 * 字幕点词查词典; 点行出操作: 定位播放 / 收藏 / AI 解释。
 */
@Composable
fun MediaPlayerScreen(vm: MediaPlayerViewModel, onBack: () -> Unit) {
    val item by vm.item.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val savedTexts by vm.savedTexts.collectAsStateWithLifecycle()
    val explanations by vm.explanations.collectAsStateWithLifecycle()
    val transcribe by vm.transcribe.collectAsStateWithLifecycle()
    val lookup = LocalWordLookup.current

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // 选中行来自「暂停自动定位」还是手动点选: 恢复播放时只清前者
    var autoSelectedFromPause by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val media = item ?: return

    fun seekTo(sec: Double) {
        when (media.type) {
            MediaItemEntity.TYPE_YOUTUBE ->
                webView?.evaluateJavascript("window.seekTo&&seekTo(${sec})", null)
            else -> vm.seekAudioTo(sec)
        }
    }

    // 当前行 = 起始时间 ≤ 播放位置的最后一行
    val currentIndex = remember(position, lines) {
        lines.indexOfLast { it.s <= position + 0.2 }
    }
    // 没选中行时跟随播放自动滚动 (选中 = 用户在操作, 不打扰)
    LaunchedEffect(currentIndex, selectedIndex) {
        if (selectedIndex == null && currentIndex >= 2) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
        }

        when (media.type) {
            MediaItemEntity.TYPE_YOUTUBE -> YouTubePane(
                videoId = media.source,
                onTime = vm::updatePosition,
                onUserPause = { sec ->
                    // 用户点了暂停 (全屏时已同步缩回): 字幕定位到暂停那句并弹出整句操作
                    val idx = lines.indexOfLast { it.s <= sec + 0.2 }
                    if (idx >= 0) {
                        selectedIndex = idx
                        autoSelectedFromPause = true
                        scope.launch { listState.animateScrollToItem((idx - 1).coerceAtLeast(0)) }
                    }
                },
                onPlaying = {
                    // 恢复播放: 清掉暂停时自动选中的行, 回到跟随滚动; 手动选的不动
                    if (autoSelectedFromPause) {
                        autoSelectedFromPause = false
                        selectedIndex = null
                    }
                },
                onWebView = { webView = it },
            )

            else -> AudioControls(
                playing = playing,
                position = position,
                onToggle = vm::toggleAudio,
                onSeekBy = vm::seekBy,
            )
        }

        if (!media.transcribed) {
            TranscribePrompt(vm, transcribe)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(lines.size, key = { it }) { index ->
                val line = lines[index]
                TranscriptRow(
                    line = line,
                    isCurrent = index == currentIndex,
                    isSelected = index == selectedIndex,
                    saved = line.t.trim().lowercase() in savedTexts,
                    explanation = explanations[index],
                    onWordTap = lookup,
                    onSelect = {
                        selectedIndex = if (selectedIndex == index) null else index
                        autoSelectedFromPause = false
                    },
                    onSeek = { seekTo(line.s) },
                    onCollect = { vm.collect(line) },
                    onExplain = { vm.explain(index, line) },
                    onRetryExplain = { vm.retryExplain(index, line) },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePane(
    videoId: String,
    onTime: (Double) -> Unit,
    onUserPause: (Double) -> Unit,
    onPlaying: () -> Unit,
    onWebView: (WebView) -> Unit,
) {
    // 起播前盖加载层, 超时换成错误+重试 —— 不允许再出现「无解释的黑屏」
    var started by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var pane by remember { mutableStateOf<WebView?>(null) }
    // factory 只跑一次, 回调经 rememberUpdatedState 转发, 避免闭包捕获过期的 lines
    val currentOnTime by rememberUpdatedState(onTime)
    val currentOnUserPause by rememberUpdatedState(onUserPause)
    val currentOnPlaying by rememberUpdatedState(onPlaying)

    LaunchedEffect(loadAttempt) {
        timedOut = false
        delay(35_000)
        if (!started) timedOut = true
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // watch 页模式下播放器被 CSS 钉满 WebView 视口, 16:9 视频正好铺满无黑边
            .aspectRatio(16f / 9f),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    // 没有 DOM 存储 YouTube 播放器会直接黑屏
                    settings.domStorageEnabled = true
                    // 允许 JS 起播: 自动起播和「从这句播」都是代码发起的, 不算页内手势
                    settings.mediaPlaybackRequiresUserGesture = false
                    // 桌面版 watch 页要宽视口 + 整体缩放贴合窗格宽度
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // 和字幕通道完全同款环境 (桌面 UA + 登录 cookie): 这是这台设备上
                    // 唯一验证过能真正起播的通道; iframe 嵌入会被风控静默拦死 (黑屏根源)
                    settings.userAgentString = WebViewCaptionFetcher.DESKTOP_UA
                    runCatching {
                        val cookies = android.webkit.CookieManager.getInstance()
                        cookies.setAcceptThirdPartyCookies(this, true)
                        cookies.setCookie("https://www.youtube.com", "SOCS=CAI")
                    }

                    // 播放器的 ⤢ 全屏按钮: 把视频视图铺到整个 Activity 窗口上;
                    // exitFullscreen() 供「用户暂停 → 画面缩回」程序化退出
                    val chromeClient = object : android.webkit.WebChromeClient() {
                        private var fullscreenView: android.view.View? = null
                        private var fullscreenCallback: CustomViewCallback? = null

                        override fun onShowCustomView(
                            view: android.view.View,
                            callback: CustomViewCallback,
                        ) {
                            if (fullscreenView != null) {
                                callback.onCustomViewHidden()
                                return
                            }
                            val decor = context.findActivity()?.window?.decorView
                                as? android.widget.FrameLayout ?: return
                            fullscreenView = view
                            fullscreenCallback = callback
                            decor.addView(
                                view,
                                android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }

                        override fun onHideCustomView() {
                            val decor = context.findActivity()?.window?.decorView
                                as? android.widget.FrameLayout
                            fullscreenView?.let { decor?.removeView(it) }
                            fullscreenView = null
                            fullscreenCallback?.onCustomViewHidden()
                            fullscreenCallback = null
                        }

                        fun exitFullscreen() {
                            if (fullscreenView == null) return
                            // 先让页面自己退全屏 (会触发 onHideCustomView, 播放器状态才一致);
                            // 页面没响应就直接收掉悬浮视图兜底
                            evaluateJavascript(
                                "try{if(document.fullscreenElement)document.exitFullscreen()}catch(e){}",
                                null,
                            )
                            postDelayed({ if (fullscreenView != null) onHideCustomView() }, 400)
                        }
                    }
                    webChromeClient = chromeClient

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun postTime(t: Double) {
                            // 有真实进度 = 起播成功, 撤掉加载层
                            if (t > 0.2 && !started) post { started = true }
                            currentOnTime(t)
                        }

                        @JavascriptInterface
                        fun postPaused(t: Double) {
                            // 用户主动暂停: 全屏先缩回, 再让字幕定位到暂停处
                            post {
                                chromeClient.exitFullscreen()
                                currentOnUserPause(t)
                            }
                        }

                        @JavascriptInterface
                        fun postPlaying() {
                            post {
                                started = true
                                currentOnPlaying()
                            }
                        }
                    }, "Bridge")
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            // 挂全局 seekTo + 自动起播 + 进度轮询 + 版面钉死 (脚本幂等)
                            if (url.contains("youtube.com/watch")) {
                                view.evaluateJavascript(WATCH_PAGE_JS, null)
                            }
                        }
                    }
                    loadWatchPage(this, videoId)
                    onWebView(this)
                    pane = this
                }
            },
        )
        // 起播前的原生盖层: 页面加载/风控/代理抖动期间用户看到的是状态, 不是黑屏
        if (!started) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LinguaBackground),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (timedOut) {
                    Text(
                        "播放器没起播 — 多半是网络不稳; 反复失败就回听读页确认 YouTube 登录还在",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    TextButton(onClick = {
                        pane?.let { loadWatchPage(it, videoId) }
                        loadAttempt++
                    }) { Text("重试") }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        "播放器加载中…",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * 加载 watch 页并安排两次补注入: onPageFinished 在慢代理下可能迟迟不来。
 * 脚本幂等, 多注入无害。调度用主线程 Handler 而非 View.post ——
 * factory 时 View 还没挂载, 未挂载 View 的 post 永远不执行 (字幕通道掉过的坑)。
 */
private fun loadWatchPage(webView: WebView, videoId: String) {
    webView.loadUrl("https://www.youtube.com/watch?v=$videoId&hl=en")
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    listOf(6_000L, 15_000L).forEach { delayMs ->
        handler.postDelayed({
            if (webView.isAttachedToWindow) {
                runCatching { webView.evaluateJavascript(WATCH_PAGE_JS, null) }
            }
        }, delayMs)
    }
}

/** WebView 所在的 Activity (全屏视图要挂到它的 decorView 上)。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * watch 页注入脚本 (播放器唯一模式; iframe 嵌入已整个移除——它在风控 IP 下
 * 会静默失败, 没有任何错误回调, 就是「一直黑屏」的根源), 干五件事:
 * ① 自动起播: 等 movie_player / <video> 就绪后反复 playVideo() 直到真播起来
 *    (字幕通道触发播放器的同款套路, 在这台设备上验证过); 用户暂停过就不抢;
 * ② 同名 seekTo (优先 movie_player API, 处理未缓冲区间更稳);
 * ③ 每 500ms 回报 video.currentTime; 广告播放中 (ad-showing) 不回报,
 *    字幕不跟着广告时间轴跑;
 * ④ 播放/暂停状态机: 触屏在先的 pause = 用户主动 → 通知原生 (退全屏+字幕定位);
 *    无触屏的 pause = 平台自动暂停, 由看门狗定时恢复——
 *    比单次事件里抢救可靠 (事件丢了/play() 被再次掐掉都能兜住);
 * ⑤ 版面: 播放器直接 position:fixed 钉满 WebView 视口, 页面排版不再影响它,
 *    video 绝对定位填满 + object-fit:contain 自己居中 → 不依赖 YouTube 的
 *    尺寸计算, 没有排版偏移就没有黑边。
 */
private val WATCH_PAGE_JS = """
    (function(){
      window.seekTo = function(s){
        var p=document.getElementById('movie_player');
        if(p&&p.seekTo){ try{ p.seekTo(s,true); p.playVideo&&p.playVideo(); return; }catch(e){} }
        var v=document.querySelector('video');
        if(v){ v.currentTime=s; var pr=v.play(); if(pr&&pr.catch) pr.catch(function(){}); }
      };
      if (!window.__linguaPoll) {
        window.__linguaPoll = setInterval(function(){
          try {
            var p=document.getElementById('movie_player');
            if(p&&p.classList&&p.classList.contains('ad-showing')) return;
            var v=document.querySelector('video');
            if(v) Bridge.postTime(v.currentTime);
          } catch(e){}
        }, 500);
      }
      if (!window.__linguaEvents) {
        window.__linguaEvents = true;
        var lastTouch = 0;
        window.__linguaUserPaused = false;
        window.__linguaEverPlayed = false;
        var mark = function(){ lastTouch = Date.now(); };
        document.addEventListener('touchstart', mark, true);
        document.addEventListener('touchend', mark, true);
        document.addEventListener('pointerdown', mark, true);
        // play/pause 事件不冒泡, 捕获阶段在 document 上截 (video 换元素也不失效)
        document.addEventListener('play', function(e){
          if (e.target && e.target.tagName === 'VIDEO') {
            window.__linguaUserPaused = false;
            window.__linguaEverPlayed = true;
            try { Bridge.postPlaying(); } catch(err){}
          }
        }, true);
        document.addEventListener('pause', function(e){
          var v = e.target;
          if (!v || v.tagName !== 'VIDEO' || v.ended) return;
          if (Date.now() - lastTouch < 900) {
            // 有触屏在先 = 用户主动暂停: 记住这个意图, 通知原生
            window.__linguaUserPaused = true;
            try { Bridge.postPaused(v.currentTime); } catch(err){}
          }
          // 无触屏的暂停 = 平台自动暂停, 交给看门狗恢复
        }, true);
        // 看门狗: 用户没暂停但视频停了 → 恢复播放。最近有触屏时不动手
        // (可能是没归类到的用户暂停, 别抢); 起播前 (currentTime≈0) 也不硬拉。
        setInterval(function(){
          try {
            var v = document.querySelector('video');
            if (!v || !v.paused || v.ended || window.__linguaUserPaused) return;
            if (v.currentTime < 0.3) return;
            if (Date.now() - lastTouch < 2500) return;
            var p = v.play(); if (p && p.catch) p.catch(function(){});
          } catch(e){}
        }, 1200);
      }
      if (!window.__linguaAutoStart) {
        window.__linguaAutoStart = true;
        // 起播驱动: watch 页自己不会自动播; 等播放器就绪反复按, 直到真播起来。
        // 800ms × 45 ≈ 36s, 盖住慢代理; 播过一次或用户暂停过就永久收手。
        var startTries = 0;
        var start = function(){
          if (window.__linguaEverPlayed || window.__linguaUserPaused) return;
          try {
            var p = document.getElementById('movie_player');
            if (p && p.playVideo) { try { p.playVideo(); } catch(e){} }
            var v = document.querySelector('video');
            if (v) { var pr = v.play(); if (pr && pr.catch) pr.catch(function(){}); }
          } catch(e){}
          if (!window.__linguaEverPlayed && ++startTries < 45) setTimeout(start, 800);
        };
        start();
      }
      if (!document.getElementById('__linguaCss')) {
        var st = document.createElement('style');
        st.id = '__linguaCss';
        st.textContent =
          'html,body{overflow:hidden!important;background:#06080f!important;}' +
          '#masthead-container,#masthead,ytd-mini-guide-renderer,' +
          'ytm-mobile-topbar-renderer,.mobile-topbar-header,ytm-pivot-bar-renderer,' +
          'header,.mobile-topbar-header-content{display:none!important;}' +
          // 播放器钉满视口, 盖住页面其它内容; 没有排版偏移就没有黑边
          '#player-container-id,#player,.player-container{position:fixed!important;' +
          'left:0!important;top:0!important;width:100vw!important;height:100vh!important;' +
          'margin:0!important;padding:0!important;z-index:9999!important;}' +
          '#movie_player,.html5-video-player{position:absolute!important;left:0!important;' +
          'top:0!important;width:100%!important;height:100%!important;max-width:none!important;}' +
          '.html5-video-container{position:absolute!important;left:0!important;top:0!important;' +
          'width:100%!important;height:100%!important;}' +
          'video.video-stream,video.html5-main-video{position:absolute!important;' +
          'left:0!important;top:0!important;width:100%!important;height:100%!important;' +
          'object-fit:contain!important;transform:none!important;}';
        (document.head||document.documentElement).appendChild(st);
        window.scrollTo(0,0);
      }
      // 布局变了让播放器重排控件层; 多打几次等 SPA 稳定
      var kick = function(){ try { window.dispatchEvent(new Event('resize')); } catch(e){} };
      kick(); setTimeout(kick, 300); setTimeout(kick, 900); setTimeout(kick, 1800);
    })();
""".trimIndent()

@Composable
private fun AudioControls(
    playing: Boolean,
    position: Double,
    onToggle: () -> Unit,
    onSeekBy: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { onSeekBy(-10) }) {
            Icon(Icons.Filled.Replay10, contentDescription = "后退10秒", tint = LinguaOnSurfaceMuted)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                tint = LinguaPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { onSeekBy(10) }) {
            Icon(Icons.Filled.Forward10, contentDescription = "前进10秒", tint = LinguaOnSurfaceMuted)
        }
        Text(
            "%d:%02d".format((position / 60).toInt(), (position % 60).toInt()),
            color = LinguaOnSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TranscribePrompt(vm: MediaPlayerViewModel, state: TranscribeState) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val sizeMb = remember { vm.audioSizeMb() }
        Text(
            buildString {
                append("还没有字幕。AI 转写走你的 Gemini Key")
                sizeMb?.let { append(" (文件 ${"%.0f".format(it)}MB, 上限 18MB)") }
                append(", 几分钟音频约花几分钱, 一次转写永久保存。")
            },
            color = LinguaOnSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = vm::startTranscribe, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
            if (state.running) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  转写中, 长音频要几分钟…")
            } else {
                Text("开始 AI 转写")
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun TranscriptRow(
    line: TranscriptLine,
    isCurrent: Boolean,
    isSelected: Boolean,
    saved: Boolean,
    explanation: String?,
    onWordTap: (String) -> Unit,
    onSelect: () -> Unit,
    onSeek: () -> Unit,
    onCollect: () -> Unit,
    onExplain: () -> Unit,
    onRetryExplain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isCurrent -> LinguaPrimary.copy(alpha = 0.14f)
                    isSelected -> LinguaSurfaceVariant.copy(alpha = 0.5f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "%d:%02d".format((line.s / 60).toInt(), (line.s % 60).toInt()),
                color = LinguaOnSurfaceMuted.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp, end = 8.dp),
            )
            TappableText(
                line.t,
                onWordTap = onWordTap,
                // bodyLarge: 手机上看视频学习, 字幕小了没法用
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else LinguaOnSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            // 整行操作的把手: 正文每个单词都是查词热区, 空白难点中, 必须有明确入口
            IconButton(onClick = onSelect, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "整句操作",
                    tint = LinguaOnSurfaceMuted.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (isSelected) {
            Row {
                TextButton(onClick = onSeek) { Text("▶ 从这句播") }
                TextButton(onClick = onCollect, enabled = !saved) {
                    Text(if (saved) "✓ 已收" else "收藏", color = if (saved) LinguaTertiary else MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { if (explanation != null && explanation != "…") onRetryExplain() else onExplain() }) {
                    Text("翻译")
                }
            }
        }
        explanation?.let { text ->
            Text(
                if (text == "…") "AI 解释中…" else text,
                color = LinguaAmber,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
