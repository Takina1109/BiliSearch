package com.takina.bilisearch.data.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 只管理 bilibili 域下的 Cookie，并支持导出/恢复（用于登录态持久化）。
 */
class BiliCookieJar : CookieJar {

    private val cookies = ConcurrentHashMap<String, String>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            if (cookie.value.isNotBlank()) {
                this.cookies[cookie.name] = cookie.value
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.map { (name, value) ->
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain("bilibili.com")
            .path("/")
            .build()
    }

    fun put(name: String, value: String) {
        if (value.isNotBlank()) cookies[name] = value
    }

    fun get(name: String): String? = cookies[name]

    fun remove(name: String) {
        cookies.remove(name)
    }

    fun snapshot(): Map<String, String> = HashMap(cookies)

    fun restore(map: Map<String, String>) {
        cookies.putAll(map.filterValues { it.isNotBlank() })
    }

    fun clear() = cookies.clear()
}
