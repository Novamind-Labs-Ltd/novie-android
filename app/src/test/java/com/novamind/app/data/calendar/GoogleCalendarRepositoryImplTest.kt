package com.novamind.app.data.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleCalendarRepositoryImplTest {

    @Test
    fun eventsOn_convertsExclusiveAllDayEndToInclusiveDomainDate() = runBlocking {
        val api = FakeCalendarApi(
            event = allDayEvent(start = "2026-07-29", exclusiveEnd = "2026-07-30"),
        )
        val repository = GoogleCalendarRepositoryImpl(api, ZoneId.of("Pacific/Auckland"))

        val event = repository.eventsOn(LocalDate.of(2026, 7, 29)).single()

        assertEquals(LocalDateTime.of(2026, 7, 29, 0, 0), event.start)
        assertEquals(LocalDateTime.of(2026, 7, 29, 0, 0), event.end)
    }

    @Test
    fun updateEvent_convertsInclusiveAllDayEndBackToExclusiveApiDate() = runBlocking {
        val api = FakeCalendarApi(
            event = allDayEvent(start = "2026-07-29", exclusiveEnd = "2026-07-31"),
        )
        val repository = GoogleCalendarRepositoryImpl(api, ZoneId.of("Pacific/Auckland"))
        val event = CalendarEvent(
            id = "event-1",
            title = "All day",
            isAllDay = true,
            start = LocalDate.of(2026, 7, 29).atStartOfDay(),
            end = LocalDate.of(2026, 7, 30).atStartOfDay(),
            location = null,
        )

        repository.updateEvent(event)

        assertEquals("2026-07-29", api.lastPatch?.start?.date)
        assertEquals("2026-07-31", api.lastPatch?.end?.date)
    }

    private fun allDayEvent(start: String, exclusiveEnd: String) = EventDto(
        id = "event-1",
        summary = "All day",
        start = EventDateTimeDto(date = start),
        end = EventDateTimeDto(date = exclusiveEnd),
        attendees = listOf(AttendeeDto(email = "guest@example.com")),
    )

    private class FakeCalendarApi(private val event: EventDto) : GoogleCalendarApi {
        var lastPatch: EventPatchDto? = null

        override suspend fun getCalendar(calendarId: String) = CalendarDto(id = "user@example.com")

        override suspend fun listEvents(
            calendarId: String,
            timeMin: String,
            timeMax: String,
            singleEvents: Boolean,
            orderBy: String,
            maxResults: Int,
        ) = EventsResponse(items = listOf(event))

        override suspend fun patchEvent(
            calendarId: String,
            eventId: String,
            body: EventPatchDto,
        ): EventDto {
            lastPatch = body
            return event
        }
    }
}
