package com.triforge.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityKindTest {

    private val start = Instant.parse("2026-11-18T17:30:00Z")

    private fun read(vararg fields: Pair<String, Any?>) =
        ActivityMapper.fromFields("a", mapOf("source" to "garmin", "durationSec" to 3600L) + fields, start)!!

    @Test
    fun `a gym session is the gym, whether the service said so or only its type does`() {
        assertEquals(Sport.STRENGTH, read("sport" to "strength", "type" to "strength_training").sport)
        // Synced before the service knew the gym: stored as "other".
        assertEquals(Sport.STRENGTH, read("sport" to "other", "type" to "strength_training").sport)
        assertEquals(Sport.OTHER, read("sport" to "other", "type" to "hiking").sport)
        assertEquals("strength", ActivityMapper.sportKey(Sport.STRENGTH))
    }

    @Test
    fun `the other activities are told apart by their type`() {
        fun kind(type: String) = read("sport" to "other", "type" to type).kind
        assertEquals(OtherKind.WALK, kind("walking"))
        assertEquals(OtherKind.WALK, kind("casual_walking"))
        assertEquals(OtherKind.HIKE, kind("hiking"))
        assertEquals(OtherKind.MOBILITY, kind("yoga"))
        assertEquals(OtherKind.MOBILITY, kind("pilates"))
        assertEquals(OtherKind.CARDIO, kind("indoor_cardio"))
        assertEquals(OtherKind.CARDIO, kind("elliptical"))
        assertEquals(OtherKind.CARDIO, kind("indoor_rowing"))
        assertEquals(OtherKind.GENERIC, kind("resort_skiing_snowboarding_ws"))
        // Health Connect has no type key: it sends the kind.
        assertEquals(OtherKind.HIKE, read("sport" to "other", "type" to "health_connect_37", "kind" to "hike").kind)
        // A sport has no kind of its own.
        assertEquals(OtherKind.GENERIC, read("sport" to "run", "type" to "trail_running").kind)
    }

    @Test
    fun `sets, repetitions and exercises of a gym session are read, broken ones skipped`() {
        val gym = read(
            "sport" to "strength", "type" to "strength_training", "sets" to 24L, "reps" to 212L,
            "exercises" to listOf(
                mapOf("category" to "SQUAT", "sets" to 4L, "reps" to 32L, "volumeKg" to 2560.0),
                mapOf("category" to "pull_up", "sets" to 3L, "reps" to 21L),
                mapOf("category" to "", "sets" to 3L),
                mapOf("category" to "plank", "sets" to 0L),
                "nonsense",
            ),
        )
        assertEquals(24, gym.sets)
        assertEquals(212, gym.reps)
        assertEquals(listOf("squat", "pull_up"), gym.exercises.map { it.category })
        assertEquals(2560.0, gym.liftedKg)
        assertNull(read("sport" to "strength").liftedKg)
    }

    // ---- Load ----------------------------------------------------------------

    private fun session(sport: Sport, minutes: Long, garminLoad: Double? = null, kind: OtherKind = OtherKind.GENERIC) = Activity(
        id = "$sport-$kind-$minutes-$garminLoad", source = ActivitySource.GARMIN, sport = sport, name = "x", startTime = start,
        durationSec = minutes * 60, distanceMeters = 0.0, trainingLoad = garminLoad, kind = kind,
    )

    private fun tss(activity: Activity) = SessionLoadModel.estimate(listOf(activity), AthleteProfile()).single()

    @Test
    fun `the gym never counts less than its floor, because the heart rate misses the lifting`() {
        // 20 of watch load is 14 TSS: under the 36 an hour of the floor.
        val light = tss(session(Sport.STRENGTH, 60, garminLoad = 20.0))
        assertEquals(36.0, light.tss, 0.01)
        assertEquals(LoadMethod.DURATION, light.method)
        // A hard circuit the watch read high stays as the watch read it.
        val hard = tss(session(Sport.STRENGTH, 60, garminLoad = 100.0))
        assertEquals(70.0, hard.tss, 0.01)
        assertEquals(LoadMethod.GARMIN_LOAD, hard.method)
        assertEquals(LoadQuality.LOW, hard.quality)
        assertEquals(54.0, tss(session(Sport.STRENGTH, 90)).tss, 0.01)
    }

    @Test
    fun `every recorded activity has a load, by its kind when the watch gave none`() {
        fun hour(kind: OtherKind) = tss(session(Sport.OTHER, 60, kind = kind)).tss
        assertEquals(16.0, hour(OtherKind.WALK), 0.01)
        assertEquals(30.25, hour(OtherKind.HIKE), 0.01)
        assertEquals(9.0, hour(OtherKind.MOBILITY), 0.01)
        assertEquals(49.0, hour(OtherKind.CARDIO), 0.01)
        assertEquals(36.0, hour(OtherKind.GENERIC), 0.01)
        // The watch's own load wins over the duration, and has no floor outside the gym.
        assertEquals(7.0, tss(session(Sport.OTHER, 60, garminLoad = 10.0, kind = OtherKind.HIKE)).tss, 0.01)
    }

    @Test
    fun `the week at a glance shows the gym and the rest only when there is some`() {
        val monday = LocalDate.of(2026, 11, 16)
        fun at(sport: Sport, day: Long) = Activity(
            id = "$sport$day", source = ActivitySource.GARMIN, sport = sport, name = "x",
            startTime = monday.plusDays(day).atTime(7, 0).toInstant(ZoneOffset.UTC), durationSec = 3600, distanceMeters = 0.0,
        )
        val plain = WeekGlanceModel.compute(listOf(at(Sport.SWIM, 0)), monday.plusDays(2), ZoneOffset.UTC)
        assertEquals(TrainingStats.triathlonSports, plain.sports.map { it.sport })
        val withGym = WeekGlanceModel.compute(listOf(at(Sport.SWIM, 0), at(Sport.STRENGTH, 1), at(Sport.OTHER, 1)), monday.plusDays(2), ZoneOffset.UTC)
        assertEquals(TrainingStats.allSports, withGym.sports.map { it.sport })
        assertTrue(withGym.sports.first { it.sport == Sport.STRENGTH }.byTime)
        assertEquals(3600.0, withGym.sports.first { it.sport == Sport.STRENGTH }.done)
    }

    @Test
    fun `the most frequent exercises are counted once a session`() {
        val monday = LocalDate.of(2026, 11, 16)
        fun gym(day: Long, vararg exercises: ExerciseSummary) = Activity(
            id = "gym$day", source = ActivitySource.GARMIN, sport = Sport.STRENGTH, name = "",
            startTime = monday.plusDays(day).atTime(18, 0).toInstant(ZoneOffset.UTC), durationSec = 3600, distanceMeters = 0.0,
            exercises = exercises.toList(),
        )
        val sessions = listOf(
            gym(0, ExerciseSummary("squat", 4, 32, 2560.0), ExerciseSummary("pull_up", 3, 21)),
            // The same exercise twice in one session is still one session.
            gym(2, ExerciseSummary("squat", 2, 16, 1200.0), ExerciseSummary("squat", 2, 10, 900.0), ExerciseSummary("plank", 3)),
            gym(-30, ExerciseSummary("deadlift", 5, 25, 3000.0)),
        )
        val since = monday.atStartOfDay().toInstant(ZoneOffset.UTC)
        val top = StrengthStats.frequentExercises(sessions, since)
        assertEquals(listOf("squat", "plank", "pull_up"), top.map { it.category })
        assertEquals(ExerciseTotal("squat", sessions = 2, sets = 8, reps = 58, volumeKg = 4660.0), top[0])
        assertEquals(null, top[2].volumeKg)
        assertEquals(0, top[1].reps)
        assertTrue(StrengthStats.frequentExercises(sessions.map { it.copy(sport = Sport.OTHER) }, since).isEmpty())
        assertEquals("Trazioni", StrengthStats.exerciseName("PULL_UP"))
        assertEquals("Kettlebell swing", StrengthStats.exerciseName("kettlebell_swing"))
        assertEquals("Altro esercizio", StrengthStats.exerciseName(" "))
    }
}
