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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.local.entity.MediaItemEntity
import com.lingua.english.data.media.TranscriptLine
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.TappableText
import com.lingua.english.ui.theme.LinguaAmber
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
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val media = item ?: return

    fun seekTo(sec: Double) {
        when (media.type) {
            MediaItemEntity.TYPE_YOUTUBE ->
                webView?.evaluateJavascript("seekTo(${sec})", null)
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
                    onSelect = { selectedIndex = if (selectedIndex == index) null else index },
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
    onWebView: (WebView) -> Unit,
) {
    var playerError by remember { mutableStateOf<String?>(null) }
    // watch 页模式: 页面自带播放器 chrome, 窗口加高一档才能放下完整画框
    var watchMode by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (watchMode) 16f / 10.5f else 16f / 9f),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    // 没有 DOM 存储 YouTube 播放器会直接黑屏
                    settings.domStorageEnabled = true
                    // 允许 JS 起播: 「从这句播」是原生按钮发起的, 不算 WebView 内手势
                    settings.mediaPlaybackRequiresUserGesture = false
                    // 去掉 "; wv" WebView 标记: 嵌入播放器风控会把 WebView 当机器人 (code 152/153)
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    var fellBack = false

                    fun fallbackToWatchPage() {
                        if (fellBack) return
                        fellBack = true
                        post {
                            playerError = null
                            watchMode = true
                            loadUrl("https://m.youtube.com/watch?v=$videoId")
                        }
                    }
                    // 播放器的 ⤢ 全屏按钮: 把视频视图铺到整个 Activity 窗口上
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        private var fullscreenView: android.view.View? = null

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
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun postTime(t: Double) = onTime(t)

                        @JavascriptInterface
                        fun postError(code: Int) {
                            if (code != 100 && !fellBack) {
                                // 嵌入被拒 (101/150/152/153…): 换真 watch 页播放, 这条通道已验证可用
                                fallbackToWatchPage()
                            } else {
                                playerError = when (code) {
                                    100 -> "视频不存在或已删除"
                                    else -> "播放器加载失败 (code $code), 退出重进试试"
                                }
                            }
                        }
                    }, "Bridge")
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            // watch 页模式: 挂全局 seekTo + 进度轮询 (和 iframe 模式同名接口, 调用方无感)
                            if (url.contains("youtube.com/watch")) {
                                view.evaluateJavascript(WATCH_PAGE_JS, null)
                            }
                        }
                    }
                    loadDataWithBaseURL(
                        "https://www.youtube.com",
                        """
                        <html><body style="margin:0;background:#06080f;">
                        <div id="p"></div>
                        <script src="https://www.youtube.com/iframe_api"></script>
                        <script>
                        var player;
                        function onYouTubeIframeAPIReady(){
                          player=new YT.Player('p',{videoId:'$videoId',width:'100%',height:'100%',
                            playerVars:{playsinline:1},
                            events:{
                              onReady:function(){
                                setInterval(function(){try{Bridge.postTime(player.getCurrentTime())}catch(e){}},500);
                              },
                              onError:function(e){try{Bridge.postError(e.data)}catch(err){}}
                            }});
                        }
                        function seekTo(s){ if(player){player.seekTo(s,true); player.playVideo();} }
                        </script></body></html>
                        """.trimIndent(),
                        "text/html", "utf-8", null,
                    )
                    onWebView(this)
                }
            },
        )
        playerError?.let { message ->
            Text(
                message,
                color = LinguaOnSurfaceMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            )
        }
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
 * watch 页播放模式的注入脚本: 同名 seekTo + 每 500ms 回报 video.currentTime;
 * 藏掉页面顶部的 YouTube 横幅 (logo/打开应用/搜索) 让视频顶到窗口顶部。
 */
private val WATCH_PAGE_JS = """
    (function(){
      window.seekTo = function(s){ var v=document.querySelector('video'); if(v){ v.currentTime=s; v.play(); } };
      if (!window.__linguaPoll) {
        window.__linguaPoll = setInterval(function(){
          try { var v=document.querySelector('video'); if(v) Bridge.postTime(v.currentTime); } catch(e){}
        }, 500);
      }
      if (!document.getElementById('__linguaCss')) {
        var st = document.createElement('style');
        st.id = '__linguaCss';
        // masthead = 顶栏; pivot bar = 底部导航; 都藏掉, 只留播放器
        st.textContent = 'ytm-mobile-topbar-renderer,.mobile-topbar-header,ytm-pivot-bar-renderer,' +
          'header,.mobile-topbar-header-content{display:none!important;}' +
          'ytm-watch,#player,.player-container{margin-top:0!important;top:0!important;}' +
          'body{background:#06080f!important;}';
        (document.head||document.documentElement).appendChild(st);
        window.scrollTo(0,0);
      }
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
                style = MaterialTheme.typography.bodyMedium,
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
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
