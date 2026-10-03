package com.monsivamon.golender.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.ui.SearchResultCard
import com.monsivamon.golender.ui.theme.AppColors

// 検索モード中に表示する検索結果リストを描画する
@Composable
fun SearchResultsList(
    query: String,
    results: List<Event>,
    isLoading: Boolean,
    colors: AppColors,
    showCalendarName: Boolean = false,
    onResultSelected: (Event) -> Unit,
) {
    when {
        // クエリが空のときはプレースホルダを中央表示する
        query.isBlank() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "予定を検索...",
                    color = colors.textGray,
                    fontSize = 16.sp,
                )
            }
        }

        // 検索中はローディング表示
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }
        }

        // 該当なし
        results.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "該当する予定はありません",
                    color = colors.textGray,
                    fontSize = 16.sp,
                )
            }
        }

        // 検索結果一覧（件数ヘッダー＋安定キー付きリスト）
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                item {
                    Text(
                        "検索結果: ${results.size}件",
                        color = colors.textGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(results, key = { "${it.id}_${it.startTime}" }) { event ->
                    SearchResultCard(
                        event = event,
                        colors = colors,
                        showCalendarName = showCalendarName,
                        onClick = onResultSelected,
                    )
                }
            }
        }
    }
}