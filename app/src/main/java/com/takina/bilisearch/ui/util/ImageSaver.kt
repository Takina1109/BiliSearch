package com.takina.bilisearch.ui.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.takina.bilisearch.data.net.BiliHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ImageSaver {

    /**
     * 下载图片并保存到系统相册（Pictures/BiliSearch）。
     * Android 10+ 使用 MediaStore，无需存储权限。
     */
    suspend fun saveToGallery(
        context: Context,
        url: String,
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val bytes = BiliHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                response.body?.bytes()
            } ?: return@withContext false

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext false

            val name = if (fileName.contains('.')) fileName else "$fileName.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(context, bitmap, name)
            } else {
                saveToPublicPictures(context, bitmap, name)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun saveWithMediaStore(context: Context, bitmap: Bitmap, name: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/BiliSearch"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        } ?: return false

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    private fun saveToPublicPictures(context: Context, bitmap: Bitmap, name: String): Boolean {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "BiliSearch"
        )
        if (!dir.exists() && !dir.mkdirs()) return false

        val file = File(dir, name)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null
        )
        return true
    }
}
