package com.monsivamon.golender.ui.dialogs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.util.MediaSaver
import com.monsivamon.golender.data.util.PhotoStorage
import com.monsivamon.golender.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 添付写真を全画面表示するビューア（複数枚は左右スワイプで切替）
@Composable
fun EventPhotoViewerDialog(
    photos: List<EventPhoto>,
    initialIndex: Int,
    colors: AppColors,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    // 現在表示中のページを管理するページャー状態
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size },
    )

    // 現在ページの写真を端末のピクチャフォルダに書き出す
    fun performSave(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        val src = PhotoStorage.getFile(context, photo.fileName)
        // タイムスタンプ付きの保存ファイル名を生成する
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "Golendar_${stamp}_${index + 1}.jpg"
        val result = MediaSaver.saveToPictures(context, src, name)
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
    }

    // 権限要求後に保存するための保留インデックス
    var pendingSaveIndex by remember { mutableStateOf<Int?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val idx = pendingSaveIndex
        pendingSaveIndex = null
        if (granted && idx != null) {
            performSave(idx)
        } else if (!granted) {
            Toast.makeText(
                context,
                "保存にはストレージへのアクセス許可が必要です",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 表示中の写真を保存する（権限を考慮）
    fun saveCurrent() {
        val idx = pagerState.currentPage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 以降は権限不要
            performSave(idx)
        } else {
            // 以前はストレージ権限を確認してから保存する
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                performSave(idx)
            } else {
                pendingSaveIndex = idx
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    // 現在ページの写真を FileProvider 経由で他アプリへ共有する
    fun shareCurrent() {
        val idx = pagerState.currentPage
        val photo = photos.getOrNull(idx) ?: return
        val file = PhotoStorage.getFile(context, photo.fileName)
        if (!file.exists()) {
            Toast.makeText(context, "写真ファイルが見つかりません", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // content:// URI に変換して共有 Intent を組み立てる
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "写真を共有"))
        } catch (e: Exception) {
            Toast.makeText(context, "共有に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.95f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 写真をページャーで左右スワイプ表示する
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val file = PhotoStorage.getFile(context, photos[page].fileName)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        )
                    }
                }
                // 複数枚のときはページ番号を上部に表示する
                if (photos.size > 1) {
                    Text(
                        "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }

                // 左上：保存＋共有ボタン
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { saveCurrent() }) {
                        Text("保存", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { shareCurrent() }) {
                        Text("共有", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // 右上：閉じるボタン
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) { Text("閉じる", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}