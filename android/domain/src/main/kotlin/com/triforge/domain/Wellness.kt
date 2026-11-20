package com.triforge.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Overnight recovery data for one morning: the night's sleep and HRV are
 * filed under the day the athlete wakes up, as Garmin does.
 */
data class DailyWellness(
    val date: LocalDate,
    /** Average overnight RMSSD, in milliseconds. */
    val hrvRmssd: Double? = null,
    /** Resting heart rate, in beats per minute. */
    val restingHr: Int? = null,
    /** Time asleep, in seconds. */
    val sleepSec: Long? = null,
    /** The watch's own 0-100 sleep score. Shown, never used in the index. */
    val sleepScore: Int? = null,
    /** Garmin's HRV status (BALANCED, UNBALANCED, LOW, POOR), for comparison. */
    val hrvStatus: String? = null,
)

/**
 * The 30-second morning check-in. Every item goes from 1 (very good) to 7
 * (very bad), so a higher total always means a worse day. Motivation is asked
 * as "how little do you feel like training".
 */
data class CheckIn(
    val date: LocalDate,
    val sleepQuality: Int,
    val fatigue: Int,
    val soreness: Int,
    val stress: Int,
    val motivation: Int,
    /** The athlete says they feel ill. Pauses the overload logic. */
    val sick: Boolean = false,
) {
    /** From 5 (everything great) to 35 (everything bad). */
    val total: Int get() = sleepQuality + fatigue + soreness + stress + motivation

    /**
     * How the athlete feels in one number, as Oggi shows it: from 1 (bad)
     * to 5 (great), the other way round from the items. Coaches are told
     * this one too, so they speak of the check-in on the scale the athlete sees.
     */
    val feel: Double get() = 5.0 - (total - 5) / 30.0 * 4.0

    companion object {
        const val MIN = 1
        const val MAX = 7
    }
}

object WellnessMapper {

    /**
     * Reads one `users/{uid}/wellness/{id}` document. Returns null without a
     * valid date or when the source is not one the app knows.
     */
    fun fromFields(fields: Map<String, Any?>): Pair<ActivitySource, DailyWellness>? {
        val source = when (fields["source"]) {
            "garmin" -> ActivitySource.GARMIN
            "health_connect" -> ActivitySource.HEALTH_CONNECT
            else -> return null
        }
        val date = parseDate(fields["date"]) ?: return null
        return source to DailyWellness(
            date = date,
            hrvRmssd = positive(fields["hrvRmssd"]),
            restingHr = positive(fields["restingHr"])?.toInt(),
            sleepSec = positive(fields["sleepSec"])?.toLong(),
            sleepScore = positive(fields["sleepScore"])?.toInt(),
            hrvStatus = fields["hrvStatus"] as? String,
        )
    }

    /**
     * One record per day. Garmin wins field by field because it is the
     * reference source; Health Connect only fills what Garmin left empty.
     * Each marker must keep one source over time, or its baseline shifts:
     * this order keeps it stable as long as Garmin stays connected.
     */
    fun merge(records: List<Pair<ActivitySource, DailyWellness>>): List<DailyWellness> =
        records.groupBy { it.second.date }.map { (date, list) ->
            val ordered = list.sortedBy { if (it.first == ActivitySource.GARMIN) 0 else 1 }.map { it.second }
            DailyWellness(
                date = date,
                hrvRmssd = ordered.firstNotNullOfOrNull { it.hrvRmssd },
                restingHr = ordered.firstNotNullOfOrNull { it.restingHr },
                sleepSec = ordered.firstNotNullOfOrNull { it.sleepSec },
                sleepScore = ordered.firstNotNullOfOrNull { it.sleepScore },
                hrvStatus = ordered.firstNotNullOfOrNull { it.hrvStatus },
            )
        }.sortedBy { it.date }

    private fun positive(value: Any?): Double? =
        (value as? Number)?.toDouble()?.takeIf { it > 0 && it.isFinite() }
}

object CheckInMapper {

    fun documentId(date: LocalDate): String = date.toString()

    fun toFields(checkIn: CheckIn): Map<String, Any> = mapOf(
        "date" to checkIn.date.toString(),
        "sleepQuality" to checkIn.sleepQuality,
        "fatigue" to checkIn.fatigue,
        "soreness" to checkIn.soreness,
        "stress" to checkIn.stress,
        "motivation" to checkIn.motivation,
        "sick" to checkIn.sick,
    )

    fun fromFields(fields: Map<String, Any?>): CheckIn? {
        val date = parseDate(fields["date"]) ?: return null
        fun item(key: String): Int? =
            (fields[key] as? Number)?.toInt()?.takeIf { it in CheckIn.MIN..CheckIn.MAX }
        return CheckIn(
            date = date,
            sleepQuality = item("sleepQuality") ?: return null,
            fatigue = item("fatigue") ?: return null,
            soreness = item("soreness") ?: return null,
            stress = item("stress") ?: return null,
            motivation = item("motivation") ?: return null,
            sick = fields["sick"] as? Boolean ?: false,
        )
    }
}

private fun parseDate(value: Any?): LocalDate? = (value as? String)?.let {
    try {
        LocalDate.parse(it)
    } catch (error: DateTimeParseException) {
        null
    }
}
