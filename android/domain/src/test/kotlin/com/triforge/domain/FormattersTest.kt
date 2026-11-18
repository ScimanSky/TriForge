package com.triforge.domain

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormattersTest {

    @Test
    fun `duration under an hour shows minutes only`() {
        assertEquals("45′", Formatters.duration(45 * 60))
        assertEquals("0′", Formatters.duration(0))
    }

    @Test
    fun `duration over an hour pads minutes`() {
        assertEquals("1 h 05′", Formatters.duration(3900))
        assertEquals("2 h 00′", Formatters.duration(7200))
    }

    @Test
    fun `duration rounds to the nearest minute and carries into the hour`() {
        assertEquals("1 h 00′", Formatters.duration(3599))
    }

    @Test
    fun `swim distance is in metres, ride and run in kilometres`() {
        assertEquals("2.400 m", Formatters.distance(Sport.SWIM, 2400.0))
        assertEquals("42,2 km", Formatters.distance(Sport.RUN, 42_195.0))
        assertEquals("90,0 km", Formatters.distance(Sport.BIKE, 90_000.0))
    }

    @Test
    fun `distance follows the given locale`() {
        assertEquals("42.2 km", Formatters.distance(Sport.RUN, 42_195.0, Locale.US))
    }

    @Test
    fun `each discipline uses its own pace measure`() {
        assertEquals("1:45 /100 m", Formatters.pace(Sport.SWIM, 2000.0, 2100))
        assertEquals("5:00 /km", Formatters.pace(Sport.RUN, 10_000.0, 3000))
        assertEquals("30,0 km/h", Formatters.pace(Sport.BIKE, 60_000.0, 7200))
    }

    @Test
    fun `pace seconds never show as 60`() {
        // 299.6 s/km rounds to 5:00, not 4:60.
        assertEquals("5:00 /km", Formatters.pace(Sport.RUN, 10_000.0, 2996))
    }

    @Test
    fun `pace is absent without distance or time`() {
        assertNull(Formatters.pace(Sport.RUN, 0.0, 3000))
        assertNull(Formatters.pace(Sport.RUN, 10_000.0, 0))
        assertNull(Formatters.pace(Sport.OTHER, 10_000.0, 3000))
    }

    @Test
    fun `clock shows exact time like a watch`() {
        assertEquals("1:38:12", Formatters.clock(5892))
        assertEquals("44:05", Formatters.clock(2645))
        assertEquals("0:00", Formatters.clock(0))
    }

    @Test
    fun `pace value and unit are available separately`() {
        assertEquals("5:00", Formatters.paceValue(Sport.RUN, 10_000.0, 3000))
        assertEquals("/km", Formatters.paceUnit(Sport.RUN))
        assertEquals("30,0", Formatters.paceValue(Sport.BIKE, 60_000.0, 7200))
        assertEquals("km/h", Formatters.paceUnit(Sport.BIKE))
    }

    @Test
    fun `chart labels use one decimal`() {
        assertEquals("18,4", Formatters.kilometers(18_420.0))
        assertEquals("2,5", Formatters.hours(9000))
    }

    @Test
    fun `initials take the first letters of up to two names`() {
        assertEquals("LR", Formatters.initials("Luca Rossi"))
        assertEquals("L", Formatters.initials("  luca "))
        assertEquals("MG", Formatters.initials("Maria Grazia De Santis"))
        assertEquals("", Formatters.initials("   "))
    }
}
