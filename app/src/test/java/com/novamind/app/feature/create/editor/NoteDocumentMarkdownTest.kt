package com.novamind.app.feature.create.editor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `remote document removes local image paths and pending images`() {
        val local = """{"blocks":[
            {"type":"text","text":"Note","html":"<p>Note</p>"},
            {"type":"image","path":"/data/user/0/app/files/uploaded.jpg","width":356,"height":280,"fileId":"file-1"},
            {"type":"image","path":"/data/user/0/app/files/pending.jpg","width":356,"height":280}
        ]}""".trimIndent()

        val blocks = Json.parseToJsonElement(NoteDocument.forRemoteStorage(local))
            .jsonObject.getValue("blocks").jsonArray

        assertEquals(2, blocks.size)
        assertEquals("file-1", blocks[1].jsonObject.getValue("fileId").jsonPrimitive.content)
        assertFalse(blocks[1].jsonObject.containsKey("path"))
    }
}
