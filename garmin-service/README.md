# Servizio Garmin di TriForge

Piccolo servizio FastAPI pensato per Cloud Run. L'app Android lo chiama con il
token Firebase dell'atleta; il servizio entra in Garmin Connect tramite la
libreria non ufficiale [python-garminconnect](https://github.com/cyberjunky/python-garminconnect)
e copia le attività in Firestore, dove l'app le legge.

## Perché gira lato server

La libreria usa email e password Garmin e produce token di lunga durata. Dentro
l'app sarebbero esposti; qui la password serve per una sola chiamata e non viene
mai salvata, e i token vengono cifrati (Fernet) prima di arrivare in Firestore,
in un documento che l'app non può leggere.

## Endpoint

| Metodo | Percorso | Cosa fa |
| --- | --- | --- |
| GET | `/healthz` | Controllo di vita |
| POST | `/garmin/connect` | Avvia il login con `{email, password}`. Risponde `connected` oppure `mfa_required` |
| POST | `/garmin/mfa` | Completa il login con `{code}` |
| POST | `/garmin/sync` | Importa le attività recenti. Accetta `{daysBack}` |
| DELETE | `/garmin/connection` | Dimentica i token Garmin |
| POST | `/internal/sync-all` | Sincronizza tutti gli atleti collegati (per Cloud Scheduler) |

Tutti gli endpoint `/garmin/*` richiedono `Authorization: Bearer <ID token Firebase>`.
`/internal/sync-all` richiede l'header `X-Scheduler-Secret`.

## Dati in Firestore

Tutto sotto `users/{uid}`:

- `private/garmin` e `private/garminMfa`: token e stato MFA cifrati. L'app non li legge.
- `integrations/garmin`: stato del collegamento, in sola lettura per l'app.
- `activities/garmin_{id}`: attività normalizzate (`sport` vale `swim`, `bike`, `run` o `other`).

## Variabili d'ambiente

| Variabile | Obbligatoria | Descrizione |
| --- | --- | --- |
| `TOKEN_ENCRYPTION_KEY` | sì | Chiave Fernet. Da tenere in Secret Manager |
| `SCHEDULER_SECRET` | no | Segreto per `/internal/sync-all`. Se vuoto l'endpoint è disattivato |
| `INITIAL_SYNC_DAYS` | no | Giorni importati alla prima sincronizzazione (90) |
| `RESYNC_OVERLAP_DAYS` | no | Giorni riletti a ogni sincronizzazione successiva (3) |
| `RATE_LIMIT_COOLDOWN_SECONDS` | no | Pausa dopo un 429 di Garmin (900) |

Generare la chiave:

```bash
python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
```

## Test

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
pytest
```

I test usano un Garmin finto e un archivio in memoria: non servono credenziali.

## Deploy su Cloud Run

Dal progetto Google Cloud collegato a Firebase:

```bash
gcloud run deploy triforge-garmin \
  --source garmin-service \
  --region europe-west1 \
  --allow-unauthenticated \
  --set-secrets TOKEN_ENCRYPTION_KEY=garmin-token-key:latest,SCHEDULER_SECRET=garmin-scheduler-secret:latest
```

`--allow-unauthenticated` apre solo il livello di rete: ogni richiesta viene
comunque verificata con il token Firebase. Il service account di Cloud Run deve
avere il ruolo `Cloud Datastore User` per leggere e scrivere in Firestore.

## Limiti noti

- La libreria non è ufficiale: Garmin può cambiare il login o bloccare gli
  accessi automatici. Tutto il codice che la tocca sta in `app/gateway.py`.
- Dopo un 429 il servizio non contatta Garmin per 15 minuti per quell'atleta.
- Se i token vengono revocati, lo stato passa a `reconnect_required` e l'atleta
  deve ricollegare l'account.
