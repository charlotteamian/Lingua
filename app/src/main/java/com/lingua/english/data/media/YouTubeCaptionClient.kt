package com.lingua.english.data.media

import com.lingua.english.data.ai.AiException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一行字幕: 起始秒 + 英文。 */
@Serializable
data class TranscriptLine(val s: Double, val t: String)

/**
 * 拉 YouTube 英文字幕 (免费, 0 token): 抓 watch 页解析 captionTracks →
 * 取英文轨 (人工优先, 没有就自动生成 asr) → json3 拉全文。
 * 非官方接口, YouTube 改版可能失效; 失败给可读错误。
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
        return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
    }

    suspend fun fetch(videoId: String): CaptionResult = withContext(Dispatchers.IO) {
        val html = get("https://www.youtube.com/watch?v=$videoId&hl=en")

        val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?.removeSuffix(" - YouTube")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: videoId

        val tracksJson = Regex("\"captionTracks\":(\\[.*?\\])").find(html)?.groupValues?.get(1)
            ?: throw AiException("没拿到这个视频的字幕信息 (可能是网络被拦, 或视频没有字幕)。")
        val tracks = json.parseToJsonElement(tracksJson).jsonArray.map { it.jsonObject }
        val english = tracks.filter {
            it["languageCode"]?.jsonPrimitive?.content.orEmpty().startsWith("en")
        }
        // 人工字幕优先, 其次自动生成 (kind=asr)
        val track = english.firstOrNull { it["kind"]?.jsonPrimitive?.content != "asr" }
            ?: english.firstOrNull()
            ?: throw AiException("这个视频没有英文字幕 (其它语言的也没法用来学英文)。")
        val baseUrl = track["baseUrl"]!!.jsonPrimitive.content

        val captionJson = get("$baseUrl&fmt=json3")
        val events = json.parseToJsonElement(captionJson).jsonObject["events"]?.jsonArray
            ?: throw AiException("字幕内容解析失败, 再试一次。")

        val raw = events.mapNotNull { event ->
            val obj = event.jsonObject
            val start = obj["tStartMs"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val text = obj["segs"]?.jsonArray
                ?.joinToString("") { seg -> seg.jsonObject["utf8"]?.jsonPrimitive?.content.orEmpty() }
                ?.replace('\n', ' ')?.trim()
            if (text.isNullOrEmpty()) null else TranscriptLine(start / 1000.0, text)
        }
        if (raw.isEmpty()) throw AiException("字幕是空的, 这个视频可能没法用。")
        CaptionResult(videoId, title, mergeLines(raw))
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
            if (!resp.isSuccessful) throw AiException("请求失败 HTTP ${resp.code} (检查网络/代理)。")
            return resp.body?.string().orEmpty()
        }
    }
}
