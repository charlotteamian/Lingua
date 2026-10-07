package com.lingua.english.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 界面字体方案。
 *
 * Android 不能随包分发 Apple 的 SF Pro / 苹方；系统方案在 Android 上使用设备原生
 * sans-serif，未来 iOS 端可把同一选项映射到 SF Pro + PingFang SC。
 */
enum class AppFontChoice(
    val id: String,
    val label: String,
    val description: String,
) {
    COMFORTABLE(
        id = "comfortable",
        label = "舒适黑体",
        description = "推荐 · 中英文都更厚实，最接近设计稿",
    ),
    SYSTEM(
        id = "system",
        label = "系统原生",
        description = "跟随这台 Android 手机的默认字体",
    ),
    ROUNDED(
        id = "rounded",
        label = "圆润",
        description = "柔和亲切；不支持时自动回退系统黑体",
    ),
    SERIF(
        id = "serif",
        label = "衬线阅读",
        description = "书本感更强，适合阅读较长语料",
    );

    companion object {
        val DEFAULT = COMFORTABLE

        fun fromId(id: String): AppFontChoice = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

private val SystemSans = FontFamily.SansSerif

/**
 * Android 设备字体是可选资源；每个字重都显式追加 sans-serif 作为兜底，避免不同厂商
 * 缺少 rounded/serif 字族时回到不可控的细字重。
 */
private fun deviceFamily(primary: String): FontFamily {
    val families = listOf(primary, "sans-serif").distinct()
    val weights = listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
    )
    return FontFamily(
        *weights.flatMap { weight ->
            families.map { family -> Font(DeviceFontFamilyName(family), weight = weight) }
        }.toTypedArray(),
    )
}

private val RoundedSans = deviceFamily("sans-serif-rounded")
private val ReadingSerif = deviceFamily("serif")
private val base = Typography()

fun linguaTypography(choice: AppFontChoice): Typography {
    val family = when (choice) {
        AppFontChoice.COMFORTABLE, AppFontChoice.SYSTEM -> SystemSans
        AppFontChoice.ROUNDED -> RoundedSans
        AppFontChoice.SERIF -> ReadingSerif
    }
    val headingWeight = when (choice) {
        AppFontChoice.SYSTEM, AppFontChoice.SERIF -> FontWeight.Bold
        else -> FontWeight.ExtraBold
    }
    val bodyWeight = when (choice) {
        AppFontChoice.SYSTEM, AppFontChoice.SERIF -> FontWeight.Medium
        else -> FontWeight.SemiBold
    }
    val labelWeight = when (choice) {
        AppFontChoice.SYSTEM, AppFontChoice.SERIF -> FontWeight.SemiBold
        else -> FontWeight.Bold
    }

    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = headingWeight),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = headingWeight),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = headingWeight),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = headingWeight),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = headingWeight,
            fontSize = 26.sp,
            lineHeight = 34.sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = headingWeight),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = headingWeight,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = headingWeight,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = headingWeight),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = bodyWeight,
            fontSize = 16.sp,
            lineHeight = 26.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = bodyWeight,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        bodySmall = base.bodySmall.copy(fontFamily = family, fontWeight = bodyWeight),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = labelWeight),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = labelWeight,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = labelWeight),
    )
}
