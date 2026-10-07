package com.lingua.english.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.data.local.entity.DailyLogEntity
import com.lingua.english.domain.DailyPlan
import com.lingua.english.domain.PlanTask
import com.lingua.english.domain.PlanTier
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.theme.LinguaAmber
import com.lingua.english.ui.theme.LinguaGradients
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaOutline
import com.lingua.english.ui.theme.LinguaPrimary
import com.lingua.english.ui.theme.LinguaSecondary
import com.lingua.english.ui.theme.LinguaTertiary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onOpenSettings: () -> Unit,
    onGoChat: () -> Unit,
    onGoReview: () -> Unit,
    onGoCompose: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = LinguaOnSurfaceMuted)
                }
            }
        }

        if (!state.apiConfigured) {
            item {
                LinguaCard(background = LinguaGradients.primaryAccent) {
                    Text("先把 AI 接上", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "对话和随手记转语料都靠大模型。去设置里选服务商、填 API Key, 一分钟搞定。",
                        color = LinguaOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "去设置 →",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenSettings),
                    )
                }
            }
        }

        item {
            LinguaCard(modifier = Modifier.clickable(onClick = onOpenStats)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "这一周",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "看全部 →",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                WeekStrip(state.weekLogs)
            }
        }

        item {
            LinguaCard {
                Text("今天的三档", style = MaterialTheme.typography.titleMedium)
                PlanSection(
                    "保底 · 坏天也别断", LinguaAmber,
                    state.plan.minimumTasks, state.plan.tier >= PlanTier.MINIMUM,
                )
                PlanSection(
                    "正常 · 日常维护", LinguaPrimary,
                    state.plan.normalTasks, state.plan.tier >= PlanTier.NORMAL,
                )
                PlanSection(
                    "好天 · 多吃进一点", LinguaTertiary,
                    state.plan.goodTasks, state.plan.tier >= PlanTier.GOOD,
                )
            }
        }

        item {
            QuickActionCard(
                title = if (state.dueCount > 0) "复习 ${state.dueCount} 张到期卡" else "今日到期已清零",
                subtitle = "词块内化 · 每张十几秒",
                onClick = onGoReview,
            )
        }
        item {
            QuickActionCard(
                title = "跟 Poppy 聊聊今天",
                subtitle = "迭代输出 · 说正在做的事",
                onClick = onGoChat,
            )
        }
        item {
            QuickActionCard(
                title = "随手记一条",
                subtitle = "生活变素材 · 中文就行",
                onClick = onGoCompose,
            )
        }
    }
}

/** 今日 streak 卡里的迷你周视图: 周一到周日, 按当天档位上色。 */
@Composable
private fun WeekStrip(weekLogs: List<Pair<LocalDate, DailyLogEntity?>>) {
    val today = LocalDate.now()
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        weekLogs.forEachIndexed { i, (date, log) ->
            val isFuture = date.isAfter(today)
            val isToday = date == today
            val tier = if (isFuture) null else DailyPlan.tierForLog(log)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    labels.getOrElse(i) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = LinguaOnSurfaceMuted,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(dayTierColor(tier))
                        .then(
                            if (isToday) Modifier.border(2.dp, LinguaSecondary, RoundedCornerShape(9.dp))
                            else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = dayTierTextColor(tier),
                    )
                }
            }
        }
    }
}

/** 三档配色: 好天薄荷 / 正常蓝 / 保底琥珀 / 没打卡或未来淡灰。共享给打卡成就页同款逻辑。 */
@Composable
internal fun dayTierColor(tier: PlanTier?) = when (tier) {
    PlanTier.GOOD -> LinguaTertiary
    PlanTier.NORMAL -> LinguaPrimary.copy(alpha = 0.9f)
    PlanTier.MINIMUM -> LinguaAmber.copy(alpha = 0.85f)
    else -> LinguaOutline.copy(alpha = 0.22f)
}

@Composable
internal fun dayTierTextColor(tier: PlanTier?) = when (tier) {
    PlanTier.GOOD -> androidx.compose.ui.graphics.Color(0xFF06231A)
    PlanTier.NORMAL -> androidx.compose.ui.graphics.Color(0xFF08122E)
    PlanTier.MINIMUM -> androidx.compose.ui.graphics.Color(0xFF3A2400)
    else -> LinguaOnSurfaceMuted
}

/** 一档任务: 色点 + 档位名 (达成时右侧 ✓), 下挂任务行 (名称左, x/y 右)。 */
@Composable
private fun PlanSection(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    tasks: List<PlanTask>,
    achieved: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (achieved) {
                Icon(
                    Icons.Filled.Check, contentDescription = "已达成",
                    tint = LinguaTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 15.dp),
                )
                Text(
                    "${task.progress.coerceAtMost(task.target)}/${task.target}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (task.done) LinguaTertiary else LinguaOnSurfaceMuted,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    LinguaCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
