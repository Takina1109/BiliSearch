package com.takina.bilisearch.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验番剧（PGC）接口解析。
 *
 * 注意 PGC 系列接口的数据在 `result` 里而不是 `data`，样例结构来自
 * `/pgc/view/web/season` 与 `/pgc/player/web/playurl` 的实际响应。
 */
class PgcParsingTest {

    private val gson = Gson()

    private val seasonJson = """
        {
          "code": 0,
          "message": "success",
          "result": {
            "season_id": 33415,
            "media_id": 28228813,
            "title": "名侦探柯南（中配）",
            "cover": "https://i0.hdslb.com/bfs/bangumi/image/x.png",
            "evaluate": "简介",
            "episodes": [
              {
                "aid": 540491915,
                "bvid": "BV1wi4y1b7rM",
                "cid": 184593713,
                "id": 323085,
                "ep_id": 323085,
                "cover": "https://i0.hdslb.com/a.jpg",
                "duration": 1494000,
                "title": "1",
                "long_title": "云霄 飞车杀人事件",
                "show_title": "第1话 云霄 飞车杀人事件",
                "section_type": 0
              },
              {
                "aid": 540491916,
                "bvid": "BV1wi4y1b7rN",
                "cid": 184593714,
                "id": 323086,
                "ep_id": 323086,
                "duration": 1494000,
                "long_title": "第二话",
                "section_type": 0
              }
            ],
            "section": [
              {
                "id": 46606,
                "title": "SP",
                "type": 2,
                "episodes": [
                  {
                    "aid": 840445178,
                    "bvid": "BV1C54y1Q7ua",
                    "cid": 184814832,
                    "id": 324059,
                    "ep_id": 324059,
                    "duration": 11000,
                    "long_title": "SP",
                    "section_type": 2
                  }
                ]
              }
            ],
            "up_info": {
              "mid": 928123,
              "uname": "哔哩哔哩番剧",
              "avatar": "https://i1.hdslb.com/bfs/face/x.jpg",
              "follower": 9076357
            }
          }
        }
    """.trimIndent()

    private val playJson = """
        {
          "code": 0,
          "message": "success",
          "result": {
            "quality": 80,
            "accept_quality": [80, 64, 32, 16],
            "support_formats": [
              { "quality": 80, "new_description": "1080P 高清" },
              { "quality": 64, "new_description": "720P 高清" }
            ],
            "dash": {
              "video": [
                {
                  "id": 80,
                  "baseUrl": "https://19e949o.edge.mountaintoys.cn/v.m4s",
                  "codecid": 7,
                  "height": 1080
                }
              ],
              "audio": [
                { "id": 30280, "baseUrl": "https://19e949o.edge.mountaintoys.cn/a.m4s", "bandwidth": 320000 }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses season from result envelope`() {
        val type = object : TypeToken<PgcResponse<PgcSeason>>() {}.type
        val response: PgcResponse<PgcSeason> = gson.fromJson(seasonJson, type)
        val season = response.result
        assertNotNull("PGC 数据在 result 里，不能读 data", season)
        season!!
        assertEquals(33415L, season.seasonId)
        assertEquals("名侦探柯南（中配）", season.title)
        assertEquals("哔哩哔哩番剧", season.upInfo?.uname)
        assertEquals(928123L, season.upInfo?.mid)
    }

    @Test
    fun `allEpisodes merges main episodes and sections`() {
        val type = object : TypeToken<PgcResponse<PgcSeason>>() {}.type
        val season = gson.fromJson<PgcResponse<PgcSeason>>(seasonJson, type).result!!
        val episodes = season.allEpisodes()
        assertEquals(3, episodes.size)
        // 正片在前，花絮在后
        assertEquals(323085L, episodes[0].episodeId())
        assertEquals(324059L, episodes[2].episodeId())
        assertEquals(1494L, episodes[0].duration / 1000)
    }

    @Test
    fun `parses play url from result envelope`() {
        val type = object : TypeToken<PgcResponse<PlayData>>() {}.type
        val play = gson.fromJson<PgcResponse<PlayData>>(playJson, type).result
        assertNotNull("PGC 播放地址同样在 result 里", play)
        play!!
        assertEquals(80, play.quality)
        assertEquals(1, play.dash?.video?.size)
        assertTrue(play.dash?.video?.first()?.url().orEmpty().contains("m4s"))
        assertEquals(2, play.supportFormats?.size)
    }
}
