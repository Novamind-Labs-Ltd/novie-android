package com.novamind.app.feature.create.editor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteDocumentMarkdownTest {

    @Test
    fun `markdown is stored as an editor markdown block`() {
        val markdown = "# Plan\n\n- Ship the **first** version"
        val block = Json.parseToJsonElement(NoteDocument.fromMarkdown(markdown))
            .jsonObject.getValue("blocks").jsonArray.single().jsonObject

        assertEquals("markdown", block.getValue("type").jsonPrimitive.content)
        assertEquals(markdown, block.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `markdown preview removes structural markers`() {
        val document = NoteDocument.fromMarkdown("# Plan\n\n- Ship the **first** version")

        assertEquals("Plan\n\nShip the first version", NoteDocument.previewText(document))
    }
}
