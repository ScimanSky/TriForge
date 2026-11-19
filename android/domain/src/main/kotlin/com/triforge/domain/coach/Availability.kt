package com.triforge.domain.coach

import com.triforge.domain.Sport
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** When the athlete can do one sport: the days and the usual time. */
data class SportSlot(val days: Set<DayOfWeek> = emptySet(), val time: LocalTime? = null)

/**
 * The athlete's week as the regia sees it (board 8): the days and usual time
 * of each sport, the preferred rest day, the weak sport and a few practical
 * limits. Days left empty for a sport mean "any day".
 */
data class TrainingAvailability(
    val swim: SportSlot = SportSlot(),
    val bike: SportSlot = SportSlot(),
    val run: SportSlot = SportSlot(),
    val restDay: DayOfWeek? = null,
    /** The sport to bring up; null lets the app decide from the data. */
    val weakSport: Sport? = null,
    val poolLengthM: Int = 25,
    /** The longest ride the athlete can fit, in minutes. */
    val longestRideMin: Int? = null,
    /** Whether midweek rides can be on the indoor trainer. */
    val indoorTrainer: Boolean = false,
) {
    fun slot(sport: Sport): SportSlot? = when (sport) {
        Sport.SWIM -> swim
        Sport.BIKE -> bike
        Sport.RUN -> run
        Sport.OTHER -> null
    }

    /** Whether [sport] fits on [day]: always, when the athlete gave no days for it. */
    fun allows(sport: Sport, day: DayOfWeek): Boolean {
        val days = slot(sport)?.days ?: return true
        return days.isEmpty() || day in days
    }

    /** Whether the athlete has filled in anything yet. */
    val isSet: Boolean get() = this != TrainingAvailability()

    fun withSlot(sport: Sport, slot: SportSlot): TrainingAvailability = when (sport) {
        Sport.SWIM -> copy(swim = slot)
        Sport.BIKE -> copy(bike = slot)
        Sport.RUN -> copy(run = slot)
        Sport.OTHER -> this
    }

    companion object {
        val POOL_LENGTHS = listOf(25, 50)
        val LONGEST_RIDE_MIN = 30..480
    }
}

/** Reads and writes the `availability` map of the `users/{uid}` document. */
object AvailabilityMapper {

    const val FIELD = "availability"

    fun dayKey(day: DayOfWeek): String = day.name.lowercase().take(3)
    fun day(key: Any?): DayOfWeek? = DayOfWeek.entries.firstOrNull { dayKey(it) == key }
    fun sportKey(sport: Sport): String = sport.name.lowercase()
    fun sport(key: Any?): Sport? = Sport.entries.firstOrNull { it != Sport.OTHER && sportKey(it) == key }

    fun fromFields(document: Map<String, Any?>): TrainingAvailability {
        val fields = document[FIELD] as? Map<*, *> ?: return TrainingAvailability()
        fun slot(prefix: String) = SportSlot(
            days = (fields["${prefix}Days"] as? List<*>).orEmpty().mapNotNull(::day).toSet(),
            time = (fields["${prefix}Time"] as? String)?.let {
                try {
                    LocalTime.parse(it)
                } catch (error: DateTimeParseException) {
                    null
                }
            },
        )
        return TrainingAvailability(
            swim = slot("swim"),
            bike = slot("bike"),
            run = slot("run"),
            restDay = day(fields["restDay"]),
            weakSport = sport(fields["weakSport"]),
            poolLengthM = (fields["poolLengthM"] as? Number)?.toInt()?.takeIf { it in TrainingAvailability.POOL_LENGTHS } ?: 25,
            longestRideMin = (fields["longestRideMin"] as? Number)?.toInt()?.takeIf { it in TrainingAvailability.LONGEST_RIDE_MIN },
            indoorTrainer = fields["indoorTrainer"] as? Boolean ?: false,
        )
    }

    fun toFields(availability: TrainingAvailability): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        listOf("swim" to availability.swim, "bike" to availability.bike, "run" to availability.run).forEach { (prefix, slot) ->
            map["${prefix}Days"] = slot.days.sorted().map(::dayKey)
            map["${prefix}Time"] = slot.time?.toString()
        }
        map["restDay"] = availability.restDay?.let(::dayKey)
        map["weakSport"] = availability.weakSport?.let(::sportKey)
        map["poolLengthM"] = availability.poolLengthM
        map["longestRideMin"] = availability.longestRideMin
        map["indoorTrainer"] = availability.indoorTrainer
        return mapOf(FIELD to map)
    }
}
