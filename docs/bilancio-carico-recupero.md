# Bilancio carico-recupero

L'indicatore di TriForge per il sovrallenamento. Il ragionamento completo, con
le fonti, è nello studio [studi/overtraining.md](studi/overtraining.md): qui
c'è cosa è stato implementato, dove, e cosa resta fuori.

## Cosa misura

Un punteggio da 0 a 100: **più è alto, più segnali di fatica accumulata**. Non
stima la probabilità di overtraining, che nessuna app può diagnosticare. Dice
se il corpo risponde al carico come al solito e, se no, se il carico recente
lo spiega.

| Colore | Media a 3 giorni | Etichetta |
| --- | --- | --- |
| Verde | sotto 30 | Nella norma |
| Giallo | 30-54 | Attenzione |
| Arancione | 55-74 | Fatica accumulata |
| Rosso | 75 e oltre, solo con livello 2+ e affidabilità almeno media | Segnali persistenti |

## Componenti e pesi

| Componente | Peso | Dato | Calcolo |
| --- | --- | --- | --- |
| Check-in | 0.25 | 5 voci da 1 a 7 (sonno, stanchezza, dolori, stress, poca voglia) | z della media a 7 giorni sulla propria norma, più un ancoraggio assoluto |
| HRV | 0.25 | RMSSD notturno (Garmin `lastNightAvg`) | z di ln(RMSSD) a 7 giorni, più la variabilità (CV) aumentata di oltre il 25% |
| FC a riposo | 0.15 | Garmin `get_rhr_daily` o Health Connect | z della media a 7 giorni |
| Sonno | 0.15 | durata del sonno | z della media a 7 giorni più il debito di sonno settimanale rispetto a max(mediana, 7 h) |
| Carico | 0.15 | attività | forma (CTL-ATL)/CTL, crescita di CTL in 7 giorni, monotonia di Foster |
| Efficienza | 0.05 | uscite costanti in bici (potenza/FC) e di corsa (velocità/FC) | calo oltre il 3% delle ultime 2 settimane rispetto alle 6 precedenti |

Ogni marker si confronta con la **propria norma**: mediana e MAD scalata dei
giorni da t-60 a t-8 (almeno 14 valori), contro la media degli ultimi 7 giorni
(almeno 4 valori). La deviazione vale 0 entro mezza deviazione dalla norma e
arriva a 1 a 2,5 deviazioni. Quando una componente manca, i pesi delle altre
vengono riscalati e l'affidabilità scende.

Il carico di ogni seduta è in TSS equivalenti (100 = un'ora alla soglia):
potenza e FTP per la bici, passo soglia per la corsa, CSS per il nuoto (al
cubo, sul tempo effettivamente nuotato e non su quello totale), altrimenti il Training Load di Garmin riscalato sulle sedute
dell'atleta, altrimenti la sola durata. Per questo FTP, passo soglia e CSS nel
profilo rendono il calcolo più preciso.

## Lettura carico × risposta

- **Assorbe il carico**: carico alto, risposta normale.
- **Fatica coerente col carico**: normale in un blocco di carico.
- **Segnali non spiegati dal carico**: sonno, stress, malattia, alimentazione.
- **Ancora fuori norma dopo lo scarico**: il quadro più compatibile con un
  overreaching non funzionale.
- **HRV insolitamente alta con altri segnali di fatica**: non va letta come
  "super recupero".
- **Malattia** (dal check-in): l'indice va in pausa e quei giorni escono dalle
  norme.

## Avvisi

Un giorno solo non basta mai.

- **Livello 1**: punteggio ≥ 30 in 3 degli ultimi 5 giorni, con almeno un
  segnale fuori norma.
- **Livello 2**: ≥ 55 in 5 degli ultimi 7, con almeno due segnali fuori norma
  in 4 di quei giorni e affidabilità almeno media; oppure 7 giorni di segnali
  non spiegati dal carico.
- **Livello 3**: livello 2 per 14 giorni, oppure risposta ancora alterata dopo
  lo scarico, oppure HRV in calo, check-in in peggioramento ed efficienza giù
  nelle ultime 2 settimane.
- Si scende di livello solo dopo 3 giorni sotto soglia.
- Con affidabilità bassa o nelle prime settimane (valore provvisorio) al
  massimo livello 1.

## Avviamento

- Meno di 14 giorni di HRV, FC a riposo o check-in: solo il carico.
- 14-27 giorni: valore provvisorio.
- Da 28 giorni: indice completo.

Il servizio Garmin importa 120 giorni alla prima sincronizzazione, quindi chi
porta l'orologio di notte ha l'indice completo da subito.

## Dove sta nel codice

- `android/domain/.../RecoveryBalance.kt`: il calcolo, Kotlin puro e testato.
- `android/domain/.../SessionLoad.kt`: il carico di ogni seduta.
- `android/domain/.../Wellness.kt`: dati giornalieri e check-in.
- `android/app/.../ui/recovery/`: card in Oggi, dettaglio, check-in.
- `garmin-service/app/gateway.py` e `normalize.py`: HRV, FC a riposo e sonno
  da Garmin in `users/{uid}/wellness/garmin_{data}`.
- `HealthConnectRepository.importWellness`: gli stessi dati da Health Connect
  in `wellness/hc_{data}`. Garmin non condivide l'HRV con Health Connect.

## Non ancora implementato

Previsti dallo studio, rimandati perché mancano i dati:

- fatica percepita (RPE) per seduta e carico da sRPE;
- tempo nelle zone di FC (hrTSS) e serie temporali per il decoupling;
- regolarità dell'orario del sonno e respirazione notturna come segnale di
  malattia;
- blocco di carico pianificato, che renderebbe informativi gli avvisi attesi;
- flag per alcol, viaggi e gare da escludere dalle norme.

Pesi e soglie sono punti di partenza: lo studio suggerisce di rivederli dopo
3-6 mesi di uso reale (§9.12).
