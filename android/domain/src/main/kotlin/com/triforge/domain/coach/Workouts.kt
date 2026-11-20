package com.triforge.domain.coach

import com.triforge.domain.AthleteProfile
import com.triforge.domain.Formatters
import com.triforge.domain.Sport
import com.triforge.domain.SwimAnalysis
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** The part of a workout a step belongs to. */
enum class WorkoutBlock(val key: String, val label: String) {
    WARMUP("warmup", "Riscaldamento"),
    MAIN("main", "Serie"),
    COOLDOWN("cooldown", "Defaticamento"),
    ;

    companion object {
        fun of(key: Any?): WorkoutBlock? = entries.firstOrNull { it.key == (key as? String)?.trim()?.lowercase() }
    }
}

/**
 * One line of a structured workout: [reps] times a distance or a time in a
 * zone, with the recovery after each. The coach writes these; [target] and
 * [note] are added by the app from the athlete's zones and its checks.
 */
data class WorkoutStep(
    val block: WorkoutBlock,
    val reps: Int = 1,
    /** Metres of one rep, or null for a step by time. */
    val distanceM: Int? = null,
    /** Seconds of one rep, or null for a step by distance. */
    val durationSec: Int? = null,
    /** Zone code: A1–C3 for the swim, Z1–Z5 for bike and run. */
    val zone: String,
    val recoverySec: Int = 0,
    /** What to do, e.g. "corsa facile", "stile libero", "allunghi". */
    val text: String = "",
    /** Pace, watts or heart rate for the zone, from the athlete's thresholds. */
    val target: String = "",
    /** What the app changed, e.g. a rep that was too long and was split. */
    val note: String = "",
)

/**
 * Training zones, tied to the athlete's thresholds. The swim uses the codes
 * swimmers and their coaches use at the pool, A1 to C3, from the threshold
 * pace (CSS), with the same bounds the app reads a recorded swim with
 * ([SwimAnalysis.ZONE_SPEED_BOUNDS]): a planned B1 and a swum B1 are the
 * same pace. Bike and run use Z1 to Z5, from the FTP and the threshold pace;
 * without a threshold they fall back to heart rate from the maximum.
 *
 * Whatever the codes, [level] puts every zone on one scale of five, which is
 * what the regia and the colours go by.
 */
object Zones {

    val SWIM = SwimAnalysis.ZONE_CODES
    val LAND = listOf("Z1", "Z2", "Z3", "Z4", "Z5")

    /**
     * The swim zone a Z1–Z5 code stands for. For a while the app named the
     * swim zones like the others: plans saved then are read with these.
     */
    private val LAND_TO_SWIM = mapOf("Z1" to "A1", "Z2" to "A2", "Z3" to "B1", "Z4" to "B2", "Z5" to "C2")

    fun keys(sport: Sport?): List<String> = if (sport == Sport.SWIM) SWIM else LAND

    /** The easy zone a step falls back to when its code cannot be read. */
    fun easy(sport: Sport?): String = if (sport == Sport.SWIM) "A2" else "Z2"

    /**
     * Reads "z4", "Z4 soglia", "zona B2" as a zone of [sport]; null when
     * there is none inside. A swim step written with Z1–Z5 becomes the swim
     * zone it stands for.
     */
    fun read(sport: Sport?, raw: Any?): String? {
        val text = (raw as? String)?.trim()?.uppercase() ?: return null
        fun find(codes: Collection<String>): String? = codes.firstOrNull { it == text } ?: codes.firstOrNull { Regex("\\b$it\\b").containsMatchIn(text) }
        return if (sport == Sport.SWIM) find(SWIM) ?: find(LAND)?.let(LAND_TO_SWIM::getValue) else find(LAND)
    }

    /** How hard a zone is, 1 (recovery) to 5 (above threshold), the same scale for every sport. */
    fun level(zone: String): Int = when (zone) {
        "A1", "Z1" -> 1
        "A2", "Z2" -> 2
        "B1", "Z3" -> 3
        "B2", "C1", "Z4" -> 4
        "C2", "C3", "Z5" -> 5
        else -> 2
    }

    /** Threshold work and above: what the regia counts as hard. */
    fun isHigh(zone: String): Boolean = level(zone) >= 4

    fun label(zone: String): String = when (zone) {
        "A1" -> "Recupero"
        "A2" -> "Aerobico"
        "B1" -> "Medio"
        "B2" -> "Soglia"
        "C1" -> "Sopra soglia"
        "C2" -> "VO2max"
        "C3" -> "Sprint"
        "Z1" -> "Recupero"
        "Z2" -> "Fondo"
        "Z3" -> "Medio"
        "Z4" -> "Soglia"
        "Z5" -> "VO2max"
        else -> ""
    }

    /** How fast a sprint goes, as a share of the threshold speed: what C3 is planned at. */
    private const val SWIM_TOP = 1.20

    /** The slowest pace still written as a target for the recovery zone. */
    private const val SWIM_BOTTOM = 0.78

    /** Swim zones as a share of the threshold speed: from, to. A code that is not a swim zone is read as the easy one. */
    private fun swimSpeed(zone: String): Pair<Double, Double> {
        val bounds = SwimAnalysis.ZONE_SPEED_BOUNDS
        val index = SWIM.indexOf(zone).takeIf { it >= 0 } ?: SWIM.indexOf(easy(Sport.SWIM))
        return (bounds.getOrNull(index - 1) ?: SWIM_BOTTOM) to (bounds.getOrNull(index) ?: SWIM_TOP)
    }

    /** Run zones as a share of the threshold pace: slowest, fastest. */
    private fun runPace(zone: String): Pair<Double, Double> = when (zone) {
        "Z1" -> 1.45 to 1.29
        "Z2" -> 1.29 to 1.14
        "Z3" -> 1.13 to 1.06
        "Z4" -> 1.05 to 0.99
        else -> 0.98 to 0.90
    }

    /** Bike zones as a share of the FTP (Coggan). */
    private fun bikePower(zone: String): Pair<Double, Double> = when (zone) {
        "Z1" -> 0.45 to 0.55
        "Z2" -> 0.56 to 0.75
        "Z3" -> 0.76 to 0.90
        "Z4" -> 0.91 to 1.05
        else -> 1.06 to 1.20
    }

    /** Heart rate as a share of the maximum, for bike and run without a threshold. */
    private fun heartRate(zone: String): Pair<Double, Double> = when (zone) {
        "Z1" -> 0.50 to 0.60
        "Z2" -> 0.60 to 0.72
        "Z3" -> 0.72 to 0.82
        "Z4" -> 0.82 to 0.90
        else -> 0.90 to 0.97
    }

    /** Seconds per 100 m (swim) or per km (run) in the middle of [zone]; typical values without a threshold. */
    fun paceSec(sport: Sport, zone: String, profile: AthleteProfile): Double = when (sport) {
        Sport.SWIM -> {
            val (from, to) = swimSpeed(zone)
            (profile.cssSecPer100m ?: TYPICAL_CSS) / ((from + to) / 2)
        }
        Sport.RUN -> {
            val (slow, fast) = runPace(zone)
            (profile.thresholdPaceSecPerKm ?: TYPICAL_RUN) * (slow + fast) / 2
        }
        // Rides are written by time; a distance is read at a typical speed.
        else -> 3600.0 / TYPICAL_BIKE_KMH
    }

    /** The target of [zone] in the athlete's own numbers, or "" when no threshold is known. */
    fun target(sport: Sport?, zone: String, profile: AthleteProfile): String = when (sport) {
        Sport.SWIM -> profile.cssSecPer100m?.let { css ->
            val (from, to) = swimSpeed(zone)
            when (zone) {
                SWIM.last() -> "più veloce di ${pace(css / from)} /100 m"
                SWIM.first() -> "più lento di ${pace(css / to)} /100 m"
                else -> "${pace(css / to)} – ${pace(css / from)} /100 m"
            }
        }
        Sport.RUN -> profile.thresholdPaceSecPerKm?.let { threshold ->
            val (slow, fast) = runPace(zone)
            if (zone == "Z1") "oltre ${pace(threshold * fast)} /km"
            else "${pace(threshold * fast)} – ${pace(threshold * slow)} /km"
        } ?: beats(zone, profile)
        Sport.BIKE -> profile.ftpWatts?.let { ftp ->
            val (from, to) = bikePower(zone)
            if (zone == "Z1") "sotto ${(ftp * to).roundToInt()} W"
            else "${(ftp * from).roundToInt()} – ${(ftp * to).roundToInt()} W"
        } ?: beats(zone, profile)
        else -> null
    }.orEmpty()

    private fun beats(zone: String, profile: AthleteProfile): String? = profile.maxHr?.let { max ->
        val (from, to) = heartRate(zone)
        "${(max * from).roundToInt()} – ${(max * to).roundToInt()} bpm"
    }

    private fun pace(seconds: Double): String = Formatters.minutesAndSeconds(seconds)

    /**
     * The longest single rep in [zone]: metres in the pool (B2 400, C1 200,
     * C2 100, C3 one sprint), seconds on land (20 minutes at threshold, 5
     * above).
     */
    fun maxRep(sport: Sport, zone: String, poolLengthM: Int): Int? = when (sport) {
        Sport.SWIM -> when (zone) {
            "B2" -> 400
            "C1" -> 200
            "C2" -> 100
            "C3" -> if (poolLengthM >= 50) 50 else 25
            else -> null
        }
        Sport.BIKE, Sport.RUN -> when (zone) {
            "Z4" -> 20 * 60
            "Z5" -> 5 * 60
            else -> null
        }
        else -> null
    }

    const val TYPICAL_CSS = 120
    const val TYPICAL_RUN = 330
    const val TYPICAL_BIKE_KMH = 28.0
}

/**
 * What the app does to a workout the coach wrote, as SwimForge does: the
 * model writes the reps with a zone; the app reads the zones, splits a rep
 * that is too long for its zone, adds pace, watts or heart rate from the
 * athlete's thresholds and recomputes the totals, which the model is not
 * asked for.
 */
object Workouts {

    const val MAX_STEPS = 24
    const val MAX_REPS = 60

    /** Seconds of one rep of [step], from its time or its distance at the zone's pace. */
    fun repSec(sport: Sport, step: WorkoutStep, profile: AthleteProfile): Double = step.durationSec?.toDouble()
        ?: step.distanceM?.let { metres ->
            val unit = if (sport == Sport.SWIM) 100.0 else 1000.0
            metres / unit * Zones.paceSec(sport, step.zone, profile)
        }
        ?: 0.0

    /** Work and recovery of the whole step, in seconds. */
    fun stepSec(sport: Sport, step: WorkoutStep, profile: AthleteProfile): Double =
        step.reps * repSec(sport, step, profile) + recoveries(step) * step.recoverySec

    /** The recovery after the last rep of a single effort is the next step's business. */
    private fun recoveries(step: WorkoutStep): Int = if (step.reps > 1) step.reps else 0

    fun totalMin(session: PlannedSession, profile: AthleteProfile): Int {
        val sport = session.sport ?: return session.durationMin
        if (session.steps.isEmpty()) return session.durationMin
        return (session.steps.sumOf { stepSec(sport, it, profile) } / 60).roundToInt()
    }

    /** Metres of the session: written for the swim, estimated from the pace for the run; null for the bike. */
    fun distanceM(session: PlannedSession, profile: AthleteProfile): Int? {
        val sport = session.sport?.takeIf { it == Sport.SWIM || it == Sport.RUN } ?: return null
        if (session.steps.isEmpty()) return null
        val unit = if (sport == Sport.SWIM) 100.0 else 1000.0
        return session.steps.sumOf { step ->
            step.reps * (step.distanceM?.toDouble() ?: ((step.durationSec ?: 0) / Zones.paceSec(sport, step.zone, profile) * unit))
        }.roundToInt()
    }

    /** Minutes of work per zone, recoveries left out, in the order of the zones. */
    fun zoneMinutes(session: PlannedSession, profile: AthleteProfile): Map<String, Int> {
        val sport = session.sport ?: return emptyMap()
        val seconds = session.steps.groupBy { it.zone }.mapValues { (_, steps) -> steps.sumOf { it.reps * repSec(sport, it, profile) } }
        return Zones.keys(sport).mapNotNull { zone -> seconds[zone]?.let { zone to (it / 60).roundToInt() } }.filter { it.second > 0 }.toMap()
    }

    /** Minutes at threshold and above. */
    fun highMinutes(session: PlannedSession, profile: AthleteProfile): Int {
        val sport = session.sport ?: return 0
        return (session.steps.filter { Zones.isHigh(it.zone) }.sumOf { it.reps * repSec(sport, it, profile) } / 60).roundToInt()
    }

    /** The intensity the steps add up to: the hardest zone with at least three minutes of work. */
    fun intensity(session: PlannedSession, profile: AthleteProfile): Intensity {
        val sport = session.sport ?: return session.intensity
        if (session.steps.isEmpty() || session.intensity == Intensity.RACE) return session.intensity
        val byLevel = session.steps.groupBy { Zones.level(it.zone) }.mapValues { (_, steps) -> steps.sumOf { it.reps * repSec(sport, it, profile) } }
        // A sprint set is short by nature: one minute of it already makes the session hard.
        val level = (5 downTo 1).firstOrNull { (byLevel[it] ?: 0.0) >= (if (it == 5) 60.0 else 180.0) } ?: 2
        return when (level) {
            5 -> Intensity.VO2MAX
            4 -> Intensity.THRESHOLD
            3 -> Intensity.TEMPO
            2 -> Intensity.ENDURANCE
            else -> Intensity.EASY
        }
    }

    /** A rep too long for its zone becomes more, shorter reps with the same recovery. */
    private fun split(sport: Sport, step: WorkoutStep, profile: AthleteProfile, poolLengthM: Int): WorkoutStep {
        val max = Zones.maxRep(sport, step.zone, poolLengthM) ?: return step
        if (sport == Sport.SWIM) {
            val metres = step.distanceM ?: return step
            if (metres <= max) return step
            val unit = if (poolLengthM >= 50) 50 else 25
            val pieces = (ceil(metres / max.toDouble()).toInt()..metres / unit).firstOrNull { metres % it == 0 && (metres / it) % unit == 0 && metres / it <= max }
                ?: return step
            return step.copy(
                reps = (step.reps * pieces).coerceAtMost(MAX_REPS),
                distanceM = metres / pieces,
                recoverySec = max(step.recoverySec, 15),
                note = "${step.reps} × $metres m in ${step.zone} era troppo lunga: divisa in ripetute da ${metres / pieces} m.",
            )
        }
        val seconds = repSec(sport, step, profile)
        if (seconds <= max * 1.1) return step
        val pieces = ceil(seconds / max).toInt()
        return step.copy(
            reps = (step.reps * pieces).coerceAtMost(MAX_REPS),
            distanceM = step.distanceM?.let { it / pieces },
            durationSec = step.durationSec?.let { it / pieces },
            recoverySec = max(step.recoverySec, 60),
            note = "Ripetuta troppo lunga per la zona ${step.zone}: divisa in $pieces.",
        )
    }

    /**
     * The session as the athlete sees it: zones read, long reps split,
     * targets from the thresholds, duration and intensity recomputed. A
     * session without steps is left as the coach wrote it.
     */
    fun finalize(session: PlannedSession, profile: AthleteProfile, poolLengthM: Int = 25): PlannedSession {
        val sport = session.sport?.takeIf { it in TriRegia.SPORTS } ?: return session.copy(steps = emptyList())
        val steps = session.steps.take(MAX_STEPS)
            .filter { (it.distanceM ?: 0) > 0 || (it.durationSec ?: 0) > 0 }
            .map { raw ->
                val step = raw.copy(
                    reps = raw.reps.coerceIn(1, MAX_REPS),
                    zone = Zones.read(sport, raw.zone) ?: Zones.easy(sport),
                    recoverySec = raw.recoverySec.coerceIn(0, 900),
                    note = "",
                )
                split(sport, step, profile, poolLengthM).let { it.copy(target = Zones.target(sport, it.zone, profile)) }
            }
            .sortedBy { it.block.ordinal }
        if (steps.isEmpty()) return session.copy(steps = emptyList())
        val built = session.copy(steps = steps)
        return built.copy(durationMin = totalMin(built, profile).coerceIn(5, 600), intensity = intensity(built, profile))
    }

    /**
     * [session] with about [minutes] fewer minutes at threshold and above:
     * reps come off the hard sets, never below two. Used when the coaches'
     * second round still leaves too much time in the high zones.
     */
    fun trimHigh(session: PlannedSession, minutes: Int, profile: AthleteProfile): PlannedSession {
        val sport = session.sport ?: return session
        var left = minutes * 60.0
        val steps = session.steps.reversed().map { step ->
            if (left <= 0 || !Zones.isHigh(step.zone) || step.reps <= 2) return@map step
            val rep = repSec(sport, step, profile).takeIf { it > 0 } ?: return@map step
            val drop = ceil(left / rep).toInt().coerceAtMost(step.reps - 2)
            left -= drop * rep
            step.copy(reps = step.reps - drop, note = "L'app ha tolto $drop ripetute: il tempo in zona alta superava il limite della settimana.")
        }.reversed()
        val built = session.copy(steps = steps)
        return built.copy(durationMin = totalMin(built, profile), intensity = intensity(built, profile))
    }

    /**
     * [session] brought down to about [toMin] minutes by shortening its
     * longest easy step; the hard sets stay as the coach wrote them.
     */
    fun shorten(session: PlannedSession, toMin: Int, profile: AthleteProfile): PlannedSession {
        val sport = session.sport ?: return session
        if (session.steps.isEmpty()) return session.copy(durationMin = toMin.coerceAtLeast(0))
        val excess = (totalMin(session, profile) - toMin) * 60.0
        if (excess <= 0) return session
        val index = session.steps.withIndex()
            .filter { !Zones.isHigh(it.value.zone) }
            .maxByOrNull { stepSec(sport, it.value, profile) }?.index ?: return session
        val step = session.steps[index]
        val seconds = stepSec(sport, step, profile)
        val factor = ((seconds - excess) / seconds).coerceIn(0.25, 1.0)
        val shorter = step.copy(
            distanceM = step.distanceM?.let { metres ->
                val unit = if (sport == Sport.SWIM) 50 else 100
                ((metres * factor / unit).roundToInt() * unit).coerceAtLeast(unit)
            },
            durationSec = step.durationSec?.let { ((it * factor / 60).roundToInt() * 60).coerceAtLeast(60) },
            note = "Accorciata dall'app per stare nei minuti della settimana.",
        )
        val built = session.copy(steps = session.steps.toMutableList().also { it[index] = shorter })
        return built.copy(durationMin = totalMin(built, profile), intensity = intensity(built, profile))
    }

    /** The same session made easy: every zone above the endurance one comes down to it. */
    fun soften(session: PlannedSession, profile: AthleteProfile): PlannedSession {
        val sport = session.sport ?: return session
        if (session.steps.isEmpty()) return session.copy(intensity = Intensity.ENDURANCE)
        val easy = Zones.easy(sport)
        val steps = session.steps.map {
            if (Zones.level(it.zone) > 2) it.copy(zone = easy, target = Zones.target(sport, easy, profile), note = "Resa facile dall'app per rispettare la regia.") else it
        }
        val built = session.copy(steps = steps)
        return built.copy(durationMin = totalMin(built, profile), intensity = intensity(built, profile))
    }

    /**
     * [session] with the target of every step in the athlete's numbers of
     * today: a saved plan carries the targets of the day it was written,
     * and thresholds and zones may have changed since. A step whose zone has
     * no target now keeps the one it had.
     */
    fun withTargets(session: PlannedSession, profile: AthleteProfile): PlannedSession {
        val sport = session.sport ?: return session
        return session.copy(steps = session.steps.map { step -> step.copy(target = Zones.target(sport, step.zone, profile).ifBlank { step.target }) })
    }

    /** One step in words, for the requests and the coaches' briefs: "6 × 800 m Z4, rec. 2′", "8 × 100 m stile libero B2, rec. 20″". */
    fun describe(step: WorkoutStep): String {
        val size = step.distanceM?.let { "$it m" } ?: step.durationSec?.let(::clock).orEmpty()
        val head = if (step.reps > 1) "${step.reps} × $size" else size
        val recovery = if (step.recoverySec > 0) ", rec. ${clock(step.recoverySec)}" else ""
        return listOf(head, step.text, step.zone).filter { it.isNotBlank() }.joinToString(" ") + recovery
    }

    /** 90 → 1′30″, 600 → 10′, 20 → 20″. */
    fun clock(seconds: Int): String = when {
        seconds < 60 -> "$seconds″"
        seconds % 60 == 0 -> "${seconds / 60}′"
        else -> "${seconds / 60}′${"%02d".format(seconds % 60)}″"
    }
}
