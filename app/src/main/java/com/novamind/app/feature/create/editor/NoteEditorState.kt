package com.novamind.app.feature.create.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 支持的内联文字样式（映射到库 [RichTextState] 的 span 切换）。 */
enum class RichSpan { Bold, Italic }

/** 编辑器内容块：文本块 or 图片块 */
sealed interface EditorBlock {
    val id: String
}

/** AI「Polishing」骨架占位目标：[blockId] 文本块内 [start, end) 字符区间。 */
data class PolishTarget(val blockId: String, val start: Int, val end: Int)

/**
 * 文本块，内含库 [RichTextState]（加粗/斜体/列表）。
 * [initialHtml] 优先（保留格式），否则用 [initialText] 纯文本初始化。
 */
class TextBlock(
    initialText: String = "",
    initialHtml: String? = null,
    override val id: String = UUID.randomUUID().toString(),
) : EditorBlock {
    val rich = RichTextState().apply {
        when {
            !initialHtml.isNullOrBlank() -> setHtml(initialHtml)
            initialText.isNotEmpty() -> setText(initialText)
        }
    }
    val focusRequester = FocusRequester()
}

/** 图片上传态（运行期，不序列化）。 */
enum class UploadState { LOCAL, UPLOADING, UPLOADED, FAILED }

/**
 * 图片块。[path] 指向内部存储中的本地图片（即时预览用，可能在他机失效）；
 * [width]/[height] 为像素尺寸（0 = 未知）。
 *
 * 服务端相关：[fileId] 为上传成功后回填的服务端文件 id（**序列化进 content**，作稳定引用）；
 * [remoteUrl] 为运行期从 `GET attachments` 拿到的签名下载 URL（**不序列化**，有时效）；
 * [uploadState] 为运行期上传态（**不序列化**）。
 */
data class ImageBlock(
    val path: String,
    val width: Int = 0,
    val height: Int = 0,
    val fileId: String? = null,
    val remoteUrl: String? = null,
    val uploadState: UploadState = UploadState.LOCAL,
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

    // AI「Polishing」骨架占位目标（对某文本块的某段选区做扫光占位）；null = 无
    var polishTarget by mutableStateOf<PolishTarget?>(null)
        private set

    // 对全部文字做骨架（未选中文字时启用）
    var polishAll by mutableStateOf(false)
        private set

    /** 是否处于骨架占位状态。 */
    val isPolishing: Boolean get() = polishTarget != null || polishAll

    /** 已插入的附件数量：图片 + PDF + Markdown 合计（用于限制最多附件数）。 */
    val attachmentCount: Int
        get() = _blocks.count { it is ImageBlock || it is PdfBlock || it is MarkdownBlock }

    /** 正文纯文本字数（所有文本块长度合计，不含连接换行），用于字数统计与限制。 */
    val textLength: Int
        get() = _blocks.filterIsInstance<TextBlock>().sumOf { it.rich.annotatedString.text.length }

    init {
        _blocks.add(TextBlock())
        focusedTextId = (_blocks.first() as TextBlock).id
    }

    // ── 聚焦 / 格式 ─────────────────────────────────────────────────────────

    fun onTextFocused(id: String) {
        focusedTextId = id
    }

    /**
     * 开启骨架占位：
     * - 当前聚焦文本块有选区 → 只对选区做骨架；
     * - 未选中文字 → 对全部文字做骨架。
     * 笔记没有任何文字时返回 false（无内容可处理）。
     */
    fun startPolish(): Boolean {
        val block = focusedBlock()
        val sel = block?.rich?.selection
        if (block != null && sel != null && !sel.collapsed) {
            polishTarget = PolishTarget(block.id, sel.min, sel.max)
            polishAll = false
            return true
        }
        // 无选区：对所有文字做骨架（前提是确有文字）
        val hasText = _blocks.any { it is TextBlock && it.rich.annotatedString.text.isNotEmpty() }
        if (!hasText) return false
        polishAll = true
        polishTarget = null
        return true
    }

    /** 关闭骨架占位。 */
    fun clearPolish() {
        polishTarget = null
        polishAll = false
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
        val rich = focusedBlock()?.rich ?: return
        when (span) {
            RichSpan.Bold -> rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
            RichSpan.Italic -> rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
    }

    fun isActive(span: RichSpan): Boolean {
        val style = focusedBlock()?.rich?.currentSpanStyle ?: return false
        return when (span) {
            RichSpan.Bold -> style.fontWeight == FontWeight.Bold
            RichSpan.Italic -> style.fontStyle == FontStyle.Italic
        }
    }

    // ── 插入 / 删除非文本块（图片 / 文档） ──────────────────────────────────

    /** 在聚焦文本块的光标处插入图片块，返回新块（供调用方发起上传并按 id 回填状态）。 */
    fun insertImage(path: String, width: Int = 0, height: Int = 0): ImageBlock {
        val block = ImageBlock(path, width, height, uploadState = UploadState.UPLOADING)
        insertBlockAtCaret(block)
        return block
    }

    /** 用 [transform] 替换指定 id 的图片块（更新上传态 / fileId / remoteUrl），触发重组。 */
    fun updateImage(id: String, transform: (ImageBlock) -> ImageBlock) {
        val idx = _blocks.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cur = _blocks[idx] as? ImageBlock ?: return
        _blocks[idx] = transform(cur)
    }

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
        val rich = focusedBlock()?.rich ?: return
        if (numbered) rich.toggleOrderedList() else rich.toggleUnorderedList()
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
        val text = target.rich.annotatedString.text
        val caret = target.rich.selection.start.coerceIn(0, text.length)
        val before = text.substring(0, caret)
        val after = text.substring(caret)

        // 原块只保留光标前文本（拆分按纯文本，尾段格式会重置——简化实现）
        target.rich.setText(before)
        // 光标后文本另起一个新文本块
        val afterBlock = TextBlock(after)
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
            .map { it.rich.annotatedString.text }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /** 序列化为 JSON 文档结构 */
    val documentJson: String
        get() {
            val arr = JSONArray()
            _blocks.forEach { block ->
                when (block) {
                    is TextBlock -> arr.put(
                        JSONObject().put("type", "text")
                            .put("text", block.rich.annotatedString.text)   // 纯文本：供预览/搜索/旧兼容
                            .put("html", block.rich.toHtml())               // 富文本：保留加粗/斜体/列表
                    )
                    is ImageBlock -> arr.put(
                        JSONObject().put("type", "image").put("path", block.path)
                            .put("width", block.width).put("height", block.height)
                            .apply { block.fileId?.let { put("fileId", it) } }   // 稳定引用，供他机/重装后取回
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
        applyParsed(parse(json).ifEmpty { listOf(TextBlock(fallbackPlain)) })
    }

    /**
     * 同 [loadDocument]，但 JSON 解析与块构建放到后台线程，避免在进入笔记页 / 大文档时
     * 阻塞主线程造成卡顿；解析完成后回到调用方线程（应为主线程）套用到状态。
     */
    suspend fun loadDocumentAsync(json: String?, fallbackPlain: String) {
        val parsed = withContext(Dispatchers.Default) {
            parse(json).ifEmpty { listOf(TextBlock(fallbackPlain)) }
        }
        applyParsed(parsed)
    }

    /** 把解析得到的块套用到状态。必须在主线程调用（写 mutableStateList）。 */
    private fun applyParsed(parsed: List<EditorBlock>) {
        // 结构一致（块数量/类型/图片路径相同）时，原地更新文本块内容，
        // 保留现有块实例与输入焦点 —— 这样撤销/重做不会让键盘收起。
        if (canReuse(parsed)) {
            parsed.forEachIndexed { i, p ->
                val cur = _blocks[i]
                if (p is TextBlock && cur is TextBlock &&
                    cur.rich.annotatedString.text != p.rich.annotatedString.text
                ) {
                    cur.rich.setHtml(p.rich.toHtml())   // 原地更新保留格式，避免撤销/重做收键盘
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
                    "text" -> TextBlock(
                        initialText = obj.optString("text"),
                        initialHtml = obj.optString("html").ifBlank { null },
                    )
                    "image" -> {
                        val path = obj.optString("path")
                        val fid = obj.optString("fileId").ifBlank { null }
                        // 有本地 path 或有服务端 fileId 之一即可（他机加载时 path 可能失效，靠 fileId 取回）
                        if (path.isBlank() && fid == null) null
                        else ImageBlock(
                            path = path,
                            width = obj.optInt("width", 0),
                            height = obj.optInt("height", 0),
                            fileId = fid,
                            uploadState = if (fid != null) UploadState.UPLOADED else UploadState.LOCAL,
                        )
                    }
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
                    initialText = listOf(a.rich.annotatedString.text, b.rich.annotatedString.text)
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
