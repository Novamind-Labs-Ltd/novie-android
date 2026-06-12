package com.novamind.app.feature.create.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue

/** 支持的内联文字样式 */
enum class RichSpan {
    Bold,
    Italic,
}

/**
 * 支持「加粗 / 斜体」等内联样式的富文本编辑状态持有器。
 *
 * 设计：纯文本 + 每种样式各自一组字符区间（左闭右开，已归一化）。
 * - 选区非空时点击某样式：整段已应用则取消，否则应用；
 * - 折叠光标时点击：切换该样式的「续写」开关，后续输入的文字应用该样式；
 * - 编辑（插入/删除）时把所有样式区间重新映射到新坐标，未涉及的文字不变。
 *
 * 仅维护 UI 层格式状态，对外只暴露纯文本（[plainText]）用于持久化。
 */
@Stable
class RichTextState(initialText: String = "") {

    // 每种样式对应的字符区间，左闭右开，已归一化
    private val ranges: MutableMap<RichSpan, List<IntRange>> =
        RichSpan.entries.associateWith { emptyList<IntRange>() }.toMutableMap()

    // 折叠光标处各样式的「续写」开关：true 时新输入的文字应用该样式（需可观察以驱动按钮高亮）
    private val pending = mutableStateMapOf<RichSpan, Boolean>().apply {
        RichSpan.entries.forEach { put(it, false) }
    }

    var value by mutableStateOf(TextFieldValue(buildAnnotated(initialText)))
        private set

    val plainText: String get() = value.text

    /** 某样式按钮是否高亮：选区全部应用，或折叠光标处于该样式的「续写」状态 */
    fun isActive(span: RichSpan): Boolean {
        val sel = value.selection
        return if (sel.collapsed) pending[span] == true
        else (sel.min until sel.max).all { isStyledAt(span, it) }
    }

    /** 外部直接替换纯文本（加载笔记 / 撤销重做），清除所有格式 */
    fun setPlainText(text: String) {
        RichSpan.entries.forEach {
            ranges[it] = emptyList()
            pending[it] = false
        }
        value = TextFieldValue(
            annotatedString = buildAnnotated(text),
            selection = TextRange(text.length),
        )
    }

    /** 在「光标所在行」的行首插入标记（用于无序/有序列表前缀），已有样式区间随之右移 */
    fun insertAtLineStart(marker: String) {
        if (marker.isEmpty()) return
        val caret = value.selection.start.coerceIn(0, value.text.length)
        val text = value.text
        val lineStart = if (caret == 0) 0 else text.lastIndexOf('\n', caret - 1).let { if (it < 0) 0 else it + 1 }
        val insertLen = marker.length
        val newText = text.substring(0, lineStart) + marker + text.substring(lineStart)
        RichSpan.entries.forEach { span ->
            ranges[span] = normalize(ranges.getValue(span).map { r ->
                val a = if (r.first >= lineStart) r.first + insertLen else r.first
                val b = if (r.last + 1 > lineStart) r.last + 1 + insertLen else r.last + 1
                a until b
            })
        }
        value = TextFieldValue(
            annotatedString = buildAnnotated(newText),
            selection = TextRange(caret + insertLen),
        )
    }

    /** 文本/选区变化回调 */
    fun onValueChange(new: TextFieldValue) {
        val oldText = value.text
        val newText = new.text

        // 纯选区移动：按光标前一个字符的样式决定各样式的续写开关
        if (oldText == newText) {
            if (new.selection.collapsed && new.selection != value.selection) {
                syncPendingToCaret(new.selection.start)
            }
            value = new.copy(annotatedString = buildAnnotated(newText))
            return
        }

        // 定位被替换区间：[p, removedEnd) -> [p, insertedEnd)
        val p = commonPrefix(oldText, newText)
        val s = commonSuffix(oldText, newText, p)
        val removedEnd = oldText.length - s
        val insertedEnd = newText.length - s
        val delta = newText.length - oldText.length

        RichSpan.entries.forEach { span ->
            // 1) 旧区间映射到新坐标
            val mapped = ranges.getValue(span).mapNotNull { r ->
                val a = mapPos(r.first, p, removedEnd, delta)
                val b = mapPos(r.last + 1, p, removedEnd, delta) // 端点按开区间映射
                if (b > a) a until b else null
            }.toMutableList()

            // 2) 插入的文字是否应用该样式：仅取决于「续写」开关
            //    （取消某样式后其 pending=false，新输入不会被相邻区间误带上）
            if (insertedEnd > p && pending[span] == true) {
                mapped.add(p until insertedEnd)
            }

            ranges[span] = normalize(mapped)
        }

        // 续写开关：按新光标前一个字符的样式
        if (new.selection.collapsed) {
            syncPendingToCaret(new.selection.start)
        }

        value = new.copy(annotatedString = buildAnnotated(newText))

        // 回车时自动续写列表（圆点续点、数字自增；空列表项则退出）
        maybeContinueList(oldText, newText, new.selection)
    }

    private val bulletPrefix = "• "
    private val numberRegex = Regex("^(\\d+)\\.\\s")

    /**
     * 切换「当前行」的列表标记。圆点与数字互斥，每行至多一个：
     * - 已是同类型 → 移除（再次点击关闭）；
     * - 是另一类型 → 替换；
     * - 无标记 → 添加（数字按上一行自增）。
     */
    fun toggleLineMarker(bullet: Boolean) {
        val caret = value.selection.start.coerceIn(0, value.text.length)
        val text = value.text
        val lineStart = if (caret == 0) 0 else text.lastIndexOf('\n', caret - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)

        val hasBullet = line.startsWith(bulletPrefix)
        val numMatch = numberRegex.find(line)
        val existingLen = when {
            hasBullet -> bulletPrefix.length
            numMatch != null -> numMatch.value.length
            else -> 0
        }
        val newMarker = when {
            bullet && hasBullet -> ""                 // 关闭圆点
            !bullet && numMatch != null -> ""         // 关闭数字
            bullet -> bulletPrefix                     // 设为圆点（新增或覆盖数字）
            else -> "${prevLineNumber(text, lineStart) + 1}. "  // 设为数字
        }
        spliceText(lineStart, lineStart + existingLen, newMarker)
    }

    private fun prevLineNumber(text: String, lineStart: Int): Int {
        if (lineStart == 0) return 0
        val prevEnd = lineStart - 1 // 上一行末尾的 '\n'
        val prevStart = text.lastIndexOf('\n', prevEnd - 1) + 1
        val prevLine = text.substring(prevStart, prevEnd)
        return numberRegex.find(prevLine)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    // 仅当本次变化是「插入一个换行」且光标紧随其后时，处理列表续写
    private fun maybeContinueList(oldText: String, newText: String, sel: TextRange) {
        if (newText.length != oldText.length + 1 || !sel.collapsed) return
        val caret = sel.start
        val nlPos = caret - 1
        if (nlPos < 0 || newText.getOrNull(nlPos) != '\n') return

        val lineStart = newText.lastIndexOf('\n', nlPos - 1).let { if (it < 0) 0 else it + 1 }
        val brokenLine = newText.substring(lineStart, nlPos)

        when {
            brokenLine.startsWith(bulletPrefix) -> {
                if (brokenLine == bulletPrefix) {
                    spliceText(lineStart, caret, "")          // 空圆点项 → 退出列表
                } else {
                    spliceText(caret, caret, bulletPrefix)    // 续写圆点
                }
            }
            else -> {
                val m = Regex("^(\\d+)\\.\\s").find(brokenLine)
                if (m != null) {
                    val num = m.groupValues[1].toIntOrNull() ?: 0
                    val hasContent = brokenLine.length > m.value.length
                    if (hasContent) {
                        spliceText(caret, caret, "${num + 1}. ")   // 数字自增
                    } else {
                        spliceText(lineStart, caret, "")            // 空数字项 → 退出列表
                    }
                }
            }
        }
    }

    // 用 insert 替换 [start, end) 文本，重映射所有样式区间，光标置于插入末尾
    private fun spliceText(start: Int, end: Int, insert: String) {
        val old = value.text
        val s = start.coerceIn(0, old.length)
        val e = end.coerceIn(s, old.length)
        val newText = old.substring(0, s) + insert + old.substring(e)
        val delta = insert.length - (e - s)
        RichSpan.entries.forEach { span ->
            ranges[span] = normalize(ranges.getValue(span).mapNotNull { r ->
                val a = mapPos(r.first, s, e, delta)
                val b = mapPos(r.last + 1, s, e, delta)
                if (b > a) a until b else null
            })
        }
        value = TextFieldValue(
            annotatedString = buildAnnotated(newText),
            selection = TextRange(s + insert.length),
        )
    }

    /** 点击某样式按钮 */
    fun toggle(span: RichSpan) {
        val sel = value.selection
        if (!sel.collapsed) {
            val range = sel.min until sel.max
            val allStyled = (sel.min until sel.max).all { isStyledAt(span, it) }
            ranges[span] = if (allStyled) normalize(subtract(ranges.getValue(span), range))
            else normalize(ranges.getValue(span) + listOf(range))
            pending[span] = !allStyled
            value = value.copy(annotatedString = buildAnnotated(value.text))
        } else {
            // 无选区：仅切换该样式的续写开关，等待用户输入
            pending[span] = !(pending[span] ?: false)
        }
    }

    // ── 内部工具 ────────────────────────────────────────────────────────────

    // 折叠光标处：各样式续写开关取「光标前一个字符」的样式（自然延续前文）
    private fun syncPendingToCaret(caret: Int) {
        RichSpan.entries.forEach { pending[it] = isStyledAt(it, caret - 1) }
    }

    private fun isStyledAt(span: RichSpan, charIndex: Int): Boolean =
        charIndex >= 0 && ranges.getValue(span).any { charIndex in it }

    private fun spanStyleOf(span: RichSpan): SpanStyle = when (span) {
        RichSpan.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        RichSpan.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    }

    private fun buildAnnotated(text: String): AnnotatedString =
        buildAnnotatedString {
            append(text)
            RichSpan.entries.forEach { span ->
                val style = spanStyleOf(span)
                ranges[span]?.forEach { r ->
                    val start = r.first.coerceIn(0, text.length)
                    val end = (r.last + 1).coerceIn(0, text.length)
                    if (start < end) addStyle(style, start, end)
                }
            }
        }

    private fun mapPos(x: Int, p: Int, removedEnd: Int, delta: Int): Int = when {
        x <= p -> x
        x >= removedEnd -> x + delta
        else -> p // 落在被删除区间内 -> 收拢到变更起点
    }

    private fun commonPrefix(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffix(a: String, b: String, prefix: Int): Int {
        val n = minOf(a.length - prefix, b.length - prefix)
        var i = 0
        while (i < n && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }

    // 归一化：过滤空区间、排序、合并重叠或相邻区间
    private fun normalize(ranges: List<IntRange>): List<IntRange> {
        val valid = ranges.filter { it.last >= it.first }.sortedBy { it.first }
        if (valid.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var cur = valid.first()
        for (r in valid.drop(1)) {
            cur = if (r.first <= cur.last + 1) {
                cur.first..maxOf(cur.last, r.last) // 重叠或相邻 -> 合并
            } else {
                out.add(cur); r
            }
        }
        out.add(cur)
        return out
    }

    // 从 ranges 中减去闭区间 r
    private fun subtract(ranges: List<IntRange>, r: IntRange): List<IntRange> {
        val out = mutableListOf<IntRange>()
        for (range in ranges) {
            if (range.last < r.first || range.first > r.last) {
                out.add(range) // 无交集
            } else {
                if (range.first < r.first) out.add(range.first until r.first)
                if (range.last > r.last) out.add((r.last + 1)..range.last)
            }
        }
        return out
    }
}
