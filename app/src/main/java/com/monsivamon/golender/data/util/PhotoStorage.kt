package com.monsivamon.golender.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// 予定に添付する写真の保存を担当（長辺1920pxに縮小、EXIF回転を自動補正）
object PhotoStorage {
    private const val DIR_NAME = "event_photos"
    const val MAX_LONG_EDGE = 1920
    private const val JPEG_QUALITY = 85

    // 写真保存ディレクトリを取得する（存在しなければ作成）。
    fun getPhotoDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ファイル名から実ファイルを取得する。
    fun getFile(context: Context, fileName: String): File = File(getPhotoDir(context), fileName)

    // 外部URIの画像を縮小してアプリ内に保存する。
    suspend fun saveCompressed(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val bmp = decodeScaled(context, uri) ?: return@withContext null
                val name = generateName()
                getFile(context, name).outputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                bmp.recycle()
                name
            } catch (e: Exception) { e.printStackTrace(); null }
        }

    // ZIPインポート時にバイト列から写真を復元する。
    suspend fun saveBytes(context: Context, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            try {
                val name = generateName()
                getFile(context, name).writeBytes(bytes)
                name
            } catch (e: Exception) { e.printStackTrace(); null }
        }

    // 添付写真ファイルを削除する。
    fun delete(context: Context, fileName: String) {
        try { getFile(context, fileName).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
    }

    // 複数の写真ファイルをまとめて削除する。
    fun deleteAll(context: Context, fileNames: Collection<String>) {
        fileNames.forEach { delete(context, it) }
    }

    // 内部ストレージ上の全写真ファイルを削除する。
    fun deleteAllFiles(context: Context) {
        try { getPhotoDir(context).listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
    }

    // ユニークなファイル名を生成する。
    private fun generateName(): String =
        "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"

    // URI から画像を読み込み、長辺を縮小して返す。
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(context, uri)
        else decodeWithBitmapFactory(context, uri)

    // API 28+ : EXIF 回転補正とスケーリングを同時に行う。
    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap? {
        return try {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val le = maxOf(w, h)
                if (le > MAX_LONG_EDGE) {
                    val s = MAX_LONG_EDGE.toFloat() / le
                    decoder.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    // フォールバック: BitmapFactory で縮小（EXIF回転は未補正）。
    @Suppress("unused")
    private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val o1 = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o1)
            var sample = 1
            val le = maxOf(o1.outWidth, o1.outHeight)
            while (le / sample > MAX_LONG_EDGE * 2) sample *= 2
            val o2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2) ?: return null
            val w = decoded.width; val h = decoded.height; val l = maxOf(w, h)
            if (l <= MAX_LONG_EDGE) return decoded
            val s = MAX_LONG_EDGE.toFloat() / l
            val scaled = Bitmap.createScaledBitmap(decoded, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } catch (e: Exception) { e.printStackTrace(); null }
    }
}
