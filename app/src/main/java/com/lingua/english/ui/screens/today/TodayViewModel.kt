package com.lingua.english.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.english.data.prefs.SettingsPreferences
import com.lingua.english.data.repository.PlanRepository
import com.lingua.english.data.repository.ReviewRepository
import com.lingua.english.domain.DailyPlan
import com.lingua.english.domain.DailyPlanStatus
import com.lingua.english.domain.PlanTier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TodayUiState(
    val plan: DailyPlanStatus = DailyPlanStatus(PlanTier.NONE, emptyList(), emptyList(), emptyList()),
    val streak: Int = 0,
    val dueCount: Int = 0,
    val apiConfigured: Boolean = true,
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
    ) { log, due, streak, configured ->
        TodayUiState(
            plan = DailyPlan.evaluate(log, due),
            streak = streak,
            dueCount = due,
            apiConfigured = configured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}
