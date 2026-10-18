package com.takina.bilisearch.data.net

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.takina.bilisearch.data.local.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type

object BiliHttp {

    val gson = Gson()
    val cookieJar = BiliCookieJar()

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** 官方代理只认桌面浏览器 UA，用移动端 UA 会被 403 */
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                // 不要覆盖调用方指定的 Referer/Origin：
                // 投币、点赞、收藏等接口会校验 Referer，被改成首页就会返回「非法访问」
                if (chain.request().header("Referer") == null) {
                    builder.header("Referer", "https://www.bilibili.com/")
                }
                if (chain.request().header("Origin") == null) {
                    builder.header("Origin", "https://www.bilibili.com")
                }
                if (chain.request().header("Accept") == null) {
                    builder.header("Accept", "application/json, text/plain, */*")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    /** 播放器专用的客户端：带上 CDN 需要的 Referer 与 Accept，避免 403 */
    val mediaClient: OkHttpClient by lazy {
        client.newBuilder()
            // CDN 不需要（也不会用到）B 站 Cookie，带上反而可能触发校验问题
            .cookieJar(CookieJar.NO_COOKIES)
            // 部分 P2P / mcdn 节点对 HTTP/2 + Range 支持不好，固定用 HTTP/1.1
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "*/*")
                        .header("Referer", "https://www.bilibili.com/")
                        .header("Accept-Encoding", "identity")
                        .build()
                )
            }
            .build()
    }

    /**
     * 走 B 站官方代理（`proxy-tf-all-ws.bilivideo.com/?url=`）时用的客户端。
     * 实测：代理会拒组移动端 UA（返回 403），改成桌面浏览器 UA 后连续请求全部 206。
     */
    val proxyMediaClient: OkHttpClient by lazy {
        mediaClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", DESKTOP_USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    /**
     * 只访问页面、不解析响应。
     * B 站搜索等接口需要先访问一次 bilibili.com 拿到 buvid3 / b_nut 等 Cookie。
     */
    suspend fun visit(url: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                    )
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    AppLog.d(
                        "http",
                        "VISIT ${response.code} ${response.request.url.host} " +
                            "cookies=${cookieJar.snapshot().keys}"
                    )
                    response.body?.string()
                }
            }
        }
    }

    suspend fun rawGet(url: String, referer: String? = null): JsonObject =
        execute(Request.Builder().url(url).get(), referer)

    suspend fun rawPost(url: String, referer: String? = null): JsonObject {
        val emptyBody = "".toRequestBody("application/x-www-form-urlencoded".toMediaType())
        return execute(Request.Builder().url(url).post(emptyBody), referer)
    }

    /** application/x-www-form-urlencoded 表单提交 */
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        referer: String? = null
    ): JsonObject {
        val body = FormBody.Builder()
            .apply { form.forEach { (key, value) -> add(key, value) } }
            .build()
        return execute(Request.Builder().url(url).post(body), referer)
    }

    /** 直接拿原始字节（弹幕 XML 可能是 deflate 压缩的） */
    suspend fun rawBytes(url: String, referer: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            if (!referer.isNullOrBlank()) builder.header("Referer", referer)
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.e("http", "HTTP ${response.code} $url")
                    throw BiliException(response.code, "网络错误：HTTP ${response.code}")
                }
                response.body?.bytes() ?: ByteArray(0)
            }
        }

    private suspend fun execute(
        builder: Request.Builder,
        referer: String?
    ): JsonObject = withContext(Dispatchers.IO) {
        if (!referer.isNullOrBlank()) builder.header("Referer", referer)
        val request = builder.build()
        AppLog.d("http", "${request.method} ${request.url}")

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                AppLog.e("http", "HTTP ${response.code} ${request.url}\n${body.take(600)}")
                throw BiliException(response.code, "网络错误：HTTP ${response.code}")
            }
            AppLog.d("http", "${response.code} ${request.url.encodedPath} ${body.take(300)}")
            runCatching { JsonParser.parseString(body).asJsonObject }
                .getOrElse {
                    AppLog.e("http", "解析失败 ${request.url}\n${body.take(300)}")
                    throw BiliException(-1, "返回数据解析失败")
                }
        }
    }

    /**
     * 发起 GET 并校验 code == 0，然后反序列化为 [type]。
     */
    suspend fun <T> get(url: String, type: Type, referer: String? = null): T {
        val json = rawGet(url, referer)
        val code = json.optIntOr("code", -1)
        if (code != 0) {
            throw BiliException(code, json.optStringOr("message").ifBlank { "请求失败（code $code）" })
        }
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson<T>(json, type)
    }
}
