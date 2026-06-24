package com.novamind.app.feature.create.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 编辑器内容块：文本块 or 图片块 */
sealed interface EditorBlock {
    val id: String
}

/** 文本块，内含富文本状态（加粗/斜体） */
class TextBlock(
    initialText: String = "",
    override val id: String = UUID.randomUUID().toString(),
) : EditorBlock {
    val rich = RichTextState(initialText)
    val focusRequester = FocusRequester()
}

/** 图片块，path 指向内部存储中的图片文件 */
class ImageBlock(
    val path: String,
    override val id: String = UUID.randomUUID().toString(),
) : EditorBlock

/** 文档块，path 指向内部存储中的文件，name 为展示文件名 */
class FileBlock(
    val path: String,
    val name: String,
    override val id: String = UUID.randomUUID().toString(),
) : EditorBlock

/** Markdown 块：content 为原始 markdown 文本，渲染时用 markdown 渲染器展示 */
class MarkdownBlock(
    val content: String,
    override val id: String = UUID.randomUUID().toString(),
) : EditorBlock

/** PDF 块：path 指向内部存储中的 PDF 文件，渲染时用 PdfRenderer 逐页展示 */
class PdfBlock(
    val path: String,
    val name: String,
    override val id: String = UUID.randomUUID().toString(),
) : EditorBlock

/**
 * 笔记图文编辑器状态：维护一组有序的文本/图片块。
 *
 * - 文本块各自持有 [RichTextState]，加粗/斜体作用于「当前聚焦」的文本块；
 * - 在聚焦文本块的光标处插入图片，会把该文本块按光标拆成前后两段，中间夹入图片块；
 * - 对外可序列化为 [documentJson] 持久化，并提供纯文本投影 [plainText] 供列表预览/搜索。
 */
@Stable
class NoteEditorState {

    private val _blocks = mutableStateListOf<EditorBlock>()
    val blocks: List<EditorBlock> get() = _blocks

    // 当前聚焦的文本块 id
    private var focusedTextId by mutableStateOf<String?>(null)

    // 待请求焦点的文本块 id（插图后用：等新块组合完成再由 UI 请求焦点+弹键盘）
    private var pendingFocusId by mutableStateOf<String?>(null)
    val pendingFocus: String? get() = pendingFocusId

    init {
        _blocks.add(TextBlock())
        focusedTextId = (_blocks.first() as TextBlock).id
    }

    // ── 聚焦 / 格式 ─────────────────────────────────────────────────────────

    fun onTextFocused(id: String) {
        focusedTextId = id
    }

    /** 请求焦点到首个文本块（新建笔记进入时调用，用于自动弹出键盘） */
    fun requestInitialFocus() {
        (_blocks.firstOrNull { it is TextBlock } as? TextBlock)?.let {
            runCatching { it.focusRequester.requestFocus() }
        }
    }

    /** 请求焦点到最后一个文本块（点击正文空白区时调用，用于调起键盘） */
    fun focusLastTextBlock() {
        (_blocks.lastOrNull { it is TextBlock } as? TextBlock)?.let {
            focusedTextId = it.id
            runCatching { it.focusRequester.requestFocus() }
        }
    }

    private fun focusedBlock(): TextBlock? =
        _blocks.firstOrNull { it.id == focusedTextId } as? TextBlock
            ?: _blocks.lastOrNull { it is TextBlock } as? TextBlock

    fun toggle(span: RichSpan) {
        focusedBlock()?.rich?.toggle(span)
    }

    fun isActive(span: RichSpan): Boolean =
        focusedBlock()?.rich?.isActive(span) ?: false

    // ── 插入 / 删除非文本块（图片 / 文档） ──────────────────────────────────

    /** 在聚焦文本块的光标处插入图片块 */
    fun insertImage(path: String) = insertBlockAtCaret(ImageBlock(path))

    /** 在聚焦文本块的光标处插入文档块 */
    fun insertFile(path: String, name: String) = insertBlockAtCaret(FileBlock(path, name))

    /** 在聚焦文本块的光标处插入 Markdown 块（按渲染后的样式展示） */
    fun insertMarkdown(content: String) = insertBlockAtCaret(MarkdownBlock(content))

    /** 更新 Markdown 块内容（content 为 val，替换为同 id 的新实例触发重组）。 */
    fun updateMarkdown(id: String, content: String) {
        val idx = _blocks.indexOfFirst { it.id == id }
        if (idx < 0 || _blocks[idx] !is MarkdownBlock) return
        _blocks[idx] = MarkdownBlock(content, id)
    }

    /** 在聚焦文本块的光标处插入 PDF 块（按渲染后的页面展示） */
    fun insertPdf(path: String, name: String) = insertBlockAtCaret(PdfBlock(path, name))

    /**
     * 在聚焦文本块的「当前行首」插入列表序号。
     * [numbered] = false → 圆点「• 」；true → 数字「N. 」（按上一行数字自动递增，否则从 1 开始）。
     */
    fun insertListMarker(numbered: Boolean) {
        focusedBlock()?.rich?.toggleLineMarker(bullet = !numbered)
    }

    /** 在聚焦文本块光标处插入任意非文本块：按光标把文本拆成前后两段，中间夹入该块 */
    private fun insertBlockAtCaret(block: EditorBlock) {
        val target = focusedBlock()
        if (target == null) {
            // 没有可插入的文本块：追加块 + 末尾空文本块，光标落到末尾文本块
            _blocks.add(block)
            appendTrailingTextIfNeeded()
            (_blocks.lastOrNull { it is TextBlock } as? TextBlock)?.let {
                focusedTextId = it.id
                pendingFocusId = it.id
            }
            return
        }
        val index = _blocks.indexOfFirst { it.id == target.id }
        val caret = target.rich.value.selection.start.coerceIn(0, target.rich.plainText.length)
        val text = target.rich.plainText
        val before = text.substring(0, caret)
        val after = text.substring(caret)

        // 原块只保留光标前文本
        target.rich.setPlainText(before)
        // 光标后文本另起一个新文本块；setPlainText 把光标置于其末尾
        val afterBlock = TextBlock(after)
        afterBlock.rich.setPlainText(after)
        _blocks.add(index + 1, block)
        _blocks.add(index + 2, afterBlock)
        focusedTextId = afterBlock.id
        pendingFocusId = afterBlock.id
    }

    /** 由 UI 在重组后调用：对待聚焦文本块请求焦点（光标到末尾、弹键盘），随后清除标记 */
    fun consumePendingFocus() {
        val id = pendingFocusId ?: return
        pendingFocusId = null
        (_blocks.firstOrNull { it.id == id } as? TextBlock)?.let {
            runCatching { it.focusRequester.requestFocus() }
        }
    }

    /** 删除指定的非文本块（图片 / 文档），并合并相邻文本块 */
    fun removeBlock(id: String) {
        val idx = _blocks.indexOfFirst { it.id == id }
        if (idx < 0 || _blocks[idx] is TextBlock) return
        _blocks.removeAt(idx)
        mergeAdjacentTextBlocks()
        appendTrailingTextIfNeeded()
    }

    /** 删除指定图片块（兼容旧调用） */
    fun removeImage(id: String) = removeBlock(id)

    // ── 序列化 ──────────────────────────────────────────────────────────────

    /** 纯文本投影：拼接所有文本块（图片忽略），用于预览与搜索 */
    val plainText: String
        get() = _blocks.filterIsInstance<TextBlock>()
            .map { it.rich.plainText }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /** 序列化为 JSON 文档结构 */
    val documentJson: String
        get() {
            val arr = JSONArray()
            _blocks.forEach { block ->
                when (block) {
                    is TextBlock -> arr.put(
                        JSONObject().put("type", "text").put("text", block.rich.plainText)
                    )
                    is ImageBlock -> arr.put(
                        JSONObject().put("type", "image").put("path", block.path)
                    )
                    is FileBlock -> arr.put(
                        JSONObject().put("type", "file").put("path", block.path).put("name", block.name)
                    )
                    is MarkdownBlock -> arr.put(
                        JSONObject().put("type", "markdown").put("content", block.content)
                    )
                    is PdfBlock -> arr.put(
                        JSONObject().put("type", "pdf").put("path", block.path).put("name", block.name)
                    )
                }
            }
            return JSONObject().put("blocks", arr).toString()
        }

    /** 从 JSON 文档加载；解析失败或为空时退化为单个文本块（用 [fallbackPlain]） */
    fun loadDocument(json: String?, fallbackPlain: String) {
        val parsed = parse(json).ifEmpty { listOf(TextBlock(fallbackPlain)) }

        // 结构一致（块数量/类型/图片路径相同）时，原地更新文本块内容，
        // 保留现有块实例与输入焦点 —— 这样撤销/重做不会让键盘收起。
        if (canReuse(parsed)) {
            parsed.forEachIndexed { i, p ->
                val cur = _blocks[i]
                if (p is TextBlock && cur is TextBlock && cur.rich.plainText != p.rich.plainText) {
                    cur.rich.setPlainText(p.rich.plainText)
                }
            }
            return
        }

        // 结构变化（增删图片等）：整体重建
        _blocks.clear()
        _blocks.addAll(parsed)
        appendTrailingTextIfNeeded()
        focusedTextId = (_blocks.firstOrNull { it is TextBlock } as? TextBlock)?.id
    }

    // 解析出的块与当前块结构是否一致（可原地复用）
    private fun canReuse(parsed: List<EditorBlock>): Boolean {
        if (parsed.size != _blocks.size) return false
        return parsed.indices.all { i ->
            val a = parsed[i]
            val b = _blocks[i]
            (a is TextBlock && b is TextBlock) ||
                (a is ImageBlock && b is ImageBlock && a.path == b.path) ||
                (a is FileBlock && b is FileBlock && a.path == b.path) ||
                (a is MarkdownBlock && b is MarkdownBlock && a.content == b.content) ||
                (a is PdfBlock && b is PdfBlock && a.path == b.path)
        }
    }

    private fun parse(json: String?): List<EditorBlock> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONObject(json).getJSONArray("blocks")
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                when (obj.optString("type")) {
                    "text" -> TextBlock(obj.optString("text"))
                    "image" -> obj.optString("path").takeIf { it.isNotBlank() }?.let { ImageBlock(it) }
                    "file" -> obj.optString("path").takeIf { it.isNotBlank() }?.let {
                        FileBlock(it, obj.optString("name").ifBlank { "Document" })
                    }
                    "markdown" -> obj.optString("content").takeIf { it.isNotBlank() }?.let {
                        MarkdownBlock(it)
                    }
                    "pdf" -> obj.optString("path").takeIf { it.isNotBlank() }?.let {
                        PdfBlock(it, obj.optString("name").ifBlank { "PDF" })
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── 内部维护 ────────────────────────────────────────────────────────────

    // 末尾若是非文本块（图片/文档，或列表为空），补一个空文本块，保证总能在最后输入
    private fun appendTrailingTextIfNeeded() {
        if (_blocks.isEmpty() || _blocks.last() !is TextBlock) {
            _blocks.add(TextBlock())
        }
    }

    // 合并相邻的文本块（删除图片后可能出现两个挨着的文本块）
    private fun mergeAdjacentTextBlocks() {
        var i = 0
        while (i < _blocks.size - 1) {
            val a = _blocks[i]
            val b = _blocks[i + 1]
            if (a is TextBlock && b is TextBlock) {
                val merged = TextBlock(
                    initialText = listOf(a.rich.plainText, b.rich.plainText)
                        .filter { it.isNotEmpty() }
                        .joinToString("\n")
                )
                _blocks[i] = merged
                _blocks.removeAt(i + 1)
                if (focusedTextId == a.id || focusedTextId == b.id) focusedTextId = merged.id
            } else {
                i++
            }
        }
    }
}
