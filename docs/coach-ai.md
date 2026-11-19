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
