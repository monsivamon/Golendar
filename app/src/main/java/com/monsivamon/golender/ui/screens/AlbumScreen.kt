package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.monsivamon.golender.R
import com.monsivamon.golender.data.AlbumPhoto
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.util.PhotoStorage
import com.monsivamon.golender.data.util.dayShort
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.shortDate
import com.monsivamon.golender.ui.dialogs.EventPhotoViewerDialog
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.LocalDate

// 保存済み写真を日付ごとに並べるアルバム画面（写真がない日は表示しない）
@Composable
fun AlbumScreen(
    viewModel: CalendarViewModel,
    navController: NavController,
    colors: AppColors,
) {
    val albumPhotos by viewModel.albumPhotos.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()

    // 予定・写真の更新時にアルバムを再読込する
    LaunchedEffect(dataVersion) { viewModel.loadAlbumPhotos() }

    // 日付の新しい順にグループ化する（同じ日の中は取得順のまま横並びにする）
    val groups = remember(albumPhotos) {
        albumPhotos
            .groupBy { it.localStartDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, photos) -> date to photos }
    }

    // 表示中の写真ビューアの状態
    var viewerPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var viewerDate by remember { mutableStateOf<LocalDate?>(null) }

    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.al_noPhotos), color = colors.textGray, fontSize = 16.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(groups, key = { it.first.toEpochDay() }) { (date, photos) ->
            AlbumDaySection(
                date = date,
                photos = photos,
                colors = colors,
                onPhotoClick = { index ->
                    viewerPhotos = photos.map { it.toViewerPhoto() }
                    viewerIndex = index
                    viewerDate = date
                },
            )
        }
    }

    // 写真ビューア（アルバムから開いた場合のみ「その日へ」を表示）
    viewerIndex?.let { index ->
        val date = viewerDate
        EventPhotoViewerDialog(
            photos = viewerPhotos,
            initialIndex = index,
            colors = colors,
            onDismiss = { viewerIndex = null },
            onGoToDate = date?.let {
                {
                    viewerIndex = null
                    // 該当日を選択して月カレンダーへ移動する
                    viewModel.selectDate(it)
                    navigateToTab(navController, Routes.MONTHLY)
                }
            },
        )
    }
}

// 1 日分の見出しと写真の横並びを描画する
@Composable
private fun AlbumDaySection(
    date: LocalDate,
    photos: List<AlbumPhoto>,
    colors: AppColors,
    onPhotoClick: (Int) -> Unit,
) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${date.shortDate(context)} (${date.dayOfWeek.dayShort(context)})",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.al_photoCount, photos.size),
                fontSize = 13.sp, color = colors.textGray,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos, key = { _, photo -> photo.photoId }) { index, photo ->
                AlbumThumbnail(
                    fileName = photo.fileName,
                    colors = colors,
                    onClick = { onPhotoClick(index) },
                )
            }
        }
    }
}

// アルバム内の写真サムネイル
@Composable
private fun AlbumThumbnail(
    fileName: String,
    colors: AppColors,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg)
            .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = PhotoStorage.getFile(context, fileName),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ビューアで扱えるように写真レコードへ変換する
private fun AlbumPhoto.toViewerPhoto(): EventPhoto =
    EventPhoto(id = photoId, eventId = eventId, fileName = fileName, position = 0)
