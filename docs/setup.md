# Configurazione del progetto

Questi passaggi servono una volta sola. Richiedono un account Google.

## Progetto in uso

La configurazione qui sotto è già stata fatta il 19 novembre 2026:

| Cosa | Valore |
|------|--------|
| Progetto Firebase | `triforge-app` |
| App Android | `com.triforge.app` |
| Firestore | database `(default)`, `europe-west1` |
| Accesso | Email/Password |
| Servizio Garmin | Cloud Run `triforge-garmin`, `europe-west1`, da 0 a 2 istanze |
| Service account | `triforge-garmin@triforge-app.iam.gserviceaccount.com`, con `Cloud Datastore User` sul progetto e `Secret Manager Secret Accessor` sui due segreti |
| Sincronizzazione | Cloud Scheduler `triforge-garmin-sync`, ogni giorno alle 05:00 (Europe/Rome) |

`google-services.json` e `android/local.properties` restano solo sul computer:
non vanno nel repository. Il progetto predefinito per la CLI di Firebase è in
`.firebaserc`.

Per pubblicare di nuovo il servizio dopo una modifica basta ripetere il
comando `gcloud run deploy` del punto 3. Il controllo di vita è
`GET /health`.

## 1. Progetto Firebase

1. Apri la [console Firebase](https://console.firebase.google.com) e crea un progetto.
2. Aggiungi un'app Android con nome pacchetto `com.triforge.app`.
3. Scarica `google-services.json` e copialo in `android/app/`.
4. In **Authentication** attiva il metodo Email/Password.
5. In **Firestore Database** crea il database in modalità produzione, regione `eur3` o `europe-west1`.
6. Passa al piano Blaze: serve per Cloud Run. Con l'uso di una persona o di
   pochi amici si resta quasi sempre nella fascia gratuita.

## 2. Regole e indici di Firestore

```bash
npm install -g firebase-tools
firebase login
firebase use <id-progetto>
firebase deploy --only firestore
```

## 3. Servizio Garmin su Cloud Run

```bash
gcloud config set project <id-progetto>

# Chiave per cifrare i token Garmin
python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())" \
  | gcloud secrets create garmin-token-key --data-file=-

# Segreto per la sincronizzazione programmata
openssl rand -hex 32 | gcloud secrets create garmin-scheduler-secret --data-file=-

gcloud run deploy triforge-garmin \
  --source garmin-service \
  --region europe-west1 \
  --allow-unauthenticated \
  --set-secrets TOKEN_ENCRYPTION_KEY=garmin-token-key:latest,SCHEDULER_SECRET=garmin-scheduler-secret:latest
```

Il comando stampa l'URL del servizio. Il service account usato da Cloud Run
deve poter leggere i due segreti (`Secret Manager Secret Accessor`) e scrivere
in Firestore (`Cloud Datastore User`).

## 4. Collegare l'app al servizio

In `android/local.properties` aggiungi:

```properties
triforge.garminServiceUrl=https://triforge-garmin-xxxxx.a.run.app
```

## 5. Sincronizzazione automatica (facoltativa)

Per importare le attività ogni mattina senza aprire l'app:

```bash
gcloud scheduler jobs create http triforge-garmin-sync \
  --location europe-west1 \
  --schedule "0 5 * * *" \
  --time-zone "Europe/Rome" \
  --uri "https://triforge-garmin-xxxxx.a.run.app/internal/sync-all" \
  --http-method POST \
  --headers "X-Scheduler-Secret=<valore del segreto>"
```

## 6. Provare l'app

Apri la cartella `android` con Android Studio e avvia l'app su un telefono con
Android 8 o successivo. Health Connect è integrato da Android 14; sulle versioni
precedenti va installato dal Play Store.
