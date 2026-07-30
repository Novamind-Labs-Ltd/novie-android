package com.novamind.app.common.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteListItemDtoTest {

    @Test
    fun `decodes thumbnails returned by note list`() {
        val dto = Json.decodeFromString<NoteListItemDto>(
            """
            {
              "id": "note-1",
              "thumbnails": [
                {
                  "attachmentId": "attachment-1",
                  "thumbnailUrl": "https://example.com/thumbnail.jpg"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("attachment-1", dto.thumbnails.single().attachmentId)
        assertEquals("https://example.com/thumbnail.jpg", dto.thumbnails.single().thumbnailUrl)
    }

    @Test
    fun `decodes empty recycle bin summary`() {
        val dto = Json.decodeFromString<EmptyRecycleBinResultDto>(
            """{"purged":3,"deferred":1,"failed":2,"remaining":true}""",
        )

        assertEquals(3, dto.purged)
        assertEquals(1, dto.deferred)
        assertEquals(2, dto.failed)
        assertEquals(true, dto.remaining)
    }
}
