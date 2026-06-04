# TriForge Crypto Triathlon Project

Versione: 2026-06-04  
Stato: proposta strategica e blueprint prodotto  
Scope: web app/mobile friendly triathlon, Garmin-first, Move-to-Earn/GameFi, token futuro su Solana o rete alternativa

## 1. Sintesi esecutiva

TriForge puo diventare una piattaforma triathlon-first in cui gli atleti sincronizzano attivita reali da smartwatch, completano missioni, partecipano a sfide e guadagnano ricompense digitali. La parte crypto va introdotta in modo progressivo: prima XP e Forge Credits off-chain, poi token su testnet, infine token reale solo dopo aver validato sostenibilita economica, anti-cheat, compliance e domanda.

La raccomandazione e:

1. Partire con MVP off-chain gia coerente con la futura economia.
2. Integrare Garmin solo dopo richiesta ufficiale di accesso API e valutazione dei costi.
3. Usare Solana come rete preferita per la fase token, per costi bassi, rapidita e buona esperienza mobile.
4. Tenere Base come alternativa EVM forte se diventa prioritario usare wallet, exchange, tooling e partner gia Ethereum-compatible.
5. Evitare qualsiasi promessa di rendimento. Il token deve essere utility/community/reward, non investimento.
6. Non far "perdere token" agli utenti in modo automatico. Le perdite devono essere opt-in in sfide con entry stake volontario, regole chiare e revisione legale, per evitare rischi da gioco a premi, gambling o strumenti finanziari.

TriForge non deve essere una copia tecnica di SwimForge. Deve mantenere solo l'intuizione prodotto: sport, progressione, community, badge, missioni. Il dominio diventa triathlon, con economia basata su swim, bike, run e bilanciamento delle discipline.

## 2. Visione prodotto

TriForge e una app per triatleti che trasforma allenamenti verificati in progressione, status e ricompense.

### Utente target

- Triatleta age group che usa Garmin, Polar, Suunto, Wahoo o Apple Watch.
- Atleta che si allena per sprint, olimpico, 70.3 o Ironman.
- Allenatore o team che vuole creare challenge.
- Brand endurance interessato a campagne performance-based.
- Race organizer che vuole engagement prima e dopo gara.

### Promessa prodotto

"Allena le tre discipline, forgia il tuo profilo atleta, completa missioni e sblocca ricompense digitali senza trasformare lo sport in speculazione."

### Principi

- Il valore principale e sportivo, non finanziario.
- Le ricompense premiano costanza, qualita e bilanciamento, non solo volume.
- Le attivita manuali possono dare XP, ma non token reale.
- Il token non deve incentivare doping digitale, overtraining o frodi.
- L'economia deve avere emissioni limitate, sink reali e ricavi esterni.

## 3. Fattibilita

### Fattibilita tecnica

Alta per l'MVP off-chain:

- Next.js + Supabase sono sufficienti per auth, profili, attivita, XP, missioni e ledger simulato.
- La web app mobile friendly e piu rapida da lanciare rispetto a un'app nativa.
- Vercel + Supabase coprono bene la fase pre-token e community beta.

Media per Garmin:

- Garmin offre API per dati health/activity, ma l'accesso richiede richiesta, revisione e accordo commerciale.
- La piattaforma Garmin Health API e orientata a organizzazioni, research, wellness, healthcare e programmi approvati, non a scraping libero.
- Serve pianificare tempi e costi di onboarding, oltre a privacy, consenso utente e data retention.

Bassa-media per token reale:

- Creare un token e tecnicamente facile.
- Creare un'economia sostenibile, legale e resistente agli abusi e difficile.
- La parte on-chain richiede audit, legal review, treasury management, policy anti-market-manipulation e supporto utenti.

### Fattibilita legale e compliance

Il progetto entra in area sensibile se:

- Gli utenti comprano token aspettandosi rendimento.
- Le sfide prevedono quota di ingresso e premio monetizzabile.
- Il token e venduto prima che esista utility reale.
- Il marketing suggerisce guadagni, investimento o apprezzamento del prezzo.
- L'app raccoglie dati sanitari o biometrici senza consenso e governance adeguati.

Per un progetto europeo o rivolto a utenti UE, MiCA impone requisiti per emittenti di crypto-asset e crypto-asset service providers. Per utenti USA, il rischio securities/commodities/consumer protection resta rilevante. Serve consulenza legale specializzata prima di token sale, listing, staking, burn/buyback o challenge con premi monetari.

### Fattibilita economica

Il modello M2E puro tende a rompersi se i token sono pagati principalmente da nuovi utenti. TriForge deve evitare questa dinamica.

La sostenibilita deve arrivare da:

- Subscription premium.
- Sponsorship di missioni.
- Club/team SaaS.
- Race organizer packages.
- Affiliate gear.
- Marketplace commissioni.
- Eventi e challenge brandizzate.

Il token puo distribuire parte del valore, ma non deve essere l'unica ragione per usare l'app.

## 4. Scelta blockchain

### Raccomandazione

Usare Solana per la fase token di TriForge, con Base come seconda opzione strategica.

Solana e preferibile se TriForge punta a:

- Micro-ricompense frequenti.
- Esperienza mobile rapida.
- Claim piccoli e gamificati.
- Costi on-chain bassi.
- Community crypto-native.

Base e preferibile se TriForge punta a:

- Tooling EVM standard.
- Integrazione piu semplice con smart contract Solidity.
- Partner e wallet Ethereum-compatible.
- Distribuzione tramite ecosistema Coinbase e consumer crypto EVM.

Polygon resta valida, ma oggi la differenziazione e meno forte rispetto a Solana per micro-rewards e rispetto a Base per consumer EVM.

### Comparazione

| Criterio | Solana | Base | Polygon PoS |
|---|---|---|---|
| Esperienza micro-transazioni | Molto forte | Buona, ma dipende da gas L2 | Buona |
| Costi utente | Generalmente bassi | Bassi rispetto a Ethereum mainnet | Bassi |
| Velocita percepita | Molto alta | Alta | Alta |
| Tooling smart contract | Rust/Anchor, curva maggiore | Solidity/EVM, curva piu semplice | Solidity/EVM |
| Wallet consumer | Phantom, Backpack, Solflare | Coinbase Wallet, MetaMask, Rainbow | MetaMask, molte integrazioni |
| Token standard | SPL Token / Token-2022 | ERC-20 | ERC-20 |
| Mobile UX | Forte, anche per ecosystem Solana | Forte via Coinbase Wallet | Buona |
| Audit e dev talent | Buono ma piu specialistico | Ampio bacino EVM | Ampio bacino EVM |
| Fit TriForge | Migliore per reward frequenti | Migliore per compatibilita EVM | Solida alternativa |

### Decisione tecnica consigliata

Fase 1: niente token reale.

- XP off-chain.
- Forge Credits off-chain.
- Missioni e sfide simulate.
- Ledger interno auditabile.

Fase 2: Solana devnet.

- Wallet opzionale.
- Token fittizio su devnet.
- Claim simulator.
- Test anti-cheat e reward throttling.

Fase 3: Solana mainnet.

- Token SPL standard o Token-2022 con estensioni minime.
- Evitare programmi custom fino a quando non sono davvero necessari.
- Se servono logiche sofisticate, usare smart contract solo dopo audit.

### Token standard

Per un primo token TriForge, preferire:

- SPL Token standard se serve un utility token semplice.
- Token-2022 solo se servono estensioni specifiche, per esempio metadata nativi o transfer fee.

Non usare all'inizio:

- Transfer hooks complessi.
- Permanent delegate con poteri ampi.
- Meccanismi di slashing automatico.
- Staking con rendimento promesso.

Queste funzioni aumentano complessita, costo audit e rischio percepito.

## 5. Economia: XP, Forge Credits e token

TriForge deve separare tre livelli:

### XP

XP e il punteggio sportivo non trasferibile.

- Non ha valore economico diretto.
- Serve per livelli, status, ranking e accesso missioni.
- Puo derivare anche da attivita manuali, ma con limiti.
- Non deve essere convertibile direttamente in token.

### Forge Credits

Forge Credits sono crediti off-chain simulati o utility interna.

- Servono nell'MVP per testare ledger, missioni e sfide.
- Non sono crypto-asset.
- Non sono trasferibili.
- Non sono riscattabili in denaro.
- Preparano UX, accounting e anti-cheat.

### Token futuro

Nome provvisorio: `$TRIF` oppure `$FORGE`.  
Preferenza: `$TRIF`, per evitare collisioni con molti token "FORGE" e legare meglio il brand TriForge.

Il token deve avere utility concreta:

- Entry opt-in a sfide premium.
- Claim di badge digitali.
- Sconti su servizi partner.
- Accesso a eventi/community.
- Acquisto di cosmetic digitali.
- Reward sponsor mission.
- Club treasury per team e coach.

Non deve essere presentato come:

- Investimento.
- Rendita passiva.
- Salario per allenarsi.
- Garanzia di guadagno.

## 6. Tokenomics proposta

### Supply

Supply totale consigliata: 1,000,000,000 TRIF.

Motivo:

- Numero leggibile per community e rewards.
- Abbastanza granularita per micro-rewards.
- Supply fissa, piu facile da comunicare.

### Allocazione

| Allocazione | % | Token | Note |
|---|---:|---:|---|
| Community rewards | 35% | 350,000,000 | Emissioni attivita, missioni, challenge, beta rewards |
| Treasury e runway | 20% | 200,000,000 | Riserva operativa, partnership, liquidita futura |
| Team e founders | 15% | 150,000,000 | Vesting 4 anni, cliff 12 mesi |
| Ecosystem/partners | 12% | 120,000,000 | Race, club, coach, brand endurance |
| Liquidity provision | 8% | 80,000,000 | DEX/CEX liquidity, con policy trasparente |
| Advisors/legal/audit grants | 5% | 50,000,000 | Advisor, audit, grants mirati |
| User acquisition reserve | 5% | 50,000,000 | Campagne controllate, non airdrop indiscriminati |

### Vesting

Team:

- 12 mesi cliff.
- 48 mesi vesting totale.
- Nessuna vendita prima del lancio mainnet e utility reale.

Advisors:

- 6 mesi cliff.
- 24-36 mesi vesting.

Community rewards:

- Distribuzione per epoch.
- Budget decrescente.
- Reward cap per atleta.
- Bonus legati a qualita e bilanciamento, non solo distanza.

### Emissioni community

Esempio sui 350M community rewards:

| Periodo | % community pool | Token |
|---|---:|---:|
| Beta e genesis | 5% | 17,500,000 |
| Anno 1 | 18% | 63,000,000 |
| Anno 2 | 16% | 56,000,000 |
| Anno 3 | 14% | 49,000,000 |
| Anno 4 | 11% | 38,500,000 |
| Anno 5 | 8% | 28,000,000 |
| Riserva anti-inflazione | 28% | 98,000,000 |

La riserva non deve essere promessa. Deve essere attivabile solo se:

- Retention reale e forte.
- Ricavi sponsor/subscription crescono.
- Anti-cheat e stabile.
- Treasury runway e sano.

### Formula reward

Non premiare solo chilometri. Un modello solo volume favorisce abuso, overtraining e atleti gia forti.

Reward base:

```text
activity_score =
  discipline_weight
  * verified_effort_points
  * quality_multiplier
  * balance_multiplier
  * consistency_multiplier
  * anti_cheat_confidence
```

Esempio:

- Swim: metri, durata, frequenza e passo normalizzati.
- Bike: distanza, durata, dislivello, HR/power se disponibile.
- Run: distanza, durata, passo, HR e dislivello.

Multiplier:

- `quality_multiplier`: 0.5-1.5, valuta sessioni realistiche e complete.
- `balance_multiplier`: 0.8-1.3, premia chi non fa solo una disciplina.
- `consistency_multiplier`: 1.0-1.2, premia streak sostenibili.
- `anti_cheat_confidence`: 0-1, abbassa reward se dati sospetti.

Cap:

- Daily reward cap.
- Weekly reward cap.
- Per-discipline cap.
- New-user cap fino a reputazione minima.

### Reward distribution

Non dare token automaticamente ad ogni attivita. Meglio usare:

- Punti off-chain calcolati subito.
- Claim periodici settimanali.
- Reward pool fisso per epoch.
- Distribuzione proporzionale al punteggio degli utenti verificati.

Formula:

```text
weekly_user_reward =
  weekly_pool * user_verified_score / total_verified_score
```

Vantaggio:

- Emissione prevedibile.
- Nessuna esplosione inflattiva se aumentano le attivita.
- Migliore controllo anti-cheat prima del claim.

## 7. Challenge e perdita token

L'idea "vincere/perdere token" e potente ma rischiosa.

Modello consigliato:

- Le challenge gratuite danno XP, badge e Forge Credits.
- Le challenge tokenizzate sono opt-in.
- L'utente deposita volontariamente una entry fee/stake.
- Se non completa, perde solo lo stake della challenge, non il saldo generale.
- Le regole devono essere visibili prima dell'ingresso.
- Devono esserci finestre di contestazione e anti-cheat review.

Tipi challenge:

1. Completion challenge: completa X sessioni in 14 giorni.
2. Balance challenge: almeno 1 swim, 1 bike, 1 run ogni settimana.
3. Distance challenge: raggiungi volume target sostenibile.
4. Race prep challenge: piano 6 settimane verso evento.
5. Club challenge: ranking interno team.
6. Sponsor challenge: missione sponsorizzata con reward pool esterno.

Evitare:

- Sfide winner-takes-all con stake alto.
- Scommesse su prestazioni di terzi.
- Challenge basate solo su denaro.
- Slashing automatico senza revisione.

## 8. Anti-cheat e validazione attivita

TriForge vive o muore sulla fiducia del dato.

### Livelli di affidabilita

| Livello | Fonte | Reward token |
|---|---|---|
| Manuale | Inserimento utente | No token, solo XP limitato |
| File importato | GPX/FIT caricato | Solo se controllato, reward basso |
| Wearable sync | Garmin/Strava/altro | Reward pieno con score confidence |
| Verified device + HR/GPS | Garmin con metadati completi | Reward massimo |

### Controlli anti-cheat

- Soglie passo/velocita realistiche per disciplina.
- HR coerente con durata e intensita.
- GPS plausibile e non teleport.
- Distanza e durata minime.
- No doppio conteggio della stessa attivita.
- Device fingerprint e activity external id.
- Cooldown tra sessioni.
- Rilevamento pattern bot o bulk import.
- Review manuale per top reward.
- Penalita off-chain prima di qualsiasi azione token.

### Salute atleta

Non incentivare comportamenti dannosi:

- Cap giornalieri.
- Rest day mission.
- Bonus qualita invece di volume infinito.
- Avvisi se l'utente tenta challenge eccessive.
- Nessun premio extra per training load estremo.

## 9. Integrazione Garmin

### Stato realistico

Garmin non e una API pubblica libera come un endpoint OAuth consumer immediato. Serve accesso tramite programma/accordo approvato.

Azioni:

1. Preparare pitch Garmin:
   - Descrizione prodotto.
   - Casi d'uso.
   - Dati richiesti.
   - Privacy e sicurezza.
   - Volume utenti previsto.
   - Modello commerciale.
2. Richiedere accesso Garmin Health API.
3. Valutare costi e contratto.
4. Implementare solo dopo ambiente dedicato e policy privacy.

### Piano fallback

Se Garmin richiede tempi lunghi:

- Fase 1: manual entry e FIT/GPX upload.
- Fase 2: Strava API come aggregatore, se conforme ai loro termini.
- Fase 3: Garmin quando approvato.
- Fase 4: Apple Health / Health Connect via mobile app nativa o wrapper.

## 10. Architettura tecnica

### MVP attuale

- Next.js App Router.
- Supabase Auth.
- Supabase Postgres.
- Tabelle: profiles, activities, xp_events, forge_credit_ledger, missions, mission_completions.
- Reward off-chain.
- Vercel deploy.

### Architettura target

```text
Garmin / data source
  -> ingestion API
  -> activity normalization
  -> anti-cheat engine
  -> scoring engine
  -> XP ledger
  -> mission engine
  -> reward epoch engine
  -> claim queue
  -> Solana token distributor
```

### Servizi futuri

- `activity_ingestion`: importa e normalizza attivita.
- `anti_cheat`: assegna confidence score.
- `mission_engine`: valuta missioni e completamenti.
- `reward_epoch`: chiude epoch settimanali.
- `wallet_service`: collega wallet e gestisce claim.
- `compliance_admin`: blocchi, review, audit, export dati.

### Database futuro

Tabelle aggiuntive consigliate:

- `connected_accounts`
- `external_activities`
- `activity_validation_results`
- `reward_epochs`
- `reward_epoch_scores`
- `token_claims`
- `wallets`
- `challenge_entries`
- `challenge_results`
- `sponsor_campaigns`
- `audit_events`

## 11. Sostenibilita economica

### Problema dei M2E

Molti progetti M2E collassano perche:

- Pagano token per attivita senza ricavi reali.
- I token vengono venduti appena ricevuti.
- L'acquisizione utenti dipende da speculazione.
- Le emissioni superano domanda e sink.

TriForge deve progettare il token come reward limitato e utility, non come stipendio.

### Fonti ricavo

| Ricavo | Fase | Note |
|---|---|---|
| Premium subscription | MVP+ | Metriche avanzate, piani, challenge premium |
| Club/team SaaS | MVP+ | Dashboard coach, team challenge, report |
| Sponsor missions | Token beta | Brand finanzia reward pool |
| Race organizer packages | Token beta | Engagement pre/post race |
| Affiliate gear | MVP+ | Scarpe, bike gear, nutrition |
| Marketplace fee | Token beta | Fee su servizi partner |
| Digital collectibles | Token beta | Badge estetici, non pay-to-win |

### Token sinks

| Sink | Qualita | Rischio |
|---|---|---|
| Entry challenge opt-in | Alto | Legal/gambling se mal progettato |
| Cosmetic digitali | Alto | Basso |
| Club season pass | Medio-alto | Medio |
| Badge mint fee | Medio | Basso |
| Partner discount redemption | Alto | Dipende da partner |
| Burn generico | Basso | Rischia di sembrare market manipulation |

Preferire utility concreta a burn narrativi.

### Treasury policy

Regola conservativa:

- Non usare token treasury per coprire spese ordinarie senza disclosure.
- Tenere runway fiat separato.
- Pubblicare report trimestrale di emissioni, treasury e reward.
- Bloccare emissioni aggiuntive se retention o ricavi non supportano l'economia.

## 12. Costi da sostenere

Le cifre sotto sono range indicativi. Vanno confermate con fornitori, consulenti e contratti.

### Fase 0 - MVP off-chain

| Voce | Range |
|---|---:|
| Vercel Hobby | 0 EUR/mese |
| Supabase Free | 0 EUR/mese |
| Dominio | 10-30 EUR/anno |
| Email transactional | 0-20 EUR/mese |
| Design/logo/assets | 0-1,500 EUR |
| Legal prima review | 1,500-5,000 EUR |
| Marketing test | 500-3,000 EUR |

### Fase 1 - Beta con wearable

| Voce | Range |
|---|---:|
| Vercel Pro | 20 USD/utente team/mese |
| Supabase Pro | 25 USD/mese+ |
| Garmin API access | Da quotare con Garmin |
| Privacy/legal dati sportivi | 3,000-12,000 EUR |
| Monitoring/logging | 0-100 EUR/mese |
| Community/Discord/tools | 0-100 EUR/mese |
| Marketing beta | 2,000-10,000 EUR |

### Fase 2 - Token devnet/mainnet beta

| Voce | Range |
|---|---:|
| Smart contract/token setup | 2,000-15,000 EUR se semplice |
| Custom Solana program | 15,000-80,000 EUR |
| Audit | 8,000-60,000 EUR |
| Legal token/MiCA/USA | 10,000-60,000 EUR |
| RPC/indexing | 50-1,000 EUR/mese |
| Wallet/onboarding UX | 5,000-25,000 EUR |
| KYC/AML se necessario | 1-3 EUR per verifica, variabile |
| Marketing token beta | 10,000-50,000 EUR |

### Fase 3 - Lancio pubblico

| Voce | Range |
|---|---:|
| Security audit esteso | 25,000-120,000 EUR |
| Legal continuo | 3,000-15,000 EUR/mese |
| Customer support | 1,000-8,000 EUR/mese |
| Paid acquisition | 20,000-150,000 EUR |
| Partnership race/club | 5,000-100,000 EUR |
| Liquidity operations | Da definire con advisor legale/finanziario |

## 13. Strategia marketing

### Posizionamento

Non vendere "allenati e guadagna soldi". Vendere:

- Progressione sportiva verificata.
- Sfide triathlon reali.
- Community e status.
- Ricompense da partner endurance.
- Token come layer futuro, non come motivo unico.

### Messaggio

Claim consigliato:

"The triathlon training economy. Verify your effort, complete missions, earn status and unlock rewards."

Versione italiana:

"La training economy del triathlon: verifica i tuoi allenamenti, completa missioni e sblocca ricompense."

### Campagne

#### Genesis Athlete Cohort

Obiettivo: 300-1,000 triatleti beta.

- Landing waitlist.
- Badge Genesis non trasferibile.
- 8 settimane di challenge swim/bike/run.
- Leaderboard senza token reale.
- Feedback strutturato ogni 2 settimane.

KPI:

- Signup to activated athlete > 40%.
- Weekly active athlete > 35%.
- 3+ activities/month > 50%.
- NPS > 35.

#### Club Founders Program

Obiettivo: 10-25 club/team.

- Dashboard club.
- Challenge interna.
- Report settimanale.
- Founder club badge.
- Prezzo beta gratuito o simbolico.

KPI:

- Club retention 8 settimane.
- Numero atleti attivi per club.
- Numero challenge create dai coach.

#### Race Prep Missions

Obiettivo: legare TriForge a eventi reali.

- Sprint prep 4 settimane.
- Olympic prep 8 settimane.
- 70.3 prep 12 settimane.
- Missioni sponsor per nutrition/gear.

KPI:

- Completion mission.
- Repeat challenge.
- Conversion premium.

#### Sponsor Missions

Obiettivo: finanziare reward pool con ricavi esterni.

Esempi:

- Brand nutrition: completa 3 sessioni e ricevi coupon.
- Bike shop: challenge dislivello mensile.
- Race organizer: mission pre-race e finisher digital badge.

KPI:

- Cost per completed mission.
- Sponsor renewal.
- Redemption coupon.

### Canali

- Instagram e TikTok per storytelling atleti.
- YouTube Shorts per mission recap.
- Reddit e forum triathlon con attenzione a regole community.
- Newsletter settimanale "Forge Report".
- Partnership con coach e club.
- Race expo e QR code onboarding.
- Strava club ufficiale se conforme alle policy.
- Discord/Telegram per crypto community, ma moderato per evitare hype speculativo.

### Calendario 90 giorni

Settimane 1-2:

- Landing waitlist.
- Founder athlete application.
- Annuncio roadmap no-token-first.

Settimane 3-4:

- Prima challenge off-chain.
- Interviste utenti.
- 3 coach ambassador.

Settimane 5-8:

- Club beta.
- Missioni bilanciamento discipline.
- Report pubblico progressi beta.

Settimane 9-12:

- Token design paper.
- Solana devnet demo.
- Sponsor mission pilot.
- Preparazione legal review.

## 14. Roadmap prodotto

### M0 - Oggi

Gia implementato:

- Web app Next.js.
- Landing.
- Auth Supabase-ready.
- Dashboard.
- Attivita manuali.
- Missioni.
- Wallet simulato.
- Ledger Forge Credits.
- Nessun token reale.

### M1 - MVP operativo

Durata: 2-4 settimane.

- Collegare Supabase dedicato TriForge.
- Implementare profilo atleta completo.
- Salvare attivita reali in DB.
- Mission engine stabile.
- Admin view minima.
- Export audit ledger.

### M2 - Wearable beta

Durata: 4-10 settimane, dipende da Garmin.

- Richiesta accesso Garmin.
- Fallback FIT/GPX upload.
- Normalizzazione attivita.
- Activity confidence score.
- Anti-cheat base.
- Challenge off-chain.

### M3 - Devnet token

Durata: 4-8 settimane.

- Wallet connect.
- Token devnet.
- Claim weekly reward.
- Simulazione sponsor pool.
- Nessun valore reale.
- Audit interno.

### M4 - Legal/token readiness

Durata: 6-12 settimane.

- Legal opinion.
- Tokenomics final.
- Terms, privacy, risk disclosure.
- Smart contract audit.
- Treasury policy.
- Challenge compliance.

### M5 - Mainnet beta

Durata: 8-16 settimane.

- Token reale limitato.
- Reward pool piccolo.
- Utenti verificati.
- Claim controllati.
- Partner rewards.
- No public speculative launch.

## 15. Compliance e policy

### Regole comunicazione

Vietare:

- "Earn money training".
- "Passive income".
- "Guaranteed rewards".
- "Token will increase".
- "Investment opportunity".

Consentire:

- "Earn in-app rewards".
- "Unlock utility".
- "Participate in verified challenges".
- "Community token experimental phase".

### Documenti necessari

- Privacy Policy aggiornata per wearable data.
- Terms of Service con challenge rules.
- Token risk disclosure.
- Reward program rules.
- Anti-cheat policy.
- Data deletion policy.
- Cookie policy.
- Sponsor campaign terms.

### Dati personali

I dati di training possono rivelare salute, posizione, routine e abitudini. Serve:

- Consenso esplicito.
- Minimizzazione dati.
- Possibilita di cancellazione.
- Retention limitata.
- Access control forte.
- Separazione dati sportivi e wallet.

## 16. KPI

### Product-market fit

- Weekly active athletes.
- Activities per active athlete.
- 4-week retention.
- Challenge completion.
- Club activation.
- Coach adoption.

### Economy health

- Reward claimed vs reward earned.
- Token sell pressure, quando esiste token.
- Sink usage.
- Sponsor-funded reward percentage.
- Treasury runway.
- Emission per active athlete.

### Anti-cheat

- Suspicious activity rate.
- Reward blocked.
- False positive rate.
- Manual review load.
- Duplicate activity rate.

### Marketing

- CAC.
- Signup to first activity.
- First activity to second week retention.
- Club lead conversion.
- Sponsor renewal.

## 17. Risk register

| Rischio | Severita | Mitigazione |
|---|---|---|
| Garmin non approva accesso | Alta | Fallback FIT/GPX/Strava, pitch formale |
| Token considerato security | Alta | Utility reale, no rendimento, legal opinion |
| Challenge considerate gambling | Alta | Opt-in, regole, no betting, legal review |
| Economia inflazionistica | Alta | Epoch pool fisso, cap, sink, sponsor revenue |
| Cheating attivita | Alta | Device verification, anti-cheat, review |
| Overtraining incentivato | Media-alta | Cap, rest mission, quality score |
| UX crypto troppo difficile | Media | Off-chain first, wallet opzionale, claim periodici |
| Costi legal/audit alti | Media-alta | Ritardare token mainnet finche validato |
| Community speculativa | Media | Moderazione e messaggi no-investment |
| Privacy wearable data | Alta | Consenso, minimizzazione, security controls |

## 18. Piano decisionale

Decisioni da prendere prima del token reale:

1. Giurisdizione societaria e target utenti.
2. Nome token definitivo.
3. Solana vs Base finale.
4. Standard token.
5. Regole sfide con stake.
6. Policy reward e cap.
7. Budget legal/audit.
8. Garmin o fallback iniziale.
9. Partner sponsor iniziali.
10. Treasury governance.

## 19. Raccomandazione finale

TriForge e fattibile se viene costruito come sport product con reward economy, non come crypto project con sport app intorno.

La sequenza corretta e:

1. MVP off-chain e community beta.
2. Wearable verification e anti-cheat.
3. Sponsor-funded rewards.
4. Devnet token.
5. Legal/audit.
6. Mainnet limitata.
7. Solo dopo, token economy pubblica.

Solana e la scelta migliore per la visione attuale: reward frequenti, costi bassi, UX veloce e cultura crypto adatta. Base e la miglior alternativa se il progetto richiede compatibilita EVM e distribuzione piu vicina all'ecosistema Coinbase/Ethereum.

## 20. Fonti operative

- Solana fee structure: https://solana.com/docs/core/fees/fee-structure
- Solana Token-2022 / token extensions: https://solana.com/docs/tokens/extensions
- Base documentation: https://docs.base.org/
- Polygon PoS documentation: https://docs.polygon.technology/
- Garmin Activity API: https://developer.garmin.com/gc-developer-program/activity-api/
- Garmin Health API: https://developer.garmin.com/gc-developer-program/health-api/
- Garmin Connect Developer Program: https://developer.garmin.com/gc-developer-program/overview/
- ESMA MiCA: https://www.esma.europa.eu/esmas-activities/digital-finance-and-innovation/markets-crypto-assets-regulation-mica
- EUR-Lex MiCA Regulation: https://eur-lex.europa.eu/eli/reg/2023/1114/oj
- SEC crypto assets and cyber enforcement resources: https://www.sec.gov/securities-topics/crypto-assets
