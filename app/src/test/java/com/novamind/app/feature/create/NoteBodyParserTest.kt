package com.novamind.app.feature.create

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteBodyParserTest {

    @Test
    fun `extracts wrapped note body`() {
        val document = """{"blocks":[{"type":"text","text":"Hello"}]}"""
        val content = buildJsonObject { put("body", document) }.toString()

        assertEquals(document, noteBodyOf(content))
    }

    @Test
    fun `accepts ask novie doc tree as note body`() {
        val content = """{"blocks":[{"type":"markdown","content":"# Plan"}]}"""

        assertEquals(
            Json.parseToJsonElement(content).toString(),
            noteBodyOf(content),
        )
    }

    @Test
    fun `returns empty body for unsupported content`() {
        assertEquals("", noteBodyOf("{}"))
        assertEquals("", noteBodyOf("not-json"))
    }
}
