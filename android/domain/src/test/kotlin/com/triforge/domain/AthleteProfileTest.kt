package com.triforge.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AthleteProfileTest {

    private val full = AthleteProfile(
        goal = RaceGoal.HALF,
        raceName = "70.3 Pescara",
        raceDate = LocalDate.of(2027, 6, 7),
        weeklyHours = 9.0,
        weightKg = 72.5,
        heightCm = 178,
        maxHr = 188,
        cssSecPer100m = 108,
        ftpWatts = 245,
        thresholdPaceSecPerKm = 275,
    )

    @Test
    fun `profile survives a round trip through its stored fields`() {
        assertEquals(full, ProfileMapper.fromFields(ProfileMapper.toFields(full)))
    }

    @Test
    fun `stored numbers are read whatever type firestore returns`() {
        val profile = ProfileMapper.fromFields(mapOf("weightKg" to 72L, "ftpWatts" to 245.0, "goal" to "ironman"))

        assertEquals(72.0, profile.weightKg)
        assertEquals(245, profile.ftpWatts)
        assertEquals(RaceGoal.FULL, profile.goal)
    }

    @Test
    fun `a new account has an empty profile`() {
        assertEquals(AthleteProfile(), ProfileMapper.fromFields(mapOf("displayName" to "Luca")))
    }

    @Test
    fun `unreadable stored values are dropped, not fatal`() {
        val profile = ProfileMapper.fromFields(mapOf("goal" to "marathon", "raceDate" to "soon", "maxHr" to "high"))

        assertEquals(AthleteProfile(), profile)
    }

    @Test
    fun `cleared values are written as nulls`() {
        val fields = ProfileMapper.toFields(AthleteProfile())

        assertNull(fields.getValue("ftpWatts"))
        assertNull(fields.getValue("goal"))
        assertEquals("", fields["raceName"])
    }

    @Test
    fun `pace is typed as minutes and seconds`() {
        assertEquals(108, ProfileText.parsePace("1:48"))
        assertEquals(275, ProfileText.parsePace(" 4:35 "))
        assertEquals(275, ProfileText.parsePace("4.35"))
        assertEquals(300, ProfileText.parsePace("5:00"))
    }

    @Test
    fun `wrong pace text is rejected`() {
        assertNull(ProfileText.parsePace("4:75"))
        assertNull(ProfileText.parsePace("fast"))
        assertNull(ProfileText.parsePace("1:2:3"))
        assertNull(ProfileText.parsePace("0:00"))
        assertNull(ProfileText.parsePace(""))
    }

    @Test
    fun `pace is shown back the way it was typed`() {
        assertEquals("1:48", ProfileText.formatPace(108))
        assertEquals("", ProfileText.formatPace(null))
    }

    @Test
    fun `decimals accept comma or point`() {
        assertEquals(72.5, ProfileText.parseDecimal("72,5"))
        assertEquals(72.5, ProfileText.parseDecimal("72.5"))
        assertNull(ProfileText.parseDecimal("-3"))
        assertNull(ProfileText.parseDecimal("abc"))
        assertEquals("72,5", ProfileText.formatDecimal(72.5))
        assertEquals("9", ProfileText.formatDecimal(9.0))
    }

    @Test
    fun `whole numbers must be positive`() {
        assertEquals(188, ProfileText.parseWhole(" 188 "))
        assertNull(ProfileText.parseWhole("0"))
        assertNull(ProfileText.parseWhole("18.8"))
    }

    @Test
    fun `dates are day month year`() {
        assertEquals(LocalDate.of(2027, 6, 7), ProfileText.parseDate("7/6/2027"))
        assertEquals(LocalDate.of(2027, 6, 7), ProfileText.parseDate("07/06/2027"))
        assertEquals("7/6/2027", ProfileText.formatDate(LocalDate.of(2027, 6, 7)))
    }

    @Test
    fun `impossible dates are rejected`() {
        assertNull(ProfileText.parseDate("31/2/2027"))
        assertNull(ProfileText.parseDate("2027-06-07"))
        assertNull(ProfileText.parseDate(""))
    }
}
