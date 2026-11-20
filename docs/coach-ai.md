# Coach e NutriAI

TriForge ha due coach AI distinti, entrambi su Gemini tramite Firebase AI
Logic (backend Vertex AI, regione `europe-west3`, Francoforte):

- **Coach**: l'allenatore. Prepara la settimana (lunedì-domenica), la spiega e
  la adatta in chat.
- **NutriAI**: il nutrizionista. Spiega i numeri del giorno (calorie e macro,
  calcolati dall'app) e, quando l'atleta glielo chiede, prepara carburante
  intorno alle sedute e pasti sul piano dei coach e sul profilo alimentare.

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
- Quando i coach cambiano il piano, l'app ricalcola subito i numeri del
  giorno. I pasti già scritti restano e vengono segnati "da rivedere"
  (`NutriMeals.outdated`): è l'atleta a scegliere tra «Aggiorna i pasti» e
  «Tienili così».

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
  con le loro eccezioni. Ogni piatto proposto viene controllato, ingredienti
  compresi: se uno viola il profilo NutriAI riscrive la giornata una volta,
  poi l'app toglie quello che ancora non va. Le eccezioni dei cibi non
  mangiati non sbloccano mai un'allergia.
- **Sicurezza** (`NutriSafety`): prima di chiamare il modello, in entrambe le
  chat, l'app cerca segnali di disturbo alimentare (digiuni di giorni, vomito,
  lassativi, abbuffate con senso di colpa, calorie sotto 1000). Se ne trova uno
  il modello non viene chiamato e la risposta è fissa: medico di base e Numero
  Verde SOS Disturbi Alimentari 800 180 969.

### I pasti: solo su richiesta, piatto per piatto

NutriAI non scrive mai i pasti da solo (`NutriMeals`, `NutriViewModel`):

- **Su richiesta.** La scheda mostra la settimana da lunedì a domenica e, per
  il giorno scelto, i numeri dell'app, sempre aggiornati. I pasti arrivano con
  «Prepara i pasti di …» (un giorno) o «Prepara tutta la settimana» (da oggi a
  domenica, solo i giorni senza pasti, salvati uno alla volta). Stanno in
  `coaching/nutriai`, campo `days`, per data; i giorni delle settimane passate
  vengono tolti.
- **Pasti fatti di piatti.** Ogni pasto ha i suoi piatti (`Dish`: quantità,
  calorie, macro, ingredienti). Il pasto e il giorno sono la somma dei piatti:
  la calcola l'app, che corregge le calorie incoerenti con i macro e riporta
  le quantità verso l'obiettivo del giorno (`sanitized`, `settle`).
- **Cambiare un piatto.** «Cambia» apre le alternative: NutriAI ne propone
  (`DishAdvisor`), l'app scarta quelle vietate dal profilo o già viste e le
  porta alle calorie del piatto sostituito (`alternatives`). Oppure l'atleta
  scrive il piatto: NutriAI ne stima valori e ingredienti, l'app controlla
  (`typed`). Un'allergia o un'intolleranza blocca sempre, anche nelle parole
  dell'atleta e prima di chiamare il modello; un cibo "che non mangi" chiede
  conferma. Se il piatto scritto è molto più leggero, NutriAI consiglia cosa
  aggiungere per non perdere il carburante della seduta.
- **Le scelte dell'atleta restano.** Un piatto scelto o scritto dall'atleta è
  segnato (`DishOrigin`) e ricorda quello che ha sostituito: «Rimetti» e
  «Annulla» lo riportano indietro, e «Rifai i pasti» lo lascia al suo posto.
- **Se cambia qualcosa.** Piano cambiato: i numeri si aggiornano e i pasti
  sono "da rivedere". Profilo cambiato: i piatti ora vietati sono segnati
  «Da cambiare» (`flags`) e non spariscono da soli. In nessuno dei due casi
  NutriAI riscrive: decide l'atleta.

### NutriAI sulla settimana a tre sport

La giornata di un triatleta dipende anche da quello che viene dopo
(`DayContext`): le sedute di domani e la gara.

- **Vigilia.** Prima di un giorno pesante o della gara i carboidrati non
  scendono sotto 6 g/kg, e quel giorno non c'è deficit calorico.
- **Carico prima della gara.** Nei due giorni prima di un 70.3 (8 g/kg) o di
  un Ironman (10 g/kg), e il giorno prima di un olimpico (7 g/kg).
- **Rifornimento** (`NutriFueling`): per ogni sforzo del giorno l'app calcola
  i grammi prima, all'ora durante e dopo, dalla durata e dall'intensità. Un
  brick (bici e corsa di seguito) è un solo sforzo; con due sedute separate
  il recupero dopo la prima è obbligato. In vasca, sotto i 90 minuti, solo
  acqua.

NutriAI riceve questi numeri nella richiesta e li traduce in cibi adatti al
profilo: non li ricalcola.

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

### Palestra, allenatore, sport in pausa

Non tutta la settimana è dei coach AI. In "Giorni, orari e gara" ogni sport
ha un modo (`SportMode`) e la palestra un interruttore:

- **Coach AI**: il coach di quello sport scrive le sedute, come sopra.
- **Mio allenatore**: l'atleta dà solo giorni e ora. La regia mette in
  calendario una seduta per giorno (durata: la mediana delle ultime
  settimane), il coach AI di quello sport non scrive niente: legge le sedute
  fatte e le commenta nel verbale (`SportCoachPrompts.reviewRequest`).
- **In pausa**: nessuna seduta e nessun coach al tavolo. La sua quota va agli
  altri sport.
- **Palestra** (`Sport.STRENGTH`): giorni, ora e durata li decide l'atleta. È
  un blocco fisso come le sedute con l'allenatore (`PlannedSession.own`).

Le sedute dell'atleta escono per prime dalle ore disponibili; quello che
resta si divide tra gli sport dei coach AI, con gli stessi tetti di crescita.
Nessun coach e nessuna chiusura della regia le sposta o le accorcia: se c'è
un conflitto cede la seduta di un coach. Il verbale si apre con una riga
della regia che dice cosa è già in calendario, cosa è in pausa e quanto
resta ai coach (`CoachTable.opening`).

### Previsto e fatto

Il piano ricorda da quale giorno è stato scritto (`WeekPlan.plannedFrom`) e,
quando la settimana viene rifatta, tiene per i giorni passati quello che era
previsto (`CoachTable.keepPlanned`); la regia invece controlla sempre contro
le attività fatte davvero. `WeekReview` mette le due cose una accanto
all'altra: seduta fatta (stesso sport, stesso giorno) con i minuti fatti e
previsti, seduta non fatta, attività fuori programma. La schermata della
settimana lo mostra, i coach lo ricevono in parole quando scrivono la
settimana o rispondono in chat.

Ogni attività registrata conta: palestra e altre attività (camminata,
escursione, yoga, cardio) entrano nel carico (`SessionLoadModel`, con un
minimo di 36 TSS l'ora per la palestra, perché i battiti sottostimano il
lavoro di forza), nel contesto dei coach e nel fabbisogno del giorno di
NutriAI (`NutriEnergy.withDone`: quello che è stato registrato fuori dal
piano si aggiunge, senza contare due volte uno sport già previsto).

## Tre coach, un tavolo

La settimana non la scrive più un coach solo. La scrivono tre agenti, uno
per sport (`SportCoachPrompts`): Coach Nuoto, Coach Bici e Coach Corsa. Ognuno
è esperto solo del suo sport, ha le sue istruzioni e scrive solo le sue
sedute. Nessuno dei tre comanda: comandano le regole della regia.

Come si accordano (`CoachTable`, guidato da `CoachViewModel.planWeek`):

1. **Primo giro.** I tre ricevono, in parallelo, la regia della settimana, i
   propri minuti e limiti, i dati dell'atleta e la nota di NutriAI. Ognuno
   propone le sue sedute da oggi a domenica.
2. **Controllo.** L'app unisce le tre proposte (i giorni già passati sono le
   attività fatte davvero) e le passa alla regia.
3. **Secondo giro**, solo se una regola è violata: ogni coach rivede la sua
   parte leggendo le proposte e le note degli altri due e l'elenco dei
   conflitti. I giri sono al massimo due.
4. **Chiusura.** Quello che resta aperto lo sistema l'app, una regola alla
   volta: cede lo sport meno legato all'obiettivo (prima lo sport debole, poi
   gli sport per quota della settimana). Ogni intervento finisce nel verbale.

Il verbale ("Come si sono accordati") è salvato con il piano: le note dei tre
coach, gli interventi della regia e la nota di NutriAI.

### Allenamenti strutturati

Ogni seduta è una lista di passi (`WorkoutStep`): riscaldamento, serie,
defaticamento, ciascuno con ripetute, distanza o tempo, zona e recupero. Come
in SwimForge, il modello sceglie e spiega, l'app fa i conti (`Workouts`,
`Zones`):

- le zone sono Z1–Z5 per tutti gli sport: nuoto dal passo soglia (CSS, con i
  confini di `SwimAnalysis.ZONE_SPEED_BOUNDS`), bici dall'FTP, corsa dal passo
  soglia; senza soglia, bici e corsa passano ai battiti dalla frequenza
  massima. I vecchi nomi del nuoto (A1–C3) nei piani già salvati vengono
  tradotti alla lettura (`Zones.read`);
- i ritmi di ogni zona sono scritti nei dati dell'atleta («Zone dell'atleta»):
  in chat il coach cita solo quelli, così chat e sedute dicono gli stessi
  numeri;
- una ripetuta troppo lunga per la sua zona viene spezzata;
- passo, watt o battiti di ogni passo, durata, distanza, tempo per zona e
  intensità della seduta li calcola l'app: al modello non vengono chiesti;
- la regia controlla anche il tempo in zona alta (soglia e oltre) per sport.

Il piano resta nel documento `coaching/coach`.

### La soglia del nuoto sotto controllo

`SwimThreshold.check` confronta la soglia del profilo con le nuotate in vasca
delle ultime tre settimane: se più di metà dei metri esce a ritmo di soglia e
oltre, o se una nuotata intera di mezz'ora e più è stata più veloce della
soglia, la soglia è troppo lenta e zone, carico e forma sono gonfiati. L'app
propone un valore: dai migliori tempi su due distanze quando ci sono,
altrimenti dal passo abituale delle sedute, dal lato prudente (× 0,95). La
soglia del profilo non viene mai cambiata di nascosto: i coach lo leggono nei
dati dell'atleta («Soglia del nuoto da verificare») e propongono il test
400 + 200. Le ripetute con un passo impossibile (vasche contate male
dall'orologio) sono marcate «dubbie» e restano fuori da migliori tempi, zone e
stima; nel dettaglio della nuotata sono tratteggiate nel grafico e spiegate
nella tabella.

In Oggi l'avviso «La soglia del nuoto sembra troppo lenta» apre la pagina
«Soglia del nuoto» (tavola 4f): perché, che cosa falsa, e le due strade. Il
test 400 + 200 si chiede al Coach Nuoto con un tocco, o si scrivono i due
tempi (`SwimThreshold.fromTest`: la differenza dei due tempi diviso due, ogni
100 m); oppure si accetta la stima. «Lascia per ora» fa tacere l'avviso per
tre settimane (`cssSnoozedUntil` in `coaching/coach`). Finché la soglia è in
dubbio, «Forma fisica» dice «Affidabilità bassa».

### La regia non scrive nel passato

Il giorno da cui si pianifica è `CoachTable.planningDay`: oggi, oppure domani
quando la giornata è finita (`DAY_OVER`, le 21). `merge`, `revise` e `realign`
non toccano i giorni prima, e una seduta fissa dell'atleta il cui orario è già
passato non viene più scritta su oggi. Quando l'atleta cambia «Giorni, orari e
gara», `realign` riallinea la settimana senza richiamare i modelli: tolte le
sedute di uno sport ora in pausa o seguito dall'allenatore, spostate le sedute
fisse nei nuovi giorni, e la regia sistema quello che ne consegue. Che cosa ha
tolto e aggiunto, e perché, lo dice una volta sulla settimana («Il profilo è
cambiato», tavola 6i): `ProposalPreview.notice`, salvato nel campo `notice` di
`coaching/coach` fino a «Va bene» o al cambio successivo della settimana.

### Oggi tocca a

`TodayAdvice.card` è la scheda in cima a Oggi (tavola 3c): le sedute di oggi
ancora da fare, o quelle di domani quando oggi è fatto, libero o finito; una
frase sola che legge insieme recupero e forma (la risposta del corpo viene
prima, il carico la spiega), e il pasto che conta per NutriAI. Nessun modello
viene chiamato: è tutto calcolato dalla settimana e dai dati già caricati. Una
seduta di oggi non ancora registrata resta «di oggi» fino a un'ora dopo la sua
fine all'orario abituale.

### Quando un modello non risponde

Ogni chiamata ha un'attesa massima (`AiWait`: 20 secondi per un piatto, 45 per
una risposta in chat, 90 per una settimana o una giornata di pasti) e un
numero di tentativi (`geminiJson`). Se non arriva una risposta valida, l'app lo dice con il motivo
(rete, attesa scaduta, servizio occupato, risposta non leggibile) e un tasto
«Riprova» che rifà la stessa richiesta: in chat il messaggio dell'atleta
resta, e nessuna schermata rimane ferma su un caricamento.

### Una chat per coach

Ogni coach di sport ha la sua chat con l'atleta
(`coaching/{swim|bike|run}/messages`). In chat il coach vede tutta la
settimana, ma può proporre di cambiare solo le sue sedute: la proposta sono
tutte le sue sedute da oggi a domenica. Quando l'atleta tocca "Applica",
`CoachTable.revise` sostituisce le sedute di quel coach, lascia quelle degli
altri due, ripassa tutto alla regia e scrive nel verbale la modifica e gli
eventuali interventi dell'app. Il riepilogo per NutriAI viene riscritto: i
numeri dei giorni toccati cambiano subito e i loro pasti, se c'erano, sono
"da rivedere".

Prima di «Applica» la proposta si legge per intero (tavola 6h): l'app calcola
`CoachTable.revision` sulla settimana com'è adesso, senza toccarla, e mostra
ogni seduta del coach con quello che le succede (nuova, spostata, cambiata,
invariata, tolta) e i suoi blocchi, poi «Cambia anche»: le sedute degli altri
che la regia deve toccare, i suoi motivi, e che cosa cambia per NutriAI
(`ProposalPreview`). Niente cambia che non sia scritto lì. Dopo «Applica»
resta il riepilogo con «Annulla»: la settimana di prima è salvata nel campo
`undo` di `coaching/coach` e torna com'era con un tocco, finché non si applica
un'altra modifica o si rifà la settimana. Una proposta annullata ha stato
`undone` e si può applicare di nuovo.

Con la tastiera aperta la schermata sopra la chat si riduce a una riga (tavola
6g): il nome del coach, che apre i tre coach, e «Settimana». Le domande già
pronte si vedono solo finché la chat è vuota.

Una domanda che non è del suo sport il coach la passa con un tasto
(`handoff`): alla chat di un altro coach o a NutriAI.

Le regole Firestore ammettono i ruoli `swim`, `bike` e `run` sotto `coaching`
e il campo `handoff` nei messaggi: vanno pubblicate prima di distribuire una
build con le chat per coach (`firebase deploy --only firestore:rules`).

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
