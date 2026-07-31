package com.novamind.app.feature.create.editor

/** 将外部 HTML 约束到编辑器固定 26sp 行高能够安全展示的字号范围。 */
internal object EditorHtml {

    private val fontSize = Regex("font-size:\\s*(\\d+(?:\\.\\d+)?)px", RegexOption.IGNORE_CASE)

    fun normalize(html: String): String {
        if (html.isBlank()) return html
        var value = html
        for (level in 1..6) {
            val size = when (level) {
                1 -> 20
                2, 3 -> 18
                else -> 16
            }
            value = value
                .replace(
                    Regex("<h$level(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE),
                    "<p><span style=\"font-size: ${size}px;\"><b>",
                )
                .replace(
                    Regex("</h$level>", RegexOption.IGNORE_CASE),
                    "</b></span></p>",
                )
        }
        return fontSize.replace(value) { match ->
            val source = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val normalized = when {
                source > 28.0 -> 20
                source > 20.0 -> 18
                else -> source.toInt()
            }
            "font-size: ${normalized}px"
        }
    }
}
