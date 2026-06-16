package com.novamind.app.feature.asknovie

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ─── 共享数据模型（会话 / 消息 / 附件） ──────────────────────────────────────

enum class Role { User, Assistant }
enum class AttachType { Image, File, Audio }
data class Attachment(val type: AttachType, val path: String, val name: String)
data class ChatMessage(
    val role: Role,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
)

/** 一次会话。[title] 默认取第一句话。 */
data class ChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
)

/**
 * 会话历史存储：以 JSON 持久化到 SharedPreferences，应用重启仍在。
 * 体量小、无需 Room；按 updatedAt 倒序返回。
 */
object ChatSessionStore {

    private const val PREFS = "chat_sessions"
    private const val KEY = "sessions_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读取全部会话，最近更新的在前。 */
    fun load(context: Context): List<ChatSession> = runCatching {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length())
            .map { sessionFromJson(arr.getJSONObject(it)) }
            .sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    /** 新增或更新一条会话（按 id 去重）。 */
    fun upsert(context: Context, session: ChatSession) {
        val list = load(context).filterNot { it.id == session.id } + session
        save(context, list)
    }

    /** 删除一条会话。 */
    fun delete(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    private fun save(context: Context, sessions: List<ChatSession>) {
        val arr = JSONArray()
        sessions.forEach { arr.put(sessionToJson(it)) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    // ─── JSON 序列化 ──────────────────────────────────────────────────────

    private fun sessionToJson(s: ChatSession): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("title", s.title)
        put("updatedAt", s.updatedAt)
        put("messages", JSONArray().apply { s.messages.forEach { put(messageToJson(it)) } })
    }

    private fun sessionFromJson(o: JSONObject): ChatSession {
        val msgArr = o.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until msgArr.length()).map { messageFromJson(msgArr.getJSONObject(it)) }
        return ChatSession(
            id = o.getString("id"),
            title = o.optString("title", "New chat"),
            updatedAt = o.optLong("updatedAt", 0L),
            messages = messages,
        )
    }

    private fun messageToJson(m: ChatMessage): JSONObject = JSONObject().apply {
        put("role", m.role.name)
        put("text", m.text)
        put("attachments", JSONArray().apply {
            m.attachments.forEach {
                put(JSONObject().apply {
                    put("type", it.type.name)
                    put("path", it.path)
                    put("name", it.name)
                })
            }
        })
    }

    private fun messageFromJson(o: JSONObject): ChatMessage {
        val attArr = o.optJSONArray("attachments") ?: JSONArray()
        val atts = (0 until attArr.length()).map {
            val a = attArr.getJSONObject(it)
            Attachment(
                type = runCatching { AttachType.valueOf(a.getString("type")) }.getOrDefault(AttachType.File),
                path = a.optString("path", ""),
                name = a.optString("name", ""),
            )
        }
        return ChatMessage(
            role = runCatching { Role.valueOf(o.getString("role")) }.getOrDefault(Role.User),
            text = o.optString("text", ""),
            attachments = atts,
        )
    }
}
