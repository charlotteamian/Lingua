package com.lingua.english.domain

import com.lingua.english.data.local.entity.DailyLogEntity

/**
 * OIO 的「执行系统」: 好天 / 正常 / 保底 三档日常。
 * 档位由当天计数自动判定, 不需要用户手动打卡。
 */
enum class PlanTier(val label: String, val motto: String) {
    NONE("还没开始", "从最小的一步开始就好"),
    MINIMUM("保底", "坏天也没断, 这就够了"),
    NORMAL("正常", "今天的维护做完了"),
    GOOD("好天", "状态好就多吃进一点"),
}

/** 单项任务的完成状态, 用于今日页逐条展示。 */
data class PlanTask(
    val label: String,
    val progress: Int,
    val target: Int,
) {
    val done: Boolean get() = progress >= target
}

data class DailyPlanStatus(
    val tier: PlanTier,
    val minimumTasks: List<PlanTask>,
    val normalTasks: List<PlanTask>,
    val goodTasks: List<PlanTask>,
)

object DailyPlan {

    const val MIN_REVIEWS = 5
    const val NORMAL_CHAT_TURNS = 5
    const val GOOD_CHAT_TURNS = 10

    /**
     * 保底 = 任意一个学习动作 (复习/听/聊/记 ≥1)
     * 正常 = 复习 ≥5 且 (聊 ≥5 或 记 ≥1)
     * 好天 = 正常 且 到期清零 且 听 ≥1
     */
    fun evaluate(log: DailyLogEntity?, dueLeft: Int): DailyPlanStatus {
        val l = log ?: DailyLogEntity(date = "")
        val anyAction = l.reviewsDone + l.listensDone + l.chatTurns + l.materialsAdded > 0
        val normalMet = l.reviewsDone >= MIN_REVIEWS &&
            (l.chatTurns >= NORMAL_CHAT_TURNS || l.materialsAdded >= 1)
        val goodMet = normalMet && dueLeft == 0 && l.listensDone >= 1

        val tier = when {
            goodMet -> PlanTier.GOOD
            normalMet -> PlanTier.NORMAL
            anyAction -> PlanTier.MINIMUM
            else -> PlanTier.NONE
        }
        return DailyPlanStatus(
            tier = tier,
            minimumTasks = listOf(
                PlanTask("任一学习动作", if (anyAction) 1 else 0, 1),
            ),
            normalTasks = listOf(
                PlanTask("复习 ${MIN_REVIEWS} 张卡", l.reviewsDone, MIN_REVIEWS),
                PlanTask("聊 ${NORMAL_CHAT_TURNS} 轮 / 记 1 条", maxOf(l.chatTurns * 1, if (l.materialsAdded >= 1) NORMAL_CHAT_TURNS else 0), NORMAL_CHAT_TURNS),
            ),
            goodTasks = listOf(
                PlanTask("到期清零", if (dueLeft == 0 && anyAction) 1 else 0, 1),
                PlanTask("听完 1 条语料", l.listensDone, 1),
            ),
        )
    }

    /** 保底达成即续 streak。 */
    fun countsForStreak(log: DailyLogEntity): Boolean =
        log.reviewsDone + log.listensDone + log.chatTurns + log.materialsAdded > 0

    /**
     * 历史某天的档位快照, 用于打卡日历(月网格 / 年热力图)。
     * 历史行没有存"当天到期数", 所以好天的判定用"听完 ≥1 条"代替"到期清零"。
     */
    fun tierForLog(log: DailyLogEntity?): PlanTier {
        val l = log ?: return PlanTier.NONE
        val anyAction = l.reviewsDone + l.listensDone + l.chatTurns + l.materialsAdded > 0
        val normalMet = l.reviewsDone >= MIN_REVIEWS &&
            (l.chatTurns >= NORMAL_CHAT_TURNS || l.materialsAdded >= 1)
        val goodMet = normalMet && l.listensDone >= 1
        return when {
            goodMet -> PlanTier.GOOD
            normalMet -> PlanTier.NORMAL
            anyAction -> PlanTier.MINIMUM
            else -> PlanTier.NONE
        }
    }
}
