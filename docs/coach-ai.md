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

- il nuoto usa le sigle che si usano in vasca, come ha chiesto Shard: A1
  recupero, A2 aerobico, B1 medio, B2 soglia (fino alla CSS), C1 sopra soglia,
  C2 VO2max, C3 sprint, dal passo soglia (CSS) con i confini di
  `SwimAnalysis.ZONE_SPEED_BOUNDS` (85, 90, 95, 100, 104 e 110% della velocità
  di soglia, come in SwimForge). Sono le stesse nelle sedute scritte dal coach
  e nell'analisi delle nuotate fatte: un B1 previsto e un B1 nuotato sono lo
  stesso passo;
- bici e corsa usano Z1–Z5, dall'FTP e dal passo soglia; senza soglia passano
  ai battiti dalla frequenza massima;
- `Zones.level` mette tutte le sigle su una scala sola da 1 a 5 (B2 e C1
  valgono 4, C2 e C3 valgono 5): è quella che usano la regia e i colori;
- una nuotata salvata quando le zone del nuoto si chiamavano Z1–Z5 viene letta
  con le sigle della vasca (`Zones.read`: Z1→A1, Z2→A2, Z3→B1, Z4→B2, Z5→C2),
  e i ritmi dei passi si ricalcolano dalle soglie di oggi quando la seduta
  viene mostrata (`Workouts.withTargets`);
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
delle ultime tre settimane: se più di metà dei metri nuotati senza fermarsi
esce a ritmo di soglia e oltre, o se mezz'ora di ripetute lunghe in una
nuotata è stata più veloce della soglia, la soglia è troppo lenta e zone,
carico e forma sono gonfiati. Contano solo le ripetute da 200 m in su (e le
nuotate senza vasche registrate, se fatte quasi senza soste): le ripetute da
25, 50 e 100 m con recupero si nuotano più forte della soglia, e di quanto
dipende dal nuotatore. Chi tiene 1:49 sui 100 m può avere una soglia di 2:05:
il passo dei 100 non dice niente sulla CSS. L'app
propone un valore: dai migliori tempi su due distanze quando ci sono,
altrimenti dal passo abituale delle ripetute lunghe, dal lato prudente (× 0,95). La
soglia del profilo non viene mai cambiata di nascosto: i coach lo leggono nei
dati dell'atleta («Soglia del nuoto da verificare») e propongono il test
400 + 200. Le ripetute con un passo impossibile (vasche contate male
dall'orologio) sono marcate «dubbie» e restano fuori da migliori tempi, zone e
stima; nel dettaglio della nuotata sono tratteggiate nel grafico e spiegate
nella tabella. Una vasca contata due volte si riconosce prima, dai tempi
delle vasche: un pezzo che dura meno del 55% di una vasca intera della sua
ripetuta e che, unito alla vasca accanto, ne fa circa una
(`SwimAnalysis.recounted`). I due pezzi vengono riuniti e la distanza della
ripetuta segue le vasche rimaste: 150 m a 1:21 tornano 100 m in 2:02, e la
tabella dice quanti metri aveva contato l'orologio. Una vasca breve accanto
a una lunga è solo una virata sentita tardi, e resta com'è.

La stima dai migliori tempi (`SwimAnalysis.estimateCss`, card «CSS stimata
dalle tue nuotate» in Attività, Nuoto) è giusta solo se quelle ripetute erano
a tutta, e l'app non può saperlo: l'affidabilità è al più «media», mai «alta».
Diventa «bassa», e non viene proposta, quando un'altra nuotata continua da
200 m in su è andata più lontano nel suo tempo di quanto il modello permette.
La card spiega la differenza dalla soglia del profilo nei due versi, ricorda
che la CSS è più lenta del passo sui 100 m, e lascia la scelta all'atleta con
un pulsante non in evidenza.

In Oggi l'avviso «La soglia del nuoto sembra troppo lenta» apre la pagina
«Soglia del nuoto» (tavola 4f): perché, che cosa falsa, e le due strade. Il
test 400 + 200 si chiede al Coach Nuoto con un tocco, o si scrivono i due
tempi (`SwimThreshold.fromTest`: la differenza dei due tempi diviso due, ogni
100 m); oppure si accetta la stima. «Lascia per ora» fa tacere l'avviso per
tre settimane (`cssSnoozedUntil` in `coaching/coach`). Finché la soglia è in
dubbio, «Forma fisica» dice «Affidabilità bassa».

### Pinne, pull buoy e palette

Con l'attrezzatura si nuota più veloce del proprio passo, e nessun orologio
la registra (il Garmin sa solo se una vasca era un «drill»). La segna
l'atleta (tavola 5e): nel dettaglio della nuotata la card «Attrezzatura», e
in Oggi una domanda sull'ultima nuotata in piscina degli ultimi due giorni,
finché non ha una risposta (`SwimAnalysis.gearToAsk`). «Niente» è una
risposta anche lei.

Come in SwimForge, l'app propone e l'atleta conferma (tavola 5f,
`SwimSets`). `gearGuess` cerca nella nuotata le ripetute che sembrano con
attrezzatura: più veloci del 10% del passo abituale **e** con almeno il 15%
di bracciate a vasca in meno (il 25% sulla vasca singola, dove anche uno
sprint con una spinta lunga ne ha meno), oppure più veloci del 17% con
almeno il 10% di bracciate in meno su più di una vasca: con le pinne
lavorano le gambe e le braccia girano quasi come sempre. Tutte e due le
cose, perché la sola velocità è uno sprint e le sole bracciate in meno
sono un esercizio. Le soglie vengono da una nuotata vera segnata a mano
dall'atleta (100 m da 1:50 a 1:28 con le pinne, bracciate da 13 a 9–11).
Le vasche contate due volte sono già state tolte (vedi sopra): altrimenti
una ripetuta contata lunga sembrerebbe veloce e con poche bracciate. Le
ripetute dal passo dubbio si leggono lo stesso quando hanno i tempi delle
vasche. Fino a due ripetute in mezzo a ripetute proposte (uno stile
diverso, un tratto lento) sono un «forse» a parte: l'attrezzatura si
tiene per un tratto della seduta.
Il passo e le bracciate abituali sono quelli della nuotata stessa, presi
sopra la metà, così mezza nuotata con le pinne si misura ancora sull'altra
metà. Se l'orologio dà a una di queste ripetute uno stile diverso è solo
un «forse», non spuntato. Il pull buoy da solo cambia poco passo e
bracciate: non viene mai proposto. Quando c'è una proposta, la card e la
domanda in Oggi la dicono («12 ripetute sembrano con attrezzatura»); non
viene segnato niente finché l'atleta non salva.

Nella pagina si sceglie per serie (`SwimSets.series`: ripetute uguali di
fila, divise da una pausa lunga), ognuna con la sua attrezzatura; le serie
proposte sono già spuntate, con l'attrezzatura dell'ultima nuotata segnata
(`lastGear`). Una serie si apre per togliere una ripetuta. «Tutta la
nuotata» segna la nuotata intera, ed è la sola scelta per una nuotata senza
vasche registrate.

I segni stanno in `coaching/swim`, campo `gear`, per id dell'attività
(`session`: elenco di chiavi; `repeats`: numero della ripetuta → chiavi;
chiavi `fins`, `pull_buoy`, `paddles`): le attività del Garmin l'app non le
può scrivere, e il documento del Coach Nuoto viene già esportato e
cancellato con i dati sulla salute. `ActivityRepository.activities` li
unisce alle attività (`Activity.gear`), così tutti i conti li vedono.

Una ripetuta con attrezzatura (`SwimRepeat.assisted`) si vede, con il solo
contorno nel grafico e un'etichetta in elenco, ma non è «il passo
dell'atleta» (`SwimRepeat.own`): resta fuori da zone, tenuta del passo,
metri per bracciata, stima della CSS, controllo della soglia, record di
velocità e confronti tra nuotate (`SwimAnalysis.ownPace`: i metri e i
secondi senza attrezzatura, usati anche per il passo delle settimane in
`SportStats`). Non è mai «dubbia», perché si sa perché è veloce. Nel carico
conta per il tempo nuotato al passo abituale del resto della nuotata; una
nuotata tutta con attrezzatura conta come una nuotata qualunque
(`SessionLoadModel.typicalIntensity`). I coach leggono in ogni nuotata
quale parte era con attrezzatura e qual è il passo senza
(`AthleteContext.gear`), e se un passo molto più veloce del solito non ha
attrezzatura segnata lo chiedono prima di parlare di progressi. Di una
nuotata senza risposta leggono anche quante ripetute sembrano con
attrezzatura (`AthleteContext.gearGuess`); quelle ripetute restano però nei
conti finché l'atleta non le conferma.

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
fine all'orario abituale. Dopo, la scheda passa a domani ma non la salta in
silenzio: «Oggi c'era … : non risulta ancora fatta» (`TodayCard.earlier`),
perché l'app non distingue una seduta saltata da una che l'orologio non ha
ancora mandato.

I coach ricevono lo stesso giudizio che l'atleta legge in Oggi
(`TodayAdvice.state`) e il check-in sulla sua scala vera (ogni voce da 1,
molto bene, a 7, molto male) insieme al numero che Oggi mostra («Come ti
senti 3 su 5»): così chat e Oggi non raccontano due stati diversi.

### Quando un modello non risponde

Ogni risposta ha un'attesa massima (`AiWait`: 25 secondi per un piatto, 45 per
una risposta in chat, 90 per una settimana o una giornata di pasti). Dentro
quell'attesa l'app non si fida di una sola chiamata (`firstAnswer`): ogni tanto
una richiesta resta appesa, e la stessa domanda rifatta riceve risposta in
pochi secondi. Quindi, se la prima chiamata tace ancora dopo 8, 15 o 30
secondi, l'app ne fa partire un'altra accanto e usa la prima che risponde; una
chiamata fallita per un motivo passeggero (rete, servizio, risposta non
leggibile) viene rifatta subito. L'atleta vede una sola attesa. Un motivo che
riprovare non risolve (servizio occupato, AI non attiva, risposta bloccata)
chiude l'attesa subito.

Se non arriva una risposta valida, l'app lo dice con il motivo e un tasto
«Riprova» che rifà la stessa richiesta: in chat il messaggio dell'atleta
resta, e nessuna schermata rimane ferma su un caricamento. L'errore compare
dove l'atleta sta guardando: nelle chat sotto il suo ultimo messaggio (anche
in NutriAI, dove la chat è in fondo alla pagina), per i pasti in cima.

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

I giorni di ogni sport e il giorno di riposo sono dell'atleta: nessun coach li
scavalca, nemmeno se glielo si chiede in chat («spostamela a domenica»). Il
coach li riceve tra i suoi limiti (`SportCoachPrompts.days`) con la regola di
non promettere un giorno che non c'è. Se li scrive lo stesso, la seduta in
quel giorno non entra (`CoachTable.refusal`): la scheda lo dice per prima
cosa, con il motivo e dove si cambiano i giorni, e nel verbale la riga del
coach non riporta più le sue parole («ho spostato a domenica») ma quello che
è cambiato davvero. Le parole del coach restano quando le sue sedute sono
tutte nella settimana, anche se la regia le ha accorciate o rese facili: in
quel caso lo spiega la riga della regia che segue.

Quando il profilo cambia, la settimana si riallinea da sola
(`CoachTable.realign`) e lo dice una volta. Una seduta di un coach che esce
solo ricalcolata di qualche minuto (meno di `RETIMED_MIN`) non è una notizia:
la settimana resta com'è e non compare nessun avviso.

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
