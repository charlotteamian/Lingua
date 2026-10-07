package com.lingua.english.data.repository

import com.lingua.english.data.local.dao.DailyLogDao
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.domain.DailyPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlanRepository(private val dailyLogDao: DailyLogDao) {

    fun todayLog(today: LocalDate = LocalDate.now()): Flow<DailyLogEntity?> =
        dailyLogDao.observe(today.toString())

    /** 全部每日记录, 打卡日历(月网格 / 年热力图)用。 */
    fun allLogs(): Flow<List<DailyLogEntity>> = dailyLogDao.observeAll()

    /**
     * 连续天数: 从今天 (或昨天, 今天还没动) 往回数保底达成的天。
     * 观察全表, 数据一变自动重算。
     */
    fun streak(): Flow<Int> = dailyLogDao.observeAll().map { logs ->
        val byDate = logs.filter { DailyPlan.countsForStreak(it) }
            .associateBy { LocalDate.parse(it.date) }
        var day = LocalDate.now()
        if (!byDate.containsKey(day)) day = day.minusDays(1)
        var count = 0
        while (byDate.containsKey(day)) {
            count++
            day = day.minusDays(1)
        }
        count
    }
}
