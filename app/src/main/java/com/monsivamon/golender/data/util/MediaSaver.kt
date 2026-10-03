package com.monsivamon.golender.data.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

// アプリ内の写真を端末のピクチャフォルダに書き出す（Android 10+ は権限不要）
object MediaSaver {

    // 保存処理の結果を保持するデータクラス
    data class Result(val success: Boolean, val message: String, val uri: Uri? = null)

    // 内部ストレージの写真を端末のピクチャフォルダに書き出す
    fun saveToPictures(context: Context, sourceFile: File, displayName: String): Result {
        if (!sourceFile.exists()) {
            return Result(false, "元のファイルが見つかりません")
        }
        return try {
            // Android 10 以降は MediaStore、以前は File API を使う
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, sourceFile, displayName)
            } else {
                saveViaFileApi(sourceFile, displayName)
            }
        } catch (e: Exception) {
            Result(false, "保存に失敗しました: ${e.message}")
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
            ?: return Result(false, "MediaStore への登録に失敗しました")

        // 実ファイルの内容を URI へコピーする
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        } ?: return Result(false, "出力ストリームを開けませんでした")

        // IS_PENDING を解除して公開状態にする
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return Result(true, "ピクチャ/Golendar に保存しました", uri)
    }

    // Android 9 以下 : File API で保存する（要 WRITE_EXTERNAL_STORAGE）
    @Suppress("DEPRECATION")
    private fun saveViaFileApi(sourceFile: File, displayName: String): Result {
        // 公開ピクチャフォルダ配下の Golendar ディレクトリを用意する
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Golendar"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            return Result(false, "保存先フォルダを作成できませんでした")
        }
        val target = File(dir, displayName)
        sourceFile.copyTo(target, overwrite = true)
        return Result(true, "ピクチャ/Golendar に保存しました", Uri.fromFile(target))
    }
}