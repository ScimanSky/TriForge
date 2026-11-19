# Coach e NutriAI

TriForge ha due coach AI distinti, entrambi su Gemini tramite Firebase AI
Logic (backend Vertex AI, regione `europe-west3`, Francoforte):

- **Coach**: l'allenatore. Prepara la settimana (lunedì-domenica), la spiega e
  la adatta in chat.
- **NutriAI**: il nutrizionista. Prepara la giornata (calorie, macro,
  carburante intorno alle sedute, pasti) sul piano del Coach e sul profilo
  alimentare dell'atleta.

## Due agenti, non uno diviso in due

Ogni coach ha:

- le sue istruzioni di sistema (`CoachPrompts.COACH_SYSTEM`,
  `CoachPrompts.NUTRI_SYSTEM`);
- il suo modello e le sue richieste (`GeminiCoachAgent`, una istanza per
  coach);
- il suo stato in Firestore: `users/{uid}/coaching/coach` e
  `users/{uid}/coaching/nutriai`;
- la sua chat: `users/{uid}/coaching/{coach|nutriai}/messages`.

Si conoscono per nome e ruolo, e lavorano in sinergia attraverso una
**bacheca condivisa**: ognuno, quando decide qualcosa, pubblica nel proprio
documento un riepilogo (`brief.summary`) e una nota per il collega
(`brief.noteForPartner`). L'altro la legge e la riceve nella sua prossima
richiesta, ma ragiona per conto suo. Nessuno dei due chiama l'altro o ne
cambia le decisioni.

Esempi:

- Il Coach mette una soglia in bici mercoledì sera e scrive a NutriAI
  "mercoledì soglia alle 18:30". NutriAI prepara il carburante per quella
  seduta.
- NutriAI vede che l'atleta vuole perdere peso in una settimana di carico alto
  e scrive al Coach del rischio di bassa disponibilità energetica. Il Coach ne
  tiene conto quando rifà la settimana.
- Quando il Coach cambia il piano, la giornata di NutriAI diventa "superata"
  (`basedOnCoachAt` più vecchio del brief del Coach) e NutriAI la rifà alla
  prossima apertura della sua scheda.

## Che cosa ricevono

`AthleteContext` scrive un testo in italiano con profilo, volume delle ultime 8
settimane, sedute degli ultimi 14 giorni, modello di carico (fitness, fatica,
forma), indice Carico e recupero, HRV, FC a riposo, sonno e check-in. Mai nome,
email o foto. Niente viene inviato senza il consenso "Coach AI".

## NutriAI: le regole le decide l'app

Come in SwimForge, i numeri e i divieti non li decide il modello:

- **Energia** (`NutriEnergy`): metabolismo basale Mifflin-St Jeor × 1,4 per la
  vita quotidiana, più le sedute del giorno (MET × peso × ore). Carboidrati
  per tipo di giornata (da 3 g/kg a riposo a 9 g/kg in gara), proteine 1,8 g/kg
  (2 g/kg per calare o crescere), grassi tra 0,8 g/kg e il 35%. Mai sotto il
  basale né sotto 1500 kcal (1200 per una donna); per calare al massimo
  7,7 kcal × kg al giorno (circa 0,7% del peso a settimana) e nessun deficit
  nei giorni duri o di gara. Senza peso NutriAI non prepara la giornata e lo
  chiede. I numeri del modello vengono sostituiti da quelli dell'app.
- **Divieti** (`FoodBans`): allergie (i 14 allergeni UE), intolleranze
  (lattosio, glutine), dieta, carne rossa a zero, caffeina e cibi non mangiati
  con le loro eccezioni. Ogni pasto proposto viene controllato: se uno viola il
  profilo NutriAI riscrive la giornata una volta, poi l'app toglie quello che
  ancora non va. Le eccezioni dei cibi non mangiati non sbloccano mai
  un'allergia.
- **Sicurezza** (`NutriSafety`): prima di chiamare il modello, in entrambe le
  chat, l'app cerca segnali di disturbo alimentare (digiuni di giorni, vomito,
  lassativi, abbuffate con senso di colpa, calorie sotto 1000). Se ne trova uno
  il modello non viene chiamato e la risposta è fissa: medico di base e Numero
  Verde SOS Disturbi Alimentari 800 180 969.

## La regia da triathlon

La forma della settimana la decidono regole dell'app (`TriRegia`), non il
modello. Partono da "Giorni, orari e gara" (`TrainingAvailability`, nel campo
`availability` di `users/{uid}`) e dagli allenamenti delle ultime settimane:

- **Fase**: ripresa, generale, specifica (ultime 9 settimane), scarico (ultime
  3) e settimana di gara, dalla data della gara e dal volume recente.
- **Quote**: sprint e olimpico 20% nuoto, 45% bici, 35% corsa; 70.3 e Ironman
  15/50/35; il punto debole (scelto dall'atleta o letto dai dati) prende 7
  punti dagli altri due.
- **Budget**: i minuti di ogni sport sono la quota delle ore disponibili, ma
  mai oltre 1,25 volte la media delle ultime 4 settimane (1,10 per la corsa).
  Seduta più lunga: 1,5 volte la mediana (nuoto) o l'uscita più lunga recente
  (bici, corsa; il lungo di corsa anche entro il 30% della settimana).
- **Regole comuni**: al massimo 3 sedute dure (meno in ripresa, scarico e
  gara), 2 per sport, mai due giorni duri di fila, un giorno di riposo, al
  massimo due sport al giorno, brick solo in fase specifica, giorni e riposo
  scelti dall'atleta.

Il Coach riceve la regia nella richiesta; `TriRegia.check` controlla la
settimana, il Coach la riscrive una volta e quello che resta viene scritto
sotto il piano. Con i tre coach di sport (fase 3) la regia resta la stessa.

## App Check

Firebase AI Logic applica App Check: senza un token valido Gemini rifiuta la
richiesta. Le build di Play usano Play Integrity; le build di prova (App
Distribution) usano il provider di debug con un segreto registrato sul
progetto (console Firebase › App Check › app Android › Gestisci token di
debug) e scritto in `android/local.properties` come
`triforge.appCheckDebugToken=<uuid>`. Senza segreto il provider ne crea uno e
lo scrive nel logcat, da registrare a mano.

## Spostarli su un server

L'app parla con i coach solo tramite l'interfaccia `CoachAgent`. Per spostarli
su Cloud Functions o Cloud Run basta un'altra implementazione che chiama il
server; prompt e validazione (`CoachPrompts`, `CoachMapper`) sono nel modulo
`domain`, in Kotlin puro.

## Da fare

- Registro dei pasti con foto, invio della settimana al Garmin.
