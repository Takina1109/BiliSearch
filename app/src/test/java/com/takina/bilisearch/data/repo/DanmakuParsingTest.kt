package com.takina.bilisearch.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class DanmakuParsingTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <i><chatserver>chat.bilibili.com</chatserver>
        <d p="1.5,1,25,16777215,1750000000,0,abc,1">第一条弹幕</d>
        <d p="3.0,5,25,16711680,1750000001,0,def,0">顶部弹幕 &amp; 转义</d>
        <d p="0.2,4,18,255,1750000002,0,ghi,0">底部弹幕</d>
        </i>
    """.trimIndent()

    /** 真实接口返回的是 raw deflate（无 zlib 头），必须能解出来 */
    @Test
    fun `decodes raw deflate payload`() {
        val compressed = deflate(xml.toByteArray(Charsets.UTF_8), nowrap = true)
        val text = decodeDanmakuBytes(compressed)
        assertTrue("raw deflate 解码失败", text.contains("<d p="))
        assertEquals(3, parseDanmakuXml(text).size)
    }

    @Test
    fun `decodes zlib payload`() {
        val compressed = deflate(xml.toByteArray(Charsets.UTF_8), nowrap = false)
        val text = decodeDanmakuBytes(compressed)
        assertEquals(3, parseDanmakuXml(text).size)
    }

    @Test
    fun `decodes plain xml`() {
        assertEquals(3, parseDanmakuXml(decodeDanmakuBytes(xml.toByteArray(Charsets.UTF_8))).size)
    }

    @Test
    fun `parses fields and sorts by time`() {
        val items = parseDanmakuXml(xml)
        assertEquals(3, items.size)
        assertEquals(200L, items[0].timeMs)
        assertEquals(4, items[0].mode)
        assertEquals(1500L, items[1].timeMs)
        assertEquals(1, items[1].mode)
        assertEquals(16777215, items[1].color)
        assertEquals("第一条弹幕", items[1].text)
        assertEquals(5, items[2].mode)
        assertEquals("顶部弹幕 & 转义", items[2].text)
    }

    private fun deflate(bytes: ByteArray, nowrap: Boolean): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, nowrap)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
