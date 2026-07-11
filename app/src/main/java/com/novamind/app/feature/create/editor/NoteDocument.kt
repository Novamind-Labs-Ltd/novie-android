package com.novamind.app.feature.create.editor

import org.json.JSONObject

/**
 * 正文文档（图文块 JSON）的轻量工具。正文统一存在 [com.novamind.app.feature.create.model.Note.body]：
 * 新数据是块结构 JSON，旧数据是纯文本，二者都能兼容解析。
 */
object NoteDocument {

    /** 从正文文档提取用于列表预览/搜索的纯文本；图片以「[图片]」占位。 */
    fun previewText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        // 非 JSON 文档：按旧版纯文本原样返回
        if (!raw.trimStart().startsWith("{")) return raw
        return try {
            val arr = JSONObject(raw).getJSONArray("blocks")
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val piece = when (o.optString("type")) {
                    "text" -> o.optString("text")
                    "image" -> "[Image]"
                    "file" -> o.optString("name").ifBlank { "Document" }.let { "[$it]" }
                    else -> ""
                }
                if (piece.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(piece)
                }
            }
            sb.toString()
        } catch (_: Exception) {
            raw
        }
    }

    /** 取正文文档里的第一张图片路径；没有图片或为旧版纯文本时返回 null。 */
    fun firstImagePath(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (!raw.trimStart().startsWith("{")) return null
        return try {
            val arr = JSONObject(raw).getJSONArray("blocks")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("type") == "image") {
                    val path = o.optString("path")
                    if (path.isNotBlank()) return path
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
