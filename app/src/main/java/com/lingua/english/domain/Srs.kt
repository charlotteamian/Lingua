package com.lingua.english.domain

import com.lingua.english.data.local.entity.ChunkEntity
import java.time.LocalDate

/** 复习自评三档。 */
enum class ReviewGrade { FORGOT, FUZZY, GOOD }

/**
 * 简化版 SM-2 + Anki 式当天重学 (relearning): 够用、可解释。
 *  - 记得: 第一次 1 天, 第二次 3 天, 之后 interval × ease; ease 缓慢上调
 *  - 模糊: interval × 1.2, ease 微降
 *  - 忘了: 卡片留在「今天到期」(interval=0 的重学状态), 当天必须再答对一次
 *    才毕业到明天——配合会话层的重学循环, 「到期清零」天然包含重学过关。
 *    只有从已毕业状态 (interval>0) 摔下来才扣 ease / 记 lapse; 学习中/重学中
 *    反复答错不重复惩罚 (Anki 同款, 防 ease 螺旋下坠)。
 */
object Srs {

    /** interval=0 视为学习/重学中 (新卡或今天刚忘的卡)。 */
    fun isLearning(chunk: ChunkEntity): Boolean = chunk.intervalDays <= 0.0

    fun review(chunk: ChunkEntity, grade: ReviewGrade, today: LocalDate, nowEpochMs: Long): ChunkEntity {
        if (grade == ReviewGrade.FORGOT) {
            val wasGraduated = !isLearning(chunk)
            return chunk.copy(
                intervalDays = 0.0,
                ease = if (wasGraduated) (chunk.ease - 0.2).coerceAtLeast(1.3) else chunk.ease,
                reps = 0,
                lapses = if (wasGraduated) chunk.lapses + 1 else chunk.lapses,
                dueEpochDay = today.toEpochDay(),
                lastReviewedEpochMs = nowEpochMs,
            )
        }
        val (interval, ease) = when (grade) {
            ReviewGrade.FUZZY -> (chunk.intervalDays * 1.2).coerceAtLeast(1.0) to
                (chunk.ease - 0.05).coerceAtLeast(1.3)
            else -> when (chunk.reps) {
                0 -> 1.0
                1 -> 3.0
                else -> (chunk.intervalDays * chunk.ease).coerceAtLeast(chunk.intervalDays + 1)
            } to (chunk.ease + 0.03).coerceAtMost(3.0)
        }
        return chunk.copy(
            intervalDays = interval,
            ease = ease,
            reps = chunk.reps + 1,
            dueEpochDay = today.plusDays(interval.toLong().coerceAtLeast(1)).toEpochDay(),
            lastReviewedEpochMs = nowEpochMs,
        )
    }
}
