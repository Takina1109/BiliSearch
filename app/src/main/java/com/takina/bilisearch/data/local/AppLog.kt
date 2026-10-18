package com.takina.bilisearch.data.local

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量日志缓冲：同时写 logcat 并在内存里保留最近 N 条，
 * 设置页可以把这些日志导出成 txt 方便反馈问题。
 */
object AppLog {

    private const val MAX_LINES = 1200
    private const val TAG = "BiliSearch"

    private val buffer = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        append("D/$tag $message", null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        append("E/$tag $message", throwable)
    }

    private fun append(body: String, throwable: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(body)
            if (throwable != null) {
                append(" :: ")
                append(throwable.javaClass.simpleName)
                append(": ")
                append(throwable.message)
            }
        }
        synchronized(buffer) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(line)
        }
        if (body.startsWith("E/")) {
            Log.e(TAG, line, throwable)
        } else {
            Log.d(TAG, line)
        }
    }

    fun snapshot(): String = synchronized(buffer) { buffer.joinToString("\n") }

    fun count(): Int = synchronized(buffer) { buffer.size }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }
}
