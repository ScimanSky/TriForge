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
  integrations/garmin             stato del collegamento (sola lettura per l'app)
  private/garmin                  token Garmin cifrati (solo server)
  private/garminMfa               stato MFA in attesa, cifrato (solo server)
```

Campi di un'attività: `source` (`garmin` o `health_connect`), `sport` (`swim`,
`bike`, `run`, `other`), `name`, `startTime`, `durationSec`, `distanceMeters`,
più i campi opzionali `elevationGainMeters`, `avgHr`, `maxHr`, `calories`,
`avgSpeedMps`, `avgPowerWatts`, `trainingLoad`, `aerobicTe`, `vo2max` e, per il
nuoto, `poolLengthMeters`, `swolf`, `strokes`, `lengths`, `openWater`.

Le regole in `firebase/firestore.rules` permettono all'app di scrivere solo le
attività di Health Connect: quelle di Garmin le scrive soltanto il server.

## Interfaccia

Le schermate di questo passo sono volutamente essenziali. I colori vengono dal
prototipo di design; la UI definitiva seguirà quel prototipo.
