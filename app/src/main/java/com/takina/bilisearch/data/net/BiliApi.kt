package com.takina.bilisearch.data.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BiliApi {

    const val API = "https://api.bilibili.com"
    const val PASSPORT = "https://passport.bilibili.com"
    const val SEARCH_REFERER = "https://search.bilibili.com/"

    private val buvidMutex = Mutex()

    @Volatile
    private var buvidReady = false

    /**
     * 风控需要设备 id（buvid3/buvid4），匿名请求前先补齐。
     */
    suspend fun ensureBuvid() {
        if (buvidReady) return
        buvidMutex.withLock {
            if (buvidReady) return
            if (BiliHttp.cookieJar.get("buvid3").isNullOrBlank()) {
                // 先访问一次首页，让服务端下发 buvid3 / b_nut 等 Cookie（搜索接口必需）
                BiliHttp.visit("https://www.bilibili.com/")
            }
            if (BiliHttp.cookieJar.get("buvid3").isNullOrBlank()) {
                // 首页 Cookie 失败时再用指纹接口兜底
                runCatching {
                    val json = BiliHttp.rawGet("$API/x/frontend/finger/spi")
                    val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    data?.optStringOr("b_3").takeIf { !it.isNullOrBlank() }
                        ?.let { BiliHttp.cookieJar.put("buvid3", it) }
                    data?.optStringOr("b_4").takeIf { !it.isNullOrBlank() }
                        ?.let { BiliHttp.cookieJar.put("buvid4", it) }
                }
            }
            if (BiliHttp.cookieJar.get("_uuid").isNullOrBlank()) {
                BiliHttp.cookieJar.put(
                    "_uuid",
                    UUID.randomUUID().toString().uppercase() + "infoc"
                )
            }
            // bili_ticket 能显著降低搜索接口的风控概率
            if (BiliHttp.cookieJar.get("bili_ticket").isNullOrBlank()) {
                runCatching { genTicket() }
            }
            android.util.Log.d(
                "BiliApi",
                "guest cookies=${BiliHttp.cookieJar.snapshot().keys} " +
                    "buvid3=${BiliHttp.cookieJar.get("buvid3")?.take(12)}"
            )
            buvidReady = true
        }
    }

    /**
     * 丢弃当前匿名身份（不影响登录态），下次请求会重新获取 buvid 与票据。
     */
    fun resetGuest() {
        listOf(
            "buvid3", "buvid4", "b_nut",
            "bili_ticket", "bili_ticket_expires", "_uuid"
        ).forEach { BiliHttp.cookieJar.remove(it) }
        buvidReady = false
    }

    /**
     * 登录成功后调用：重新获取与当前登录态匹配的 buvid3 / bili_ticket。
     * 投币、点赞等接口会校验 buvid3 是否「存在且正常」，否则报非法访问。
     */
    suspend fun refreshBuvid() {
        resetGuest()
        ensureBuvid()
    }

    /**
     * 生成 web 端票据（key_id=ec02，消息为 "ts" + 时间戳）。
     */
    private suspend fun genTicket() {
        val ts = System.currentTimeMillis() / 1000
        val hexSign = hmacSha256(TICKET_HMAC_KEY, "ts$ts")
        val url = "$API/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket" +
            "?key_id=ec02&hexsign=$hexSign&context[ts]=$ts&csrf="

        val json = BiliHttp.rawPost(url, "https://www.bilibili.com/")
        val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val ticket = data.optStringOr("ticket")
        val ttl = data.optLongOr("ttl", 0)
        if (ticket.isNotBlank() && ttl > 0) {
            BiliHttp.cookieJar.put("bili_ticket", ticket)
            BiliHttp.cookieJar.put("bili_ticket_expires", (ts + ttl).toString())
        }
    }

    private fun hmacSha256(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        val bytes = mac.doFinal(message.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private const val TICKET_HMAC_KEY = "XgwSnGZ1p"
}
