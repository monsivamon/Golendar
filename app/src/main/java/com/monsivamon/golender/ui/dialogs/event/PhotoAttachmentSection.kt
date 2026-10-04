package com.monsivamon.golender.ui.dialogs.event

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.util.PhotoStorage
import com.monsivamon.golender.ui.theme.AppColors

// 予定に添付する写真の選択・プレビューセクション
@Composable
internal fun PhotoAttachmentSection(
    colors: AppColors,
    existingPhotos: List<EventPhoto>,
    newPhotoUris: List<Uri>,
    onAddClick: () -> Unit,
    onRemoveExisting: (EventPhoto) -> Unit,
    onRemoveNew: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val total = existingPhotos.size + newPhotoUris.size

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("写真", color = colors.textGray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "$total / ${EventPhoto.MAX_PHOTOS_PER_EVENT}",
                color = colors.textGray,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(existingPhotos, key = { "existing_${it.id}" }) { photo ->
                PhotoThumbnail(
                    colors = colors,
                    onRemove = { onRemoveExisting(photo) },
                ) {
                    val file = PhotoStorage.getFile(context, photo.fileName)
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            items(newPhotoUris, key = { "new_${it}" }) { uri ->
                PhotoThumbnail(
                    colors = colors,
                    onRemove = { onRemoveNew(uri) },
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (total < EventPhoto.MAX_PHOTOS_PER_EVENT) {
                item {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.textGray, RoundedCornerShape(8.dp))
                            .clickable(onClick = onAddClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = colors.primaryAccent, fontSize = 28.sp)
                    }
                }
            }
        }
        Text(
            "写真はアプリ内に保存されます（長辺 ${PhotoStorage.MAX_LONG_EDGE}px に縮小）",
            color = colors.textGray,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// サムネイル枠（右上に × 削除ボタン付き）
@Composable
private fun PhotoThumbnail(
    colors: AppColors,
    onRemove: () -> Unit,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bg)
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontSize = 12.sp)
        }
    }
}