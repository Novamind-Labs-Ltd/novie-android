package com.novamind.app.feature.asknovie.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MermaidDiagramCardTest {

    @Test
    fun `accepts a normal Mermaid diagram`() {
        assertNull(validateMermaidSource("flowchart LR; A[Plan] --> B[Build]"))
    }

    @Test
    fun `rejects blank source`() {
        assertEquals("The diagram source is empty.", validateMermaidSource("  \n"))
    }

    @Test
    fun `rejects source over safe limit`() {
        assertEquals(
            "The diagram is too large to render safely.",
            validateMermaidSource("a".repeat(10_001)),
        )
    }
}
