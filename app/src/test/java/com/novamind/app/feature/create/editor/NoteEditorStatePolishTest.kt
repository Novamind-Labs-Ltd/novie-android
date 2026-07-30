package com.novamind.app.feature.create.editor

import com.novamind.app.common.net.PolishRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorStatePolishTest {

    @Test
    fun `whole note polish is stored as markdown block`() {
        val state = NoteEditorState()
        val snapshot = PolishSnapshot(
            request = PolishRequestDto(text = ""),
            target = null,
            originalText = "",
        )

        assertTrue(state.applyPolish(snapshot, "# Title\n\n**Polished**"))

        val markdown = state.blocks.first() as MarkdownBlock
        assertEquals("# Title\n\n**Polished**", markdown.content)
    }
}
