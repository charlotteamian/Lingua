package com.lingua.english.ui.screens.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.domain.PlanTier
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.screens.today.dayTierColor
import com.lingua.english.ui.screens.today.dayTierTextColor
import com.lingua.english.ui.theme.LinguaAmber
import com.lingua.english.ui.theme.LinguaGradients
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaPrimary
import com.lingua.english.ui.theme.LinguaTertiary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("打卡 · 成就", style = MaterialTheme.typography.headlineSmall)
            }
        }

        item {
            LinguaCard(background = LinguaGradients.streakAccent) {
                val infinite = rememberInfiniteTransition(label = "flame")
                val flameScale by infinite.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1300, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "flameScale",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = LinguaAmber,
                        modifier = Modifier.size(36.dp).scale(flameScale),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${state.streak} 天连续打卡", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "最长纪录 ${state.longest} 天 · 累计打卡 ${state.totalDays} 天",
                            color = LinguaOnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            LinguaCard {
                val yearMonth = YearMonth.from(today)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${yearMonth.monthValue}月",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Legend()
                }

                val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekdayLabels.forEach { w ->
                        Text(
                            w,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = LinguaOnSurfaceMuted,
                        )
                    }
                }

                val leading = (yearMonth.atDay(1).dayOfWeek.value + 6) % 7
                val daysInMonth = yearMonth.lengthOfMonth()
                val cells: List<LocalDate?> = buildList {
                    repeat(leading) { add(null) }
                    for (d in 1..daysInMonth) add(yearMonth.atDay(d))
                    while (size % 7 != 0) add(null)
                }
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { date ->
                            Box(modifier = Modifier.weight(1f)) {
                                MonthDayCell(date, today, state.logsByDate[date])
                            }
                        }
                    }
                }

                val monthCount = state.logsByDate.count { (d, log) ->
                    !d.isBefore(yearMonth.atDay(1)) && !d.isAfter(today) &&
                        com.lingua.english.domain.DailyPlan.countsForStreak(log)
                }
                Text(
                    "这个月已打卡 $monthCount 天。势头正好，别断。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            LinguaCard {
                Text("过去一年", style = MaterialTheme.typography.titleMedium)
                val yearCount = state.logsByDate.count { (d, log) ->
                    !d.isBefore(today.minusYears(1)) && !d.isAfter(today) &&
                        com.lingua.english.domain.DailyPlan.countsForStreak(log)
                }
                Text(
                    "一年打卡 $yearCount 天。每一格都是那天的你。",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(2.dp))
                YearHeatmap(today = today, logsByDate = state.logsByDate)
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendDot(LinguaAmber.copy(alpha = 0.85f), "保底")
        LegendDot(LinguaPrimary.copy(alpha = 0.9f), "正常")
        LegendDot(LinguaTertiary, "好天")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = LinguaOnSurfaceMuted)
    }
}

@Composable
private fun MonthDayCell(date: LocalDate?, today: LocalDate, log: com.lingua.english.data.local.entity.DailyLogEntity?) {
    if (date == null) {
        Box(Modifier.aspectRatio(1f))
        return
    }
    val isFuture = date.isAfter(today)
    val isToday = date == today
    val tier = if (isFuture) null else com.lingua.english.domain.DailyPlan.tierForLog(log)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(dayTierColor(tier))
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(9.dp))
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = dayTierTextColor(tier),
        )
    }
}

@Composable
private fun YearHeatmap(today: LocalDate, logsByDate: Map<LocalDate, com.lingua.english.data.local.entity.DailyLogEntity>) {
    val activeDates = logsByDate.asSequence()
        .filter { (_, log) -> com.lingua.english.domain.DailyPlan.countsForStreak(log) }
        .map { (date, _) -> date }
        .toList()
    val range = heatmapRange(today, activeDates)
    var prevMonth = -1

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (w in 0 until range.totalWeeks) {
            val colStart = range.firstWeekStart.plusWeeks(w.toLong())
            val labelMonth = if (w == 0) range.visibleStart.month else colStart.month
            val label = if (labelMonth.value != prevMonth) {
                prevMonth = labelMonth.value
                labelMonth.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
            } else null

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = LinguaOnSurfaceMuted,
                    maxLines = 1,
                    modifier = Modifier.width(13.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (r in 0 until 7) {
                        val date = colStart.plusDays(r.toLong())
                        val outsideRange = date.isBefore(range.visibleStart) || date.isAfter(today)
                        val tier = if (outsideRange) null
                        else com.lingua.english.domain.DailyPlan.tierForLog(logsByDate[date])
                        Box(
                            Modifier
                                .size(13.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (outsideRange) Color.Transparent else heatColor(tier)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun heatColor(tier: PlanTier?): Color = when (tier) {
    PlanTier.GOOD -> LinguaTertiary
    PlanTier.NORMAL -> LinguaPrimary.copy(alpha = 0.85f)
    PlanTier.MINIMUM -> LinguaAmber.copy(alpha = 0.55f)
    else -> Color.White.copy(alpha = 0.05f)
}
