package com.lingua.english.data.repository

import com.lingua.english.data.ai.ChatApiClient
import com.lingua.english.data.ai.ChatTurn
import com.lingua.english.data.local.dao.ChunkDao
import com.lingua.english.data.local.dao.DailyLogDao
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.domain.ReviewGrade
import com.lingua.english.domain.Srs
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReviewRepository(
    private val chunkDao: ChunkDao,
    private val dailyLogDao: DailyLogDao,
    private val client: ChatApiClient,
) {

    fun dueCards(today: LocalDate = LocalDate.now()): Flow<List<ChunkEntity>> =
        chunkDao.observeDue(today.toEpochDay())

    fun dueCount(today: LocalDate = LocalDate.now()): Flow<Int> =
        chunkDao.observeDueCount(today.toEpochDay())

    val allChunks: Flow<List<ChunkEntity>> = chunkDao.observeAll()

    suspend fun grade(chunk: ChunkEntity, grade: ReviewGrade) {
        val today = LocalDate.now()
        chunkDao.update(Srs.review(chunk, grade, today, System.currentTimeMillis()))
        dailyLogDao.insertIgnore(DailyLogEntity(date = today.toString()))
        dailyLogDao.incrementReviews(today.toString())
    }

    suspend fun delete(chunkId: String) = chunkDao.deleteById(chunkId)

    /**
     * 独立加卡: 词典底部卡「加入生词本」(kind=word) 和对话里「＋收词块」(kind=chunk)
     * 都走这里, 与语料词块同一套复习。已存在返回 false。
     */
    suspend fun addStandalone(text: String, meaningZh: String, note: String, kind: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || chunkDao.hasText(trimmed)) return false
        val today = LocalDate.now()
        chunkDao.insertAll(
            listOf(
                ChunkEntity(
                    id = UUID.randomUUID().toString(),
                    materialId = null,
                    text = trimmed,
                    meaningZh = meaningZh.trim(),
                    note = note.trim(),
                    kind = kind,
                    createdAtEpochMs = System.currentTimeMillis(),
                    dueEpochDay = today.toEpochDay(),
                )
            )
        )
        return true
    }

    suspend fun hasText(text: String): Boolean = chunkDao.hasText(text)

    /**
     * 收藏整句进复习: 先落库 (UI 立刻显示已收藏), 中文翻译后台补上。
     * 复习时出中文 → 学习者说英文 → 跟读对比。
     */
    suspend fun addSentence(english: String): Boolean {
        val sentence = english.trim()
        if (sentence.isEmpty() || chunkDao.hasText(sentence)) return false
        val id = UUID.randomUUID().toString()
        val today = LocalDate.now()
        chunkDao.insertAll(
            listOf(
                ChunkEntity(
                    id = id,
                    materialId = null,
                    text = sentence,
                    meaningZh = "",
                    note = "收藏的句子",
                    kind = ChunkEntity.KIND_SENTENCE,
                    createdAtEpochMs = System.currentTimeMillis(),
                    dueEpochDay = today.toEpochDay(),
                )
            )
        )
        runCatching {
            client.chat(
                system = "",
                turns = listOf(ChatTurn("user", "把这句英文翻译成自然的中文, 只输出译文, 不要引号:\n$sentence")),
                maxTokens = 256,
            )
        }.getOrNull()?.trim()?.take(200)?.takeIf { it.isNotEmpty() }?.let { zh ->
            chunkDao.updateMeaning(id, zh)
        }
        return true
    }

    /** 已收藏文本集合 (归一化小写), UI 用来显示 ✓ 已收。 */
    val savedTexts: Flow<Set<String>> = chunkDao.observeAll()
        .map { list -> list.map { it.text.trim().lowercase() }.toSet() }
}
