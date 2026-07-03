package com.lingua.english.data.repository

import com.lingua.english.data.local.dao.ChunkDao
import com.lingua.english.data.local.dao.DailyLogDao
import com.lingua.english.data.local.entity.ChunkEntity
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.domain.ReviewGrade
import com.lingua.english.domain.Srs
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ReviewRepository(
    private val chunkDao: ChunkDao,
    private val dailyLogDao: DailyLogDao,
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
}
