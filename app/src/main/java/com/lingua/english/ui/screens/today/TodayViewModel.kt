package com.lingua.english.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.data.prefs.SettingsPreferences
import com.lingua.english.data.repository.PlanRepository
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.domain.DailyPlan
import com.lingua.english.domain.DailyPlanStatus
import com.lingua.english.domain.PlanTier
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TodayUiState(
    val plan: DailyPlanStatus = DailyPlanStatus(PlanTier.NONE, emptyList(), emptyList(), emptyList()),
    val streak: Int = 0,
    val dueCount: Int = 0,
    val apiConfigured: Boolean = true,
    /** 本周一到今天(或到周日)每天的记录, 今日页周视图用。 */
    val weekLogs: List<Pair<LocalDate, DailyLogEntity?>> = emptyList(),
)

class TodayViewModel(
    planRepo: PlanRepository,
    reviewRepo: ReviewRepository,
    prefs: SettingsPreferences,
) : ViewModel() {

    val state = combine(
        planRepo.todayLog(),
        reviewRepo.dueCount(),
        planRepo.streak(),
        prefs.aiApiKey.map { it.isNotBlank() },
        planRepo.allLogs(),
    ) { log, due, streak, configured, logs ->
        val today = LocalDate.now()
        val weekStart = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val byDate = logs.associateBy { LocalDate.parse(it.date) }
        val weekLogs = (0..6).map { i ->
            val d = weekStart.plusDays(i.toLong())
            d to byDate[d]
        }
        TodayUiState(
            plan = DailyPlan.evaluate(log, due),
            streak = streak,
            dueCount = due,
            apiConfigured = configured,
            weekLogs = weekLogs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}
