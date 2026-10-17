package com.monsivamon.golender.data.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.monsivamon.golender.R
import java.io.File

// アプリ内の写真を端末のピクチャフォルダに書き出す（Android 10+ は権限不要）
object MediaSaver {

    // 保存処理の結果を保持するデータクラス
    data class Result(val success: Boolean, val message: String, val uri: Uri? = null)

    // 内部ストレージの写真を端末のピクチャフォルダに書き出す
    fun saveToPictures(context: Context, sourceFile: File, displayName: String): Result {
        if (!sourceFile.exists()) {
            return Result(false, context.getString(R.string.wd_mediaOriginalNotFound))
        }
        return try {
            // Android 10 以降は MediaStore、以前は File API を使う
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, sourceFile, displayName)
            } else {
                saveViaFileApi(context, sourceFile, displayName)
            }
        } catch (e: Exception) {
            Result(false, context.getString(R.string.wd_mediaSaveFailedWithReason, e.message ?: context.getString(R.string.vm_unknown_error)))
        }
    }

    // Android 10+ : MediaStore API で保存する（権限不要）
    private fun saveViaMediaStore(context: Context, sourceFile: File, displayName: String): Result {
        val resolver = context.contentResolver

        // MediaStore 登録用のメタ情報を組み立てる
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Golendar")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result(false, context.getString(R.string.wd_mediaStoreRegisterFailed))

        // 実ファイルの内容を URI へコピーする
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        } ?: return Result(false, context.getString(R.string.wd_mediaStreamOpenFailed))

        // IS_PENDING を解除して公開状態にする
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return Result(true, context.getString(R.string.wd_mediaSavedToPictures), uri)
    }

    // Android 9 以下 : File API で保存する（要 WRITE_EXTERNAL_STORAGE）
    @Suppress("DEPRECATION")
    private fun saveViaFileApi(context: Context, sourceFile: File, displayName: String): Result {
        // 公開ピクチャフォルダ配下の Golendar ディレクトリを用意する
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Golendar"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            return Result(false, context.getString(R.string.wd_mediaMkdirFailed))
        }
        val target = File(dir, displayName)
        sourceFile.copyTo(target, overwrite = true)
        return Result(true, context.getString(R.string.wd_mediaSavedToPictures), Uri.fromFile(target))
    }
}