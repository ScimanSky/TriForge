package com.triforge.domain.coach

import com.triforge.domain.Sport
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** When the athlete can do one sport: the days and the usual time. */
data class SportSlot(val days: Set<DayOfWeek> = emptySet(), val time: LocalTime? = null)

/**
 * The gym as the athlete does it: their own programme on their own days.
 * No coach writes it; the regia puts it in the week and the coaches work
 * around it.
 */
data class GymSlot(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = emptySet(),
    val time: LocalTime? = null,
    /** How long one session usually lasts. */
    val minutes: Int = DEFAULT_MINUTES,
) {
    companion object {
        const val DEFAULT_MINUTES = 60
        const val STEP_MINUTES = 15
        val MINUTES = 30..150
    }
}

/** Who looks after one of the three disciplines in this period. */
enum class SportMode {
    /** The sport's AI coach writes the sessions. */
    AI,

    /** The athlete's own trainer does: the AI coach writes nothing, it reads what was done. */
    OWN,

    /** The athlete is not doing the sport. */
    PAUSED,
}

/**
 * The athlete's week as the regia sees it (board 8): the days and usual time
 * of each sport, the gym, who looks after each sport, the preferred rest
 * day, the weak sport and a few practical limits. Days left empty for a
 * sport mean "any day".
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
    val gym: GymSlot = GymSlot(),
    /**
     * The disciplines the athlete is not doing in this period: their coach
     * writes no session and their time goes to the others. What the watch
     * records of them still counts.
     */
    val paused: Set<Sport> = emptySet(),
    /**
     * The disciplines the athlete does with their own trainer: the days and
     * the time are when they train, the sport's AI coach writes no session
     * and comments what was done, and everybody else plans around them.
     */
    val ownCoach: Set<Sport> = emptySet(),
) {
    fun slot(sport: Sport): SportSlot? = when (sport) {
        Sport.SWIM -> swim
        Sport.BIKE -> bike
        Sport.RUN -> run
        Sport.STRENGTH -> SportSlot(gym.days, gym.time).takeIf { gym.enabled }
        Sport.OTHER -> null
    }

    fun isActive(sport: Sport): Boolean = sport !in paused

    fun mode(sport: Sport): SportMode = when (sport) {
        in paused -> SportMode.PAUSED
        in ownCoach -> SportMode.OWN
        else -> SportMode.AI
    }

    /** Whether the sport's AI coach writes its sessions. */
    fun isCoached(sport: Sport): Boolean = sport in PAUSABLE && mode(sport) == SportMode.AI

    /** The disciplines an AI coach writes, in the regia's order. */
    val coachedSports: List<Sport> get() = PAUSABLE.filter(::isCoached)

    /** The disciplines the athlete does with their own trainer, in the regia's order. */
    val ownSports: List<Sport> get() = PAUSABLE.filter { mode(it) == SportMode.OWN }

    /** The gym days the regia puts in the week: the preferred rest day stays free. */
    val gymDays: Set<DayOfWeek> get() = if (gym.enabled) gym.days - setOfNotNull(restDay) else emptySet()

    /** The days of a sport done with the athlete's trainer, as the week shows them: none on the preferred rest day. */
    fun ownDays(sport: Sport): Set<DayOfWeek> =
        if (mode(sport) == SportMode.OWN) slot(sport)?.days.orEmpty() - setOfNotNull(restDay) else emptySet()

    /** Whether [sport] fits on [day]: never while it is paused, always when the athlete gave no days for it. */
    fun allows(sport: Sport, day: DayOfWeek): Boolean {
        if (!isActive(sport)) return false
        val days = slot(sport)?.days ?: return true
        return days.isEmpty() || day in days
    }

    /** Whether the athlete has filled in anything yet. */
    val isSet: Boolean get() = this != TrainingAvailability()

    fun withSlot(sport: Sport, slot: SportSlot): TrainingAvailability = when (sport) {
        Sport.SWIM -> copy(swim = slot)
        Sport.BIKE -> copy(bike = slot)
        Sport.RUN -> copy(run = slot)
        Sport.STRENGTH -> copy(gym = gym.copy(days = slot.days, time = slot.time))
        Sport.OTHER -> this
    }

    /** [sport] paused or back in the week; only the three disciplines can be paused. */
    fun withPaused(sport: Sport, paused: Boolean): TrainingAvailability = when {
        sport !in PAUSABLE -> this
        paused -> withMode(sport, SportMode.PAUSED)
        else -> copy(paused = this.paused - sport)
    }

    /**
     * Who looks after [sport] from now on. The weak sport is one the AI
     * coaches can bring up, so it is forgotten when the sport leaves them.
     */
    fun withMode(sport: Sport, mode: SportMode): TrainingAvailability {
        if (sport !in PAUSABLE) return this
        return copy(
            paused = if (mode == SportMode.PAUSED) paused + sport else paused - sport,
            ownCoach = if (mode == SportMode.OWN) ownCoach + sport else ownCoach - sport,
            weakSport = weakSport.takeIf { it != sport || mode == SportMode.AI },
        )
    }

    companion object {
        val POOL_LENGTHS = listOf(25, 50)
        val LONGEST_RIDE_MIN = 30..480
        val PAUSABLE = listOf(Sport.SWIM, Sport.BIKE, Sport.RUN)
    }
}

/** Reads and writes the `availability` map of the `users/{uid}` document. */
object AvailabilityMapper {

    const val FIELD = "availability"

    fun dayKey(day: DayOfWeek): String = day.name.lowercase().take(3)
    fun day(key: Any?): DayOfWeek? = DayOfWeek.entries.firstOrNull { dayKey(it) == key }
    fun sportKey(sport: Sport): String = sport.name.lowercase()
    fun sport(key: Any?): Sport? = TrainingAvailability.PAUSABLE.firstOrNull { sportKey(it) == key }

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
        val gym = slot("gym")
        val paused = (fields["paused"] as? List<*>).orEmpty().mapNotNull(::sport).toSet()
        val ownCoach = (fields["ownCoach"] as? List<*>).orEmpty().mapNotNull(::sport).toSet() - paused
        return TrainingAvailability(
            swim = slot("swim"),
            bike = slot("bike"),
            run = slot("run"),
            restDay = day(fields["restDay"]),
            weakSport = sport(fields["weakSport"])?.takeIf { it !in paused && it !in ownCoach },
            poolLengthM = (fields["poolLengthM"] as? Number)?.toInt()?.takeIf { it in TrainingAvailability.POOL_LENGTHS } ?: 25,
            longestRideMin = (fields["longestRideMin"] as? Number)?.toInt()?.takeIf { it in TrainingAvailability.LONGEST_RIDE_MIN },
            indoorTrainer = fields["indoorTrainer"] as? Boolean ?: false,
            gym = GymSlot(
                enabled = fields["gymOn"] as? Boolean ?: false,
                days = gym.days,
                time = gym.time,
                minutes = (fields["gymMinutes"] as? Number)?.toInt()?.takeIf { it in GymSlot.MINUTES } ?: GymSlot.DEFAULT_MINUTES,
            ),
            paused = paused,
            ownCoach = ownCoach,
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
        map["gymOn"] = availability.gym.enabled
        map["gymDays"] = availability.gym.days.sorted().map(::dayKey)
        map["gymTime"] = availability.gym.time?.toString()
        map["gymMinutes"] = availability.gym.minutes
        map["paused"] = TrainingAvailability.PAUSABLE.filter { it in availability.paused }.map(::sportKey)
        map["ownCoach"] = availability.ownSports.map(::sportKey)
        return mapOf(FIELD to map)
    }
}
