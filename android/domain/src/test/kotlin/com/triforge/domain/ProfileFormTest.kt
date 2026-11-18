package com.triforge.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileFormTest {

    private val profile = AthleteProfile(
        goal = RaceGoal.HALF,
        raceName = "70.3 Pescara",
        raceDate = LocalDate.of(2027, 6, 7),
        weeklyHours = 9.5,
        weightKg = 72.5,
        heightCm = 178,
        maxHr = 188,
        cssSecPer100m = 108,
        ftpWatts = 245,
        thresholdPaceSecPerKm = 275,
    )

    @Test
    fun `a stored profile is shown as the athlete would type it`() {
        val form = ProfileForm.of(profile)

        assertEquals("7/6/2027", form.raceDate)
        assertEquals("9,5", form.weeklyHours)
        assertEquals("72,5", form.weight)
        assertEquals("1:48", form.css)
        assertEquals("4:35", form.thresholdPace)
    }

    @Test
    fun `a form made from a profile parses back to the same profile`() {
        assertEquals(ProfileFormResult.Valid(profile), ProfileForm.of(profile).parse())
    }

    @Test
    fun `an empty form is a valid empty profile`() {
        assertEquals(ProfileFormResult.Valid(AthleteProfile()), ProfileForm().parse())
    }

    @Test
    fun `unreadable text is reported field by field`() {
        val result = ProfileForm(raceDate = "31/2/2027", weight = "settanta", css = "1:75", ftp = "245").parse()

        val invalid = assertIs<ProfileFormResult.Invalid>(result)
        assertEquals(setOf(ProfileField.RACE_DATE, ProfileField.WEIGHT, ProfileField.CSS), invalid.fields)
    }

    @Test
    fun `values outside any human range are rejected as typing mistakes`() {
        val result = ProfileForm(
            weeklyHours = "90",
            weight = "720",
            height = "17",
            maxHr = "18",
            css = "0:20",
            ftp = "2450",
            thresholdPace = "45:00",
        ).parse()

        val invalid = assertIs<ProfileFormResult.Invalid>(result)
        assertEquals(
            setOf(
                ProfileField.WEEKLY_HOURS,
                ProfileField.WEIGHT,
                ProfileField.HEIGHT,
                ProfileField.MAX_HR,
                ProfileField.CSS,
                ProfileField.FTP,
                ProfileField.THRESHOLD_PACE,
            ),
            invalid.fields,
        )
    }

    @Test
    fun `race name is trimmed and the goal kept`() {
        val result = ProfileForm(goal = RaceGoal.SPRINT, raceName = "  Sprint di Lecco ").parse()

        assertEquals(ProfileFormResult.Valid(AthleteProfile(goal = RaceGoal.SPRINT, raceName = "Sprint di Lecco")), result)
    }
}
