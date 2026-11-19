# Privacy e GDPR

TriForge tratta dati sanitari (frequenza cardiaca, HRV, sonno, allenamenti,
check-in), che il GDPR considera una categoria particolare (art. 9). Questo
documento riassume come l'app li gestisce. I testi per l'utente sono in
`android/app/src/main/res/raw/` (informativa e termini) e sono **bozze da far
verificare a un professionista** prima della pubblicazione.

## Consensi

| Consenso | Chiave | Obbligatorio | Base giuridica |
|---|---|---|---|
| Informativa letta e termini accettati | `terms` | sì | contratto (art. 6.1.b) |
| Dati sulla salute | `health` | sì | consenso esplicito (art. 9.2.a) |
| Coach AI (invio a Google Gemini) | `aiCoach` | no | consenso (art. 6.1.a, 9.2.a) |

- Ogni risposta finisce in `users/{uid}/consents` (stato attuale) e in
  `consentLog` (storico, solo aggiunte, con l'ora del server).
- Cambiare `LegalTexts.VERSION` chiede a tutti di riaccettare al prossimo avvio.
- Il coach AI potrà leggere dati solo se `AccountInfo.aiCoachAllowed` è vero.

## Età minima

16 anni, verificati con il solo anno di nascita (minimizzazione). In Italia
l'età del consenso digitale è 14 anni (art. 2-quinquies Codice privacy): il
margine di un anno dovuto al solo anno di nascita resta sopra la soglia di
legge. Se l'età è inferiore l'anno non viene salvato e si può eliminare
l'account.

## Diritti dell'interessato, dentro l'app

- **Accesso e portabilità** (art. 15, 20): Impostazioni › Privacy e dati ›
  Scarica i tuoi dati, un JSON con profilo, attività, recupero, check-in,
  stato delle integrazioni, consensi e foto.
- **Rettifica** (art. 16): Profilo › Modifica profilo.
- **Revoca del consenso sanitario** (art. 7.3): il servizio scollega Garmin e
  cancella attività, recupero e check-in (`DELETE /account/health-data`);
  l'app resta bloccata finché il consenso non viene ridato.
- **Cancellazione** (art. 17): `DELETE /account` cancella tutto `users/{uid}`
  (token Garmin compresi) e il login Firebase; poi l'app svuota la cache
  locale di Firestore, esce e si riavvia.

## Minimizzazione e sicurezza

- Foto ritagliata e ridotta sul telefono: l'originale e i suoi metadati
  (posizione, data) non escono dal dispositivo.
- Password Garmin usata una volta sola; token cifrati (Fernet), mai leggibili
  dall'app.
- Nessun SDK di pubblicità o analytics.

## Da fare fuori dal codice

Vedi il documento per Shard: dati del titolare, revisione legale dei testi,
valutazione d'impatto (DPIA, art. 35), registro dei trattamenti (art. 30),
accettazione dei termini di trattamento dati di Google Cloud/Firebase,
pubblicazione dell'informativa a un indirizzo web (richiesto da Google Play e
da Health Connect), modulo Data safety di Google Play.
