package com.triforge.domain.coach

import com.triforge.domain.Sport
import java.time.LocalTime
import kotlin.math.roundToInt

/** What to take around one effort: grams before, per hour during, and after. */
data class EffortFuel(
    /** The sessions of the effort: one, or bike and run of a brick. */
    val sessions: List<PlannedSession>,
    val minutes: Int,
    /** Carbohydrates 1 to 3 hours before, or null when a normal meal is enough. */
    val beforeCarbsG: Int?,
    /** Carbohydrates per hour during, or null for water only. */
    val duringCarbsPerHour: IntRange?,
    /** Carbohydrates and proteins within an hour after, or null when the next meal is enough. */
    val afterCarbsG: Int?,
    val afterProteinG: Int?,
    /** Salts in the bottle: long efforts. */
    val sodium: Boolean,
)

/**
 * Fuelling around the sessions, computed by the app from the structured
 * week: NutriAI is given the grams and turns them into food the athlete
 * eats. A triathlete's day can hold two sports, or bike and run back to
 * back: a brick is fuelled as one effort, and between two separate sessions
 * the first recovery is the second session's preparation.
 */
object NutriFueling {

    private fun isHard(session: PlannedSession) = TriRegia.isHard(session)

    /** The day's efforts in order: a bike followed by a run is one effort. */
    fun efforts(sessions: List<PlannedSession>): List<List<PlannedSession>> {
        val real = sessions.filter { it.sport != null && it.durationMin > 0 }
        val efforts = mutableListOf<List<PlannedSession>>()
        var index = 0
        while (index < real.size) {
            val next = real.getOrNull(index + 1)
            if (real[index].sport == Sport.BIKE && next?.sport == Sport.RUN) {
                efforts += listOf(real[index], next)
                index += 2
            } else {
                efforts += listOf(real[index])
                index += 1
            }
        }
        return efforts
    }

    /** Carbohydrates per hour for an effort of [minutes]: none under an hour, more the longer it lasts. */
    fun duringCarbs(minutes: Int, hard: Boolean, swimOnly: Boolean): IntRange? = when {
        // In the pool there is a bottle at the wall, not gels: only long swims take carbohydrates.
        swimOnly && minutes < 90 -> null
        minutes < 60 -> null
        minutes < 90 -> if (hard) 30..60 else null
        minutes <= 150 -> 30..60
        else -> 60..90
    }

    fun plan(sessions: List<PlannedSession>, weightKg: Double): List<EffortFuel> {
        val efforts = efforts(sessions)
        return efforts.mapIndexed { position, effort ->
            val minutes = effort.sumOf { it.durationMin }
            val hard = effort.any(::isHard)
            val demanding = hard || minutes >= 90
            val another = position < efforts.lastIndex
            // After lifting the muscles ask for proteins even when the session was short.
            val strength = effort.any { it.sport == Sport.STRENGTH }
            EffortFuel(
                sessions = effort,
                minutes = minutes,
                beforeCarbsG = if (demanding) weightKg.roundToInt() else null,
                duringCarbsPerHour = duringCarbs(minutes, hard, effort.all { it.sport == Sport.SWIM }),
                // With another session later the recovery cannot wait for the next meal.
                afterCarbsG = if (demanding || another) weightKg.roundToInt() else null,
                afterProteinG = if (demanding || another || strength) (0.3 * weightKg).roundToInt() else null,
                sodium = minutes >= 90,
            )
        }
    }

    private fun name(effort: EffortFuel): String = effort.sessions.joinToString(" + ") { session ->
        "${TriRegia.sportName(session.sport!!).lowercase()} ${session.focus.ifBlank { session.title }} (${session.durationMin} min)"
    }

    /** «alle 13:20, fino alle 14:20 circa»: when the effort is, from the usual time of its first sport. */
    private fun clock(effort: EffortFuel, times: Map<Sport, LocalTime>): String? {
        val start = effort.sessions.firstOrNull()?.sport?.let(times::get) ?: return null
        val end = start.plusMinutes(effort.minutes.toLong())
        fun text(time: LocalTime) = "%d:%02d".format(time.hour, time.minute)
        return "alle ${text(start)}, fino alle ${text(end)} circa"
    }

    /**
     * The lines of the request that give NutriAI the fuelling to write
     * about. [times] is when the athlete usually does each sport, from the
     * profile: with it «prima» and «dopo» are hours of the day, and a meal
     * is not called the recovery of a session that comes after it.
     */
    fun describe(
        sessions: List<PlannedSession>,
        weightKg: Double,
        context: DayContext = DayContext(),
        times: Map<Sport, LocalTime> = emptyMap(),
    ): String = buildString {
        val plan = plan(sessions, weightKg)
        if (plan.isEmpty()) {
            appendLine("Nessuna seduta in questo giorno: \"fueling\" resta vuoto.")
        } else {
            appendLine("Numeri calcolati dall'app, da usare nei passi di \"fueling\" (tu scegli i cibi, adatti al profilo alimentare):")
        }
        plan.forEach { effort ->
            val brick = effort.sessions.size > 1
            val clock = clock(effort, times)?.let { " $it (orario abituale dell'atleta)" }.orEmpty()
            appendLine("- ${name(effort)}$clock" + if (brick) ": bici e corsa di seguito, un solo sforzo di ${effort.minutes} minuti." else ":")
            appendLine("  Prima: " + (effort.beforeCarbsG?.let { "circa $it g di carboidrati 1-3 ore prima, pochi grassi e poche fibre." } ?: "basta il pasto normale; uno spuntino leggero se sono passate più di 3 ore."))
            appendLine(
                "  Durante: " + (effort.duringCarbsPerHour?.let { "${it.first}-${it.last} g di carboidrati l'ora" + (if (it.last > 60) ", da fonti miste (glucosio e fruttosio)" else "") + ", 400-800 ml di liquidi l'ora" + (if (effort.sodium) " con sali." else ".") }
                    ?: "solo acqua.") +
                    if (brick) " Si mangia e si beve in bici, anche per la corsa: di corsa solo piccoli sorsi." else "",
            )
            appendLine(
                "  Dopo: " + (
                    effort.afterCarbsG?.let { "entro un'ora circa $it g di carboidrati e ${effort.afterProteinG} g di proteine." }
                        ?: effort.afterProteinG?.let { "entro un'ora o due circa $it g di proteine, nel pasto successivo o in uno spuntino." }
                        ?: "il pasto successivo basta."
                    ),
            )
        }
        if (plan.any { clock(it, times) != null }) {
            appendLine("Usa gli orari: in \"fueling\" scrivi l'ora di ogni passo, e il «dopo» è il primo pasto o spuntino che viene dopo la fine della seduta, non uno che viene prima. Una seduta all'ora di pranzo o di cena sposta quel pasto a dopo, con uno spuntino leggero un'ora e mezza prima.")
        } else if (plan.isNotEmpty()) {
            appendLine("L'atleta non ha indicato a che ora si allena: non dare per scontato quale pasto viene dopo la seduta, scrivi «entro un'ora dalla fine».")
        }
        if (plan.size > 1) appendLine("Più sedute nello stesso giorno: il recupero dopo la prima è la preparazione della seconda, non va saltato.")
        val next = NutriEnergy.dayType(context.tomorrow)
        when {
            context.daysToRace == 0 -> appendLine("È il giorno della gara: colazione collaudata 3 ore prima, niente cibi nuovi, e il rifornimento provato in allenamento.")
            context.daysToRace == 1 -> appendLine("Il giorno dopo c'è la gara: cena presto, ricca di carboidrati, povera di fibre e grassi, niente cibi nuovi.")
            next == DayType.HEAVY -> appendLine("Il giorno dopo è pesante: la cena di questo giorno lo prepara, con più carboidrati.")
        }
    }.trimEnd()
}
