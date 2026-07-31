package com.novamind.app.feature.create.editor

import com.novamind.app.common.net.PolishRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorStatePolishTest {

    @Test
    fun `markdown syntax converts to inline rich text html`() {
        val html = MarkdownRichText.toHtml("# Title\n\n- First\n- **Second**")

        assertEquals("<h1>Title</h1><br><ul><li>First</li><li><b>Second</b></li></ul>", html)
    }

    @Test
    fun `flattened legacy markdown restores heading and list boundaries`() {
        val html = MarkdownRichText.toHtml("# Meeting Note   **Purpose:** Summit prep. - **On the day:** Bring a laptop")

        assertEquals(
            "<h1>Meeting Note</h1><p><b>Purpose:</b> Summit prep.</p><ul><li><b>On the day:</b> Bring a laptop</li></ul>",
            html,
        )
    }

    @Test
    fun `flattened headings bypass malformed legacy html`() {
        val text = "用户登录流程图 ### 1. 登录方式选择 可选择： - 账号密码登录 - 手机验证码登录"

        assertTrue(MarkdownRichText.containsSyntax(text))
        assertEquals(
            "<p>用户登录流程图</p><h3>1. 登录方式选择 可选择：</h3><ul><li>账号密码登录</li><li>手机验证码登录</li></ul>",
            MarkdownRichText.toHtml(text),
        )
    }

    @Test
    fun `whole note polish stays a text block`() {
        val state = NoteEditorState()
        val snapshot = PolishSnapshot(
            request = PolishRequestDto(text = ""),
            target = null,
            originalText = "",
        )

        assertTrue(state.applyPolish(snapshot, "# Title\n\n**Polished**"))

        assertTrue(state.blocks.first() is TextBlock)
        assertTrue(state.blocks.none { it is MarkdownBlock })
    }
}
