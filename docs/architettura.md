# Architettura

## Quadro generale

```
 Telefono Android                         Google Cloud / Firebase
┌────────────────────────┐              ┌──────────────────────────────┐
│ App TriForge           │  ID token    │ Servizio Garmin (Cloud Run)  │
│  Compose + ViewModel   │─────────────▶│  FastAPI + python-garminconnect
│                        │              │        │            │        │
│  Health Connect ──┐    │              │        ▼            ▼        │
│                   ▼    │   lettura    │   Garmin Connect   Firestore │
│  Firestore SDK ◀───────┼──────────────┼────────────────────────┘     │
│  (cache offline)       │   in tempo   │                              │
└────────────────────────┘   reale      │ Firebase Auth                │
                                        └──────────────────────────────┘
```

L'app non parla mai direttamente con Garmin. Chiede al servizio di collegare
l'account o di sincronizzare, e poi legge le attività da Firestore, che le
tiene anche in cache sul telefono per l'uso offline.

## Scelte principali

**Garmin lato server.** La libreria `python-garminconnect` non è ufficiale:
usa email e password dell'atleta e produce token di lunga durata. Tenerli sul
telefono sarebbe un rischio, e la libreria è Python. Il servizio usa la password
per una sola chiamata, non la salva, e cifra i token prima di scriverli in un
documento che l'app non può leggere.

**Health Connect come seconda via.** È il canale ufficiale di Android: Garmin
Connect e altre app ci scrivono gli allenamenti. Se Garmin cambia il login o
blocca la libreria, l'app continua a ricevere dati. Copre anche chi usa un
orologio diverso.

**Un solo formato di attività.** Il servizio Garmin e l'import da Health
Connect scrivono documenti con gli stessi campi in `users/{uid}/activities`.
Quando la stessa sessione arriva da entrambe le fonti, vince quella di Garmin,
che ha più dettagli (`android/domain/.../Deduplication.kt`).

**Logica fuori da Android.** Statistiche settimanali, formati e deduplica
stanno nel modulo `domain`, Kotlin puro. Si testano in un secondo senza
emulatore, e saranno la base su cui i coach AI leggeranno i dati.

**Niente framework di dependency injection.** Il grafo degli oggetti è piccolo
e viene costruito a mano in `AppContainer`.

## Dati in Firestore

```
users/{uid}                       profilo atleta
  activities/{garmin_ID | hc_ID}  attività normalizzate
  wellness/{garmin_DATA | hc_DATA} HRV, FC a riposo e sonno di una mattina
  checkIns/{DATA}                 check-in del mattino dell'atleta
  consentLog/{id}                 storico dei consensi, solo aggiunte (prova ex art. 7 GDPR)
  media/avatar                    foto profilo, JPEG 384×384 ritagliato sul telefono
  integrations/garmin             stato del collegamento (sola lettura per l'app)
  private/garmin                  token Garmin cifrati (solo server)
  private/garminMfa               stato MFA in attesa, cifrato (solo server)
```

Campi di un'attività: `source` (`garmin` o `health_connect`), `sport` (`swim`,
`bike`, `run`, `strength` per la palestra, `other` per tutto il resto), `type`
(il nome del tipo secondo la fonte: da lì l'app capisce se un'altra attività
è una camminata, un'escursione, yoga o cardio), `name`, `startTime`, `durationSec`, `distanceMeters`,
più i campi opzionali `elevationGainMeters`, `avgHr`, `maxHr`, `calories`,
`movingDurationSec`, `avgSpeedMps`, `avgPowerWatts`, `trainingLoad`, `aerobicTe`,
`vo2max` e, per il nuoto, `poolLengthMeters`, `swolf`, `strokes`, `lengths`,
`openWater`, `strokeDistanceMeters`, `laps` e `hasLaps`; per la palestra, quando
l'orologio ha contato le serie, `sets`, `reps` ed `exercises` (categoria, serie,
ripetizioni e chili sollevati per esercizio).

`laps` sono le ripetute di una nuotata Garmin, nell'ordine: `distanceMeters`,
`durationSec`, e se misurati `strokes`, `swolf`, `avgHr`, `maxHr`, `stroke`
(`free`, `back`, `breast`, `fly`, `drill`, `mixed`) e `lengths`, le vasche con gli
stessi campi. Un recupero a bordo vasca è un giro senza distanza. Il servizio le
legge con una richiesta in più per nuotata (al massimo 25 per sincronizzazione,
le più recenti prima) e non le rilegge quando `hasLaps` è vero. Dalle ripetute
l'app ricava passo per ripetuta, zone di passo sulla CSS, tenuta del passo e
della bracciata, carico vasca per vasca e la CSS stimata (`SwimAnalysis`, idee
riprese da SwimForge). Health Connect non dà le ripetute.

`durationSec` è il tempo del cronometro, pause comprese; `movingDurationSec` è
il tempo in movimento. Il passo del nuoto si legge sul tempo in movimento,
perché le pause a bordo vasca fanno parte della seduta e non del passo; bici e
corsa restano sul cronometro, come l'orologio. Il dettaglio di un'attività
mostra entrambi. Garmin dà la lunghezza della vasca in centimetri: il servizio
la salva in metri.

Le regole in `firebase/firestore.rules` permettono all'app di scrivere solo le
attività di Health Connect: quelle di Garmin le scrive soltanto il server.

## Interfaccia

Le schermate seguono il prototipo di design approvato: tema scuro, Space
Grotesk per titoli e numeri, Inter per il testo, un colore per sport (nuoto
azzurro, bici giallo, corsa viola). Colori, caratteri e icone stanno in
`ui/theme`; card, sezioni collassabili e grafici in `ui/common`.

Tre regole valgono per ogni schermata nuova:

- le statistiche si mostrano sempre con card e grafici, mai come elenchi di
  numeri;
- quando i dati sono tanti, il dettaglio va in sezioni collassabili
  (`CollapsibleCard`) e in vista restano solo i numeri principali;
- si mostra solo ciò che le fonti forniscono davvero: una card senza dati non
  compare, e nessun valore è inventato.

Schermate: Accesso, Oggi (forma degli ultimi 42 giorni, settimana in corso
rispetto alla media delle quattro precedenti), Attività (schede Tutte, Nuoto,
Bici, Corsa, Palestra e Altro, con le statistiche per sport; ogni attività
mostra il suo carico), Dettaglio attività, Profilo atleta
(sorgenti dati, obiettivo, fisico, soglie).

L'app carica le ultime 16 settimane di attività, e la prima sincronizzazione
Garmin ne importa altrettante (`INITIAL_SYNC_DAYS`, 120 giorni).

Fitness, fatica e forma sono due medie mobili del carico di allenamento che
arriva dall'orologio, a 42 e a 7 giorni (`FitnessModel`). Il profilo atleta sta
nello stesso documento `users/{uid}`, nei campi `goal`, `raceName`, `raceDate`,
`weeklyHours`, `weightKg`, `heightCm`, `maxHr`, `cssSecPer100m`, `ftpWatts` e
`thresholdPaceSecPerKm`.

Mancano ancora, perché servono dati che oggi non vengono salvati: mappa e
grafici interni all'attività (tracce e parziali), tempo nelle zone, sonno e HRV,
usura dell'attrezzatura. Coach, nutrizione e social arrivano con i passi
successivi del piano.

## Account, consensi e privacy

Il documento `users/{uid}` contiene anche `displayName`, `bio`, `birthYear`,
`onboardingCompletedAt` e la mappa `consents` (`terms`, `health`, `aiCoach`,
ognuno con `granted`, `version`, `at`). Finché mancano i consensi obbligatori
per la versione corrente dei testi (`LegalTexts.VERSION`) l'app mostra il
primo accesso o la sola pagina dei consensi (`Onboarding.gate` nel modulo
`domain`). Dettagli e scelte in [privacy.md](privacy.md).
