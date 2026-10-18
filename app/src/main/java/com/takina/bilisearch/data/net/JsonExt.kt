package com.takina.bilisearch.data.net

import com.google.gson.JsonObject

fun JsonObject.optIntOr(key: String, def: Int = 0): Int {
    val el = get(key) ?: return def
    if (!el.isJsonPrimitive) return def
    return runCatching { el.asInt }.getOrDefault(def)
}

fun JsonObject.optLongOr(key: String, def: Long = 0L): Long {
    val el = get(key) ?: return def
    if (!el.isJsonPrimitive) return def
    return runCatching { el.asLong }.getOrDefault(def)
}

fun JsonObject.optStringOr(key: String, def: String = ""): String {
    val el = get(key) ?: return def
    if (!el.isJsonPrimitive) return def
    return runCatching { el.asString }.getOrDefault(def)
}
