package com.lingua.english.data.media

import com.lingua.english.data.ai.AiException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 一行字幕: 起始秒 + 英文。 */
@Serializable
data class TranscriptLine(val s: Double, val t: String)

/**
 * 拉 YouTube 英文字幕 (免费, 0 token)。
 * 首选 Innertube player API 伪装 Android 官方客户端 (yt-dlp 同款, 基本不吃风控),
 * 失败再退回 watch 页 HTML 抓取。非官方接口, YouTube 改版可能失效。
 */
class YouTubeCaptionClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class CaptionResult(
        val videoId: String,
        val title: String,
        val lines: List<TranscriptLine>,
    )

    /** 各种形态的 YouTube 链接里抠 11 位视频 id。 */
    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("shorts/([A-Za-z0-9_-]{11})"),
            Regex("embed/([A-Za-z0-9_-]{11})"),
            Regex("live/([A-Za-z0-9_-]{11})"),
        )
        return patterns.firstNotNullOfOrNull { it.find(url.trim())?.groupValues?.get(1) }
    }

    suspend fun fetch(videoId: String): CaptionResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        for (attempt in listOf(::viaInnertube, ::viaWatchPage)) {
            val result = runCatching { attempt(videoId) }
            result.getOrNull()?.let { return@withContext it }
            errors.add(result.exceptionOrNull()?.message ?: "未知错误")
        }
        throw AiException("拉字幕失败: ${errors.joinToString(" / ").take(200)}")
    }

    /** 首选: Innertube player API, 伪装 Android 官方客户端。 */
    private fun viaInnertube(videoId: String): CaptionResult {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.09.37")
                    put("androidSdkVersion", 34)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
        }
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$ANDROID_KEY&prettyPrint=false")
            .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val text = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("player API HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val root = json.parseToJsonElement(text).jsonObject
        val status = root["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
        if (status != null && status != "OK") error("视频不可播 ($status)")
        val title = root["videoDetails"]?.jsonObject?.get("title")?.jsonPrimitive?.content ?: videoId
        val tracks = root["captions"]?.jsonObject
            ?.get("playerCaptionsTracklistRenderer")?.jsonObject
            ?.get("captionTracks")?.jsonArray
            ?: error("这个视频没有字幕轨")
        return buildResult(videoId, title, tracks)
    }

    /** 兜底: watch 页 HTML 抓取 (可能吃风控页)。 */
    private fun viaWatchPage(videoId: String): CaptionResult {
        val html = get("https://www.youtube.com/watch?v=$videoId&hl=en")
        val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?.removeSuffix(" - YouTube")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: videoId
        val tracksJson = Regex("\"captionTracks\":(\\[.*?\\])").find(html)?.groupValues?.get(1)
            ?: error("页面里没有字幕信息 (可能被风控)")
        val tracks = json.parseToJsonElement(tracksJson).jsonArray
        return buildResult(videoId, title, tracks)
    }

    /** 选英文轨 (人工优先, 兜底自动生成) → 拉 json3 → 合并成句。 */
    private fun buildResult(videoId: String, title: String, tracks: JsonArray): CaptionResult {
        val english = tracks.map { it.jsonObject }.filter {
            it["languageCode"]?.jsonPrimitive?.content.orEmpty().startsWith("en")
        }
        val track = english.firstOrNull { it["kind"]?.jsonPrimitive?.content != "asr" }
            ?: english.firstOrNull()
            ?: error("没有英文字幕 (其它语言的没法用来学英文)")
        val baseUrl = track["baseUrl"]!!.jsonPrimitive.content
        return parseJson3(videoId, title, get("$baseUrl&fmt=json3"))
    }

    /** json3 字幕文本 → 合并成句。WebView 兜底通道也用这个。 */
    fun parseJson3(videoId: String, title: String, captionJson: String): CaptionResult {
        val events = json.parseToJsonElement(captionJson).jsonObject["events"]?.jsonArray
            ?: error("字幕内容解析失败")
        val raw = events.mapNotNull { event ->
            val obj = event.jsonObject
            val start = obj["tStartMs"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val text = obj["segs"]?.jsonArray
                ?.joinToString("") { seg -> seg.jsonObject["utf8"]?.jsonPrimitive?.content.orEmpty() }
                ?.replace('\n', ' ')?.trim()
            if (text.isNullOrEmpty()) null else TranscriptLine(start / 1000.0, text)
        }
        if (raw.isEmpty()) error("字幕是空的")
        return CaptionResult(videoId, title, mergeLines(raw))
    }

    /** ASR 字幕碎片很细, 合并到 ~80 字符/条, 停顿 >4s 断开。 */
    private fun mergeLines(raw: List<TranscriptLine>): List<TranscriptLine> {
        val out = mutableListOf<TranscriptLine>()
        var start = raw.first().s
        val sb = StringBuilder()
        var lastStart = start
        for (line in raw) {
            val gap = line.s - lastStart
            if (sb.isNotEmpty() && (sb.length + line.t.length > 80 || gap > 4.0)) {
                out.add(TranscriptLine(start, sb.toString()))
                sb.clear()
                start = line.s
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(line.t)
            lastStart = line.s
        }
        if (sb.isNotEmpty()) out.add(TranscriptLine(start, sb.toString()))
        return out
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36",
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    companion object {
        /** InnerTube Android 客户端公开 key (嵌在官方 app 里, 各开源项目通用)。 */
        private const val ANDROID_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
    }
}
