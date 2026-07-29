package com.novamind.app.feature.asknovie

import android.content.Context
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.feature.asknovie.data.OptionItem
import org.json.JSONArray
import org.json.JSONObject

// ─── 共享数据模型（会话 / 消息 / 附件） ──────────────────────────────────────

enum class Role { User, Assistant }
enum class AttachType { Image, File, Audio }
data class Attachment(
    val type: AttachType,
    val path: String,
    val name: String,
    val remoteFileId: String? = null,
)

/** 助手消息中的临时工具状态，不做 JSON 持久化。 */
sealed interface ChatBlock {
    data class SkillStatus(val label: String, val working: Boolean = true) : ChatBlock
}

data class ChatMessage(
    val role: Role,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    // 富内容块（非文本消息）；为空则按普通文本渲染。不持久化。
    val block: ChatBlock? = null,
    // SSE 交互卡片；卡片内容会持久化，临时选中状态不持久化。
    val card: ChatCard? = null,
    // 助手消息是否显示花标头像（用于总结/收尾语气的消息）。
    val showAvatar: Boolean = false,
    // 是否为灰字状态行（如「Creation … is done.」），不带操作行。
    val dim: Boolean = false,
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
                    it.remoteFileId?.let { fileId -> put("remoteFileId", fileId) }
                })
            }
        })
        m.card?.let { put("card", cardToJson(it)) }
    }

    private fun messageFromJson(o: JSONObject): ChatMessage {
        val attArr = o.optJSONArray("attachments") ?: JSONArray()
        val atts = (0 until attArr.length()).map {
            val a = attArr.getJSONObject(it)
            Attachment(
                type = runCatching { AttachType.valueOf(a.getString("type")) }.getOrDefault(AttachType.File),
                path = a.optString("path", ""),
                name = a.optString("name", ""),
                remoteFileId = a.optString("remoteFileId").takeIf { it.isNotBlank() },
            )
        }
        return ChatMessage(
            role = runCatching { Role.valueOf(o.getString("role")) }.getOrDefault(Role.User),
            text = o.optString("text", ""),
            attachments = atts,
            card = o.optJSONObject("card")?.let(::cardFromJson),
        )
    }

    private fun cardToJson(card: ChatCard): JSONObject = JSONObject().apply {
        when (card) {
            is ChatCard.Options -> {
                put("card_type", "options")
                put("prompt", card.prompt)
                put("allow_free_text", card.allowFreeText)
                put("select", card.select)
                put("items", JSONArray().apply {
                    card.items.forEach { item ->
                        put(JSONObject().apply {
                            put("id", item.id)
                            put("label", item.label)
                            put("description", item.description)
                        })
                    }
                })
            }
            is ChatCard.Diagram -> {
                put("card_type", "diagram")
                put("diagram_type", card.diagramType)
                put("mermaid", card.mermaid)
                put("caption", card.caption)
            }
            is ChatCard.Summary -> {
                put("card_type", "summary")
                put("title", card.title)
                put("body", card.body)
                put("saveable", card.saveable)
            }
            is ChatCard.Offer -> {
                put("card_type", "offer")
                put("kind", card.kind)
                put("label", card.label)
            }
            is ChatCard.CreateNote -> {
                put("card_type", "create_note")
                put("draft_title", card.draftTitle)
                put("draft_content", card.draftContent)
            }
            is ChatCard.SaveNote -> {
                put("card_type", "save_note")
                put("draft_title", card.draftTitle)
                put("draft_content", card.draftContent)
                put("saveable", card.saveable)
            }
            is ChatCard.Note -> {
                put("card_type", "note")
                put("note_id", card.noteId)
                put("title", card.title)
            }
        }
    }

    private fun cardFromJson(o: JSONObject): ChatCard? = when (o.optString("card_type")) {
        "options" -> ChatCard.Options(
            prompt = o.optString("prompt"),
            items = o.optJSONArray("items")?.let { items ->
                (0 until items.length()).mapNotNull { index ->
                    items.optJSONObject(index)?.let { item ->
                        OptionItem(
                            id = item.optString("id"),
                            label = item.optString("label"),
                            description = item.optString("description"),
                        )
                    }
                }
            }.orEmpty(),
            allowFreeText = o.optBoolean("allow_free_text", true),
            select = o.optString("select").takeIf { it == "many" } ?: "one",
        )
        "diagram" -> ChatCard.Diagram(
            o.optString("diagram_type"), o.optString("mermaid"), o.optString("caption"),
        )
        "summary" -> ChatCard.Summary(
            o.optString("title"), o.optString("body"), o.optBoolean("saveable", true),
        )
        "offer" -> ChatCard.Offer(o.optString("kind"), o.optString("label"))
        "create_note" -> ChatCard.CreateNote(
            o.optString("draft_title"), o.optString("draft_content"),
        )
        "save_note" -> ChatCard.SaveNote(
            o.optString("draft_title"),
            o.optString("draft_content"),
            o.optBoolean("saveable", true),
        )
        "note" -> o.optString("note_id").takeIf(String::isNotBlank)?.let {
            ChatCard.Note(it, o.optString("title"))
        }
        else -> null
    }
}
