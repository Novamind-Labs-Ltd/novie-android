package com.novamind.app.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

/**
 * Gson 工具：统一一个全局 [Gson] 实例，封装对象 ↔ JSON 的安全转换。
 *
 * - 解析失败统一返回 null / 空集合，不抛异常给调用方；
 * - 用 reified 泛型 + [TypeToken] 支持泛型集合（如 `List<Foo>`）。
 */
object GsonUtils {

    /** 全局共享实例：宽松解析、不转义 HTML、序列化时保留 null 字段。 */
    val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .create()

    /** 对象 → JSON 字符串。 */
    fun toJson(src: Any?): String = gson.toJson(src)

    /** JSON → 对象；失败返回 null。 */
    inline fun <reified T> fromJson(json: String?): T? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, T::class.java) }.getOrNull()
    }

    /** JSON → 泛型对象（如 `List<Foo>` / `Map<String, Foo>`）；失败返回 null。 */
    inline fun <reified T> fromJsonType(json: String?): T? {
        if (json.isNullOrBlank()) return null
        val type = object : TypeToken<T>() {}.type
        return runCatching { gson.fromJson<T>(json, type) }.getOrNull()
    }

    /** JSON → 列表；失败返回空列表。 */
    inline fun <reified T> toList(json: String?): List<T> =
        fromJsonType<List<T>>(json) ?: emptyList()
}
