package com.lingua.english.domain

import com.lingua.english.data.local.entity.ChunkEntity
import java.time.LocalDate

/** 复习自评三档。 */
enum class ReviewGrade { FORGOT, FUZZY, GOOD }

/**
 * 简化版 SM-2: 够用、可解释。
 *  - 记得: 第一次 1 天, 第二次 3 天, 之后 interval × ease; ease 缓慢上调
 *  - 模糊: interval × 1.2, ease 微降
 *  - 忘了: 回到 1 天, ease 下调, lapses +1
 */
object Srs {

    fun review(chunk: ChunkEntity, grade: ReviewGrade, today: LocalDate, nowEpochMs: Long): ChunkEntity {
        val (interval, ease) = when (grade) {
            ReviewGrade.FORGOT -> 1.0 to (chunk.ease - 0.2).coerceAtLeast(1.3)
            ReviewGrade.FUZZY -> (chunk.intervalDays * 1.2).coerceAtLeast(1.0) to
                (chunk.ease - 0.05).coerceAtLeast(1.3)
            ReviewGrade.GOOD -> when (chunk.reps) {
                0 -> 1.0
                1 -> 3.0
                else -> (chunk.intervalDays * chunk.ease).coerceAtLeast(chunk.intervalDays + 1)
            } to (chunk.ease + 0.03).coerceAtMost(3.0)
        }
        return chunk.copy(
            intervalDays = interval,
            ease = ease,
            reps = if (grade == ReviewGrade.FORGOT) 0 else chunk.reps + 1,
            lapses = if (grade == ReviewGrade.FORGOT) chunk.lapses + 1 else chunk.lapses,
            dueEpochDay = today.plusDays(interval.toLong().coerceAtLeast(1)).toEpochDay(),
            lastReviewedEpochMs = nowEpochMs,
        )
    }
}
