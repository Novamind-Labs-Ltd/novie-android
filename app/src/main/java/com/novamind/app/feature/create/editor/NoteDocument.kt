package com.novamind.app.feature.create.editor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject

/**
 * 正文文档（图文块 JSON）的轻量工具。正文统一存在 [com.novamind.app.feature.create.model.Note.body]：
 * 新数据是块结构 JSON，旧数据是纯文本，二者都能兼容解析。
 */
object NoteDocument {

    /**
     * 生成提交到服务端的正文：图片只保存稳定的 [fileId]，绝不上传设备本地绝对路径。
     * 尚未完成上传、没有 fileId 的图片暂不进入远端正文；上传成功后编辑器会再次触发保存。
     */
    fun forRemoteStorage(raw: String): String {
        if (!raw.trimStart().startsWith("{")) return raw
        return runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            val blocks = root["blocks"]?.jsonArray ?: return raw
            val remoteBlocks = blocks.mapNotNull { element ->
                val block = element.jsonObject
                if (block["type"]?.jsonPrimitive?.contentOrNull != "image") return@mapNotNull block
                if (block["fileId"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) return@mapNotNull null
                JsonObject(block - "path" - "remoteUrl")
            }
            JsonObject(root + ("blocks" to JsonArray(remoteBlocks))).toString()
        }.getOrDefault(raw)
    }

    /** 从正文文档提取用于列表预览/搜索的纯文本；图片以「[图片]」占位。 */
    fun previewText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        // 非 JSON 文档：按旧版纯文本原样返回
        if (!raw.trimStart().startsWith("{")) return raw
        return try {
            val arr = Json.parseToJsonElement(raw).jsonObject["blocks"]?.jsonArray ?: return raw
            val sb = StringBuilder()
            arr.forEach { element ->
                val block = element.jsonObject
                fun value(key: String) = block[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                val piece = when (value("type")) {
                    "text" -> value("text")
                    "image" -> "[Image]"
                    "file" -> value("name").ifBlank { "Document" }.let { "[$it]" }
                    "markdown" -> markdownPlainText(value("content"))
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

    /** 将 Ask Novie 返回的 Markdown 保存成编辑器原生 Markdown 块文档。 */
    fun fromMarkdown(markdown: String): String = buildJsonObject {
        put(
            "blocks",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "markdown")
                        put("content", markdown)
                    }
                )
            },
        )
    }.toString()

    private fun markdownPlainText(markdown: String): String = markdown
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .lineSequence()
        .map { line ->
            line.replace(Regex("^\\s{0,3}(#{1,6}|>|[-+*]|\\d+[.)])\\s+"), "")
                .replace(Regex("[*_~`]+"), "")
                .trimEnd()
        }
        .joinToString("\n")
        .trim()

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

    /** 取正文文档里第一张图片的服务端 fileId（本地路径失效时用它换签名 URL）；无则 null。 */
    fun firstImageFileId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (!raw.trimStart().startsWith("{")) return null
        return try {
            val arr = JSONObject(raw).getJSONArray("blocks")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("type") == "image") {
                    val fid = o.optString("fileId")
                    if (fid.isNotBlank()) return fid
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
