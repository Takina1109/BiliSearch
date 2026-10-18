package com.takina.bilisearch.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.takina.bilisearch.data.repo.toEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验合集接口（seasons_series_list / seasons_archives_list）的字段解析。
 * 样例结构来自实际接口响应。
 */
class SeasonParsingTest {

    private val gson = Gson()

    @Test
    fun parseSeasonList() {
        val json = """
            {
              "code": 0,
              "message": "OK",
              "ttl": 1,
              "data": {
                "items_lists": {
                  "page": { "page_num": 1, "page_size": 20, "total": 32 },
                  "seasons_list": [
                    {
                      "archives": [
                        { "aid": 238588630, "bvid": "BV1be41127ft", "title": "样片", "duration": 974 }
                      ],
                      "meta": {
                        "category": 0,
                        "cover": "https://archive.biliimg.com/bfs/archive/e2ca3e5a.jpg",
                        "description": "",
                        "mid": 946974,
                        "name": "合集·样片日记",
                        "ptime": 1785488487,
                        "season_id": 2046621,
                        "total": 26,
                        "title": "样片日记"
                      },
                      "recent_aids": [238588630]
                    }
                  ],
                  "series_list": []
                }
              }
            }
        """.trimIndent()

        val type = object : TypeToken<BiliResponse<SeasonListData>>() {}.type
        val response: BiliResponse<SeasonListData> = gson.fromJson(json, type)
        val items = response.data?.itemsLists?.seasonsList.orEmpty()

        assertEquals(1, items.size)
        assertEquals(32, response.data?.itemsLists?.page?.total)

        val entry = items.first().toEntry()
        assertEquals(2046621L, entry?.id)
        assertEquals("合集·样片日记", entry?.title)
        assertEquals(26, entry?.count)
        assertTrue(entry?.cover.orEmpty().startsWith("https://"))
    }

    @Test
    fun parseSeasonArchives() {
        val json = """
            {
              "code": 0,
              "message": "OK",
              "data": {
                "archives": [
                  {
                    "aid": 238588630,
                    "bvid": "BV1be41127ft",
                    "duration": 974,
                    "pic": "http://i2.hdslb.com/bfs/archive/e2ca3e5a.jpg",
                    "pubdate": 1704877200,
                    "stat": { "view": 3258865, "danmaku": 15214 },
                    "title": "零下26度 的新疆，跟着影视飓风去拍样片！"
                  }
                ],
                "page": { "page_num": 1, "page_size": 20, "total": 26 },
                "aids": [238588630]
              }
            }
        """.trimIndent()

        val type = object : TypeToken<BiliResponse<SeasonArchivesData>>() {}.type
        val response: BiliResponse<SeasonArchivesData> = gson.fromJson(json, type)
        val archives = response.data?.archives.orEmpty()

        assertEquals(1, archives.size)
        assertEquals(26, response.data?.page?.total)

        val entry = archives.first().toEntry()
        assertEquals("BV1be41127ft", entry.id)
        assertEquals("https://www.bilibili.com/video/BV1be41127ft", entry.target)
        assertEquals("http://i2.hdslb.com/bfs/archive/e2ca3e5a.jpg".replace("http://", "https://"), entry.cover)
        assertTrue(entry.meta.contains("播放"))
    }

    @Test
    fun parseBlacklist() {
        val json = """
            {
              "code": 0,
              "message": "OK",
              "data": {
                "total": 1,
                "list": [
                  {
                    "mid": 123,
                    "attribute": 128,
                    "uname": "被拉黑的人",
                    "face": "http://i0.hdslb.com/bfs/face/abc.jpg",
                    "sign": "签名"
                  }
                ]
              }
            }
        """.trimIndent()

        val type = object : TypeToken<BiliResponse<BlacklistData>>() {}.type
        val response: BiliResponse<BlacklistData> = gson.fromJson(json, type)
        val users = response.data?.list.orEmpty()

        assertEquals(1, users.size)
        assertEquals(1, response.data?.total)
        val entry = users.first().toEntry()
        assertEquals(123L, entry.mid)
        assertEquals("被拉黑的人", entry.name)
        assertEquals("签名", entry.sign)
        assertTrue(entry.avatar.orEmpty().startsWith("https://"))
    }
}
