package com.lingua.english.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.data.repository.PlanRepository
import com.lingua.english.domain.DailyPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val streak: Int = 0,
    val longest: Int = 0,
    val totalDays: Int = 0,
    val logsByDate: Map<LocalDate, DailyLogEntity> = emptyMap(),
)

class StatsViewModel(planRepo: PlanRepository) : ViewModel() {

    val state = combine(planRepo.allLogs(), planRepo.streak()) { logs, streak ->
        val byDate = logs.associateBy { LocalDate.parse(it.date) }
        val totalDays = byDate.values.count { DailyPlan.countsForStreak(it) }
        val longest = longestStreak(byDate)
        StatsUiState(
            streak = streak,
            longest = maxOf(longest, streak),
            totalDays = totalDays,
            logsByDate = byDate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** 逐日扫描找最长连续保底天数(而不是只看有记录的天, 中间断的空档要算断)。 */
    private fun longestStreak(byDate: Map<LocalDate, DailyLogEntity>): Int {
        if (byDate.isEmpty()) return 0
        val start = byDate.keys.min()
        val end = LocalDate.now()
        var longest = 0
        var run = 0
        var d = start
        while (!d.isAfter(end)) {
            val log = byDate[d]
            if (log != null && DailyPlan.countsForStreak(log)) {
                run++
                if (run > longest) longest = run
            } else {
                run = 0
            }
            d = d.plusDays(1)
        }
        return longest
    }
}
