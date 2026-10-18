package com.takina.bilisearch.data.repo

import com.takina.bilisearch.data.model.DanmakuItem
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * 弹幕接口（`/x/v1/dm/list.so`）的响应解码。
 *
 * **实测**：该接口返回 **raw deflate**（首字节形如 `8c bd`，没有 zlib 头 `78 9c`），
 * 用默认的 `Inflater()`（带 zlib 头）解会直接抛异常，弹幕列表就永远是空的。
 * 这里先按 raw deflate 解，失败再按 zlib 解，都不行就当明文。
 */
internal fun decodeDanmakuBytes(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    // 跳过空白后如果就是 '<'，说明没压缩
    var i = 0
    while (i < bytes.size && (bytes[i] == 0x20.toByte() ||
            bytes[i] == 0x0A.toByte() ||
            bytes[i] == 0x0D.toByte() ||
            bytes[i] == 0x09.toByte())
    ) {
        i++
    }
    if (i < bytes.size && bytes[i] == 0x3C.toByte()) {
        return String(bytes, Charsets.UTF_8)
    }
    return inflate(bytes, nowrap = true).ifBlank { inflate(bytes, nowrap = false) }
}

private fun inflate(bytes: ByteArray, nowrap: Boolean): String = runCatching {
    val inflater = Inflater(nowrap)
    try {
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream(bytes.size * 4)
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buffer, 0, count)
            }
        }
        String(out.toByteArray(), Charsets.UTF_8)
    } finally {
        inflater.end()
    }
}.getOrDefault("")

private val DANMAKU_REGEX =
    Regex("<d p=\"([^\"]*)\"[^>]*>(.*?)</d>", RegexOption.DOT_MATCHES_ALL)

/** 解析弹幕 XML：`<d p="时间,模式,字号,颜色,...">文本</d>` */
internal fun parseDanmakuXml(text: String): List<DanmakuItem> {
    if (text.isBlank()) return emptyList()
    return DANMAKU_REGEX.findAll(text).mapNotNull { match ->
        val parts = match.groupValues[1].split(",")
        val seconds = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val size = parts.getOrNull(2)?.toIntOrNull() ?: 25
        val color = parts.getOrNull(3)?.toLongOrNull()?.toInt() ?: 0xFFFFFF
        val content = match.groupValues[2]
        if (content.isBlank()) {
            null
        } else {
            DanmakuItem((seconds * 1000).toLong(), mode, size, color, unescapeXml(content))
        }
    }.sortedBy { it.timeMs }.toList()
}

private fun unescapeXml(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
