package com.lingua.english.ui.screens.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.english.domain.PlanTask
import com.lingua.english.domain.PlanTier
import com.lingua.english.ui.components.LinguaCard
import com.lingua.english.ui.theme.LinguaAmber
import com.lingua.english.ui.theme.LinguaGradients
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
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
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
            LinguaCard(background = LinguaGradients.streakAccent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = LinguaAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.streak > 0) "连续 ${state.streak} 天" else "今天开始第 1 天",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    "今天档位: ${state.plan.tier.label} — ${state.plan.tier.motto}",
                    color = LinguaOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            LinguaCard {
                Text("今天的三档", style = MaterialTheme.typography.titleMedium)
                PlanSection("保底 · 坏天也别断", state.plan.minimumTasks, state.plan.tier >= PlanTier.MINIMUM)
                PlanSection("正常 · 日常维护", state.plan.normalTasks, state.plan.tier >= PlanTier.NORMAL)
                PlanSection("好天 · 多吃进一点", state.plan.goodTasks, state.plan.tier >= PlanTier.GOOD)
            }
        }

        item {
            LinguaCard(background = LinguaGradients.reviewAccent) {
                QuickAction(
                    title = if (state.dueCount > 0) "复习 ${state.dueCount} 张到期卡" else "今日到期已清零",
                    subtitle = "词块内化 · 每张十几秒",
                    onClick = onGoReview,
                )
                QuickAction(
                    title = "跟 Poppy 聊聊今天",
                    subtitle = "迭代输出 · 用英文说正在做的事",
                    onClick = onGoChat,
                )
                QuickAction(
                    title = "随手记一条",
                    subtitle = "把今天的生活变成你的英文语料",
                    onClick = onGoCompose,
                )
            }
        }
    }
}

@Composable
private fun PlanSection(title: String, tasks: List<PlanTask>, achieved: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = if (achieved) LinguaTertiary else LinguaOnSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            if (achieved) {
                Icon(
                    Icons.Filled.Check, contentDescription = "已达成",
                    tint = LinguaTertiary,
                    modifier = Modifier.width(16.dp),
                )
            }
        }
        tasks.forEach { task ->
            Row {
                Text(
                    task.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${task.progress.coerceAtMost(task.target)}/${task.target}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (task.done) LinguaTertiary else LinguaOnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = LinguaOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
