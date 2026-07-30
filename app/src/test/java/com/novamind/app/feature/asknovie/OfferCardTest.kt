package com.novamind.app.feature.asknovie

import com.novamind.app.feature.asknovie.data.ChatCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OfferCardTest {

    @Test
    fun `offer becomes a single select card`() {
        val options = ChatCard.Offer("summary", "Create a summary?").asSingleSelectOptions()

        assertEquals("Create a summary?", options.prompt)
        assertEquals(listOf("Yes", "Not right now"), options.items.map { it.label })
        assertEquals("one", options.select)
        assertFalse(options.allowFreeText)
    }

    @Test
    fun `known offer kind maps to action`() {
        assertEquals("pull_summary", ChatCard.Offer("summary", "Summary?").acceptAction())
        assertEquals("save_note", ChatCard.Offer("note", "Note?").acceptAction())
        assertEquals("start_grilling", ChatCard.Offer("grilling", "Start?").acceptAction())
    }

    @Test
    fun `diagram offer uses its label as meaningful chat input`() {
        val offer = ChatCard.Offer("diagram", "Turn this into a diagram")

        assertNull(offer.acceptAction())
        assertEquals("Turn this into a diagram", offer.acceptText())
    }

    @Test
    fun `unknown offer kind is unsupported`() {
        val offer = ChatCard.Offer("future_kind", "Continue?")

        assertFalse(offer.isSupported())
        assertNull(ChatCard.Offer("future_kind", "Continue?").acceptAction())
        assertNull(ChatCard.Offer("future_kind", "Continue?").acceptText())
    }
}
