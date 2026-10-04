package com.monsivamon.golender.ui.dialogs.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors

// 繰り返し種別（毎日／平日／毎週／毎月／毎年）の選択チップ
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecurrenceChips(
    recurringType: String,
    onTypeChange: (String) -> Unit,
    colors: AppColors,
    isLightBackground: Boolean,
) {
    val options = listOf(
        "DAILY" to "毎日",
        "WEEKDAYS" to "平日",
        "WEEKLY" to "毎週",
        "MONTHLY" to "毎月",
        "YEARLY" to "毎年",
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (type, label) ->
            val isSelected = recurringType == type
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.primaryAccent else colors.bg)
                    .clickable {
                        onTypeChange(type)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = if (isSelected) {
                        if (isLightBackground) Color.White else Color(0xFF1A1A1A)
                    } else colors.text,
                    fontSize = 12.sp,
                )
            }
        }
    }
}