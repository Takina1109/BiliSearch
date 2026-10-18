package com.takina.bilisearch.data.model

import com.google.gson.Gson
import com.takina.bilisearch.data.repo.ProfileSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验 nav 接口的大会员、经验、硬币字段解析。
 * 样例结构来自 bilibili-API-collect 文档。
 */
class ProfileParsingTest {

    private val gson = Gson()

    @Test
    fun parseLv6AnnualVip() {
        val json = """
            {
              "isLogin": true,
              "level_info": {
                "current_level": 6,
                "current_min": 28800,
                "current_exp": 52689,
                "next_exp": "--"
              },
              "mid": 293793435,
              "money": 172.4,
              "uname": "社会易姐QwQ",
              "face": "https://i0.hdslb.com/bfs/face/aebb2639a0d47f2ce1fec0631f412eaf53d4a0be.jpg",
              "vipStatus": 1,
              "vipType": 2,
              "vip_nickname_color": "#FB7299",
              "vip": {
                "type": 2,
                "status": 1,
                "due_date": 1707494400000,
                "label": {
                  "text": "年度大会员",
                  "label_theme": "annual_vip",
                  "text_color": "#FFFFFF",
                  "bg_color": "#FB7299",
                  "use_img_label": true,
                  "img_label_uri_hans_static": "https://i0.hdslb.com/bfs/vip/label.png"
                },
                "avatar_subscript": 1,
                "nickname_color": "#FB7299"
              }
            }
        """.trimIndent()

        val nav = gson.fromJson(json, NavInfo::class.java)

        assertTrue(nav.isLogin)
        assertEquals(293793435L, nav.mid)
        assertEquals("社会易姐QwQ", nav.uname)
        assertEquals(6, nav.levelInfo?.currentLevel)
        assertEquals(52689L, nav.levelInfo?.currentExp)
        assertNull(nav.levelInfo?.nextExpValue()) // Lv6 返回 "--"

        assertEquals(172.4, nav.money, 0.001)
        assertEquals(1, nav.vip?.status)
        assertEquals("年度大会员", nav.vip?.label?.text)
        assertEquals("#FB7299", nav.vip?.nicknameColor)
        assertEquals("https://i0.hdslb.com/bfs/vip/label.png", nav.vip?.label?.imgLabelUriHansStatic)
    }

    @Test
    fun parseLv5NumericNextExp() {
        val json = """
            {
              "isLogin": true,
              "level_info": {
                "current_level": 5,
                "current_min": 10800,
                "current_exp": 14270,
                "next_exp": 28800
              },
              "mid": 1,
              "money": 2.5,
              "uname": "test",
              "vipStatus": 0,
              "vip": { "status": 0, "nickname_color": "" }
            }
        """.trimIndent()

        val nav = gson.fromJson(json, NavInfo::class.java)

        assertEquals(5, nav.levelInfo?.currentLevel)
        assertEquals(28800L, nav.levelInfo?.nextExpValue())
        assertEquals(0, nav.vip?.status)
    }

    @Test
    fun expProgressUsesCurrentLevelRange() {
        val profile = ProfileSummary(
            isLogin = true,
            level = 5,
            expCurrent = 14270,
            expMin = 10800,
            expNext = 28800
        )
        // (14270 - 10800) / (28800 - 10800)
        assertEquals(0.1927f, profile.expProgress, 0.001f)
    }

    @Test
    fun expProgressIsFullWhenMaxLevel() {
        val profile = ProfileSummary(
            isLogin = true,
            level = 6,
            expCurrent = 52689,
            expMin = 28800,
            expNext = null
        )
        assertEquals(1f, profile.expProgress, 0.001f)
    }

    @Test
    fun parseOtherUserSpaceInfo() {
        val json = """
            {
              "mid": 2,
              "name": "碧诗",
              "sex": "男",
              "face": "https://i2.hdslb.com/bfs/face/x.jpg",
              "sign": "这是个人签名",
              "level": 6,
              "coins": 0,
              "vip": {
                "status": 1,
                "label": {
                  "text": "十年大会员",
                  "img_label_uri_hans_static": "https://i0.hdslb.com/bfs/vip/label.png"
                },
                "nickname_color": "#FB7299"
              }
            }
        """.trimIndent()

        val space = gson.fromJson(json, SpaceInfo::class.java)

        assertEquals(2L, space.mid)
        assertEquals("碧诗", space.name)
        assertEquals("男", space.sex)
        assertEquals("这是个人签名", space.sign)
        assertEquals(6, space.level)
        assertEquals(1, space.vip?.status)
        assertEquals("十年大会员", space.vip?.label?.text)
        assertEquals("#FB7299", space.vip?.nicknameColor)
    }
}
