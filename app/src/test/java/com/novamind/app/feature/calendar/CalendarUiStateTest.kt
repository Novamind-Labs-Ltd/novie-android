package com.novamind.app.feature.calendar

import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarUiStateTest {

    @Test
    fun displayedEvents_placesPastEventsAfterUpcomingEvents() {
        val events = listOf(
            event(id = "past-later", start = LocalDateTime.of(2020, 1, 1, 11, 0)),
            event(id = "upcoming-later", start = LocalDateTime.of(2100, 1, 1, 11, 0)),
            event(id = "past-earlier", start = LocalDateTime.of(2020, 1, 1, 9, 0)),
            event(id = "upcoming-earlier", start = LocalDateTime.of(2100, 1, 1, 9, 0)),
        )

        val displayedIds = CalendarUiState(events = events).displayedEvents.map { it.id }

        assertEquals(
            listOf("upcoming-earlier", "upcoming-later", "past-earlier", "past-later"),
            displayedIds,
        )
    }

    private fun event(id: String, start: LocalDateTime) = CalendarEvent(
        id = id,
        title = id,
        isAllDay = false,
        start = start,
        end = start.plusHours(1),
        location = null,
        eventType = CalendarEventType.DEFAULT,
    )
}
