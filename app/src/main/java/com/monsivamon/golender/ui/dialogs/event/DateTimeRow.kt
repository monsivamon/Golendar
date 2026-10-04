package com.monsivamon.golender.ui.dialogs.event

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monsivamon.golender.ui.dialogs.LABEL_WIDTH
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

// 日付＋時刻を 1 行で表示する。終日予定では時刻ボックスを非表示にする。
// isDateLocked=true で日付ボックスをグレーアウトし、タップを無効化する。
@Composable
internal fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    isAllDay: Boolean,
    isDateLocked: Boolean,
    topPadding: Dp,
    colors: AppColors,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    val dateBorder = if (isDateLocked) colors.textGray.copy(alpha = 0.3f) else colors.textGray
    val dateText = colors.text.copy(alpha = if (isDateLocked) 0.4f else 1f)
    val labelColor = colors.textGray.copy(alpha = if (isDateLocked) 0.4f else 1f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = topPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = labelColor, modifier = Modifier.width(LABEL_WIDTH))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, dateBorder, RoundedCornerShape(8.dp))
                    .then(
                        if (!isDateLocked) Modifier.clickable(onClick = onDateClick) else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "${date.year}年${date.monthValue}月${date.dayOfMonth}日",
                    color = dateText,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isAllDay) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.textGray, RoundedCornerShape(8.dp))
                        .clickable(onClick = onTimeClick)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute),
                        color = colors.text,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}