package com.novamind.app.feature.asknovie

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCardBodyTest {

    @Test
    fun `extracts readable text from note content envelope`() {
        val content = buildJsonObject {
            put("body", "First line\nSecond line")
        }.toString()

        assertEquals("First line\nSecond line", noteCardBody(content, preview = "Short preview"))
    }

    @Test
    fun `falls back to preview when content has no body`() {
        assertEquals("Short preview", noteCardBody("{}", preview = "Short preview"))
    }

    @Test
    fun `falls back to draft when response content and preview are empty`() {
        assertEquals("Draft content", noteCardBody("{}", preview = null, fallback = "Draft content"))
    }
}
