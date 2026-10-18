package com.takina.bilisearch.data.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * B 站 WBI 签名。
 * 参考：bilibili-API-collect / docs/misc/sign/wbi.md
 */
object WbiSigner {

    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    private const val KEY_TTL_MS = 12 * 60 * 60 * 1000L

    private val mutex = Mutex()

    @Volatile
    private var mixinKey: String? = null

    @Volatile
    private var keyFetchedAt: Long = 0L

    private suspend fun key(): String {
        val cached = mixinKey
        if (cached != null && System.currentTimeMillis() - keyFetchedAt < KEY_TTL_MS) {
            return cached
        }
        return mutex.withLock {
            val again = mixinKey
            if (again != null && System.currentTimeMillis() - keyFetchedAt < KEY_TTL_MS) {
                again
            } else {
                fetchKey()
            }
        }
    }

    private suspend fun fetchKey(): String {
        val json = BiliHttp.rawGet("https://api.bilibili.com/x/web-interface/nav")
        val wbiImg = json.get("data")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("wbi_img")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw BiliException(-1, "无法获取 WBI 密钥")

        val imgUrl = wbiImg.optStringOr("img_url")
        val subUrl = wbiImg.optStringOr("sub_url")
        val imgKey = imgUrl.substringAfterLast('/').substringBefore('.')
        val subKey = subUrl.substringAfterLast('/').substringBefore('.')
        val raw = imgKey + subKey
        if (raw.isBlank()) throw BiliException(-1, "WBI 密钥格式异常")

        val key = buildString {
            for (index in mixinKeyEncTab) {
                if (index < raw.length) append(raw[index])
            }
        }.take(32)

        mixinKey = key
        keyFetchedAt = System.currentTimeMillis()
        return key
    }

    /**
     * 对参数签名，返回可直接请求的完整 URL。
     */
    suspend fun sign(baseUrl: String, params: Map<String, String>): String {
        val key = key()
        val withTs = params.toMutableMap()
        withTs["wts"] = (System.currentTimeMillis() / 1000).toString()

        val query = withTs.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

        val wRid = md5(query + key)
        return "$baseUrl?$query&w_rid=$wRid"
    }

    private fun encode(value: String): String {
        val filtered = value.filterNot { it == '!' || it == '\'' || it == '(' || it == ')' || it == '*' }
        return URLEncoder.encode(filtered, "UTF-8").replace("+", "%20")
    }

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
