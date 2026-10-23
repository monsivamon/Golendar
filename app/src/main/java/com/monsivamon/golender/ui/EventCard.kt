package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.holiday.HolidayCountry
import com.monsivamon.golender.data.util.accentColor
import com.monsivamon.golender.data.util.dayShort
import com.monsivamon.golender.data.util.endTimeText
import com.monsivamon.golender.data.util.localStartDateTime
import com.monsivamon.golender.data.util.shortDate
import com.monsivamon.golender.data.util.startTimeText
import com.monsivamon.golender.data.util.timeRangeString
import com.monsivamon.golender.ui.theme.AppColors

// 予定カード（日・週・月下部リスト用）
// compact=true のときは縦方向の余白と内部パディングを縮小する（ダイアログ内での間延び対策）
@Composable
fun EventCard(
    event: Event,
    colors: AppColors,
    showCalendarName: Boolean = false,
    compact: Boolean = false,
    photoCount: Int = 0,
    onClick: (Event) -> Unit,
) = EventCardBase(
    event = event,
    colors = colors,
    timeLine = event.timeRangeString(LocalContext.current),
    lineHeight = if (compact) 32.dp else 40.dp,
    showCalendarName = showCalendarName,
    compact = compact,
    photoCount = photoCount,
    onClick = onClick,
)

// 月表示の下部リスト専用の予定行。
// 左から「時刻列 → カレンダー色の太い縦バー → 角丸カード」の 3 カラム構成で、
// カード内はタイトル（太字）・場所（青ピン＋グレー小文字）・カレンダー名・
// 写真バッジ・メモ（右寄せ最大 2 行）を表示する。
// 週・日・日別ダイアログは従来の EventCard を使い続ける（影響を月表示に限定）。
@Composable
fun MonthBottomEventRow(
    event: Event,
    colors: AppColors,
    showCalendarName: Boolean = false,
    photoCount: Int = 0,
    onClick: (Event) -> Unit,
) {
    val context = LocalContext.current
    // 種別に応じた絵文字プレフィックス（祝日は国旗バッジ／誕生日／文化）
    val holidayFlags = event.holidayCountries
        .mapNotNull { HolidayCountry.flagOf(it).takeIf { flag -> flag.isNotBlank() } }
        .joinToString(" ")
    val prefix = when {
        holidayFlags.isNotEmpty() -> "$holidayFlags "
        event.isBirthdayCalendar -> "🎂 "
        event.isCulturalEvent -> "🎌 "
        event.isHolidayCalendar -> "🗾 "
        else -> ""
    }
    // 縦バーをカードの高さに揃えるため、行の高さを内容の最小固有高で決める
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 時刻列（開始と終了を縦積みにし、横幅を詰めてカードへ譲る）
        Column(
            modifier = Modifier
                .width(54.dp)
                .padding(top = 10.dp, end = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (event.isAllDay) {
                Text(
                    text = context.getString(R.string.dt_all_day),
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textGray,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = event.startTimeText(),
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textGray,
                    maxLines = 1,
                )
                Text(
                    text = "–",
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    color = colors.textGray.copy(alpha = 0.7f),
                )
                Text(
                    text = event.endTimeText(),
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textGray,
                    maxLines = 1,
                )
            }
        }
        // カレンダー色の太い縦バー（カード高さに追従）
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(event.accentColor(colors)),
        )
        Spacer(Modifier.width(10.dp))
        // 予定カード
        Card(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 56.dp)
                .clickable { onClick(event) },
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // タイトル（1 行・省略）
                    Text(
                        prefix + event.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 場所（未設定なら行ごと省略）
                    if (event.location.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = colors.satBlue,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                event.location,
                                fontSize = 11.sp,
                                color = colors.textGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // カレンダー名（複数カレンダー表示時のみ）と写真バッジ
                    val displayName = event.calendarDisplayName
                    val showName = showCalendarName && displayName.isNotBlank()
                    if (showName || photoCount > 0) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showName) {
                                val nameColor = if (event.calendarColorArgb != null && event.calendarColorArgb != 0)
                                    Color(event.calendarColorArgb!!) else colors.textGray
                                Text(
                                    text = if (displayName.length > 8) displayName.take(8) + "…" else displayName,
                                    color = nameColor,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (photoCount > 0) {
                                if (showName) Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (photoCount > 1) "📎 $photoCount" else "📎",
                                    color = colors.textGray,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                // メモ（右端に最大 2 行・狭いカード幅に合わせて控えめに）
                if (event.description.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        event.description, fontSize = 11.sp, color = colors.textGray,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

// 検索結果用カード（年月日＋曜日＋時刻＋カレンダー名を表示）
@Composable
fun SearchResultCard(
    event: Event,
    colors: AppColors,
    showCalendarName: Boolean = false,
    onClick: (Event) -> Unit,
) {
    val context = LocalContext.current
    val dt = event.localStartDateTime()
    val dateLabel = "${dt.toLocalDate().shortDate(context)}(${dt.dayOfWeek.dayShort(context)})"
    EventCardBase(
        event = event,
        colors = colors,
        timeLine = "$dateLabel  ${event.timeRangeString(context)}",
        lineHeight = 50.dp,
        showCalendarName = showCalendarName,
        onClick = onClick,
    )
}

// 予定カードの共通レイアウト（アクセントライン＋タイトル＋時刻＋カレンダー名＋メモ）
@Composable
private fun EventCardBase(
    event: Event,
    colors: AppColors,
    timeLine: String,
    lineHeight: Dp,
    showCalendarName: Boolean,
    compact: Boolean = false,
    photoCount: Int = 0,
    onClick: (Event) -> Unit,
) {
    // 種別に応じた絵文字プレフィックス（祝日は国旗バッジ／誕生日／文化）
    val holidayFlags = event.holidayCountries
        .mapNotNull { HolidayCountry.flagOf(it).takeIf { flag -> flag.isNotBlank() } }
        .joinToString(" ")
    val prefix = when {
        holidayFlags.isNotEmpty() -> "$holidayFlags "
        event.isBirthdayCalendar -> "🎂 "
        event.isCulturalEvent -> "🎌 "
        event.isHolidayCalendar -> "🗾 "
        else -> ""
    }
    val outerVerticalPad = if (compact) 2.dp else 6.dp
    val innerPad = if (compact) 8.dp else 12.dp
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = outerVerticalPad).clickable { onClick(event) },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(innerPad), verticalAlignment = Alignment.CenterVertically) {
            // 種別ごとのアクセントライン
            Box(
                Modifier.width(4.dp).height(lineHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.accentColor(colors))
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                // タイトル（絵文字プレフィックス付き）
                Text(
                    prefix + event.title,
                    fontSize = if (compact) 15.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
                // 日時 or 時刻範囲
                Text(timeLine, fontSize = if (compact) 13.sp else 14.sp, color = colors.textGray)

                // カレンダー名（複数カレンダー表示時のみ／8 文字で省略）
                if (showCalendarName && event.calendarDisplayName.isNotBlank()) {
                    val displayName = if (event.calendarDisplayName.length > 8)
                        event.calendarDisplayName.take(8) + "…"
                    else event.calendarDisplayName
                    val nameColor = if (event.calendarColorArgb != null && event.calendarColorArgb != 0)
                        Color(event.calendarColorArgb!!) else colors.textGray
                    Text(
                        text = displayName, color = nameColor, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // 写真添付インジケータ（Golendar モードで写真がある予定に表示）
                if (photoCount > 0) {
                    Text(
                        text = if (photoCount > 1) "📎 $photoCount" else "📎",
                        color = colors.textGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // メモ（右端に最大 2 行）
            if (event.description.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    event.description, fontSize = 12.sp, color = colors.textGray,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}