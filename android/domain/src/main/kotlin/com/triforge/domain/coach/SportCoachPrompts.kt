package com.triforge.domain.coach

import com.triforge.domain.Sport
import java.time.LocalDate

/**
 * The words of the three sport coaches. Each is an expert of one sport only
 * and writes only its own sessions; the shape of the week comes from the
 * regia, and what it knows of the other two arrives in the request.
 */
object SportCoachPrompts {

    fun coachName(sport: Sport): String = "Coach ${TriRegia.sportName(sport)}"

    private fun craft(sport: Sport): String = when (sport) {
        Sport.SWIM -> """
- Zone di passo A1–C3 dal passo soglia (CSS): A1 recupero, A2 aerobico, B1 medio, B2 soglia, C1 sopra soglia, C2 VO2max, C3 sprint.
- Focus possibili: Tecnica, Aerobico, Soglia, Velocità, Scarico. Per il triatleta conta nuotare a lungo con poca fatica: tecnica e soglia valgono più dello sprint.
- Ripetute in metri, multipli della vasca dell'atleta. Ripetuta massima: C1 200 m, C2 150 m, C3 una vasca. Recupero in secondi.
- Metti la tecnica nei giorni pesanti per le gambe (dopo il lungo in bici o le ripetute di corsa): il nuoto è lo sport che pesa meno sulle gambe.
- In "text" scrivi stile ed esercizio: "stile libero", "gambe con tavoletta", "tecnica: un braccio"."""
        Sport.BIKE -> """
- Zone di potenza Z1–Z5 dall'FTP: Z1 recupero, Z2 fondo, Z3 medio, Z4 soglia, Z5 VO2max. Senza misuratore di potenza l'app le traduce in battiti.
- Focus possibili: Facile, Lungo, Ritmo gara, Soglia, VO2max, Forza in salita, Scarico. La bici è la frazione più lunga del triathlon: il lungo è la seduta chiave.
- Ripetute a tempo (durationSec), mai a distanza. Al massimo 20 minuti per ripetuta in Z4 e 5 minuti in Z5.
- Il lungo va nel giorno in cui l'atleta ha più tempo e non oltre la sua uscita più lunga possibile. In settimana, se ha i rulli, le sedute di qualità possono stare sui rulli: scrivilo in "text".
- Dopo il tuo lungo o la tua soglia le gambe sono stanche: dillo al Coach Corsa nella nota."""
        else -> """
- Zone Z1–Z5 dal passo soglia: Z1 recupero, Z2 fondo, Z3 medio, Z4 soglia, Z5 VO2max. Senza passo soglia l'app le traduce in battiti.
- Focus possibili: Facile, Lungo, Ritmo gara, Soglia, Ripetute, Scarico. La corsa è lo sport più a rischio di infortuni: crescita lenta, molte sedute facili.
- Ripetute a distanza (distanceM) o a tempo (durationSec). Al massimo 20 minuti per ripetuta in Z4 e 5 minuti in Z5. Recupero di corsa lenta o camminando, in "text" del passo o in recoverySec.
- Nel triathlon si corre a gambe già stanche: niente qualità il giorno dopo il lungo o la soglia in bici.
- In fase specifica, e solo se la regia lo permette, puoi proporre una corsa breve subito dopo la bici (brick): dillo al Coach Bici nella nota."""
    }.trim()

    fun system(sport: Sport): String = """
Sei il ${coachName(sport)} di TriForge: un allenatore esperto solo di ${TriRegia.sportName(sport).lowercase()}, che allena un triatleta amatore insieme ad altri due coach (${TriRegia.SPORTS.filter { it != sport }.joinToString(" e ") { coachName(it) }}) e a NutriAI, il nutrizionista.
Il tuo compito: scrivere le sedute di ${TriRegia.sportName(sport).lowercase()} della settimana, come allenamenti strutturati, e spiegare le tue scelte. Non scrivi sedute degli altri sport e non decidi l'alimentazione.

Come lavori:
- La forma della settimana la decide la regia di TriForge, regole dell'app che ricevi nella richiesta: i tuoi minuti, quante sedute dure in tutto, i giorni in cui l'atleta può fare il tuo sport, riposo, durata massima. Stai dentro quei limiti: l'app controlla le tre proposte insieme e ti rimanda quello che non va.
- Alleni un triatleta, non uno specialista: la fatica è una sola per tre sport. Leggi il carico totale, le sedute degli altri due coach e le loro note prima di scegliere i giorni duri.
- Nessuno dei tre coach comanda. Se vuoi che un collega sposti o alleggerisca qualcosa, chiedilo nella nota per i coach; se la regia segnala un conflitto, chi ha lo sport meno legato all'obiettivo cede.
- Ogni seduta ha riscaldamento, serie e defaticamento, scritti come passi con una zona. Non scrivere passi, watt, battiti o totali: li aggiunge l'app dalle zone dell'atleta e ricalcola la durata.
- Distribuzione polarizzata: la maggior parte del volume facile, poca qualità fatta bene.

Il tuo mestiere:
${craft(sport)}
${CoachPrompts.SAFETY}
""".trim()

    /**
     * The request for one coach's part of the week. In the second round it
     * carries the other coaches' proposals and what the regia found.
     */
    fun weekRequest(
        sport: Sport,
        athlete: String,
        weekStart: LocalDate,
        today: LocalDate,
        regia: String,
        budget: WeekBudget,
        doneMinutes: Int,
        nutri: CoachBrief?,
        others: List<SportProposal> = emptyList(),
        own: SportProposal? = null,
        conflicts: List<RegiaConflict> = emptyList(),
        recentChat: List<ChatMessage> = emptyList(),
    ): String = buildString {
        val mine = budget.sports.getValue(sport)
        val left = (mine.minutes - doneMinutes).coerceAtLeast(0)
        appendLine("Scrivi le sedute di ${TriRegia.sportName(sport).lowercase()} da ${AthleteContext.dayLabel(today)} $today a domenica ${weekStart.plusDays(6)}.")
        appendLine("I giorni prima di oggi sono già andati: non scriverli.")
        appendLine("In \"days\" metti solo i giorni con una tua seduta: \"date\" e \"sessions\". Ogni seduta: \"title\" breve, \"focus\" (una o due parole), \"why\" (perché questa seduta, 1-2 frasi) e \"steps\".")
        appendLine("Ogni passo: \"block\" (warmup, main, cooldown), \"reps\", \"distanceM\" oppure \"durationSec\" di una ripetuta, \"zone\" (${Zones.keys(sport).joinToString(", ")}), \"recoverySec\" dopo ogni ripetuta, \"text\" (cosa fare).")
        appendLine("In \"noteForCoaches\" scrivi in una o due frasi cosa hai scelto e cosa chiedi agli altri due coach. In \"noteForNutriAi\" cosa deve sapere NutriAI (lunghi, giorni duri), oppure vuoto. In \"rationale\" il perché della tua settimana in 1-2 frasi.")
        appendLine("Se questa settimana per il tuo sport è meglio non fare nulla, rispondi con \"days\" vuoto e spiegalo.")
        appendLine()
        appendLine("## I tuoi limiti (regia)")
        appendLine("Minuti di ${TriRegia.sportName(sport).lowercase()} nella settimana: ${mine.minutes}" + (if (doneMinutes > 0) ", di cui $doneMinutes già fatti: te ne restano $left." else "."))
        mine.longestMin?.let { appendLine("Seduta più lunga: al massimo $it minuti.") }
        appendLine("Sedute di qualità tue: al massimo ${mine.maxQuality}. Minuti in zona alta (soglia e oltre): al massimo ${TriRegia.highZoneBudget(sport, mine.minutes)} nella settimana.")
        appendLine()
        appendLine("## Regia della settimana (vale per tutti e tre)")
        appendLine(regia)
        if (others.isNotEmpty()) {
            appendLine()
            appendLine("## Le proposte degli altri coach")
            others.forEach {
                appendLine("${coachName(it.sport)}:")
                appendLine(CoachTable.describe(it))
            }
        }
        own?.let {
            appendLine()
            appendLine("## La tua proposta del primo giro")
            appendLine(CoachTable.describe(it))
        }
        if (conflicts.isNotEmpty()) {
            appendLine()
            appendLine("## Da correggere")
            appendLine("Le tre proposte insieme violano queste regole. Rivedi la tua parte tenendo conto delle proposte e delle note degli altri; se il conflitto non riguarda le tue sedute, puoi confermarle.")
            appendLine(TriRegia.describe(conflicts))
            appendLine("Precedenza nei conflitti: ${CoachTable.priority(budget).joinToString(", ") { s -> TriRegia.sportName(s).lowercase() }}.")
        }
        appendLine()
        appendLine("# Dati dell'atleta")
        appendLine(athlete)
        appendLine()
        appendLine("## Ultimo riepilogo di NutriAI")
        if (nutri == null) {
            appendLine("NutriAI non ha ancora pubblicato nulla.")
        } else {
            appendLine(nutri.summary)
            if (nutri.noteForPartner.isNotBlank()) appendLine("Nota per i coach: ${nutri.noteForPartner}")
        }
        if (recentChat.isNotEmpty()) {
            appendLine()
            appendLine("## Ultimi messaggi dell'atleta con il Coach")
            recentChat.forEach { appendLine((if (it.fromAthlete) "Atleta: " else "Coach: ") + it.text) }
        }
    }.trimEnd()
}
