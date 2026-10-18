package com.takina.bilisearch.ui.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.local.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 把 AppLog 里的日志导出成 txt，方便用户反馈 */
object LogExporter {

    fun buildReport(context: Context): String = buildString {
        appendLine("BiliSearch 运行日志")
        appendLine("生成时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            appendLine("版本: ${info.versionName}")
        }
        appendLine("已登录: ${runCatching { AppGraph.repo.isLoggedIn() }.getOrDefault(false)}")
        appendLine("日志条数: ${AppLog.count()}")
        appendLine("----------------------------------------")
        append(AppLog.snapshot())
    }

    /** 保存到「下载/BiliSearch/」目录，返回可展示的路径 */
    fun save(context: Context): String? {
        val content = runCatching { buildReport(context) }.getOrNull() ?: return null
        val fileName = "bilisearch-log-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/BiliSearch")
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
                "下载/BiliSearch/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val root = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val dir = File(root, "BiliSearch").apply { mkdirs() }
                val file = File(dir, fileName)
                file.writeText(content)
                file.absolutePath
            }
        }.getOrNull()
    }
}
