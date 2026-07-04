package com.lingua.english.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条对话消息。context break 用于「新话题」: 组装上下文时只取最后一个 break 之后的消息。
 * 语音消息: audioPath 指向本地录音, content 是模型转写的文字 (转写前为空串)。
 */
@Entity(tableName = "chat_message")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String,                 // user / assistant
    val content: String,
    val createdAtEpochMs: Long,
    val contextBreak: Boolean = false,
    val audioPath: String? = null,
)

/** 一条「随手记→英文语料」。sourceText 是原始记录 (中文/混合), english 是 AI 转写。 */
@Entity(tableName = "material_entry")
data class MaterialEntity(
    @PrimaryKey val id: String,
    val date: String,                 // yyyy-MM-dd
    val sourceText: String,
    val english: String,
    val createdAtEpochMs: Long,
)

/**
 * 复习卡: 词块 (来自语料) 或生词本单词, 统一走同一套 SRS。
 * kind = chunk / word。
 */
@Entity(
    tableName = "chunk",
    indices = [Index("materialId"), Index("dueEpochDay")],
)
data class ChunkEntity(
    @PrimaryKey val id: String,
    val materialId: String?,          // 生词本词条为 null
    val text: String,                 // 英文词块 / 单词
    val meaningZh: String,
    val note: String = "",            // 用法说明 (AI 给的语境提示)
    val kind: String = KIND_CHUNK,
    val createdAtEpochMs: Long,
    // ─── SRS 状态 ───
    val dueEpochDay: Long,            // 到期日 (LocalDate.toEpochDay)
    val intervalDays: Double = 0.0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewedEpochMs: Long? = null,
) {
    companion object {
        const val KIND_CHUNK = "chunk"
        const val KIND_WORD = "word"
        /** 收藏的整句: 复习时出中文, 学习者说英文再对比。 */
        const val KIND_SENTENCE = "sentence"
    }
}

/**
 * 听读素材: YouTube 视频 (免费字幕) 或本地音频/播客 (AI 转写)。
 * 字幕正文存 filesDir/transcripts/<id>.json, 不进表。
 */
@Entity(tableName = "media_item")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val type: String,                 // youtube / audio
    val title: String,
    val source: String,               // youtube: videoId; audio: content uri
    val createdAtEpochMs: Long,
    val transcribed: Boolean = false, // 字幕/转写是否已就绪
) {
    companion object {
        const val TYPE_YOUTUBE = "youtube"
        const val TYPE_AUDIO = "audio"
    }
}

/** 每日执行计数, 三档任务达成由这里的计数派生。 */
@Entity(tableName = "daily_log")
data class DailyLogEntity(
    @PrimaryKey val date: String,     // yyyy-MM-dd
    val reviewsDone: Int = 0,
    val listensDone: Int = 0,         // 完整听一条语料算一次
    val chatTurns: Int = 0,           // 用户发出的消息数
    val materialsAdded: Int = 0,
)
