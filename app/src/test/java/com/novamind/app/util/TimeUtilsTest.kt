package com.novamind.app.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TimeUtilsTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun smart_sameYear_includesDayMonthAnd24HourTime() {
        val now = instant(2026, Calendar.AUGUST, 20, 12, 0)
        val timestamp = instant(2026, Calendar.AUGUST, 1, 22, 18)

        assertEquals("1 AUG    22:18", TimeUtils.smart(timestamp, now))
    }

    @Test
    fun smart_differentYear_includesDayMonthAndYearOnly() {
        val now = instant(2026, Calendar.JANUARY, 1, 12, 0)
        val timestamp = instant(2025, Calendar.DECEMBER, 31, 22, 30)

        assertEquals("31 DEC 2025", TimeUtils.smart(timestamp, now))
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
