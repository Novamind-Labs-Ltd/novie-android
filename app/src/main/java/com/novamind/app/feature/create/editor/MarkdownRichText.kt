package com.novamind.app.feature.create.editor

/** 将正文中的常用 Markdown 转成 RichTextState 可消费的 HTML。 */
internal object MarkdownRichText {

    private val blockSyntax = Regex(
        pattern = "(?m)^\\s*(#{1,6}\\s+|[-+*]\\s+|\\d+[.)]\\s+|>\\s+|```)",
    )
    private val flattenedHeadingSyntax = Regex("\\s+#{1,6}\\s+")
    private val inlineSyntax = Regex("(\\*\\*[^*]+\\*\\*|__[^_]+__|`[^`]+`|\\[[^]]+]\\([^)]+\\))")

    fun containsSyntax(text: String): Boolean =
        blockSyntax.containsMatchIn(text) ||
            flattenedHeadingSyntax.containsMatchIn(text) ||
            inlineSyntax.containsMatchIn(text)

    fun toHtml(markdown: String): String {
        if (markdown.isEmpty()) return ""
        var normalized = markdown
            // 兼容历史 TextBlock：富文本编辑器曾把 Markdown 的块级换行压成空格。
            .replace(Regex("^(\\s*#{1,6}\\s+.+?)\\s{2,}(?=(?:\\*\\*|__))"), "$1\n")
            .replace(Regex("\\s+-\\s+(?=(?:\\*\\*|__))"), "\n- ")
        if (flattenedHeadingSyntax.containsMatchIn(normalized)) {
            normalized = normalized
                .replace(Regex("\\s+(?=#{1,6}\\s+)"), "\n")
                .replace(Regex("\\s+-\\s+"), "\n- ")
        }
        val output = StringBuilder()
        var listTag: String? = null

        fun closeList() {
            listTag?.let { output.append("</").append(it).append('>') }
            listTag = null
        }

        normalized.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                closeList()
                output.append("<br>")
                return@forEach
            }

            val heading = Regex("^\\s*(#{1,6})\\s+(.+)$").matchEntire(line)
            val unordered = Regex("^\\s*[-+*]\\s+(.+)$").matchEntire(line)
            val ordered = Regex("^\\s*\\d+[.)]\\s+(.+)$").matchEntire(line)
            when {
                heading != null -> {
                    closeList()
                    val level = heading.groupValues[1].length
                    output.append("<h").append(level).append('>')
                        .append(inlineHtml(heading.groupValues[2]))
                        .append("</h").append(level).append('>')
                }
                unordered != null -> {
                    if (listTag != "ul") {
                        closeList()
                        output.append("<ul>")
                        listTag = "ul"
                    }
                    output.append("<li>").append(inlineHtml(unordered.groupValues[1])).append("</li>")
                }
                ordered != null -> {
                    if (listTag != "ol") {
                        closeList()
                        output.append("<ol>")
                        listTag = "ol"
                    }
                    output.append("<li>").append(inlineHtml(ordered.groupValues[1])).append("</li>")
                }
                line.trimStart().startsWith("> ") -> {
                    closeList()
                    output.append("<blockquote>")
                        .append(inlineHtml(line.trimStart().removePrefix("> ")))
                        .append("</blockquote>")
                }
                else -> {
                    closeList()
                    output.append("<p>").append(inlineHtml(line)).append("</p>")
                }
            }
        }
        closeList()
        return output.toString()
    }

    private fun inlineHtml(source: String): String {
        var value = escapeHtml(source)
        value = value.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        value = value.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
        value = value.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
        value = value.replace(Regex("__([^_]+)__"), "<b>$1</b>")
        value = value.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "<i>$1</i>")
        value = value.replace(Regex("(?<!_)_([^_]+)_(?!_)"), "<i>$1</i>")
        return value
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '\"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> char
                },
            )
        }
    }
}
