package com.novamind.app.feature.asknovie.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskNovieChatCardTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `options card parses many descriptions and ignores additive fields`() {
        val card = AskNovieChat.parseCard(
            json.parseToJsonElement(
                """{
                    "card_type":"options",
                    "prompt":"Choose",
                    "items":[{"id":"0","label":"First","description":"Detail","future":1}],
                    "allow_free_text":false,
                    "select":"many",
                    "future_card_field":"ignored"
                }""",
            ).jsonObject,
        )

        assertTrue(card is ChatCard.Options)
        card as ChatCard.Options
        assertEquals("many", card.select)
        assertEquals("Detail", card.items.single().description)
        assertEquals(false, card.allowFreeText)
    }

    @Test
    fun `unknown select falls back to one`() {
        val card = AskNovieChat.parseCard(
            json.parseToJsonElement(
                """{"card_type":"options","items":[],"select":"future"}""",
            ).jsonObject,
        ) as ChatCard.Options

        assertEquals("one", card.select)
    }

    @Test
    fun `grilling offer parses without a new card type`() {
        val card = AskNovieChat.parseCard(
            json.parseToJsonElement(
                """{"card_type":"offer","kind":"grilling","label":"Go deeper?"}""",
            ).jsonObject,
        )

        assertEquals(ChatCard.Offer("grilling", "Go deeper?"), card)
    }

    @Test
    fun `save note card stays distinct from create note`() {
        val card = AskNovieChat.parseCard(
            json.parseToJsonElement(
                """{"card_type":"save_note","draft_title":"Plan","draft_content":"Next steps"}""",
            ).jsonObject,
        )

        assertEquals(ChatCard.SaveNote("Plan", "Next steps"), card)
    }

    @Test
    fun `unknown card type is skipped`() {
        val card = AskNovieChat.parseCard(
            json.parseToJsonElement("""{"card_type":"future_card"}""").jsonObject,
        )

        assertNull(card)
    }
}
