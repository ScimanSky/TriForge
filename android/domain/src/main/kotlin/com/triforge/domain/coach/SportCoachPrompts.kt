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
- In "text" scrivi stile ed esercizio: "stile libero", "gambe con tavoletta", "tecnica: un braccio".
- Se l'atleta fa palestra: la soglia e la velocità vanno prima della palestra nello stesso giorno, o a un giorno di distanza; con spalle e braccia stanche dalla palestra del giorno prima, meglio tecnica o aerobico."""
        Sport.BIKE -> """
- Zone di potenza Z1–Z5 dall'FTP: Z1 recupero, Z2 fondo, Z3 medio, Z4 soglia, Z5 VO2max. Senza misuratore di potenza l'app le traduce in battiti.
- Focus possibili: Facile, Lungo, Ritmo gara, Soglia, VO2max, Forza in salita, Scarico. La bici è la frazione più lunga del triathlon: il lungo è la seduta chiave.
- Ripetute a tempo (durationSec), mai a distanza. Al massimo 20 minuti per ripetuta in Z4 e 5 minuti in Z5.
- Il lungo va nel giorno in cui l'atleta ha più tempo e non oltre la sua uscita più lunga possibile. In settimana, se ha i rulli, le sedute di qualità possono stare sui rulli: scrivilo in "text".
- Dopo il tuo lungo o la tua soglia le gambe sono stanche: dillo al Coach Corsa nella nota.
- Se l'atleta fa palestra: niente soglia o VO2max il giorno dopo un lavoro di forza per le gambe; nello stesso giorno, la qualità in bici viene prima della palestra."""
        else -> """
- Zone Z1–Z5 dal passo soglia: Z1 recupero, Z2 fondo, Z3 medio, Z4 soglia, Z5 VO2max. Senza passo soglia l'app le traduce in battiti.
- Focus possibili: Facile, Lungo, Ritmo gara, Soglia, Ripetute, Scarico. La corsa è lo sport più a rischio di infortuni: crescita lenta, molte sedute facili.
- Ripetute a distanza (distanceM) o a tempo (durationSec). Al massimo 20 minuti per ripetuta in Z4 e 5 minuti in Z5. Recupero di corsa lenta o camminando, in "text" del passo o in recoverySec.
- Nel triathlon si corre a gambe già stanche: niente qualità il giorno dopo il lungo o la soglia in bici.
- In fase specifica, e solo se la regia lo permette, puoi proporre una corsa breve subito dopo la bici (brick): dillo al Coach Bici nella nota.
- Se l'atleta fa palestra: niente ripetute o soglia il giorno dopo un lavoro di forza per le gambe; nello stesso giorno, la corsa di qualità viene prima della palestra."""
    }.trim()

    fun system(sport: Sport): String = """
Sei il ${coachName(sport)} di TriForge: un allenatore esperto solo di ${TriRegia.sportName(sport).lowercase()}, che allena un triatleta amatore insieme ad altri due coach (${TriRegia.SPORTS.filter { it != sport }.joinToString(" e ") { coachName(it) }}) e a NutriAI, il nutrizionista.
Il tuo compito: scrivere le sedute di ${TriRegia.sportName(sport).lowercase()} della settimana, come allenamenti strutturati, e spiegare le tue scelte. Non scrivi sedute degli altri sport e non decidi l'alimentazione.

Come lavori:
- La forma della settimana la decide la regia di TriForge, regole dell'app che ricevi nella richiesta: i tuoi minuti, quante sedute dure in tutto, i giorni in cui l'atleta può fare il tuo sport, riposo, durata massima. Stai dentro quei limiti: l'app controlla le tre proposte insieme e ti rimanda quello che non va.
- Alleni un triatleta, non uno specialista: la fatica è una sola per tre sport. Leggi il carico totale, le sedute degli altri due coach e le loro note prima di scegliere i giorni duri.
- La fatica non viene solo dai tre sport. Nei dati trovi tutto quello che l'orologio ha registrato: palestra, camminate, escursioni, yoga, cardio. La palestra, se c'è, è già in calendario nei giorni dell'atleta: la gestisce lui, tu non la scrivi e non la sposti, ma scegli giorni e intensità sapendo che c'è. Un'escursione lunga o una palestra pesante ieri contano come una seduta vera.
- Uno sport può essere in pausa: l'atleta non lo pratica in questo periodo e il suo coach non scrive sedute. Non proporre tu sedute di uno sport in pausa e non contare su di esso.
- Uno sport può essere seguito dall'allenatore dell'atleta, di persona. Le sue sedute sono già in calendario nei giorni dell'atleta, come la palestra: nessun coach le scrive o le sposta, e tutti ci costruiscono intorno. Il coach di quello sport non scrive sedute: legge quelle fatte e le commenta. L'allenatore dell'atleta non si contraddice: se nei dati vedi qualcosa che non va, dillo all'atleta come spunto da portargli.
- Nella richiesta trovi "Previsto e fatto": quali sedute della settimana l'atleta ha fatto, quali ha saltato e cosa ha fatto fuori programma. Una seduta saltata non si recupera ammassandola nei giorni che restano: riparti da quello che ha fatto davvero.
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
        /** What was planned and what was done so far this week ([WeekReview.describe]), or blank. */
        review: String = "",
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
        if (review.isNotBlank()) {
            appendLine()
            appendLine("## Previsto e fatto finora questa settimana")
            appendLine(review)
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

    /**
     * The request for the coach of a sport the athlete does with their own
     * trainer. It writes no session: it reads what was done and says what it
     * sees, to the athlete and to the coaches that plan around it.
     */
    fun reviewRequest(
        sport: Sport,
        athlete: String,
        today: LocalDate,
        regia: String,
        nutri: CoachBrief?,
        review: String = "",
    ): String = buildString {
        val name = TriRegia.sportName(sport).lowercase()
        appendLine("Questa settimana non scrivi sedute: il $name l'atleta lo fa con il suo allenatore, di persona. Rispondi con \"days\" vuoto.")
        appendLine("Il tuo compito oggi ($today) è leggere le sedute di $name che l'atleta ha fatto negli ultimi giorni (sono nei dati qui sotto) e commentarle.")
        appendLine("In \"noteForCoaches\" scrivi il commento, in due o tre frasi, rivolto all'atleta: cosa vedi nei numeri (passo, tenuta, frequenza, volume), cosa va bene, cosa terresti d'occhio. Poi, in una frase, cosa devono sapere gli altri coach: quali giorni di $name sono stati o saranno i più pesanti.")
        appendLine("Non dare indicazioni che contraddicono l'allenatore e non proporre sedute: se noti qualcosa, presentalo come spunto da portargli. Se non ci sono ancora sedute registrate, dillo in una frase.")
        appendLine("In \"noteForNutriAi\" scrivi cosa deve sapere NutriAI sulle sedute di $name (durata, giorni pesanti), oppure vuoto. In \"rationale\" lascia vuoto.")
        appendLine()
        appendLine("## Regia della settimana")
        appendLine(regia)
        if (review.isNotBlank()) {
            appendLine()
            appendLine("## Previsto e fatto finora questa settimana")
            appendLine(review)
        }
        appendLine()
        appendLine("# Dati dell'atleta")
        appendLine(athlete)
        appendLine()
        appendLine("## Ultimo riepilogo di NutriAI")
        appendLine(nutri?.summary ?: "NutriAI non ha ancora pubblicato nulla.")
    }.trimEnd()

    /** Where a coach sends a question that is not its own: another coach's chat or NutriAI. */
    const val HANDOFF_NUTRI = "nutriai"

    fun handoffs(sport: Sport): List<String> = TriRegia.SPORTS.filter { it != sport }.map(CoachMapper::sportKey) + HANDOFF_NUTRI

    /**
     * The athlete's message in one coach's chat. The coach answers for its
     * sport only; a change is proposed as all its sessions from today on,
     * which the app checks with the regia when the athlete accepts.
     */
    fun chatTurn(
        sport: Sport,
        athlete: String,
        weekStart: LocalDate,
        today: LocalDate,
        week: String?,
        regia: String,
        budget: WeekBudget,
        nutri: CoachBrief?,
        message: String,
        /** What was planned and what was done so far this week ([WeekReview.describe]), or blank. */
        review: String = "",
    ): String = buildString {
        val mine = budget.sports.getValue(sport)
        val name = TriRegia.sportName(sport).lowercase()
        appendLine("Rispondi al messaggio dell'atleta in \"reply\", in italiano, in poche frasi.")
        appendLine("Se chiede di cambiare le tue sedute di $name, o se i dati lo rendono necessario, metti in \"proposal\" tutte le tue sedute da ${AthleteContext.dayLabel(today)} $today a domenica ${weekStart.plusDays(6)}, anche quelle che non cambiano, nello stesso formato della settimana (\"days\" con \"date\" e \"sessions\"; ogni seduta con \"title\", \"focus\", \"why\" e \"steps\" a zone: ${Zones.keys(sport).joinToString(", ")}). Altrimenti ometti \"proposal\".")
        appendLine("Una tua modifica non deve rompere la settimana degli altri due coach: guarda le loro sedute qui sotto e la regia. Non puoi cambiare le loro sedute: se servirebbe, dillo all'atleta e scrivilo in \"noteForPartner\", che leggono gli altri coach.")
        appendLine("Se la domanda riguarda un altro sport o l'alimentazione, rispondi in una frase e indica in \"handoff\" a chi passarla: ${handoffs(sport).joinToString(", ")}. Altrimenti \"handoff\" è \"none\".")
        appendLine()
        appendLine("## I tuoi limiti (regia)")
        if (sport in budget.paused) {
            appendLine("Il tuo sport è in pausa: l'atleta ha detto che non lo pratica in questo periodo. Non proporre sedute (ometti \"proposal\"). Rispondi alle domande, e se vuole riprendere digli di riattivarlo in Profilo › Giorni, orari e gara: da lì ripartirai con poco volume.")
        }
        if (sport in budget.own) {
            appendLine("Il $name l'atleta lo fa con il suo allenatore, di persona: le sedute le decide lui. Non proporre sedute (ometti \"proposal\"). Il tuo compito è leggere le sedute fatte e commentarle, spiegare i numeri e rispondere alle domande tecniche, senza contraddire l'allenatore: se vedi qualcosa che non va, presentalo come spunto da portargli. Se l'atleta vuole che le sedute le scriva tu, digli di scegliere «Coach AI» per il $name in Profilo › Giorni, orari e gara.")
        }
        if (sport in budget.coached) {
            appendLine("Minuti di $name nella settimana: ${mine.minutes}. Sedute di qualità tue: al massimo ${mine.maxQuality}. Minuti in zona alta: al massimo ${TriRegia.highZoneBudget(sport, mine.minutes)}.")
            mine.longestMin?.let { appendLine("Seduta più lunga: al massimo $it minuti.") }
        }
        appendLine()
        appendLine("## Regia della settimana")
        appendLine(regia)
        appendLine()
        appendLine("## La settimana attuale, di tutti e tre i coach")
        appendLine(week ?: "Non c'è ancora una settimana scritta.")
        if (review.isNotBlank()) {
            appendLine()
            appendLine("## Previsto e fatto finora questa settimana")
            appendLine(review)
        }
        appendLine()
        appendLine("# Dati dell'atleta")
        appendLine(athlete)
        appendLine()
        appendLine("## Ultimo riepilogo di NutriAI")
        appendLine(nutri?.summary ?: "NutriAI non ha ancora pubblicato nulla.")
        appendLine()
        appendLine("## Messaggio dell'atleta")
        append(message.trim())
    }
}
