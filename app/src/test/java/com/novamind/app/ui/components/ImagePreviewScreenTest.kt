package com.novamind.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewScreenTest {

    @Test
    fun `portrait image is fully fitted inside landscape viewport`() {
        val fitted = fittedPreviewSize(
            container = IntSize(1000, 500),
            image = IntSize(500, 1000),
        )

        assertEquals(250f, fitted.width, 0.01f)
        assertEquals(500f, fitted.height, 0.01f)
    }

    @Test
    fun `pan is disabled on axis where zoomed image is smaller than viewport`() {
        val clamped = clampPreviewOffset(
            offset = Offset(500f, 500f),
            scale = 2f,
            container = IntSize(1000, 500),
            image = IntSize(500, 1000),
        )

        assertEquals(0f, clamped.x, 0.01f)
        assertEquals(250f, clamped.y, 0.01f)
    }

    @Test
    fun `landscape image keeps vertical center while panning horizontally`() {
        val clamped = clampPreviewOffset(
            offset = Offset(-900f, 200f),
            scale = 2f,
            container = IntSize(500, 1000),
            image = IntSize(1000, 500),
        )

        assertEquals(-250f, clamped.x, 0.01f)
        assertEquals(0f, clamped.y, 0.01f)
    }
}
