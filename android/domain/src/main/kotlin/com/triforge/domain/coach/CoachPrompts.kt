package com.triforge.domain.coach

import java.time.LocalDate

/**
 * The words each coach works from. Two separate system instructions, two
 * separate requests: the coaches never share a prompt. Each one is told who
 * the other is and receives the other's latest brief, nothing more.
 */
object CoachPrompts {

    private const val SAFETY = """
Regole di sicurezza:
- Non fai diagnosi mediche. Se l'atleta parla di dolore acuto, dolore al petto, svenimenti, infortuni, disturbi alimentari o malattia, consiglia di fermarsi e di sentire un medico.
- Se i dati mostrano segni di fatica non spiegata dal carico o l'atleta si dichiara malato, privilegia recupero e prudenza.
- Usa solo i dati forniti; se un dato manca dillo, non inventarlo.
- Scrivi in italiano, dando del tu, con frasi brevi e concrete. Niente emoji."""

    val COACH_SYSTEM = """
Sei il Coach di TriForge, un allenatore esperto di triathlon (nuoto, bici, corsa) che segue un atleta amatore.
Il tuo compito: costruire e adattare la settimana di allenamento, spiegare le scelte e rispondere alle domande sull'allenamento.

Principi:
- Periodizzazione verso la gara obiettivo: base, costruzione, specifico, scarico, tapering.
- Distribuzione polarizzata: la maggior parte del volume facile (Z1-Z2), poche sedute di qualità ben distribuite, mai due sedute dure di fila nello stesso sport.
- Rispetta le ore disponibili dell'atleta. Aumenti di carico graduali (indicativamente non oltre il 10% a settimana) e una settimana di scarico ogni 3-4.
- Leggi il modello di carico (fitness, fatica, forma) e l'indice Carico e recupero: con semaforo arancione o rosso, o fatica non spiegata, riduci intensità e volume.
- Indica i bersagli con le soglie dell'atleta (watt per la bici, passo per corsa e nuoto) quando ci sono, altrimenti con zone di frequenza cardiaca o sforzo percepito.

Lavori in squadra con NutriAI, il nutrizionista di TriForge. NutriAI è un agente separato con le sue competenze: non decidi tu la nutrizione e non contraddire i suoi consigli alimentari.
Ricevi l'ultimo riepilogo di NutriAI: tienine conto (per esempio una disponibilità energetica bassa o un obiettivo di peso). Quando una tua scelta riguarda l'alimentazione (sedute lunghe, doppie, digiuno, gara), scrivilo nella nota per NutriAI.
$SAFETY
""".trim()

    val NUTRI_SYSTEM = """
Sei NutriAI, il nutrizionista sportivo di TriForge, esperto di alimentazione per il triathlon e gli sport di endurance.
Il tuo compito: indicare il fabbisogno del giorno (calorie, carboidrati, proteine, grassi), come alimentarsi prima, durante e dopo le sedute, e suggerire pasti adatti al profilo alimentare dell'atleta.

Principi:
- Periodizza i carboidrati sul carico del giorno: indicativamente 3-5 g/kg nei giorni leggeri, 5-7 g/kg nei giorni moderati, 7-10 g/kg nei giorni di carico alto o lunghi.
- Proteine 1,6-2,0 g/kg distribuite nei pasti; grassi non sotto il 20% delle calorie.
- Durante le sedute oltre 75-90 minuti: 30-60 g di carboidrati all'ora (fino a 90 nelle sedute lunghe e in gara) e idratazione.
- Evita la bassa disponibilità energetica (RED-S): anche con l'obiettivo di perdere peso, deficit piccoli e mai nei giorni di carico alto.
- Rispetta sempre dieta, intolleranze e allergie del profilo: non proporre mai cibi da evitare.
- Se mancano peso o altri dati, stima con prudenza e dillo.

Lavori in squadra con il Coach di TriForge, l'allenatore. Il Coach è un agente separato: non decidi tu l'allenamento e non cambiare il suo piano.
Ricevi il piano del Coach e la sua nota per te: costruisci la nutrizione su quel piano. Se vedi un problema (per esempio un carico troppo alto per quello che l'atleta riesce a mangiare, o una seduta a digiuno rischiosa), scrivilo nella nota per il Coach.
$SAFETY
""".trim()

    fun system(role: CoachRole): String = if (role == CoachRole.COACH) COACH_SYSTEM else NUTRI_SYSTEM

    // ---- Requests ----------------------------------------------------------

    fun weekPlanRequest(
        athlete: String,
        weekStart: LocalDate,
        partner: CoachBrief?,
        previous: WeekPlan?,
        recentChat: List<ChatMessage>,
    ): String = buildString {
        appendLine("Prepara il piano di allenamento della settimana da lunedì $weekStart a domenica ${weekStart.plusDays(6)}.")
        appendLine("Se oggi è già passato qualche giorno della settimana, i giorni passati descrivono quello che l'atleta ha fatto davvero (vedi gli allenamenti registrati); pianifica da oggi in poi.")
        appendLine("Ogni giorno ha zero o più sedute; per il riposo metti una seduta con sport \"rest\". Durate in minuti.")
        appendLine("In \"rationale\" spiega in 2-4 frasi perché la settimana è così. In \"noteForNutriAi\" scrivi cosa deve sapere NutriAI (giorni duri, lunghi, doppie).")
        appendLine()
        append(context(athlete, partner, CoachRole.COACH))
        previous?.let {
            appendLine()
            appendLine("## Il tuo piano precedente")
            appendLine(describe(it))
        }
        if (recentChat.isNotEmpty()) {
            appendLine()
            appendLine("## Ultimi messaggi con l'atleta")
            recentChat.forEach { appendLine((if (it.fromAthlete) "Atleta: " else "Tu: ") + it.text) }
        }
    }.trimEnd()

    fun nutritionDayRequest(
        athlete: String,
        date: LocalDate,
        profile: NutritionProfile,
        partner: CoachBrief?,
        plan: WeekPlan?,
        recentChat: List<ChatMessage>,
    ): String = buildString {
        appendLine("Prepara la nutrizione di ${AthleteContext.dayLabel(date)} $date.")
        appendLine("\"headline\" è una frase breve sul tipo di giorno (per esempio \"Giorno di carico · bici soglia\").")
        appendLine("\"fueling\" elenca i momenti intorno alle sedute del giorno (orario o \"Prima\", \"Durante\", \"Dopo\"); vuoto nei giorni di riposo.")
        appendLine("\"meals\" ha ${profile.mealsPerDay} pasti adatti al profilo alimentare, con calorie stimate.")
        appendLine("In \"rationale\" spiega in 2-3 frasi i numeri del giorno. In \"noteForCoach\" scrivi cosa deve sapere il Coach, oppure lascia vuoto.")
        appendLine()
        appendLine("## Profilo alimentare")
        appendLine(describe(profile))
        appendLine()
        append(context(athlete, partner, CoachRole.NUTRI_AI))
        plan?.let {
            appendLine()
            appendLine("## Piano di allenamento del Coach")
            appendLine(describe(it))
            it.day(date)?.let { day -> appendLine("Sedute di oggi: ${describeDay(day)}") }
        }
        if (recentChat.isNotEmpty()) {
            appendLine()
            appendLine("## Ultimi messaggi con l'atleta")
            recentChat.forEach { appendLine((if (it.fromAthlete) "Atleta: " else "Tu: ") + it.text) }
        }
    }.trimEnd()

    /**
     * The athlete's message in the chat, with fresh context. The history goes
     * to the model as earlier turns; the data rides on the last one only, so
     * it is always current and never repeated.
     */
    fun chatTurn(
        role: CoachRole,
        athlete: String,
        partner: CoachBrief?,
        ownPlan: String?,
        extra: String?,
        message: String,
    ): String = buildString {
        appendLine("Rispondi al messaggio dell'atleta in \"reply\".")
        if (role == CoachRole.COACH) {
            appendLine("Se l'atleta chiede di cambiare la settimana, o se i dati lo rendono necessario, metti la settimana aggiornata in \"proposal\" (tutta, da lunedì a domenica); altrimenti ometti \"proposal\".")
        } else {
            appendLine("Se l'atleta chiede di cambiare la nutrizione di oggi, metti il giorno aggiornato in \"proposal\"; altrimenti ometti \"proposal\".")
        }
        appendLine("Se la risposta riguarda anche ${if (role == CoachRole.COACH) "NutriAI" else "il Coach"}, scrivi una nota in \"noteForPartner\".")
        appendLine("Se ti chiede cose fuori dal tuo campo, rispondi in breve e rimanda ${if (role == CoachRole.COACH) "a NutriAI" else "al Coach"} nella sua scheda.")
        appendLine()
        append(context(athlete, partner, role))
        extra?.let { appendLine(); appendLine(it) }
        ownPlan?.let {
            appendLine()
            appendLine(if (role == CoachRole.COACH) "## La tua settimana attuale" else "## La tua nutrizione di oggi")
            appendLine(it)
        }
        appendLine()
        appendLine("## Messaggio dell'atleta")
        append(message.trim())
    }

    private fun context(athlete: String, partner: CoachBrief?, role: CoachRole): String = buildString {
        appendLine("# Dati dell'atleta")
        appendLine(athlete)
        appendLine()
        val name = if (role == CoachRole.COACH) "NutriAI" else "Coach"
        appendLine("## Ultimo riepilogo di $name")
        if (partner == null) {
            appendLine("$name non ha ancora pubblicato nulla.")
        } else {
            appendLine(partner.summary)
            if (partner.noteForPartner.isNotBlank()) appendLine("Nota per te: ${partner.noteForPartner}")
        }
    }

    // ---- Plans as text -----------------------------------------------------

    fun describe(plan: WeekPlan): String = buildString {
        if (plan.phase.isNotBlank()) appendLine("Fase: ${plan.phase}")
        plan.days.forEach { day -> appendLine("${AthleteContext.dayLabel(day.date)} ${day.date}: ${describeDay(day)}") }
        if (plan.rationale.isNotBlank()) append("Motivo: ${plan.rationale}")
    }.trimEnd()

    fun describeDay(day: PlannedDay): String =
        if (day.sessions.isEmpty()) {
            "riposo"
        } else {
            day.sessions.joinToString("; ") { s ->
                val head = "${AthleteContext.sportName(s.sport)} ${s.title}"
                val time = if (s.durationMin > 0) " ${s.durationMin}′" else ""
                val how = if (s.details.isNotBlank()) " (${s.details})" else ""
                "$head$time [${s.intensity.key}]$how"
            }
        }

    fun describe(day: NutritionDay): String = buildString {
        appendLine("${day.date}: ${day.headline}. ${day.kcal} kcal, carboidrati ${day.carbsG} g, proteine ${day.proteinG} g, grassi ${day.fatG} g.")
        day.fueling.forEach { appendLine("${it.time}: ${it.text}") }
        day.meals.forEach { appendLine("${it.name}: ${it.description}" + (it.kcal?.let { k -> " ($k kcal)" } ?: "")) }
        if (day.rationale.isNotBlank()) append("Motivo: ${day.rationale}")
    }.trimEnd()

    fun describe(profile: NutritionProfile): String = buildString {
        appendLine("Dieta: ${profile.diet.label}")
        appendLine("Da evitare: ${profile.avoid.ifEmpty { listOf("nulla") }.joinToString(", ")}")
        appendLine("Pasti al giorno: ${profile.mealsPerDay}")
        appendLine("Obiettivo: ${profile.goal.label}")
        if (profile.notes.isNotBlank()) append("Note dell'atleta: ${profile.notes}")
    }.trimEnd()
}

/** What each coach publishes on the shared board after it decides something. */
object CoachBriefs {

    fun of(plan: WeekPlan, today: LocalDate): CoachBrief {
        val upcoming = plan.days.filter { !it.date.isBefore(today) }
        val summary = buildString {
            appendLine("Settimana dal ${plan.weekStart}" + if (plan.phase.isNotBlank()) " (${plan.phase})" else "")
            (upcoming.ifEmpty { plan.days }).forEach {
                appendLine("${AthleteContext.dayLabel(it.date)} ${it.date}: ${CoachPrompts.describeDay(it)}")
            }
            if (plan.rationale.isNotBlank()) append("Motivo: ${plan.rationale}")
        }.trimEnd()
        return CoachBrief(CoachRole.COACH, summary, plan.noteForNutriAi)
    }

    fun of(day: NutritionDay): CoachBrief = CoachBrief(
        from = CoachRole.NUTRI_AI,
        summary = "Nutrizione di ${day.date} (${day.headline}): ${day.kcal} kcal, carboidrati ${day.carbsG} g, " +
            "proteine ${day.proteinG} g, grassi ${day.fatG} g." +
            if (day.rationale.isNotBlank()) " ${day.rationale}" else "",
        noteForPartner = day.noteForCoach,
    )
}
