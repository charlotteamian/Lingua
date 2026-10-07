package com.lingua.english.ui.screens.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapRangeTest {

    private val today = LocalDate.of(2026, 7, 24)

    @Test
    fun startsAtMonthContainingFirstActiveDay() {
        val range = heatmapRange(
            today = today,
            activeDates = listOf(
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 21),
            ),
        )

        assertEquals(LocalDate.of(2026, 7, 1), range.visibleStart)
        assertEquals(LocalDate.of(2026, 6, 29), range.firstWeekStart)
        assertEquals(4, range.totalWeeks)
    }

    @Test
    fun ignoresEmptyHistoryBeforeFirstActiveMonth() {
        val range = heatmapRange(
            today = today,
            activeDates = listOf(LocalDate.of(2026, 5, 18)),
        )

        assertEquals(LocalDate.of(2026, 5, 1), range.visibleStart)
        assertEquals(LocalDate.of(2026, 4, 27), range.firstWeekStart)
        assertEquals(13, range.totalWeeks)
    }

    @Test
    fun fallsBackToCurrentMonthWhenNoRecentActivityExists() {
        val range = heatmapRange(
            today = today,
            activeDates = listOf(LocalDate.of(2025, 6, 30)),
        )

        assertEquals(LocalDate.of(2026, 7, 1), range.visibleStart)
        assertEquals(4, range.totalWeeks)
    }
}
