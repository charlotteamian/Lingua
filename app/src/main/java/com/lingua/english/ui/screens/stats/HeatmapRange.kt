package com.lingua.english.ui.screens.stats

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal data class HeatmapRange(
    /** 真正需要展示的第一天；周对齐产生的更早日期不画格子。 */
    val visibleStart: LocalDate,
    /** 热力图每列从周一开始。 */
    val firstWeekStart: LocalDate,
    val totalWeeks: Int,
)

/**
 * 最近一年内，从第一次实际打卡所在月份开始展示，避免新用户先看到大段无数据月份。
 * 还没有打卡时只展示本月。
 */
internal fun heatmapRange(today: LocalDate, activeDates: Collection<LocalDate>): HeatmapRange {
    val earliestActiveDate = activeDates.asSequence()
        .filter { !it.isBefore(today.minusYears(1)) && !it.isAfter(today) }
        .minOrNull()
    val visibleStart = YearMonth.from(earliestActiveDate ?: today).atDay(1)
    val firstWeekStart = visibleStart.minusDays((visibleStart.dayOfWeek.value - 1).toLong())
    val currentWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val totalWeeks = ChronoUnit.WEEKS.between(firstWeekStart, currentWeekStart).toInt() + 1
    return HeatmapRange(visibleStart, firstWeekStart, totalWeeks)
}
