package com.triforge.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

enum class RaceGoal(val key: String) {
    SPRINT("sprint"),
    OLYMPIC("olympic"),
    HALF("70.3"),
    FULL("ironman"),
    FITNESS("fitness"),
}

/**
 * What the athlete tells the app about themselves. The thresholds are what
 * the coach will use to work out zones and load; all of them are optional.
 */
data class AthleteProfile(
    val goal: RaceGoal? = null,
    val raceName: String = "",
    val raceDate: LocalDate? = null,
    val weeklyHours: Double? = null,
    val weightKg: Double? = null,
    val heightCm: Int? = null,
    val maxHr: Int? = null,
    /** Critical swim speed, in seconds per 100 m. */
    val cssSecPer100m: Int? = null,
    /** Functional threshold power on the bike, in watts. */
    val ftpWatts: Int? = null,
    /** Threshold running pace, in seconds per km. */
    val thresholdPaceSecPerKm: Int? = null,
)

/** Reads and writes the profile fields of the `users/{uid}` document. */
object ProfileMapper {

    fun fromFields(fields: Map<String, Any?>): AthleteProfile = AthleteProfile(
        goal = RaceGoal.entries.firstOrNull { it.key == fields["goal"] },
        raceName = fields["raceName"] as? String ?: "",
        raceDate = (fields["raceDate"] as? String)?.let {
            try {
                LocalDate.parse(it)
            } catch (error: DateTimeParseException) {
                null
            }
        },
        weeklyHours = (fields["weeklyHours"] as? Number)?.toDouble(),
        weightKg = (fields["weightKg"] as? Number)?.toDouble(),
        heightCm = (fields["heightCm"] as? Number)?.toInt(),
        maxHr = (fields["maxHr"] as? Number)?.toInt(),
        cssSecPer100m = (fields["cssSecPer100m"] as? Number)?.toInt(),
        ftpWatts = (fields["ftpWatts"] as? Number)?.toInt(),
        thresholdPaceSecPerKm = (fields["thresholdPaceSecPerKm"] as? Number)?.toInt(),
    )

    /** Every field is written, nulls included, so clearing a value clears it in storage. */
    fun toFields(profile: AthleteProfile): Map<String, Any?> = mapOf(
        "goal" to profile.goal?.key,
        "raceName" to profile.raceName.trim(),
        "raceDate" to profile.raceDate?.toString(),
        "weeklyHours" to profile.weeklyHours,
        "weightKg" to profile.weightKg,
        "heightCm" to profile.heightCm,
        "maxHr" to profile.maxHr,
        "cssSecPer100m" to profile.cssSecPer100m,
        "ftpWatts" to profile.ftpWatts,
        "thresholdPaceSecPerKm" to profile.thresholdPaceSecPerKm,
    )
}

/**
 * Parsing of what the athlete types into the profile form. Every function
 * returns null for text it cannot read, so the form can tell empty from wrong.
 */
object ProfileText {

    private val dateFormat = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)

    /** "1:48" or "1.48" as seconds; a plain number is taken as seconds. */
    fun parsePace(text: String): Int? {
        val parts = text.trim().split(':', '.', '′', '\'')
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.takeIf { it > 0 }
            2 -> {
                val minutes = parts[0].toIntOrNull() ?: return null
                val seconds = parts[1].toIntOrNull() ?: return null
                if (minutes < 0 || seconds !in 0..59) null else (minutes * 60 + seconds).takeIf { it > 0 }
            }
            else -> null
        }
    }

    fun formatPace(seconds: Int?): String = seconds?.let { Formatters.minutesAndSeconds(it.toDouble()) } ?: ""

    /** Accepts both "72,5" and "72.5". */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    fun formatDecimal(value: Double?): String = when {
        value == null -> ""
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString().replace('.', ',')
    }

    fun parseWhole(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

    /** Day/month/year, as written in Italy: "7/6/2027". */
    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim(), dateFormat)
    } catch (error: DateTimeParseException) {
        null
    }

    fun formatDate(date: LocalDate?): String = date?.let { "%d/%d/%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: ""
}

enum class ProfileField { RACE_DATE, WEEKLY_HOURS, WEIGHT, HEIGHT, MAX_HR, CSS, FTP, THRESHOLD_PACE }

sealed interface ProfileFormResult {
    data class Valid(val profile: AthleteProfile) : ProfileFormResult

    /** The fields whose text could not be read, or is outside any human range. */
    data class Invalid(val fields: Set<ProfileField>) : ProfileFormResult
}

/**
 * The profile as the athlete types it: every number is still text. A blank
 * field means "not set"; only text that cannot be read is an error.
 */
data class ProfileForm(
    val goal: RaceGoal? = null,
    val raceName: String = "",
    val raceDate: String = "",
    val weeklyHours: String = "",
    val weight: String = "",
    val height: String = "",
    val maxHr: String = "",
    val css: String = "",
    val ftp: String = "",
    val thresholdPace: String = "",
) {

    fun parse(): ProfileFormResult {
        val errors = mutableSetOf<ProfileField>()

        fun <T : Any> read(text: String, field: ProfileField, parse: (String) -> T?): T? {
            if (text.isBlank()) return null
            val value = parse(text)
            if (value == null) errors += field
            return value
        }

        val profile = AthleteProfile(
            goal = goal,
            raceName = raceName.trim(),
            raceDate = read(raceDate, ProfileField.RACE_DATE, ProfileText::parseDate),
            weeklyHours = read(weeklyHours, ProfileField.WEEKLY_HOURS) {
                ProfileText.parseDecimal(it)?.takeIf { hours -> hours <= MAX_WEEKLY_HOURS }
            },
            weightKg = read(weight, ProfileField.WEIGHT) {
                ProfileText.parseDecimal(it)?.takeIf { kg -> kg in WEIGHT_KG }
            },
            heightCm = read(height, ProfileField.HEIGHT) {
                ProfileText.parseWhole(it)?.takeIf { cm -> cm in HEIGHT_CM }
            },
            maxHr = read(maxHr, ProfileField.MAX_HR) {
                ProfileText.parseWhole(it)?.takeIf { bpm -> bpm in MAX_HR_BPM }
            },
            cssSecPer100m = read(css, ProfileField.CSS) {
                ProfileText.parsePace(it)?.takeIf { seconds -> seconds in CSS_SEC }
            },
            ftpWatts = read(ftp, ProfileField.FTP) {
                ProfileText.parseWhole(it)?.takeIf { watts -> watts in FTP_WATTS }
            },
            thresholdPaceSecPerKm = read(thresholdPace, ProfileField.THRESHOLD_PACE) {
                ProfileText.parsePace(it)?.takeIf { seconds -> seconds in THRESHOLD_PACE_SEC }
            },
        )
        return if (errors.isEmpty()) ProfileFormResult.Valid(profile) else ProfileFormResult.Invalid(errors)
    }

    companion object {
        // Wide on purpose: they catch typing mistakes, not unusual athletes.
        const val MAX_WEEKLY_HOURS = 40.0
        val WEIGHT_KG = 30.0..200.0
        val HEIGHT_CM = 120..230
        val MAX_HR_BPM = 100..230
        val CSS_SEC = 50..300
        val FTP_WATTS = 50..600
        val THRESHOLD_PACE_SEC = 150..600

        fun of(profile: AthleteProfile): ProfileForm = ProfileForm(
            goal = profile.goal,
            raceName = profile.raceName,
            raceDate = ProfileText.formatDate(profile.raceDate),
            weeklyHours = ProfileText.formatDecimal(profile.weeklyHours),
            weight = ProfileText.formatDecimal(profile.weightKg),
            height = profile.heightCm?.toString().orEmpty(),
            maxHr = profile.maxHr?.toString().orEmpty(),
            css = ProfileText.formatPace(profile.cssSecPer100m),
            ftp = profile.ftpWatts?.toString().orEmpty(),
            thresholdPace = ProfileText.formatPace(profile.thresholdPaceSecPerKm),
        )
    }
}
