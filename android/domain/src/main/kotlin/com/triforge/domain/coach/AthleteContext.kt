package com.triforge.domain.coach

import com.triforge.domain.Activity
import com.triforge.domain.AthleteProfile
import com.triforge.domain.CheckIn
import com.triforge.domain.CssCheck
import com.triforge.domain.CssSource
import com.triforge.domain.Formatters
import com.triforge.domain.FormZone
import com.triforge.domain.LoadResponsePattern
import com.triforge.domain.OtherKind
import com.triforge.domain.RaceGoal
import com.triforge.domain.RecoveryLight
import com.triforge.domain.RecoveryPhase
import com.triforge.domain.RecoveryReport
import com.triforge.domain.SessionLoad
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import java.time.LocalDate
import java.time.LocalTime
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
    /** Days and hours for each sport, for the regia. */
    val availability: TrainingAvailability = TrainingAvailability(),
    /** What the swims say of the swim threshold, when it does not fit them ([com.triforge.domain.SwimThreshold]). */
    val cssCheck: CssCheck? = null,
) {
    /** Age in years this year, from the year of birth of the account. */
    val age: Int? get() = birthYear?.let { today.year - it }?.takeIf { it in 10..110 }
}

object AthleteContext {

    private val it = Locale.ITALIAN

    /** Detailed sessions of the last [RECENT_DAYS] days; older ones only as weekly totals. */
    const val RECENT_DAYS = 14L
    const val TREND_WEEKS = 8

    /**
     * [now] is the time on the athlete's phone: late in the evening the
     * coaches are told that the day is over, so nothing is proposed for it.
     */
    fun describe(data: AthleteData, now: LocalTime? = null): String = buildString {
        append("Oggi è ${dayLabel(data.today)} ${data.today}")
        if (now == null) {
            appendLine(".")
        } else {
            append(", sono le ${"%02d:%02d".format(now.hour, now.minute)}.")
            appendLine(if (now >= CoachTable.DAY_OVER) " La giornata è finita: non proporre e non spostare nulla su oggi, si riparte da domani." else "")
        }
        appendLine()
        appendLine("## Profilo")
        profile(data.profile, data.today, data.age).forEach { appendLine("- $it") }
        habits(data.availability).forEach { appendLine("- $it") }
        data.cssCheck?.let { check ->
            appendLine()
            appendLine("## Soglia del nuoto da verificare")
            appendLine(cssWarning(check))
        }
        val zones = zones(data.profile)
        if (zones.isNotEmpty()) {
            appendLine()
            appendLine("## Zone dell'atleta (calcolate dall'app dalle sue soglie)")
            zones.forEach { appendLine("- $it") }
            appendLine("Quando citi un ritmo, una potenza o dei battiti usa solo questi valori: sono gli stessi che l'atleta legge nelle sedute.")
        }
        appendLine()
        appendLine("## Volume settimanale (ultime $TREND_WEEKS settimane, dalla più vecchia)")
        weeks(data).forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Allenamenti degli ultimi $RECENT_DAYS giorni (tutto quello che l'orologio ha registrato: sport, palestra e altre attività)")
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

    /** The zones of each sport with a threshold, in the athlete's own numbers: the only paces a coach may quote. */
    fun zones(profile: AthleteProfile): List<String> = TriRegia.SPORTS.mapNotNull { sport ->
        val targets = Zones.ALL.map { zone -> zone to Zones.target(sport, zone, profile) }.filter { it.second.isNotEmpty() }
        if (targets.isEmpty()) null
        else sportName(sport).replaceFirstChar { c -> c.uppercase() } + ": " + targets.joinToString("; ") { (zone, target) -> "$zone ${Zones.label(zone).lowercase()} $target" }
    }

    /** What the coaches must know when the swim threshold does not fit the swims. */
    fun cssWarning(check: CssCheck): String {
        fun pace(seconds: Int) = Formatters.minutesAndSeconds(seconds.toDouble())
        val source = if (check.suggestion.source == CssSource.BEST_EFFORTS) "dai suoi migliori tempi su due distanze" else "stima prudente dal passo a cui nuota di solito"
        return buildString {
            append("La CSS del profilo (${pace(check.currentSecPer100)}/100 m) è con ogni probabilità troppo lenta: nelle ultime tre settimane ")
            append("${(check.highShare * 100).roundToInt()}% dei metri in vasca risulta a passo soglia o più veloce (${check.sessions} sedute, passo abituale ${pace(check.usualPaceSecPer100)}/100 m")
            check.avgHr?.let { append(", FC media $it") }
            append("), cosa che con una soglia giusta non succede. ")
            append("Finché non la verifica, zone, carico (TSS) e forma del nuoto sono gonfiati e i ritmi delle sedute di nuoto troppo lenti: non leggere quelle nuotate come lavori di soglia e non dare per certo un sovraccarico che viene dal nuoto. ")
            append("L'app propone all'atleta ${pace(check.suggestion.paceSecPer100)}/100 m ($source) oppure un test: 400 m e 200 m al massimo, con recupero completo tra i due. ")
            append("La soglia non la cambi tu: se se ne parla, digli cosa vedi nei dati e invitalo al test.")
        }
    }

    /** What the athlete does besides the plan, from board 8: the gym, the sports done with a trainer, the sports on hold. */
    private fun habits(availability: TrainingAvailability): List<String> = buildList {
        val gym = availability.gym
        if (gym.enabled) {
            val days = availability.gymDays.sorted().joinToString(", ") { TriRegia.dayName(it) }
            add(
                "Palestra (forza, programma suo): " + (if (days.isEmpty()) "giorni variabili" else days) +
                    (gym.time?.let { " alle $it" } ?: "") + ", circa ${gym.minutes} minuti a seduta",
            )
        }
        availability.ownSports.forEach { sport ->
            val days = availability.ownDays(sport).sorted().joinToString(", ") { TriRegia.dayName(it) }
            add(
                "${sportName(sport).replaceFirstChar { c -> c.uppercase() }}: lo fa con il suo allenatore, di persona (" +
                    (if (days.isEmpty()) "giorni variabili" else days) + (availability.slot(sport)?.time?.let { " alle $it" } ?: "") +
                    "). Le sedute le decide l'allenatore: nei dati trovi quelle fatte",
            )
        }
        if (availability.paused.isNotEmpty()) {
            add("Sport che non pratica in questo periodo: ${availability.paused.joinToString(", ") { sportName(it) }}")
        }
    }

    private fun weeks(data: AthleteData): List<String> =
        TrainingStats.weeklyTrend(data.activities, TREND_WEEKS, data.today, data.zone).map { week ->
            val parts = TrainingStats.allSports.mapNotNull { sport ->
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
            activityName(a),
            Formatters.duration(a.durationSec),
        )
        if (a.sport == Sport.STRENGTH) {
            a.sets?.let { parts += "$it serie" }
            a.liftedKg?.let { parts += "${it.roundToInt()} kg sollevati in tutto" }
        }
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
        Sport.STRENGTH -> "palestra"
        Sport.OTHER -> "altro"
        null -> "riposo"
    }

    /** What a recorded session was, in a word: the sport, or the kind of an activity outside the sports. */
    fun activityName(activity: Activity): String =
        if (activity.sport == Sport.OTHER) kindName(activity.kind) else sportName(activity.sport)

    fun kindName(kind: OtherKind): String = when (kind) {
        OtherKind.WALK -> "camminata"
        OtherKind.HIKE -> "escursione"
        OtherKind.MOBILITY -> "yoga e mobilità"
        OtherKind.CARDIO -> "cardio"
        OtherKind.GENERIC -> "altra attività"
    }

    fun dayLabel(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, it)

    private fun num(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.ITALY, value)

    private fun signed(value: Double): String = (if (value >= 0) "+" else "") + num((value * 10).roundToInt() / 10.0)
}
