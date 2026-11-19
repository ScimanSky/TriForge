package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.CheckIn
import com.triforge.domain.Formatters
import com.triforge.domain.FormZone
import com.triforge.domain.LoadResponsePattern
import com.triforge.domain.RaceGoal
import com.triforge.domain.RecoveryLight
import com.triforge.domain.RecoveryPhase
import com.triforge.domain.RecoveryReport
import com.triforge.domain.SessionLoad
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything the coaches may know about the athlete, as plain Italian text.
 *
 * Built on the phone from data the athlete already sees, and only sent when
 * the AI consent is on. Names, email and photo are never included: the model
 * needs the training, not the person's identity.
 */
data class AthleteData(
    val today: LocalDate,
    val zone: ZoneId,
    val profile: AthleteProfile,
    val activities: List<Activity>,
    val loads: Map<String, SessionLoad> = emptyMap(),
    val recovery: RecoveryReport? = null,
    val checkIn: CheckIn? = null,
    val birthYear: Int? = null,
) {
    /** Age in years this year, from the year of birth of the account. */
    val age: Int? get() = birthYear?.let { today.year - it }?.takeIf { it in 10..110 }
}

object AthleteContext {

    private val it = Locale.ITALIAN

    /** Detailed sessions of the last [RECENT_DAYS] days; older ones only as weekly totals. */
    const val RECENT_DAYS = 14L
    const val TREND_WEEKS = 8

    fun describe(data: AthleteData): String = buildString {
        appendLine("Oggi è ${dayLabel(data.today)} ${data.today}.")
        appendLine()
        appendLine("## Profilo")
        profile(data.profile, data.today, data.age).forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Volume settimanale (ultime $TREND_WEEKS settimane, dalla più vecchia)")
        weeks(data).forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Allenamenti degli ultimi $RECENT_DAYS giorni")
        val recent = recent(data)
        if (recent.isEmpty()) appendLine("- nessun allenamento registrato") else recent.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Carico e recupero")
        recovery(data).forEach { appendLine("- $it") }
    }.trimEnd()

    private fun profile(p: AthleteProfile, today: LocalDate, age: Int?): List<String> = buildList {
        val goal = when (p.goal) {
            RaceGoal.SPRINT -> "triathlon sprint"
            RaceGoal.OLYMPIC -> "triathlon olimpico"
            RaceGoal.HALF -> "70.3 (half ironman)"
            RaceGoal.FULL -> "ironman"
            RaceGoal.FITNESS -> "forma fisica, nessuna gara"
            null -> null
        }
        add("Obiettivo: ${goal ?: "non indicato"}")
        p.raceDate?.let { date ->
            val weeks = ChronoUnit.WEEKS.between(today, date)
            val name = p.raceName.ifBlank { "gara obiettivo" }
            add(if (date.isBefore(today)) "$name il $date (già passata)" else "$name il $date, tra $weeks settimane")
        }
        p.weeklyHours?.let { add("Ore disponibili a settimana: ${num(it)}") }
        age?.let { add("Età: $it anni") }
        p.weightKg?.let { add("Peso: ${num(it)} kg") }
        p.heightCm?.let { add("Altezza: $it cm") }
        p.maxHr?.let { add("FC massima: $it bpm") }
        p.cssSecPer100m?.let { add("CSS nuoto: ${Formatters.minutesAndSeconds(it.toDouble())}/100 m") }
        p.ftpWatts?.let { add("FTP bici: $it W") }
        p.thresholdPaceSecPerKm?.let { add("Passo soglia corsa: ${Formatters.minutesAndSeconds(it.toDouble())}/km") }
        if (p.cssSecPer100m == null && p.ftpWatts == null && p.thresholdPaceSecPerKm == null) {
            add("Soglie non indicate: usa zone di frequenza cardiaca o percezione dello sforzo")
        }
    }

    private fun weeks(data: AthleteData): List<String> =
        TrainingStats.weeklyTrend(data.activities, TREND_WEEKS, data.today, data.zone).map { week ->
            val parts = (TrainingStats.triathlonSports + Sport.OTHER).mapNotNull { sport ->
                val t = week.of(sport).takeIf { it.sessions > 0 } ?: return@mapNotNull null
                val distance = if (t.distanceMeters > 0) " ${Formatters.distance(sport, t.distanceMeters)}" else ""
                "${sportName(sport)} ${t.sessions}× ${Formatters.duration(t.durationSec)}$distance"
            }
            "sett. dal ${week.weekStart}: ${parts.joinToString(", ").ifEmpty { "nessun allenamento" }}"
        }

    private fun recent(data: AthleteData): List<String> {
        val from = data.today.minusDays(RECENT_DAYS - 1)
        return data.activities
            .filter { !it.startTime.atZone(data.zone).toLocalDate().isBefore(from) }
            .sortedBy { it.startTime }
            .map { a -> session(a, data) }
    }

    private fun session(a: Activity, data: AthleteData): String {
        val date = a.startTime.atZone(data.zone).toLocalDate()
        val parts = mutableListOf(
            "$date ${dayLabel(date)}",
            sportName(a.sport),
            Formatters.duration(a.durationSec),
        )
        if (a.distanceMeters > 0) parts += Formatters.distance(a.sport, a.distanceMeters)
        Formatters.pace(a.sport, a.distanceMeters, a.paceSec)?.let { parts += it }
        a.avgHr?.let { parts += "FC media $it" }
        (a.normPowerWatts ?: a.avgPowerWatts)?.let { parts += "$it W" }
        data.loads[a.id]?.let { parts += "carico ${it.tss.roundToInt()} TSS" }
        a.aerobicTe?.let { parts += "TE aerobico ${num(it)}" }
        return parts.joinToString(" · ")
    }

    private fun recovery(data: AthleteData): List<String> = buildList {
        val report = data.recovery
        if (report == null) add("dati di recupero non disponibili") else addAll(report(report))
        data.checkIn?.let { c ->
            add(
                "Check-in di oggi (1 = male, 7 = benissimo): sonno ${c.sleepQuality}, fatica ${c.fatigue}, " +
                    "dolori ${c.soreness}, stress ${c.stress}, motivazione ${c.motivation}" +
                    if (c.sick) ". Si sente malato." else "",
            )
        } ?: add("Check-in di oggi: non fatto")
    }

    private fun report(report: RecoveryReport): List<String> {
        val today = report.today
        return buildList {
            today.load?.let { load ->
                add(
                    "Forma (modello carico): fitness ${load.ctl.roundToInt()}, fatica ${load.atl.roundToInt()}, " +
                        "forma ${(load.formPct * 100).roundToInt()}% (${formZone(FormZone.of(load.formPct))}), " +
                        "variazione fitness in 7 giorni ${signed(load.ramp)}",
                )
                add("Monotonia del carico: ${num(load.monotony)}")
            }
            when (today.phase) {
                RecoveryPhase.NO_DATA, RecoveryPhase.LEARNING ->
                    add("Indice Carico e recupero: ancora in apprendimento (meno di 14 giorni di dati)")
                else -> {
                    val score = today.smoothedScore ?: today.score
                    add(
                        "Indice Carico e recupero (0 = nessun segno di fatica, 100 = molti): ${score ?: "n.d."}, " +
                            "semaforo ${light(today.light)}, livello di allerta ${today.level}" +
                            if (today.phase == RecoveryPhase.PROVISIONAL) " (provvisorio)" else "",
                    )
                    add("Lettura: ${pattern(today.pattern)}")
                }
            }
            if (report.runLoadJump) add("Il carico di corsa di questa settimana è molto sopra le quattro precedenti")
            report.latestWellness?.let { w ->
                val bits = buildList {
                    w.hrvRmssd?.let { add("HRV ${it.roundToInt()} ms") }
                    report.hrvBand.lastOrNull()?.let { b ->
                        if (b.low != null && b.high != null) add("normale ${b.low.roundToInt()}–${b.high.roundToInt()}")
                    }
                    w.restingHr?.let { add("FC a riposo $it bpm") }
                    w.sleepSec?.let { add("sonno ${Formatters.duration(it)}") }
                    w.sleepScore?.let { add("punteggio sonno $it") }
                }
                if (bits.isNotEmpty()) add("Ultimi dati del mattino (${w.date}): ${bits.joinToString(", ")}")
            }
        }
    }

    private fun formZone(zone: FormZone) = when (zone) {
        FormZone.DETRAINING -> "riposo eccessivo, la forma cala"
        FormZone.FRESH -> "fresco"
        FormZone.NEUTRAL -> "neutra"
        FormZone.PRODUCTIVE -> "carico produttivo"
        FormZone.OVERLOAD -> "sovraccarico"
    }

    private fun light(light: RecoveryLight?) = when (light) {
        RecoveryLight.GREEN -> "verde"
        RecoveryLight.YELLOW -> "giallo"
        RecoveryLight.ORANGE -> "arancione"
        RecoveryLight.RED -> "rosso"
        null -> "n.d."
    }

    private fun pattern(p: LoadResponsePattern) = when (p) {
        LoadResponsePattern.BASELINE -> "nella norma"
        LoadResponsePattern.ABSORBING -> "carico alto ma il corpo lo assorbe bene"
        LoadResponsePattern.LOAD_FATIGUE -> "fatica spiegata dal carico recente"
        LoadResponsePattern.UNEXPLAINED -> "segni di fatica che il carico non spiega (stress, sonno, malattia?)"
        LoadResponsePattern.PERSISTING_AFTER_TAPER -> "ancora affaticato dopo una settimana di scarico"
        LoadResponsePattern.ATYPICAL_HIGH_HRV -> "HRV insolitamente alta con altri segni di fatica"
        LoadResponsePattern.ILLNESS -> "si è dichiarato malato"
    }

    fun sportName(sport: Sport?): String = when (sport) {
        Sport.SWIM -> "nuoto"
        Sport.BIKE -> "bici"
        Sport.RUN -> "corsa"
        Sport.OTHER -> "altro"
        null -> "riposo"
    }

    fun dayLabel(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, it)

    private fun num(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.ITALY, value)

    private fun signed(value: Double): String = (if (value >= 0) "+" else "") + num((value * 10).roundToInt() / 10.0)
}
