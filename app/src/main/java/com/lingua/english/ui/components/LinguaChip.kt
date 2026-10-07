package com.lingua.english.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingua.english.ui.theme.LinguaOnSurfaceMuted
import com.lingua.english.ui.theme.LinguaOutline
import com.lingua.english.ui.theme.LinguaPrimary
import com.lingua.english.ui.theme.LinguaSurfaceVariant

/** 全 app 统一的筛选 chip: 胶囊形, 选中 = 皇家蓝描边+蓝字, 未选 = 暗底灰字。 */
@Composable
fun LinguaChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = LinguaSurfaceVariant.copy(alpha = 0.4f),
            labelColor = LinguaOnSurfaceMuted,
            selectedContainerColor = LinguaPrimary.copy(alpha = 0.16f),
            selectedLabelColor = LinguaPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = LinguaOutline.copy(alpha = 0.5f),
            selectedBorderColor = LinguaPrimary.copy(alpha = 0.7f),
            selectedBorderWidth = 1.dp,
        ),
    )
}
