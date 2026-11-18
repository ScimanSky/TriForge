# TriForge

App Android per triatleti: nuoto, bici e corsa in un unico posto, con dati presi
da Garmin e Health Connect, e in arrivo coaching AI, un coach nutrizionista AI e
una parte social.

## Stato

Passo 1 del piano, le fondamenta:

- account e profilo atleta (Firebase Auth)
- collegamento a Garmin Connect e sincronizzazione delle attività
- lettura da Health Connect come seconda via
- dashboard settimanale per nuoto, bici e corsa, con andamento delle ultime 8 settimane

Prossimi passi: coach AI di allenamento, coach AI nutrizionista (Firebase AI
Logic), poi social e Strava.

## Struttura

| Cartella | Contenuto |
| --- | --- |
| `android/app` | App Android: Kotlin, Jetpack Compose, Firebase, Health Connect |
| `android/domain` | Logica pura in Kotlin (statistiche, formati, deduplica), senza dipendenze Android |
| `garmin-service` | Servizio Python per Cloud Run che parla con Garmin Connect |
| `firebase` | Regole di sicurezza e indici di Firestore |
| `docs` | Note di architettura |

L'architettura è descritta in [docs/architettura.md](docs/architettura.md).

## Configurazione

Serve un progetto Firebase. I passaggi sono in [docs/setup.md](docs/setup.md); in breve:

1. Crea il progetto Firebase e aggiungi un'app Android con package `com.triforge.app`.
2. Scarica `google-services.json` in `android/app/` (il file non va nel repo).
3. Attiva Authentication (email e password) e Firestore.
4. Pubblica le regole: `firebase deploy --only firestore`.
5. Pubblica il servizio Garmin su Cloud Run e scrivi il suo URL in
   `android/local.properties` come `triforge.garminServiceUrl=https://...`.

Senza `google-services.json` l'app compila comunque e mostra una schermata che
spiega cosa manca.

## Sviluppo

```bash
# App Android
cd android
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug

# Servizio Garmin
cd garmin-service
pip install -r requirements-dev.txt
pytest
```

## Storia

Il repository è stato azzerato a novembre 2026. Il codice precedente (SwimForge
e l'MVP Move-to-Earn) resta nella storia git fino al commit `7265512`.
