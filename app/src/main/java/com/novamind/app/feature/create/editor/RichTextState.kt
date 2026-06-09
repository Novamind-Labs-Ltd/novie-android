package com.novamind.app.feature.create.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 支持「加粗」的富文本编辑状态持有器。
 *
 * 设计：纯文本 + 一组加粗字符区间（左闭右开，已归一化）。
 * - 选区非空时点击加粗：整段已加粗则取消，否则加粗；
 * - 折叠光标时点击加粗：切换「续写加粗」开关，后续输入的文字加粗；
 * - 编辑（插入/删除）时把加粗区间重新映射到新坐标，未涉及的文字不变。
 *
 * 仅维护 UI 层格式状态，对外只暴露纯文本（[plainText]）用于持久化。
 */
@Stable
class RichTextState(initialText: String = "") {

    // 已加粗的字符区间，左闭右开，按起点升序、互不重叠/相邻
    private var boldRanges: List<IntRange> = emptyList()

    // 折叠光标处的「续写加粗」开关：true 时新输入的文字会被加粗
    private var pendingBold by mutableStateOf(false)

    var value by mutableStateOf(
        TextFieldValue(buildAnnotated(initialText, emptyList()))
    )
        private set

    val plainText: String get() = value.text

    /** 加粗按钮是否高亮：选区全部加粗，或折叠光标处于「续写加粗」状态 */
    val isBoldActive: Boolean
        get() {
            val sel = value.selection
            return if (sel.collapsed) pendingBold
            else (sel.min until sel.max).all { isBoldAt(it) }
        }

    /** 外部直接替换纯文本（加载笔记 / 撤销重做），清除已有格式 */
    fun setPlainText(text: String) {
        boldRanges = emptyList()
        pendingBold = false
        value = TextFieldValue(
            annotatedString = buildAnnotated(text, boldRanges),
            selection = TextRange(text.length),
        )
    }

    /** 文本/选区变化回调 */
    fun onValueChange(new: TextFieldValue) {
        val oldText = value.text
        val newText = new.text

        // 纯选区移动：按光标前一个字符的样式决定续写加粗
        if (oldText == newText) {
            if (new.selection.collapsed && new.selection != value.selection) {
                pendingBold = isBoldAt(new.selection.start - 1)
            }
            value = new.copy(annotatedString = buildAnnotated(newText, boldRanges))
            return
        }

        // 定位被替换区间：[p, removedEnd) -> [p, insertedEnd)
        val p = commonPrefix(oldText, newText)
        val s = commonSuffix(oldText, newText, p)
        val removedEnd = oldText.length - s
        val insertedEnd = newText.length - s
        val delta = newText.length - oldText.length

        // 1) 旧加粗区间映射到新坐标
        val mapped = boldRanges.mapNotNull { r ->
            val a = mapPos(r.first, p, removedEnd, delta)
            val b = mapPos(r.last + 1, p, removedEnd, delta) // 端点按开区间映射
            if (b > a) a until b else null
        }.toMutableList()

        // 2) 插入的文字是否加粗：仅取决于「续写加粗」开关
        //    （取消加粗后 pendingBold=false，新输入不会被相邻加粗区误带上）
        if (insertedEnd > p && pendingBold) {
            mapped.add(p until insertedEnd)
        }

        boldRanges = normalize(mapped)

        // 续写开关：按新光标前一个字符的样式
        if (new.selection.collapsed) {
            pendingBold = isBoldAt(new.selection.start - 1)
        }

        value = new.copy(annotatedString = buildAnnotated(newText, boldRanges))
    }

    /** 点击「B」 */
    fun toggleBold() {
        val sel = value.selection
        if (!sel.collapsed) {
            val range = sel.min until sel.max
            val allBold = (sel.min until sel.max).all { isBoldAt(it) }
            boldRanges = if (allBold) normalize(subtract(boldRanges, range))
            else normalize(boldRanges + listOf(range))
            pendingBold = !allBold
            value = value.copy(annotatedString = buildAnnotated(value.text, boldRanges))
        } else {
            // 无选区：仅切换续写加粗，等待用户输入
            pendingBold = !pendingBold
        }
    }

    // ── 内部工具 ────────────────────────────────────────────────────────────

    private fun isBoldAt(charIndex: Int): Boolean =
        charIndex >= 0 && boldRanges.any { charIndex in it }

    private fun buildAnnotated(text: String, ranges: List<IntRange>): AnnotatedString =
        buildAnnotatedString {
            append(text)
            ranges.forEach { r ->
                val start = r.first.coerceIn(0, text.length)
                val end = (r.last + 1).coerceIn(0, text.length)
                if (start < end) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
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
        val valid = ranges.filter { it.first < it.last + 1 && it.last >= it.first }
            .sortedBy { it.first }
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

    // 从 ranges 中减去区间 r（开区间 r = [r.first, r.last]）
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
