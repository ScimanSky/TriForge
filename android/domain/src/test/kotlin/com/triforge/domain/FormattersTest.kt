package com.triforge.domain

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormattersTest {

    @Test
    fun `duration under an hour shows minutes only`() {
        assertEquals("45m", Formatters.duration(45 * 60))
        assertEquals("0m", Formatters.duration(0))
    }

    @Test
    fun `duration over an hour pads minutes`() {
        assertEquals("1h 05m", Formatters.duration(3900))
        assertEquals("2h 00m", Formatters.duration(7200))
    }

    @Test
    fun `duration rounds to the nearest minute and carries into the hour`() {
        assertEquals("1h 00m", Formatters.duration(3599))
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
        assertEquals("1:45 /100m", Formatters.pace(Sport.SWIM, 2000.0, 2100))
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
}
