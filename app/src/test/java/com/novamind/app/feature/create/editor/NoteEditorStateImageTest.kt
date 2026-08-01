package com.novamind.app.feature.create.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorStateImageTest {

    @Test
    fun `consecutive image inserts do not leave an empty text block between images`() {
        val state = NoteEditorState()

        state.insertImage(path = "/first.jpg")
        state.insertImage(path = "/second.jpg")

        assertEquals(4, state.blocks.size)
        assertEquals(
            listOf(TextBlock::class, ImageBlock::class, ImageBlock::class, TextBlock::class),
            state.blocks.map { it::class },
        )
        assertTrue((state.blocks.last() as TextBlock).rich.annotatedString.text.isEmpty())
    }
}
