package com.lingua.english.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 深色基调 (和 Vita / Fortuna 同款 #06080f)
val LinguaBackground = Color(0xFF06080F)
val LinguaSurface = Color(0xFF0E1320)
val LinguaSurfaceVariant = Color(0xFF161C2E)
val LinguaOutline = Color(0xFF2A3149)

val LinguaPrimary = Color(0xFF8FA8FF)        // 皇家蓝 — 主操作 / 对话
val LinguaSecondary = Color(0xFFFF8FA8)      // 玫红 — 词块 / 强调
val LinguaTertiary = Color(0xFFA8E0C0)       // 薄荷 — 完成态 / 复习
val LinguaAmber = Color(0xFFFFC678)          // 琥珀 — streak / 提示
val LinguaError = Color(0xFFFF6B6B)

val LinguaOnBackground = Color(0xFFE7EAF3)
val LinguaOnSurfaceMuted = Color(0xFF8A94B0)

object LinguaGradients {

    /** 通用卡片: surface → surfaceVariant 对角渐变 */
    val cardSurface: Brush = Brush.linearGradient(
        colors = listOf(
            LinguaSurface,
            LinguaSurfaceVariant.copy(alpha = 0.7f),
        ),
    )

    val primaryAccent: Brush = Brush.linearGradient(
        colors = listOf(
            LinguaPrimary.copy(alpha = 0.15f),
            LinguaSurface,
        ),
    )

    val streakAccent: Brush = Brush.linearGradient(
        colors = listOf(
            LinguaAmber.copy(alpha = 0.14f),
            LinguaSurface,
        ),
    )

    val reviewAccent: Brush = Brush.linearGradient(
        colors = listOf(
            LinguaTertiary.copy(alpha = 0.12f),
            LinguaSurface,
        ),
    )
}
