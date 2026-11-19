# Overtraining, overreaching e monitoraggio del carico negli sport di endurance

**Studio critico per progettare un indicatore di rischio di sovraccarico calcolabile con i dati di Garmin Connect e di Android Health Connect**

Versione 1.0, novembre 2026

---

## Come leggere questo documento

Il documento ha due parti.

- **Parte generale (sezioni 1-8).** Scienza dell'overtraining, modelli di carico, marker fisiologici, indicatori di prestazione, prodotti commerciali e fonti di dati (Garmin e Health Connect). Vale per qualsiasi app di endurance, anche per un solo sport: triathlon, sola corsa, solo nuoto.
- **Parte applicativa (sezione 9, "Proposta per TriForge").** Un indice composito progettato per TriForge, app Android per triatleti (nuoto, bici, corsa) che oggi salva solo le attività. La sezione 10 indica cosa cambia se l'app copre **solo il nuoto** (per esempio SwimForge). La sezione 11 elenca ciò che l'indice non può dedurre.

Il grado di evidenza è segnalato con tre etichette.

- **[Evidenza]**: supportato da consensus statement, review sistematiche, meta-analisi o studi controllati concordi.
- **[Pratica]**: pratica comune di allenatori, piattaforme o aziende, con poca validazione indipendente.
- **[Proposta]**: scelta progettuale di questo documento, ragionata ma non validata. Va verificata sui dati reali.

Glossario minimo. **FC** = frequenza cardiaca. **HRV** = variabilità della frequenza cardiaca. **RMSSD** = radice quadrata della media dei quadrati delle differenze tra intervalli RR successivi, in ms. È l'indice di HRV più usato nel monitoraggio quotidiano e riflette soprattutto l'attività vagale (parasimpatica). **lnRMSSD** = logaritmo naturale di RMSSD. **FTP** = Functional Threshold Power, potenza sostenibile per circa un'ora. **CSS** = Critical Swim Speed, velocità critica di nuoto. **LTHR** = FC alla soglia del lattato. **PPG** = fotopletismografia, il sensore ottico al polso.

---

## 1. Definizioni e continuum

### 1.1 Il consensus ECSS/ACSM (Meeusen et al., 2013) [Evidenza]

Il riferimento è il consensus congiunto dello European College of Sport Science (ECSS) e dell'American College of Sports Medicine (ACSM) (Meeusen et al., 2013). Il consensus descrive un **continuum**, non categorie nette.

| Stato | Definizione | Calo di prestazione | Tempo di recupero | Esito |
|---|---|---|---|---|
| **Allenamento / fatica acuta** | Carico normale, fatica che si risolve in ore o giorni | Nessuno o transitorio | Ore, 1-2 giorni | Adattamento |
| **Overreaching funzionale (FOR)** | Carico intensificato deliberatamente | Temporaneo | Giorni, fino a 2 settimane | Supercompensazione (possibile) |
| **Overreaching non funzionale (NFOR)** | Carico eccessivo protratto, spesso con stressor esterni | Stagnazione o calo | Settimane, fino a mesi | Nessun beneficio; sintomi psicologici e neuroendocrini |
| **Sindrome da overtraining (OTS)** | Maladattamento grave, diagnosi di esclusione | Calo prolungato | Mesi, a volte anni | Danno alla carriera sportiva |

Punti chiave del consensus.

- La distinzione tra NFOR e OTS è **retrospettiva**: dipende dal tempo che serve per recuperare, non da un marker misurabile in tempo reale.
- Il termine "overtraining" indica il **processo** (troppo carico rispetto al recupero). "Sindrome da overtraining" indica l'**esito clinico**.
- Lo stress non sportivo conta: lavoro, studio, sonno, malattie, viaggi e alimentazione si sommano al carico. Il consensus parla di "stress totale".
- La diagnosi richiede di **escludere** altre cause: infezioni, anemia, disturbi tiroidei, carenze nutrizionali, depressione, disturbi alimentari e bassa disponibilità energetica.

### 1.2 Il FOR è davvero "funzionale"? [Evidenza, con dibattito]

Studi del gruppo INSEP su triatleti allenati hanno indotto tre settimane di sovraccarico. I soggetti con calo di prestazione e alta fatica percepita (FOR) sono stati confrontati con quelli solo "affaticati acutamente" (AF), cioè con fatica senza calo di prestazione.

- Nel taper successivo, il gruppo FOR **non** ha ottenuto una supercompensazione maggiore del gruppo AF (Aubry et al., 2014).
- Nel gruppo FOR durata ed efficienza del sonno sono calate (circa −5% la durata, misurata con actigrafia). Sono aumentate anche le infezioni delle vie respiratorie superiori: 67% nel gruppo FOR, 22% nel gruppo AF, 11% nei controlli (Hausswirth et al., 2014).
- Il gruppo FOR mostrava FC massima e FC submassimale più basse, e segni di **iperattività parasimpatica** (HRV più alta) (Le Meur et al., 2013a; 2013b).

Bellinger (2020) conclude che il FOR non è necessario per ottenere il picco di forma e comporta rischi. **Implicazione per un'app:** non bisogna "premiare" l'atleta che arriva al FOR. È meglio un carico che produca fatica senza calo di prestazione.

### 1.3 RED-S / REDs: il grande confondente [Evidenza]

La **Relative Energy Deficiency in Sport** (RED-S, dal 2023 "REDs") è una sindrome causata da **bassa disponibilità energetica** (LEA). La disponibilità energetica si calcola così:

```
EA = (energia introdotta − energia spesa nell'esercizio) / massa magra   [kcal/kg FFM/giorno]
```

Una disponibilità energetica persistentemente sotto ~30 kcal/kg FFM/giorno è associata a disfunzioni. La soglia è indicativa e la variabilità individuale è ampia (Mountjoy et al., 2018; 2023).

Stellingwerff et al. (2021) mostrano che OTS e REDs condividono sintomi, meccanismi e vie diagnostiche:

- fatica e calo di prestazione;
- alterazioni dell'umore;
- infezioni ricorrenti;
- disturbi del sonno;
- alterazioni ormonali.

Una parte dei casi etichettati come "overtraining" potrebbe essere, in realtà o in parte, un problema di energia. Il triathlon è uno sport ad alto volume e con forte attenzione al peso, quindi è particolarmente esposto.

**Implicazione per un'app:** un segnale di maladattamento **non spiegato dal carico** va indirizzato anche verso la nutrizione (in TriForge, verso il coach nutrizionista), non solo verso "riposa di più". Un'app non può calcolare la disponibilità energetica in modo affidabile. Le stime di spesa energetica dei wearable hanno errori ampi e l'introito registrato è sottostimato. L'app può però porre domande di screening e suggerire un consulto. Esempi di domande: cicli mestruali irregolari o assenti, calo della libido, fratture da stress, perdita di peso non voluta.

### 1.4 Prevalenza [Evidenza, di qualità modesta]

I dati di prevalenza sono eterogenei: le definizioni variano e quasi tutti gli studi si basano su questionari retrospettivi auto-riferiti.

- Nuotatori universitari USA: circa il 10% all'anno sviluppava "staleness" (Morgan et al., 1987).
- Atleti giovani svedesi di sport individuali: circa il 37% riferiva almeno un episodio di OTS nella vita (Kenttä et al., 2001).
- Giovani atleti inglesi: circa il 29% riferiva almeno un episodio di NFOR/OTS, con frequenza maggiore negli sport individuali e nei livelli più alti (Matos et al., 2011).
- Un'indagine su atleti d'élite svizzeri conferma che il fenomeno non è raro (Birrer et al., 2013).
- Per il triathlon mancano stime di prevalenza robuste. Vleck et al. (2014) segnalano una frequenza elevata di problemi di salute legati al volume (infezioni, infortuni da sovraccarico, soprattutto nella corsa). Gli studi INSEP mostrano che tre settimane di sovraccarico portano in FOR circa metà dei triatleti esposti (Hausswirth et al., 2014).

**Lettura critica:** le cifre "lifetime" (riferite a tutta la vita) del 20-60% riguardano quasi sempre episodi auto-diagnosticati, e quindi probabilmente NFOR o fatica prolungata più che vera OTS. La vera OTS è considerata **rara**.

---

## 2. Perché è difficile da diagnosticare e cosa può fare onestamente un'app

### 2.1 Nessun marker singolo [Evidenza]

Il consensus ECSS/ACSM e le review successive concordano sui punti seguenti.

- **Nessun biomarker** (cortisolo, testosterone, rapporto testosterone/cortisolo, catecolamine, citochine, glutammina/glutammato, CK) distingue in modo affidabile FOR, NFOR e OTS. Lo stesso vale per i marker ormonali: vanno interpretati sul singolo e in dinamica (Meeusen et al., 2013; Cadegiani & Kater, 2017).
- I test di provocazione, come il doppio test massimale a 4 ore di distanza con risposta di ACTH e prolattina, sono promettenti ma da laboratorio.
- Il **calo di prestazione** è il segno cardine, ma da solo non basta: dipende da motivazione, condizioni ambientali, attrezzatura e tipo di percorso.
- La diagnosi è **di esclusione** e richiede settimane: il calo deve persistere nonostante il riposo e le altre cause devono essere escluse.

### 2.2 Cosa può fare un'app [Proposta, coerente con l'evidenza]

| Può fare | Non può fare |
|---|---|
| Quantificare il carico e la sua dinamica rispetto alla storia personale | Diagnosticare FOR, NFOR o OTS |
| Rilevare scostamenti persistenti dei marker fisiologici dalla baseline personale | Distinguere una malattia incipiente da un sovraccarico |
| Rilevare la **concordanza** di più segnali (carico alto + HRV giù + FC a riposo su + benessere giù) | Escludere anemia, tiroide, REDs, depressione |
| Notare quando i segnali **non sono spiegati dal carico** | Stabilire una relazione causale |
| Suggerire scarico, check-in, confronto con allenatore, nutrizionista o medico | Prescrivere terapie |

La formulazione corretta è "**allarme precoce di rischio / segnali di affaticamento accumulato**", non "rischio di overtraining al 73%".

---

## 3. Modelli di carico di allenamento

### 3.1 Carico esterno e interno [Evidenza]

- Il **carico esterno** è il lavoro prodotto: watt, km, ritmo.
- Il **carico interno** è la risposta dell'organismo: FC, RPE, lattato.

Il consensus sul monitoraggio del carico raccomanda di misurare entrambi (Bourdon et al., 2017; Impellizzeri et al., 2019). Il carico interno è quello biologicamente rilevante per lo stress. Il rapporto tra carico interno ed esterno è a sua volta un indicatore di stato di forma.

### 3.2 Modello impulso-risposta di Banister (fitness-fatigue) [Evidenza per il concetto, debole per la previsione]

La prestazione al giorno *t* è la somma di due risposte esponenziali al carico *w* dei giorni precedenti:

```
p(t) = p0 + k1 · Σ_{s<t} w(s)·e^{-(t-s)/τ1}  −  k2 · Σ_{s<t} w(s)·e^{-(t-s)/τ2}
```

Costanti tipiche: τ1 (fitness) ≈ 40-50 giorni, τ2 (fatica) ≈ 7-15 giorni, k2 > k1 (Banister et al., 1975; Morton et al., 1990).

**Limiti.**

- I parametri vanno stimati per individuo e servono molte misure di prestazione.
- I parametri sono instabili nel tempo (Busso, 2003).
- Il potere predittivo out-of-sample è modesto (Hellard et al., 2006).
- Il modello è lineare: ignora la saturazione e il fatto che lo stesso carico produce fatica diversa a seconda dello stato.

**Uso corretto:** come schema concettuale e come base del Performance Manager (§3.5), non come previsore di prestazione.

### 3.3 TRIMP: impulso di allenamento basato sulla FC [Evidenza]

**TRIMP di Banister.** Con `HRr = (FCmedia − FCriposo)/(FCmax − FCriposo)`:

```
TRIMP = durata_min · HRr · 0.64 · e^{1.92·HRr}     (uomini)
TRIMP = durata_min · HRr · 0.86 · e^{1.67·HRr}     (donne)
```

Il fattore esponenziale pesa di più le intensità alte, in modo analogo alla curva del lattato.

**TRIMP di Edwards (1993).** Somma dei minuti in 5 zone di FC (50-60%, 60-70%, 70-80%, 80-90%, 90-100% della FCmax), moltiplicati rispettivamente per 1, 2, 3, 4, 5.

**TRIMP di Lucia et al. (2003).** Tre zone delimitate dalle soglie ventilatorie, con pesi 1, 2, 3.

**Limiti.**

- Usare la FC **media** per una seduta a intervalli sottostima il carico, perché la funzione è convessa. È meglio calcolare il TRIMP sul tempo nelle zone o sul campionamento secondo per secondo.
- La FC ritarda negli sforzi brevi e sottostima sprint e lavoro anaerobico.
- La FC è influenzata da caldo, disidratazione, caffeina e deriva cardiaca.
- Nel nuoto la FC ottica al polso è poco affidabile (§6.3).

### 3.4 Session-RPE di Foster [Evidenza]

```
sRPE_load = RPE_CR10 (0-10, chiesto ~30 min dopo la seduta) × durata_min   [unità arbitrarie, UA]
```

Il metodo è validato in molti sport e mostra correlazioni moderate-alte con i metodi basati sulla FC. È economico e cattura componenti non cardiache come il danno muscolare e lo sforzo mentale (Foster et al., 2001; Haddad et al., 2017).

**Monotonia e strain di Foster (1998).**

```
monotonia_7g = media(carico giornaliero, 7g) / DS(carico giornaliero, 7g)
strain_7g    = somma(carico, 7g) × monotonia_7g
```

Foster osservò che le malattie e i "cali" erano più frequenti quando monotonia > ~2 e strain alti. **Limiti:** studio osservazionale su piccoli numeri. La soglia di 2 è empirica [Pratica]. La monotonia esplode quando la DS è vicina a zero (per esempio carico identico ogni giorno): va limitata con un tetto.

### 3.5 TSS e Performance Manager [Pratica, con basi nel modello di Banister]

**TSS (Training Stress Score), bici con potenza** (Allen & Coggan, 2019):

```
NP  = (media mobile 30 s della potenza)^4, mediata, poi ^(1/4)
IF  = NP / FTP
TSS = (durata_s × NP × IF) / (FTP × 3600) × 100  =  ore × IF² × 100
```

Un'ora alla soglia vale 100 TSS.

**Derivati di TrainingPeaks** (formule pubbliche ma proprietarie, validazione indipendente scarsa):

- **rTSS** (corsa): stessa logica del TSS, con il passo normalizzato per la pendenza (NGP) al posto della NP e la soglia di passo al posto dell'FTP. `rTSS = ore × (NGP/passo_soglia)² × 100`.
- **sTSS** (nuoto): `IF_swim = velocità_media / CSS`, `sTSS = ore × IF_swim³ × 100`. L'esponente è 3 perché la resistenza in acqua cresce circa con il cubo della velocità.
- **hrTSS**: TSS stimato dalla FC, con 100 = un'ora alla LTHR. Si calcola dal tempo nelle zone o normalizzando un TRIMP: `hrTSS = 100 × TRIMP_seduta / TRIMP_(60 min @ LTHR)`.

**Performance Manager (CTL/ATL/TSB).** Medie mobili esponenziali (EWMA) del carico giornaliero *L*:

```
CTL_t = CTL_{t-1} + (L_t − CTL_{t-1}) · (1/42)     "Fitness",  costante 42 giorni
ATL_t = ATL_{t-1} + (L_t − ATL_{t-1}) · (1/7)      "Fatigue",  costante 7 giorni
TSB_t = CTL_{t-1} − ATL_{t-1}                      "Form"
```

Alcune implementazioni usano `1 − e^{−1/τ}` al posto di `1/τ`. La differenza è minima.

Intervals.icu esprime la forma anche in percentuale: `Form% = TSB/CTL`.

Lettura comune [Pratica]:

- TSB da −10 a −30 (o Form% da −10% a −30%): zona "produttiva";
- sotto −30: rischio elevato;
- sopra +5/+10: freschezza o perdita di forma.

**Ramp rate:** aumento di CTL in 7 giorni. Valori sostenibili spesso citati: 3-8 TSS/giorno per settimana [Pratica].

**Limiti.**

- Il TSS è "ore × IF²": dà lo stesso valore a sedute fisiologicamente diverse (2 ore facili contro 1 ora alla soglia).
- Non distingue il costo meccanico tra sport.
- Dipende in modo critico da soglie aggiornate. Un FTP non aggiornato del 10% sposta il TSS di circa il 20%.
- Le costanti 7 e 42 sono convenzioni, non parametri individuali.
- CTL e TSB **non sono validati** come predittori di NFOR/OTS.

### 3.6 ACWR (Gabbett) e le critiche [Evidenza delle critiche]

**ACWR (rapporto tra carico acuto e cronico)** = carico acuto (7 giorni) / carico cronico (28 giorni). La "sweet spot" proposta è 0.8-1.3; sopra 1.5 il rischio di infortunio sarebbe elevato (Hulin et al., 2016; Gabbett, 2016). Williams et al. (2017) hanno proposto la versione EWMA (λ_a = 2/(7+1), λ_c = 2/(28+1)) al posto delle medie mobili.

**Critiche principali.**

1. **Accoppiamento matematico.** Se la finestra cronica di 28 giorni include l'acuta di 7, numeratore e denominatore condividono dati e si generano correlazioni spurie. La versione "uncoupled" (cronico sui giorni 8-35) cambia i risultati (Lolli et al., 2019).
2. **Problemi dei rapporti.** Un rapporto tra variabili ha proprietà statistiche ingannevoli: non linearità e assunzione di proporzionalità non verificata. Lo stesso rapporto corrisponde a carichi assoluti molto diversi. Il carico cronico non svolge il ruolo "protettivo" ipotizzato: il segnale viene quasi tutto dal numeratore (Impellizzeri et al., 2020; 2021).
3. **Metodologia degli studi originali.** Categorizzazione arbitraria (discretizzazione), analisi post hoc, scarsa replicabilità, causalità non dimostrata (Wang et al., 2020; Impellizzeri et al., 2021).
4. **Rolling average vs EWMA.** Le due versioni producono classificazioni diverse. Nessuna è validata come strumento predittivo individuale.

**Conclusione per un'app:** l'ACWR può servire come **descrittore** della progressione ("questa settimana hai fatto circa il 40% in più della tua media recente"). **Non** va presentato come probabilità di infortunio o di overtraining, e non deve ricevere un peso dominante in un indice. Garmin lo usa ("load ratio") nel Training Readiness: anche questo va trattato come descrittivo.

### 3.7 Sintesi comparativa

| Modello | Dati richiesti | Forza dell'evidenza | Uso consigliato |
|---|---|---|---|
| sRPE | RPE + durata | Alta per la validità come misura di carico | Universale; fallback per il nuoto |
| TRIMP / hrTSS | FC (meglio il tempo nelle zone), FCmax, FCriposo, LTHR | Media | Bici e corsa senza potenza |
| TSS potenza | Potenza, FTP | Media (fisiologicamente plausibile, poco validato sugli esiti) | Bici con potenziometro |
| rTSS / sTSS | Passo, soglia / CSS | Bassa-media | Corsa e nuoto |
| CTL/ATL/TSB | Serie di TSS | Bassa sugli esiti clinici | Contesto e periodizzazione |
| ACWR | Serie di carico | Contestata | Solo descrittivo |
| Monotonia/strain | Serie di carico | Bassa-media | Segnale accessorio |

---

## 4. Marker fisiologici giornalieri

### 4.1 HRV [Evidenza]

**Principi metodologici** (Plews et al., 2012; 2013a; 2013b; Buchheit, 2014).

- Usare **lnRMSSD**: la trasformazione logaritmica riduce l'asimmetria e l'eteroschedasticità.
- Il singolo giorno è rumoroso: il CV giorno-per-giorno di lnRMSSD è circa 5-10%, quello di RMSSD grezzo spesso oltre il 15%. Bisogna interpretare la **media mobile a 7 giorni** (lnRMSSD_7d).
- Servono **almeno 3-5 misure valide a settimana** perché la media settimanale sia attendibile (Plews et al., 2014).
- La baseline è individuale: media (e DS) degli ultimi **30-60 giorni**, confrontata con la media a 7 giorni. Il **cambiamento minimo rilevante (SWC)** è circa 0.5 × DS individuale (oppure 0.5 × CV), cioè la "banda normale" (Buchheit, 2014; Plews et al., 2013b). HRV4Training usa una logica analoga: media a 7 giorni contro banda normale sui 60 giorni [Pratica] (Altini & Plews, 2021).
- Il **coefficiente di variazione (CV) di lnRMSSD sulla settimana** (lnRMSSD_cv) è informativo. Un CV in aumento insieme a una media in calo è associato a maladattamento. Un CV basso con media stabile o in aumento è associato a buon adattamento (Plews et al., 2012; Flatt & Esco, 2016).
- Allenamento guidato dall'HRV: Kiviniemi et al. (2007) e Vesterinen et al. (2016) mostrano risultati pari o migliori dell'allenamento predefinito, riducendo l'intensità quando l'HRV è sotto la banda.
- Meta-analisi di Bellenger et al. (2016): l'HRV vagale tende ad **aumentare** con l'adattamento positivo. Nel breve termine (fatica acuta) **diminuisce**. Nell'overreaching l'effetto è **eterogeneo**.

**Saturazione parasimpatica e HRV "alta" paradossale** [Evidenza].

- Nei triatleti in FOR, Le Meur et al. (2013a) hanno osservato HRV **aumentata**, FC a riposo più bassa e FC massima ridotta.
- Plews et al. (2012) descrivono un caso di triatleta d'élite in NFOR con lnRMSSD in calo progressivo, ma segnalano che con FC a riposo molto bassa RMSSD può "saturare" e perdere sensibilità.

Conseguenza: **un aumento di HRV non è automaticamente un buon segno**. Se HRV sopra la banda coesiste con carico alto, benessere soggettivo peggiorato, FC massima non raggiunta o prestazione in calo, il quadro va trattato come **atipico** e non come "ottimo recupero".

**Misura notturna vs mattutina** [Evidenza/Pratica].

- Gli studi classici usano registrazioni mattutine (1-5 min, supino o in piedi, con fascia o smartphone validato; Plews et al., 2017).
- Garmin, WHOOP, Oura e Polar misurano di notte (media di tutta la notte o di finestre di sonno profondo).
- La misura notturna è più standardizzata nel contesto (sonno) ma è influenzata da orario dei pasti, alcol, temperatura e allenamento serale.
- Le due misure non sono intercambiabili nei valori assoluti, ma lo sono **nel trend individuale** se si usa sempre la stessa fonte.
- La PPG al polso ha accordo variabile con l'ECG per l'HRV notturna: migliore per i trend che per gli assoluti (Miller et al., 2022).

### 4.2 FC a riposo [Evidenza]

- Un aumento sostenuto di qualche bpm sopra la baseline è un segnale classico di fatica, malattia, disidratazione o stress (Buchheit, 2014).
- La FC a riposo è **meno sensibile** dell'HRV ai cambiamenti di stato, ma **più robusta** al rumore di misura.
- Nell'overreaching "parasimpatico" la FC a riposo può **scendere** (Le Meur et al., 2013a), quindi la direzione non è univoca.
- Nei dati free-living, HRV e FC a riposo reagiscono in modo prevedibile a malattia, alcol, ciclo mestruale, allenamento intenso e viaggi (Altini & Plews, 2021).

Garmin calcola la FC a riposo come la media più bassa su 30 minuti nelle 24 ore. È un buon indicatore per i trend.

### 4.3 Sonno [Evidenza]

- Il consensus sul sonno nell'atleta (Walsh et al., 2021) indica che durata insufficiente, scarsa efficienza e **irregolarità** degli orari compromettono recupero, funzione immunitaria e prestazione.
- Il gruppo FOR di Hausswirth et al. (2014) mostrava un calo misurabile di durata ed efficienza del sonno.
- **Affidabilità dei wearable:** durata totale e orari sono stimati discretamente, in modo simile all'actigrafia. Le **fasi** (profondo/REM) hanno accordo solo moderato con la polisonnografia (Miller et al., 2022). Per l'indice conviene usare la durata, la regolarità e al massimo il punteggio di sonno del produttore. Le fasi vanno escluse.

### 4.4 Marker soggettivi: i più sensibili [Evidenza]

La review sistematica di Saw et al. (2016), su 56 studi, ha trovato che le misure soggettive auto-riferite rispondono ai cambiamenti di carico acuto e cronico con **maggiore sensibilità e coerenza** delle misure oggettive comuni (marker ematici, FC, consumo di ossigeno). In particolare:

- il benessere peggiora con carico acuto alto e migliora con lo scarico;
- le misure soggettive si muovono anche quando quelle oggettive non mostrano nulla.

Strumenti disponibili.

- **Hooper Index** (Hooper et al., 1995): 4 item su scala 1-7 (qualità del sonno, fatica, stress, dolori muscolari). L'indice è la somma (4-28). È semplice e quotidiano.
- **RESTQ-Sport** (Kellmann & Kallus, 2001): 76 item (forma breve disponibile). È validato ma troppo lungo per l'uso quotidiano.
- **Questionari di benessere personalizzati** di 4-6 item: molto diffusi ma raramente validati [Pratica]. Il consensus su recupero e prestazione (Kellmann et al., 2018) raccomanda strumenti brevi, validati e coerenti nel tempo.

**Limiti:** sono manipolabili, possono "stancare" chi li compila (aderenza in calo) e sono influenzati dalla desiderabilità sociale (soprattutto se l'allenatore li legge). Restano però il **segnale con il miglior rapporto costo/informazione**.

---

## 5. Indicatori di prestazione

Il calo di prestazione è il cardine della definizione (Meeusen et al., 2013), ma è difficile da misurare in campo.

- **Prestazione a parità di sforzo** [Pratica, plausibile]: a FC simile, il passo o la potenza scendono. Oppure, a passo o potenza simili, la FC sale.
- **Efficiency Factor (EF)** (Allen & Coggan, 2019) [Pratica]: `EF = NP / FCmedia` in bici, `EF = velocità_normalizzata / FCmedia` nella corsa. Si confronta solo su sedute **comparabili**: aerobiche, steady, durata simile, condizioni simili.
- **Disaccoppiamento aerobico** (Pa:HR o Pw:HR) [Pratica]: `decoupling% = (EF_prima_metà − EF_seconda_metà) / EF_prima_metà × 100`. Sotto il 5% sulle uscite lunghe indica buona base aerobica (Friel). Un aumento rispetto alla storia personale su uscite simili è un indizio di fatica o di caldo/disidratazione.
- **FC massima o di picco non raggiunta** [Evidenza nel FOR]: nei triatleti in FOR la FC massima scende (Le Meur et al., 2013a). Un pattern utile: in sedute "dure" (per esempio intervalli con RPE ≥ 8) la FC di picco resta sotto la propria norma di qualche bpm, mentre l'RPE è alto.
- **Discrepanza RPE/carico esterno** [Evidenza moderata]: lo stesso lavoro percepito come più duro.

Questi indicatori sono **lenti e rumorosi**. Servono come conferma su 2-4 settimane, non come trigger giornalieri.

---

## 6. Specificità del triathlon

### 6.1 Sommare il carico di tre sport [Pratica / Proposta]

- **Pratica comune** (TrainingPeaks, Intervals.icu): sommare i TSS di tutti gli sport in un unico CTL/ATL. Il presupposto è che 100 TSS = un'ora alla soglia in qualunque sport. È una semplificazione.
- **Cejuela & Esteve-Lanao (2011)** propongono di quantificare il carico in triathlon con unità basate su zone di intensità e fattori di correzione per sport. Riconoscono che lo stesso tempo in zona costa diversamente nei tre sport.
- **Corsa vs bici** [Evidenza]: la corsa comporta impatti e contrazioni eccentriche, quindi più danno muscolare e un recupero più lungo della bici a pari intensità metabolica (Millet et al., 2009). Gli infortuni da sovraccarico nei triatleti sono concentrati nella corsa (Vleck et al., 2014).
- **Nuoto**: carico meccanico sugli arti inferiori basso, carico sulla spalla alto. A parità di FC, il carico metabolico relativo è diverso (in acqua la FC è più bassa per posizione orizzontale, immersione e riflesso di immersione).

**Proposta.** Mantenere un **carico totale** (somma dei TSS-equivalenti per CTL/ATL e risposta sistemica) **e** carichi **per sport** separati. Il carico per sport serve soprattutto a sorvegliare la progressione della corsa, per il rischio muscoloscheletrico. **Non** applicare moltiplicatori arbitrari al TSS totale. Un fattore "meccanico" per la corsa (per esempio ×1.2-1.3) non ha evidenza solida. È più trasparente monitorare la progressione della corsa in parallelo.

### 6.2 Gerarchia delle fonti di carico per seduta [Proposta]

1. Bici con potenza: TSS dalla potenza (campo `trainingStressScore` di Garmin se presente, altrimenti dalla NP e dall'FTP dell'utente).
2. Corsa: rTSS dal passo con soglia (oppure dalla potenza di corsa, se c'è); altrimenti hrTSS.
3. Nuoto: sTSS da passo e CSS (per la vasca); altrimenti sRPE convertito.
4. Qualsiasi sport con FC affidabile: hrTSS dal tempo nelle zone.
5. Fallback universale: sRPE convertito in TSS-equivalente con una regressione personale (§9.3).
6. Fallback alternativo: il "Training Load" EPOC di Garmin (`activityTrainingLoad`), calibrato sul TSS personale.

### 6.3 FC al polso nel nuoto [Pratica, ampiamente riconosciuta]

La FC ottica al polso in acqua è spesso inaffidabile:

- movimento;
- acqua tra sensore e pelle;
- vasocostrizione periferica da freddo;
- ritardo.

La fascia toracica con memoria (per esempio HRM-Swim/HRM-Pro di Garmin) migliora molto i dati. Senza fascia, i parametri di nuoto basati sulla FC (hrTSS, Training Effect, Training Load EPOC) vanno considerati a **bassa confidenza**. Conviene preferire passo/CSS e sRPE.

---

## 7. Come lo fanno le app esistenti e cosa se ne sa

Tutti questi algoritmi sono **proprietari**. La validazione indipendente riguarda al massimo i sensori (FC, HRV, sonno), quasi mai i punteggi compositi rispetto a esiti come NFOR o infortuni.

| Prodotto | Ingressi principali | Logica nota | Limiti |
|---|---|---|---|
| **Garmin Training Readiness** (Firstbeat) | Sleep score, storico del sonno, tempo di recupero, HRV status, acute load (rapporto acuto/cronico), storico dello stress | Punteggio 0-100 con contributi in % per fattore, ricalcolato più volte al giorno | Proprietario; pesi non pubblicati; eredita le critiche all'ACWR |
| **Garmin HRV Status** | RMSSD notturno | Media a 7 giorni contro baseline personale su ~3 settimane; stati "Balanced/Unbalanced/Low/Poor" | Servono ~3 settimane per la baseline; solo modelli compatibili |
| **Garmin Training Status / Acute Load** | Carico EPOC per attività, trend del VO2max, HRV status | Acute load = somma pesata ~7 giorni di EPOC; load focus (aerobico basso/alto/anaerobico) a 4 settimane; stato "Productive/Maintaining/Strained/Overreaching..." | VO2max stimato da FC e passo/potenza (errore ±5-10%); l'EPOC del nuoto senza fascia è dubbio |
| **WHOOP Recovery / Strain** | HRV (RMSSD in sonno profondo), FC a riposo, prestazione del sonno, frequenza respiratoria, temperatura cutanea | Recovery 0-100% rispetto alla baseline; Strain 0-21 su scala logaritmica basata sulla FC | Proprietario; lo Strain basato sulla FC sottostima il carico muscolare (pesi) |
| **Oura Readiness** | FC a riposo, HRV balance, temperatura corporea, indice di recupero, sonno, sleep balance, attività | Contributori con baseline a 2 settimane e 2 mesi | Pensato per il benessere generale; carico sportivo grossolano |
| **Polar Nightly Recharge** | ANS charge (FC, HRV, respirazione nelle prime ~4 h di sonno) + sleep charge | Confronto con la media personale a 28 giorni | Proprietario |
| **TrainingPeaks Performance Manager** | TSS di tutti gli sport | CTL 42 giorni, ATL 7 giorni, TSB | Nessun marker fisiologico; dipende dalle soglie |
| **Intervals.icu** | TSS/hrTSS/sRPE + wellness importato (HRV, FC a riposo, sonno, soggettivi) | Fitness/Fatigue/Form, Form% con zone colorate; mostra il wellness accanto | Non fa un indice composito validato; lascia l'interpretazione all'utente |
| **HRV4Training** | Misura mattutina (fotocamera o fascia) + questionario | lnRMSSD_7d contro banda normale a 60 giorni; CV; consigli giornalieri | Richiede disciplina mattutina; è l'approccio più trasparente e aderente alla letteratura (Altini & Plews, 2021) |

**Lezione per il design:**

- i prodotti migliori usano una baseline **individuale**;
- mostrano i **contributi** dei singoli fattori;
- usano medie mobili, non giorni singoli.

Nessuno ha dimostrato di prevenire l'OTS. Fare meglio di loro "in precisione" è irrealistico. È realistico essere **più trasparenti**, **multi-sport** e **integrati con nutrizione e contesto**.

---

## 8. Dati disponibili: Garmin Connect e Health Connect

### 8.1 python-garminconnect

La libreria (cyberjunky/python-garminconnect) usa endpoint **non ufficiali** della web app Garmin Connect, ricavati per reverse engineering. Le conseguenze pratiche:

- può rompersi senza preavviso, a ogni cambiamento lato Garmin;
- l'autenticazione è cambiata con la versione 0.3.0, che ha abbandonato `garth` e salva i token in `garmin_tokens.json`; supporta l'MFA con `resume_login`;
- richiede Python ≥ 3.12;
- il README **non documenta i limiti di frequenza**, ma Garmin può rispondere con HTTP 429.

Gira in Python, quindi su Android serve un backend (TriForge ha già un servizio `garmin-service` con `garminconnect==0.3.20`). L'alternativa ufficiale è il Garmin Connect Developer Program (Health/Activity API), che richiede un'approvazione aziendale. Anche il rispetto dei termini d'uso di Garmin va valutato.

Il file `docs/API_ENDPOINTS.md` del repository segnala quali endpoint sono "verified live". Per esempio `get_daily_heart_rate_stats` → `/usersummary-service/stats/heartRate/daily/{start}/{end}` (FC a riposo, minima e massima per giorno). `get_sleep_data` e `get_spo2_data` sono mantenuti per compatibilità, mentre la web app usa varianti più recenti (`get_daily_sleep`, `get_spo2_acclimation_data`).

**Nota:** i nomi dei campi JSON elencati sotto vengono dall'uso corrente della libreria e dal codice di normalizzazione già presente in TriForge. Vanno **verificati su risposte reali**, perché la struttura può variare tra modelli di orologio e nel tempo.

| Metodo (esiste nel README) | Cosa restituisce (campi principali) | Affidabilità / note |
|---|---|---|
| `get_activities_by_date(start, end, type)`, `get_activities`, `get_activity(id)` | Per attività: `activityId`, `activityType.typeKey`, `startTimeLocal/GMT`, `duration`, `movingDuration`, `distance`, `averageHR`, `maxHR`, `averageSpeed`, `avgPower`, `normPower`, `trainingStressScore` e `intensityFactor` (solo con potenza e FTP), `aerobicTrainingEffect`, `anaerobicTrainingEffect`, `activityTrainingLoad` (EPOC Firstbeat), `vO2MaxValue`; nuoto: `poolLength`, `activeLengths`, `strokes`, `averageSwolf` | Alta per durata e distanza (GPS/vasca); FC buona con fascia, media al polso, scarsa nel nuoto senza fascia; TSS presente solo con potenziometro; Training Load e Training Effect proprietari ma coerenti nel tempo |
| `get_activity_hr_in_timezones(id)` | Secondi in ciascuna zona di FC | Utile per il TRIMP di Edwards e l'hrTSS a zone (meglio della FC media) |
| `get_activity_details(id)`, `get_activity_splits/laps` | Serie temporali (FC, velocità, potenza, cadenza) e lap | Necessari per decoupling ed EF; payload pesante: scaricare solo le sedute rilevanti |
| `get_activity_swim_details`, `get_activity_power_in_timezones` | Dettagli vasche; tempo nelle zone di potenza | Il primo serve per il nuoto (passo per vasca → sTSS) |
| `get_hrv_data(date)`, `get_hrv_data_range` | `hrvSummary`: `lastNightAvg` (RMSSD medio notturno, ms), `weeklyAvg`, `lastNight5MinHigh`, `baseline{balancedLow, balancedUpper, lowUpper}`, `status` (BALANCED/UNBALANCED/LOW/POOR); letture ogni 5 min durante il sonno | Buona per i trend se l'orologio è indossato tutta la notte; solo modelli con HRV Status; i valori assoluti differiscono dall'ECG |
| `get_sleep_data(date)`, `get_daily_sleep`, `get_sleep_daily` | `dailySleepDTO`: `sleepTimeSeconds`, secondi per fase (deep/light/REM/awake), inizio e fine, `sleepScores.overall.value`, respirazione media, stress medio nel sonno, FC a riposo, HRV medio notturno | Durata e orari buoni; fasi solo moderate; il punteggio è proprietario |
| `get_rhr_day(date)`, `get_rhr_daily`, `get_daily_heart_rate_stats` | FC a riposo giornaliera (media più bassa su 30 min nelle 24 h), minima e massima | Buona e robusta; marker giornaliero di prima scelta insieme all'HRV |
| `get_heart_rates(date)` | `restingHeartRate`, `minHeartRate`, `maxHeartRate`, `lastSevenDaysAvgRestingHeartRate`, serie `heartRateValues` | Serie utile per calcoli propri (per esempio la FC minima notturna) |
| `get_stress_data(date)`, `get_daily_stress_stats` | `avgStressLevel`, `maxStressLevel`, serie 0-100 (valori negativi = non misurato o in attività) | Derivato da HRV diurna (Firstbeat); confuso da postura, caffè, digestione; bassa specificità |
| `get_body_battery(start, end)`, `get_daily_body_battery_stats` | Livello 0-100, `charged`, `drained`, serie ed eventi | Composito proprietario di HRV, stress e attività; non va sommato agli stessi ingressi (doppio conteggio) |
| `get_training_readiness(date)`, `get_morning_training_readiness`, `..._range` | `score` 0-100, `level`, percentuali per fattore (sonno, tempo di recupero, carico acuto/ACWR, HRV, storico dello stress, storico del sonno), `acuteLoad`, `hrvWeeklyAverage`, `recoveryTime` | Proprietario; più letture al giorno (usare quella mattutina); utile come confronto, non come ingresso principale |
| `get_training_status(date)`, `get_daily/weekly_training_status`, `get_training_four_week_load_balance` | VO2max (generico e bici), stato di allenamento con frase di feedback, `acuteTrainingLoadDTO` (carico acuto, cronico, ACWR, stato), bilancio del carico a 4 settimane (aerobico basso, alto, anaerobico con target), acclimatazione a caldo e quota | Proprietario; VO2max stimato; stati qualitativi utili da mostrare, non da ricalcolare |
| `get_respiration_data(date)`, `get_daily_respiration_stats` | Respirazione media da sveglio e nel sonno, minimi e massimi, serie | Una frequenza respiratoria notturna in aumento è un segnale precoce di malattia [Pratica]; accuratezza al polso discreta |
| `get_spo2_data(date)` | SpO2 media, minima, ultima, media nel sonno, medie orarie | Bassa accuratezza al polso; utile solo in quota; non adatto all'indice |
| `get_max_metrics(date)`, `get_endurance_score`, `get_race_predictions` | VO2max, endurance score, previsioni di gara | Stime; utili come trend di forma su settimane |

### 8.2 Android Health Connect

Record pertinenti, dalla documentazione ufficiale Android:

- **ExerciseSessionRecord**: `exerciseType` (per esempio RUNNING, BIKING, SWIMMING_POOL, SWIMMING_OPEN_WATER), inizio e fine, `laps`, `segments`.
- **HeartRateRecord**: serie di campioni in bpm.
- **RestingHeartRateRecord**: `beatsPerMinute`, istantaneo.
- **HeartRateVariabilityRmssdRecord**: `heartRateVariabilityMillis`, istantaneo.
- **SleepSessionRecord**: inizio, fine, `stages` (awake, light, deep, REM, sleeping, out of bed...).
- **PowerRecord**: serie in watt.
- **SpeedRecord**, **DistanceRecord**, **CyclingPedalingCadenceRecord**.
- **Vo2MaxRecord**, **RespiratoryRateRecord**, **OxygenSaturationRecord**.
- **SkinTemperatureRecord**: delta rispetto alla baseline; richiede il flag di funzionalità.
- **MenstruationPeriodRecord** e **MenstruationFlowRecord**: utili per contestualizzare l'HRV nelle atlete, con consenso esplicito.
- **NutritionRecord**, **ActiveCaloriesBurnedRecord**, **TotalCaloriesBurnedRecord**, **WeightRecord**: rilevanti per il coach nutrizionista.

Vincoli.

- Leggere dati più vecchi di 30 giorni richiede il permesso `READ_HEALTH_DATA_HISTORY`. Serve per costruire subito una baseline a 60 giorni.
- La lettura in background richiede `READ_HEALTH_DATA_IN_BACKGROUND`.
- Ogni tipo di dato richiede un permesso specifico e una dichiarazione nella Play Console.

**Cosa scrive davvero Garmin in Health Connect** (comunicazione di Garmin, luglio 2025):

- calorie, distanza, dislivello, FC, velocità, passi, bracciate di nuoto, cadenza di pedalata, sonno, peso e massa grassa;
- **l'HRV non è condivisa** e la potenza non è elencata;
- la FC a riposo come record dedicato non è confermata.

Conseguenze per un utente Garmin.

- Health Connect basta per attività, FC e sonno.
- Per l'HRV serve python-garminconnect oppure un'altra sorgente che scriva `HeartRateVariabilityRmssdRecord` (alcuni anelli o orologi di altri marchi).
- La FC a riposo, se manca come record, si può stimare dalla serie `HeartRateRecord` notturna. Per esempio: 5° percentile o minima media su 30 min durante la `SleepSessionRecord` [Proposta].

**Regola:** per ciascun marker usare **una sola fonte coerente** nel tempo. Garmin e un altro wearable hanno algoritmi diversi: mescolarli nella stessa baseline crea salti artificiali. Se la fonte cambia, la baseline va ricominciata, o almeno marcata.

---

## 9. Proposta per TriForge

*Tutto ciò che segue è [Proposta], salvo dove indicato. Il contesto: TriForge salva oggi solo le attività. Il servizio Garmin già interroga attività, HRV (range), FC a riposo giornaliera e sonno giornaliero, quindi la base per i marker fisiologici c'è.*

### 9.1 Obiettivo e principio

L'indice si chiama **"Bilancio Carico-Recupero"** (BCR). È un punteggio 0-100 in cui **più alto significa più segnali di affaticamento accumulato**. Si rappresenta con un semaforo e un livello di confidenza.

Non stima la probabilità di overtraining. Risponde a due domande:

1. Il corpo sta rispondendo al carico come al solito?
2. I segnali di affaticamento sono coerenti con il carico, oppure non spiegati?

Principi:

- baseline individuale con statistiche robuste;
- medie mobili;
- persistenza e concordanza prima di ogni allarme;
- spiegabilità;
- degradazione graduale quando mancano dati.

### 9.2 Pre-elaborazione e statistiche di base

Per ogni marker giornaliero *x* (lnRMSSD, FC a riposo, durata del sonno, punteggio soggettivo):

```
# Baseline robusta: finestra B = giorni [t-60, t-8] (esclude l'ultima settimana per non "inseguire" il trend)
valid_B   = valori in B non marcati come "esclusi" (malattia dichiarata, alcol dichiarato, fonte cambiata)
μ_x(t)    = mediana(valid_B)
σ_x(t)    = max( 1.4826 · MAD(valid_B),  σ_min_x )      # MAD scalata ≈ DS se normale; σ_min evita z-score esplosivi
m7_x(t)   = media dei valori validi in [t-6, t], richiede ≥ 4 valori (altrimenti NA)
z_x(t)    = (m7_x(t) − μ_x(t)) / σ_x(t)
```

Minimi di σ (σ_min) proposti: lnRMSSD 0.05; FC a riposo 1.5 bpm; sonno 20 min; soggettivo 1 punto su scala 4-28.

Il **segno "cattivo"** dipende dal marker:

- lnRMSSD: z negativo;
- FC a riposo: z positivo;
- sonno: z negativo;
- benessere soggettivo (dove alto = peggio): z positivo.

Si definisce `zb_x = z_x` orientato in modo che positivo = peggio.

Funzione di deviazione da 0 a 1, con la banda normale di ±0.5 σ (SWC, Buchheit 2014; Plews et al., 2013b) che vale 0:

```
dev(zb) = clamp( (zb − 0.5) / 2.0 , 0, 1 )     # 0 entro la SWC, 1 a ≥ 2.5 σ dalla baseline
```

### 9.3 Componente CARICO (contesto, peso moderato)

**Carico per seduta in TSS-equivalenti (TSSe)**, con la gerarchia del §6.2.

```
bici:  se trainingStressScore presente → TSSe = trainingStressScore
       elif potenza e FTP → TSSe = ore · (NP/FTP)² · 100
corsa: se passo soglia noto → TSSe = ore · (v_norm / v_soglia)² · 100    (v_norm ≈ velocità media corretta per dislivello se disponibile)
nuoto: se CSS nota → TSSe = ore · (v_media / CSS)³ · 100
FC affidabile (non nuoto senza fascia):
       TSSe = 100 · Σ_z (min_in_zona_z · k_z) / (60 · k_LTHR)          # hrTSS a zone; k_z = pesi Edwards 1..5, k_LTHR = peso della zona di soglia
fallback 1: sRPE → TSSe = a_u · (RPE · min)                            # a_u stimato per utente; default a = 100/(7·60) ≈ 0.24  (1 h a RPE 7 ≈ 100)
fallback 2: activityTrainingLoad Garmin → TSSe = b_u · TL              # b_u = mediana(TSSe/TL) sulle sedute dell'utente con entrambe le misure
```

Ogni seduta porta un flag di qualità `q_load` ∈ {alta, media, bassa}:

- **alta**: potenza, passo con soglia o CSS;
- **media**: FC a zone, sRPE;
- **bassa**: solo durata (TSSe = durata × IF tipico per sport) o FC del nuoto al polso.

**Serie giornaliere:** `L_t = Σ TSSe` di tutte le sedute del giorno, più `L_t^run` per la sola corsa.

```
ATL_t = ATL_{t-1} + (L_t − ATL_{t-1})/7
CTL_t = CTL_{t-1} + (L_t − CTL_{t-1})/42
Form%_t = (CTL_{t-1} − ATL_{t-1}) / max(CTL_{t-1}, 20)      # pavimento 20 per principianti
Ramp_t  = CTL_t − CTL_{t-7}
Mono_t  = min( media(L_{t-6..t}) / max(DS(L_{t-6..t}), 1), 4 )
# progressione corsa (descrittiva, disaccoppiata — Lolli 2019):
RunRatio_t = Σ L^run_{t-6..t} / ( Σ L^run_{t-34..t-7} / 4 )   se denominatore ≥ 30 TSSe/sett.
```

**Pressione di carico** (0-1):

```
p_form = clamp( (−Form% − 0.10) / 0.30 , 0, 1 )       # 0 sopra −10%, 1 a −40% o peggio
p_ramp = clamp( (Ramp − 7) / 7 , 0, 1 )               # 0 fino a +7 CTL/sett., 1 a +14
p_mono = clamp( (Mono − 1.5) / 1.0 , 0, 1 )           # 0 sotto 1.5, 1 a ≥ 2.5
D_load = clamp( 0.6·p_form + 0.25·p_ramp + 0.15·p_mono , 0, 1 )
```

`RunRatio > 1.5` **non** entra nell'indice. Genera una nota separata ("la corsa è cresciuta molto più del solito"), perché riguarda il rischio muscoloscheletrico e non quello sistemico.

**Perché peso moderato:** il carico è la **causa** potenziale, non la **risposta**. Un TSB molto negativo durante un blocco pianificato è voluto. Il carico serve soprattutto a **interpretare** la risposta (§9.6).

### 9.4 Componenti RISPOSTA

**HRV** (fonte: Garmin `lastNightAvg` → ln):

```
x = ln(RMSSD_notte)
D_hrv_low  = dev(zb = −z_lnRMSSD)
cv7 = DS(x_{t-6..t}) / media(x_{t-6..t})          # CV di lnRMSSD
cv_base = mediana dei cv7 nei 60 g precedenti
D_hrv_cv = clamp( (cv7/cv_base − 1.25) / 1.0 , 0, 1 )   # CV aumentato di oltre il 25%
D_hrv = max( D_hrv_low , 0.5·D_hrv_low + 0.5·D_hrv_cv )
flag_hrv_alta = (z_lnRMSSD > +1.5) per ≥ 5 dei 7 giorni      # possibile saturazione parasimpatica, vedi §9.6
```

**FC a riposo** (Garmin `get_rhr_daily` o `restingHeartRate` del sonno; in alternativa stima da Health Connect):

```
D_rhr = dev(zb = z_RHR)
flag_rhr_bassa = z_RHR < −1.5 per ≥ 5/7 giorni     # da leggere insieme a flag_hrv_alta
```

**Sonno** (durata da Garmin `sleepTimeSeconds` o da `SleepSessionRecord` meno gli stadi awake):

```
target = max( mediana(durata, 60 g), 7 h )                      # Walsh et al. 2021: la maggior parte degli atleti necessita ≥ 7 h
debito7 = Σ_{t-6..t} max(0, target − durata_g)                  # ore
midpoint_sd7 = DS circolare del punto medio del sonno su 7 g     # regolarità
D_sleep = clamp( 0.5·dev(−z_durata) + 0.35·clamp((debito7 − 3)/5,0,1) + 0.15·clamp((midpoint_sd7 − 60min)/60min,0,1) , 0, 1 )
```

Le fasi del sonno non si usano. Il `sleepScore` di Garmin si mostra ma non entra nel calcolo, per evitare doppi conteggi.

**Soggettivo** (check-in opzionale di 30 secondi, consigliato al mattino). Cinque item su scala 1-7, con 1 = ottimo e 7 = pessimo:

1. qualità del sonno;
2. fatica generale;
3. dolori muscolari;
4. stress/umore;
5. motivazione ad allenarsi.

Più un flag "mi sento malato / sintomi" (sì/no). Gli item 1-4 ricalcano l'Hooper Index; il quinto è aggiunto.

```
S = somma (5..35)
D_subj = 0.7·dev(zb = z_S) + 0.3·clamp((S − 20)/10, 0, 1)    # z personale + piccolo ancoraggio assoluto
```

Se è spuntato "sintomi di malattia" → `illness_flag`, gestito a parte (§9.7).

**Prestazione** (aggiornata settimanalmente, peso basso):

```
Per sedute "steady aerobiche" confrontabili (durata ≥ 40 min, IF < 0.8, per sport):
  EF_s = NP/FCmedia (bici) o velocità/FCmedia (corsa)
  dEF  = (mediana EF ultime 14 g − mediana EF g 15-60) / mediana EF g 15-60
D_perf_ef = clamp( (−dEF − 0.03) / 0.07 , 0, 1 )            # calo > 3% inizia a contare, 10% = 1
Picco FC in sedute dure (RPE ≥ 8 o TE anaerobico ≥ 3):
  gap = mediana(FCpicco ultime 3 sedute dure) − p90(FCpicco sedute dure 60 g)
D_perf_hr = clamp( (−gap − 3) / 5 , 0, 1 )                  # ≥ 3 bpm sotto norma inizia a contare
D_perf = max(D_perf_ef, D_perf_hr)   (NA se < 3 sedute confrontabili)
```

### 9.5 Indice composito, pesi e confidenza

**Pesi nominali.**

| Componente | Peso nominale | Motivazione |
|---|---|---|
| Soggettivo | 0.25 | Il più sensibile secondo Saw et al. (2016); costo minimo |
| HRV | 0.25 | Marker autonomico più studiato negli atleti di endurance (Plews, Buchheit) |
| FC a riposo | 0.15 | Robusta, ma meno sensibile; in parte ridondante con l'HRV |
| Sonno | 0.15 | Associato a FOR e malattia (Hausswirth 2014); misura della durata affidabile |
| Carico | 0.15 | Contesto/causa, non risposta; evidenza debole sugli esiti |
| Prestazione | 0.05 | Segno cardine ma lento e rumoroso in campo |

**Formula.**

```
Per ogni componente i: disponibile_i ∈ {0,1}, qualità_i ∈ [0,1]
   (per esempio HRV: n notti valide nella settimana / 7, con un tetto a 1 da 5 notti;
                     carico: frazione di TSSe con q_load ≥ media)
w'_i = w_i · disponibile_i
BCR_raw = Σ w'_i · D_i / Σ w'_i
BCR     = round(100 · BCR_raw)

Confidenza = Σ (w_i · disponibile_i · qualità_i)            # da 0 a 1
   ≥ 0.70 → alta;  0.45–0.70 → media;  < 0.45 → bassa (mostrare solo il trend, nessun allarme di livello ≥ 2)

Vincolo minimo per mostrare un valore: carico disponibile E almeno uno tra {HRV, FC a riposo, soggettivo}.
```

**Ri-normalizzare i pesi** è la degradazione graduale: un atleta senza check-in ottiene un indice basato su HRV, FC a riposo, sonno e carico, ma con confidenza più bassa.

**Semaforo** (sul BCR medio a 3 giorni, per stabilità):

- verde < 30;
- giallo 30-54;
- arancione 55-74;
- rosso ≥ 75.

Il rosso **non** si visualizza senza confidenza almeno media e senza una regola di persistenza soddisfatta (§9.7).

### 9.6 Interpretazione carico × risposta

L'informazione più utile non è il numero: è **la combinazione** di carico e risposta.

```
R = media pesata delle sole componenti di risposta (HRV, FC a riposo, sonno, soggettivo, prestazione)
P = D_load
```

| | Risposta normale (R < 0.3) | Risposta alterata (R ≥ 0.3) |
|---|---|---|
| **Carico in aumento / alto** (P ≥ 0.4) | A. "Stai assorbendo bene il carico" | B. "Fatica accumulata, coerente con il carico". Normale in un blocco pianificato: monitora, prevedi lo scarico |
| **Carico normale / basso** (P < 0.4) | Stato di base | C. "Segnali non spiegati dal carico". Pensa a sonno, stress, malattia incipiente, alimentazione (coach nutrizionista, screening REDs) |

Situazioni speciali.

- **D. Scarico o taper in corso** (ATL in calo ≥ 20% in 7-10 giorni) **e** risposta ancora alterata dopo 7-10 giorni: è il pattern più compatibile con NFOR ed è quello che merita l'attenzione maggiore.
- **E. HRV molto alta + FC a riposo bassa** (`flag_hrv_alta` e/o `flag_rhr_bassa`) **insieme a** soggettivo peggiorato **o** D_perf > 0.3 **o** carico alto: "pattern atipico". Possibile saturazione parasimpatica tipica del FOR (Le Meur 2013a). Non va mostrato come "ottimo recupero". Se il soggettivo è buono e la prestazione è stabile, si tratta probabilmente di adattamento positivo.

### 9.7 Regole di allarme (persistenza e concordanza)

Un singolo giorno non genera mai un allarme. Serve la combinazione di **persistenza** e **concordanza tra domini**.

```
dominio_alterato_i(t) = D_i(t) ≥ 0.4     per i ∈ {HRV, FC a riposo, sonno, soggettivo, prestazione}
n_dom(t) = numero di domini alterati (tra quelli disponibili)

Livello 0 – Nessun messaggio.
Livello 1 – "Attenzione" (giallo):
     BCR ≥ 30 in ≥ 3 degli ultimi 5 giorni  E  n_dom ≥ 1
Livello 2 – "Fatica accumulata" (arancione):
     BCR ≥ 55 in ≥ 5 degli ultimi 7 giorni  E  n_dom ≥ 2 per ≥ 4 di quei giorni  E  confidenza ≥ media
     OPPURE pattern C (non spiegato dal carico) per ≥ 7 giorni
Livello 3 – "Segnali persistenti" (rosso):
     Livello 2 mantenuto ≥ 14 giorni
     OPPURE pattern D (risposta alterata nonostante 7–10 g di scarico)
     OPPURE trend: pendenza (regressione lineare) di m7_lnRMSSD su 14 g < −SWC/7 al giorno  E  pendenza di S (soggettivo) > 0  E  D_perf > 0.3
     → suggerire confronto con allenatore e medico sportivo; proporre screening energetico (nutrizionista)
```

Regole di esclusione e modulazione.

- `illness_flag` attivo, oppure frequenza respiratoria notturna > baseline + 2σ per ≥ 2 notti [Pratica]: sospendere la logica di overtraining. Mostrare "possibile malattia / stato febbrile: il riposo ha priorità" e non contare quei giorni nelle baseline.
- Blocco di carico pianificato, dichiarato dall'utente o letto dal piano: il livello 2 in pattern B diventa un messaggio informativo e non un avviso, finché il blocco dura ≤ 21 giorni. Il pattern C e il livello 3 restano attivi.
- Giorni marcati (alcol, viaggio con fuso ≥ 3 h, notte di gara) restano nel calcolo giornaliero ma sono **esclusi dalle baseline** e non contano ai fini della persistenza. Al massimo 1 giorno per finestra.
- Isteresi: si scende di livello solo dopo 3 giorni consecutivi sotto la soglia di ingresso, per evitare messaggi alternati.

### 9.8 Periodo di avviamento

| Fase | Condizione | Cosa mostrare |
|---|---|---|
| **Giorno 0** | Import storico: 90 giorni di attività Garmin (o Health Connect con permesso storico); HRV, FC a riposo e sonno per 60 giorni se disponibili | Se c'è lo storico, si salta subito alla fase con baseline |
| **Giorni 1-13** | Baseline insufficiente | Solo carico (CTL/ATL/Form%) e valori grezzi; messaggio "sto imparando i tuoi valori normali" |
| **Giorni 14-27** | ≥ 14 valori validi di almeno un marker di risposta | BCR provvisorio, confidenza al massimo "media"; solo livello 1 |
| **Giorni ≥ 28** | ≥ 28 valori validi; CTL considerata stabile dopo 42 giorni di storico carico | BCR completo; tutti i livelli |

Il periodo è coerente con Garmin HRV Status (circa 3 settimane per la baseline) e con la finestra di 60 giorni di HRV4Training.

### 9.9 Spiegabilità nell'interfaccia

- **Contributi:** mostrare `w'_i·D_i / Σw'_i` per ciascun componente, come barre impilate o elenco ordinato. Esempio: "Oggi l'indice è 58: HRV sotto il tuo normale da 5 giorni (+22), sonno ridotto (−4 h nella settimana) (+15), carico in rapida crescita (+12)".
- **Banda normale:** per HRV e FC a riposo, grafico a 30-60 giorni con la media a 7 giorni e la banda ±0.5 σ, come in HRV4Training o Intervals.icu.
- **Matrice carico × risposta** (§9.6) come etichetta testuale semplice.
- **Confidenza** visibile, con l'indicazione di cosa manca ("mancano 3 notti di HRV: indossa l'orologio a letto", "aggiungi il check-in per una lettura più precisa").
- **Valori Garmin a confronto**, solo informativi: Training Readiness, HRV Status, Training Status. Se divergono, dirlo senza drammi: "algoritmi diversi, segnali diversi".

### 9.10 Messaggi: tono e limiti

Regole:

- linguaggio descrittivo, non diagnostico;
- mai "overtraining", "sindrome" o percentuali di rischio;
- sempre un'azione concreta e reversibile;
- mai prescrivere farmaci o integratori.

| Situazione | Esempio di messaggio |
|---|---|
| Livello 1, pattern B | "Negli ultimi giorni il tuo recupero è un po' sotto il solito, in linea con il carico recente. Se la fatica continua, valuta una giornata facile." |
| Livello 2, pattern B, blocco pianificato | "Sei nel pieno del blocco di carico e i segnali di fatica sono attesi. Ricordati di proteggere il sonno; lo scarico previsto tra X giorni è importante." |
| Pattern C | "I tuoi indicatori di recupero sono sotto il normale anche se il carico non è aumentato. Spesso dipende da sonno, stress, alimentazione o un malanno in arrivo. Vuoi fare il punto con il coach nutrizionista?" |
| Pattern E | "La tua HRV è insolitamente alta mentre la fatica percepita è alta. A volte succede quando il corpo è molto affaticato: meglio non leggerlo come 'super recupero'." |
| Livello 3 | "Da oltre due settimane diversi segnali indicano un recupero insufficiente, anche dopo aver ridotto il carico. Ti consigliamo di parlarne con il tuo allenatore e con un medico sportivo, per escludere altre cause (per esempio carenze, infezioni o un apporto energetico insufficiente)." |
| Malattia | "Sembra che tu non stia bene. In questi giorni il riposo vale più dell'allenamento; l'indice è in pausa." |

Ogni messaggio deve avere un link a "Perché vedo questo?", con i contributi, e la nota fissa: "TriForge non fornisce diagnosi mediche".

### 9.11 Pseudocodice riassuntivo

```python
def bcr_giornaliero(t, dati, profilo):
    L = carico_giornaliero(dati.attivita, profilo)            # TSSe con qualità (§9.3)
    atl, ctl = ewma(L, 7), ewma(L, 42)
    D = {}
    D["load"] = pressione_carico(atl, ctl, L, t)               # p_form, p_ramp, p_mono
    for m in ["hrv", "rhr", "sleep", "subj"]:
        serie = dati.marker(m, escludi=giorni_marcati(dati))
        if valori_validi(serie, finestra=(t-60, t-8)) >= 14 and valori_validi(serie, (t-6, t)) >= 4:
            D[m] = deviazione_componente(m, serie, t)          # §9.4
    D["perf"] = deviazione_prestazione(dati.attivita, t)       # può essere None
    W = {"subj":.25, "hrv":.25, "rhr":.15, "sleep":.15, "load":.15, "perf":.05}
    disp = {k: v for k, v in D.items() if v is not None}
    if "load" not in disp or not ({"hrv","rhr","subj"} & disp.keys()):
        return Stato(valore=None, motivo="dati insufficienti")
    bcr = 100 * sum(W[k]*disp[k] for k in disp) / sum(W[k] for k in disp)
    conf = sum(W[k]*qualita(k, dati, t) for k in disp)
    pattern = classifica(P=D["load"], R=risposta(disp, W), storia=dati.storia_bcr, flags=flag(dati, t))
    livello = regole_allarme(storia_bcr + [bcr], pattern, conf, dati.flag_malattia, dati.blocco_pianificato)
    contributi = {k: W[k]*disp[k]/sum(W[k] for k in disp) for k in disp}
    return Stato(valore=round(bcr), confidenza=conf, pattern=pattern, livello=livello, contributi=contributi)
```

### 9.12 Validazione suggerita

- Calcolare l'indice **retrospettivamente** sullo storico degli utenti, offline e in ombra, prima di mostrarlo.
- Verificare la frequenza degli allarmi: livello 2 nel 5-10% dei giorni al massimo, livello 3 raro.
- Chiedere feedback: "il messaggio era sensato?".
- Correlare l'indice con gli eventi dichiarati: malattie, infortuni, gare deludenti.
- Rivedere le soglie dopo 3-6 mesi. I pesi e le soglie qui proposti sono **punti di partenza**.

---

## 10. Nota: cosa cambia per un'app di solo nuoto (per esempio SwimForge)

Le sezioni 1-8 valgono invariate. Nella proposta cambiano questi elementi [Proposta].

- **Carico.** La fonte principale è lo **sTSS da passo e CSS** (`ore × (v/CSS)³ × 100`, sui tratti nuotati, escluse le pause), oppure lo **sRPE**. La CSS si stima con un test 400/200 m: `CSS = (400 − 200)/(t400 − t200)`, in m/s; va aggiornata ogni 4-8 settimane. Per sedute a intervalli è meglio calcolare l'sTSS **per ripetuta** dai dati delle vasche (`get_activity_swim_details`, lap) e sommare. Gli stili diversi dallo stile libero costano di più a parità di velocità: usare CSS separate o sRPE. Le bracciate e lo SWOLF non sono misure di carico, ma un peggioramento dello SWOLF a parità di passo è un indizio di fatica tecnica [Pratica].
- **FC.** Senza fascia toracica, FC, hrTSS, Training Effect e `activityTrainingLoad` di Garmin nel nuoto vanno considerati a **bassa qualità** (q_load = bassa) e non usati per l'EF o il decoupling. Con la fascia HRM-Swim/Pro si possono recuperare.
- **Prestazione.** Il decoupling FC/passo è quasi sempre impraticabile. Sono più utili il **passo a RPE costante** su serie standard ripetute (per esempio 10×100 a RPE 6), il tempo su un test-set ricorrente e lo sRPE delle serie standard.
- **Pesi.** I marker notturni (HRV e FC a riposo), misurati fuori dall'acqua, restano validi. Poiché il carico è meno preciso, aumenta il peso relativo della **risposta**: HRV 0.30, soggettivo 0.30, sonno 0.15, FC a riposo 0.15, carico 0.10. Il sonno pesa di più nei nuotatori con allenamenti all'alba, dove la privazione cronica è comune: tenere conto della regolarità.
- **Specificità del nuoto.** Il carico sulla spalla non è catturato da nessuna metrica sistemica. Un item soggettivo "dolore alla spalla" (0-10) va tenuto separato dall'indice e trattato come allerta locale, analogo al RunRatio per la corsa.
- **Prevalenza.** La letteratura storica sulla "staleness" nasce proprio dal nuoto (Morgan et al., 1987). Il nuoto giovanile con alti volumi è un contesto a rischio documentato.

---

## 11. Cosa NON si può dedurre e rischi

### 11.1 Limiti inferenziali

- **Nessuna diagnosi.** L'indice non distingue FOR, NFOR e OTS, e non distingue il sovraccarico dalle sue cause alternative.
- **Nessuna probabilità di infortunio.** Né l'ACWR né il TSB ne danno una valida per il singolo individuo (Impellizzeri et al., 2020; 2021).
- **Nessuna stima della disponibilità energetica.** Le calorie dei wearable e i diari alimentari non bastano. Il REDs si sospetta e si invia allo specialista, non si misura.
- **Nessun nesso causale.** Un HRV basso dopo un giorno di carico non dimostra che il carico ne sia la causa.
- **Gli assoluti non si confrontano tra persone:** un RMSSD di 40 ms non è "peggio" di uno di 80 ms in un altro atleta.

### 11.2 Fonti di falsi positivi e falsi negativi

| Fattore | Effetto atteso | Mitigazione |
|---|---|---|
| **Blocco di carico pianificato** | HRV giù, FC a riposo su, soggettivo giù: è voluto | Flag "blocco pianificato"; pattern B; messaggi informativi |
| **Taper** | HRV su e FC a riposo giù: positivo. A volte, se c'era FOR, la risposta resta alterata o "rimbalza" | Pattern D solo dopo 7-10 giorni; non allarmare nei primi giorni |
| **Malattia incipiente** | HRV giù, FC a riposo e respirazione su, spesso prima dei sintomi | Flag malattia, respirazione notturna; sospendere la logica overtraining |
| **Alcol** | HRV notturna in forte calo e FC a riposo su per 1-2 notti (Pietilä et al., 2018) | Check-in "alcol ieri sera"; escludere dalla baseline |
| **Viaggi / jet lag / quota** | Sonno, HRV e FC a riposo alterati; in quota la FC a riposo sale e la SpO2 scende | Flag viaggio; acclimatazione Garmin |
| **Ciclo mestruale** | Nella fase luteale la HRV vagale tende a scendere e la FC a riposo a salire (Schmalenberger et al., 2019) | Con consenso, leggere `MenstruationPeriodRecord`; baseline per fase o tolleranza maggiore; l'**amenorrea** è un segnale REDs, non "rumore" |
| **Stress psicologico, lavoro, esami** | Tutti i marker peggiorano | Corretto che l'indice salga: lo stress è parte del carico totale (Meeusen 2013); pattern C |
| **Caldo / disidratazione** | FC più alta in attività (EF giù, decoupling su); FC a riposo su | Considerare la temperatura (`get_activity_weather`); peso basso della prestazione |
| **Allenamento serale intenso o pasto tardivo** | HRV notturna più bassa la notte stessa | Media a 7 giorni; persistenza |
| **Cambio di dispositivo o firmware** | Salto nella baseline | Ripartire con la baseline; flag "fonte cambiata" |
| **Orologio non indossato / batteria** | Dati mancanti, falsi negativi | Confidenza; nessun allarme con confidenza bassa |
| **Soglie non aggiornate (FTP, CSS, LTHR)** | TSS sovra- o sottostimato | Promemoria per il test; stima automatica dalle migliori prestazioni |
| **Saturazione parasimpatica** | HRV alta letta come "ottimo", quindi falso negativo | Pattern E |
| **Farmaci (betabloccanti ecc.), patologie cardiache** | Marker autonomici non interpretabili | Domanda in onboarding; disattivare le componenti FC/HRV |

### 11.3 Rischi d'uso

- **Nocebo e ansia da dati.** Monitoraggi ossessivi possono peggiorare sonno e benessere. Servono messaggi sobri e la possibilità di nascondere l'indice.
- **Falsa rassicurazione.** Un indice verde non garantisce l'assenza di problemi, soprattutto con confidenza bassa o se il REDs riduce la FC a riposo.
- **Dati sanitari.** HRV, ciclo e alimentazione sono dati sanitari sensibili (art. 9 GDPR). Servono consenso esplicito, minimizzazione e uso trasparente.
- **Dipendenza da API non ufficiali.** Se python-garminconnect si rompe, l'indice deve degradare con eleganza (§9.5), non sparire o dare valori errati.

---

## Riferimenti bibliografici

- Allen H, Coggan AR, McGregor S (2019). *Training and Racing with a Power Meter*, 3rd ed. VeloPress.
- Altini M, Plews D (2021). What is behind changes in resting heart rate and heart rate variability? A large-scale analysis of longitudinal measurements acquired in free-living. *Sensors* 21(23):7932. doi:10.3390/s21237932
- Aubry A, Hausswirth C, Louis J, Coutts AJ, Le Meur Y (2014). Functional overreaching: the key to peak performance during the taper? *Med Sci Sports Exerc* 46(9):1769-1777. doi:10.1249/MSS.0000000000000301
- Banister EW, Calvert TW, Savage MV, Bach T (1975). A systems model of training for athletic performance. *Aust J Sports Med* 7:57-61.
- Bellenger CR, Fuller JT, Thomson RL, Davison K, Robertson EY, Buckley JD (2016). Monitoring athletic training status through autonomic heart rate regulation: a systematic review and meta-analysis. *Sports Med* 46(10):1461-1486. doi:10.1007/s40279-016-0484-2
- Bellinger P (2020). Functional overreaching in endurance athletes: a necessity or cause for concern? *Sports Med* 50(6):1059-1073. doi:10.1007/s40279-020-01269-w
- Birrer D, Lienhard D, Williams CA, Röthlin P, Morgan G (2013). Prevalence of non-functional overreaching and the overtraining syndrome in Swiss elite athletes. *Schweiz Z Sportmed Sporttraumatol* 61(4):23-29.
- Bourdon PC, Cardinale M, Murray A, et al. (2017). Monitoring athlete training loads: consensus statement. *Int J Sports Physiol Perform* 12(Suppl 2):S2161-S2170. doi:10.1123/IJSPP.2017-0208
- Buchheit M (2014). Monitoring training status with HR measures: do all roads lead to Rome? *Front Physiol* 5:73. doi:10.3389/fphys.2014.00073
- Busso T (2003). Variable dose-response relationship between exercise training and performance. *Med Sci Sports Exerc* 35(7):1188-1195. doi:10.1249/01.MSS.0000074465.13621.37
- Cadegiani FA, Kater CE (2017). Hormonal aspects of overtraining syndrome: a systematic review. *BMC Sports Sci Med Rehabil* 9:14. doi:10.1186/s13102-017-0079-8
- Cejuela R, Esteve-Lanao J (2011). Training load quantification in triathlon. *J Hum Sport Exerc* 6(2):218-232. doi:10.4100/jhse.2011.62.03
- Edwards S (1993). *The Heart Rate Monitor Book*. Polar Electro / Fleet Feet Press.
- Flatt AA, Esco MR (2016). Evaluating individual training adaptation with smartphone-derived heart rate variability in a collegiate female soccer team. *J Strength Cond Res* 30(2):378-385. doi:10.1519/JSC.0000000000001095
- Foster C (1998). Monitoring training in athletes with reference to overtraining syndrome. *Med Sci Sports Exerc* 30(7):1164-1168. doi:10.1097/00005768-199807000-00023
- Foster C, Florhaug JA, Franklin J, et al. (2001). A new approach to monitoring exercise training. *J Strength Cond Res* 15(1):109-115.
- Gabbett TJ (2016). The training-injury prevention paradox: should athletes be training smarter and harder? *Br J Sports Med* 50(5):273-280. doi:10.1136/bjsports-2015-095788
- Garmin / Garmin Rumors (2025). Garmin clarifies what data it's sharing with Google Health Connect. https://garminrumors.com/garmin-clarifies-what-data-its-sharing-with-google-health-connect-heres-the-full-list/
- Google / Android Developers. Health Connect data types. https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types
- Haddad M, Stylianides G, Djaoui L, Dellal A, Chamari K (2017). Session-RPE method for training load monitoring: validity, ecological usefulness, and influencing factors. *Front Neurosci* 11:612. doi:10.3389/fnins.2017.00612
- Hausswirth C, Louis J, Aubry A, Bonnet G, Duffield R, Le Meur Y (2014). Evidence of disturbed sleep and increased illness in overreached endurance athletes. *Med Sci Sports Exerc* 46(5):1036-1045. doi:10.1249/MSS.0000000000000177
- Hellard P, Avalos M, Lacoste L, Barale F, Chatard JC, Millet GP (2006). Assessing the limitations of the Banister model in monitoring training. *J Sports Sci* 24(5):509-520. doi:10.1080/02640410500244697
- Hooper SL, Mackinnon LT, Howard A, Gordon RD, Bachmann AW (1995). Markers for monitoring overtraining and recovery. *Med Sci Sports Exerc* 27(1):106-112.
- Hulin BT, Gabbett TJ, Lawson DW, Caputi P, Sampson JA (2016). The acute:chronic workload ratio predicts injury: high chronic workload may decrease injury risk in elite rugby league players. *Br J Sports Med* 50(4):231-236. doi:10.1136/bjsports-2015-094817
- Impellizzeri FM, Marcora SM, Coutts AJ (2019). Internal and external training load: 15 years on. *Int J Sports Physiol Perform* 14(2):270-273. doi:10.1123/ijspp.2018-0935
- Impellizzeri FM, Tenan MS, Kempton T, Novak A, Coutts AJ (2020). Acute:chronic workload ratio: conceptual issues and fundamental pitfalls. *Int J Sports Physiol Perform* 15(6):907-913. doi:10.1123/ijspp.2019-0864
- Impellizzeri FM, Woodcock S, Coutts AJ, Fanchini M, McCall A, Vigotsky AD (2021). What role do chronic workloads play in the acute to chronic workload ratio? Time to dismiss ACWR and its underlying theory. *Sports Med* 51(3):581-592. doi:10.1007/s40279-020-01378-6
- Kellmann M, Kallus KW (2001). *Recovery-Stress Questionnaire for Athletes: User Manual*. Human Kinetics.
- Kellmann M, Bertollo M, Bosquet L, et al. (2018). Recovery and performance in sport: consensus statement. *Int J Sports Physiol Perform* 13(2):240-245. doi:10.1123/ijspp.2017-0759
- Kenttä G, Hassmén P, Raglin JS (2001). Training practices and overtraining syndrome in Swedish age-group athletes. *Int J Sports Med* 22(6):460-465. doi:10.1055/s-2001-16250
- Kiviniemi AM, Hautala AJ, Kinnunen H, Tulppo MP (2007). Endurance training guided individually by daily heart rate variability measurements. *Eur J Appl Physiol* 101(6):743-751. doi:10.1007/s00421-007-0552-2
- Le Meur Y, Pichon A, Schaal K, et al. (2013a). Evidence of parasympathetic hyperactivity in functionally overreached athletes. *Med Sci Sports Exerc* 45(11):2061-2071. doi:10.1249/MSS.0b013e3182980125
- Le Meur Y, Hausswirth C, Natta F, Couturier A, Bignet F, Vidal PP (2013b). A multidisciplinary approach to overreaching detection in endurance trained athletes. *J Appl Physiol* 114(3):411-420. doi:10.1152/japplphysiol.01254.2012
- Lolli L, Batterham AM, Hawkins R, Kelly DM, Strudwick AJ, Thorpe R, Gregson W, Atkinson G (2019). Mathematical coupling causes spurious correlation within the conventional acute-to-chronic workload ratio calculations. *Br J Sports Med* 53(15):921-922. doi:10.1136/bjsports-2017-098110
- Lucia A, Hoyos J, Santalla A, Earnest C, Chicharro JL (2003). Tour de France versus Vuelta a España: which is harder? *Med Sci Sports Exerc* 35(5):872-878. doi:10.1249/01.MSS.0000064999.82036.B4
- Matos NF, Winsley RJ, Williams CA (2011). Prevalence of nonfunctional overreaching/overtraining in young English athletes. *Med Sci Sports Exerc* 43(7):1287-1294. doi:10.1249/MSS.0b013e318207f87b
- Meeusen R, Duclos M, Foster C, Fry A, Gleeson M, Nieman D, Raglin J, Rietjens G, Steinacker J, Urhausen A (2013). Prevention, diagnosis, and treatment of the overtraining syndrome: joint consensus statement of the European College of Sport Science and the American College of Sports Medicine. *Med Sci Sports Exerc* 45(1):186-205. doi:10.1249/MSS.0b013e318279a10a (also *Eur J Sport Sci* 13(1):1-24. doi:10.1080/17461391.2012.730061)
- Miller DJ, Sargent C, Roach GD (2022). A validation of six wearable devices for estimating sleep, heart rate and heart rate variability in healthy adults. *Sensors* 22(16):6317. doi:10.3390/s22166317
- Millet GP, Vleck VE, Bentley DJ (2009). Physiological differences between cycling and running: lessons from triathletes. *Sports Med* 39(3):179-206. doi:10.2165/00007256-200939030-00002
- Morgan WP, Brown DR, Raglin JS, O'Connor PJ, Ellickson KA (1987). Psychological monitoring of overtraining and staleness. *Br J Sports Med* 21(3):107-114. doi:10.1136/bjsm.21.3.107
- Morton RH, Fitz-Clarke JR, Banister EW (1990). Modeling human performance in running. *J Appl Physiol* 69(3):1171-1177. doi:10.1152/jappl.1990.69.3.1171
- Mountjoy M, Sundgot-Borgen JK, Burke LM, et al. (2018). IOC consensus statement on relative energy deficiency in sport (RED-S): 2018 update. *Br J Sports Med* 52(11):687-697. doi:10.1136/bjsports-2018-099193
- Mountjoy M, Ackerman KE, Bailey DM, et al. (2023). 2023 International Olympic Committee's (IOC) consensus statement on Relative Energy Deficiency in Sport (REDs). *Br J Sports Med* 57(17):1073-1097. doi:10.1136/bjsports-2023-106994
- Pietilä J, Helander E, Korhonen I, Myllymäki T, Kujala UM, Lindholm H (2018). Acute effect of alcohol intake on cardiovascular autonomic regulation during the first hours of sleep in a large real-world sample of Finnish employees: observational study. *JMIR Ment Health* 5(1):e23. doi:10.2196/mental.9519
- Plews DJ, Laursen PB, Kilding AE, Buchheit M (2012). Heart rate variability in elite triathletes, is variation in variability the key to effective training? A case comparison. *Eur J Appl Physiol* 112(11):3729-3741. doi:10.1007/s00421-012-2354-4
- Plews DJ, Laursen PB, Stanley J, Kilding AE, Buchheit M (2013a). Training adaptation and heart rate variability in elite endurance athletes: opening the door to effective monitoring. *Sports Med* 43(9):773-781. doi:10.1007/s40279-013-0071-8
- Plews DJ, Laursen PB, Kilding AE, Buchheit M (2013b). Evaluating training adaptation with heart-rate measures: a methodological comparison. *Int J Sports Physiol Perform* 8(6):688-691. doi:10.1123/ijspp.8.6.688
- Plews DJ, Laursen PB, Le Meur Y, Hausswirth C, Kilding AE, Buchheit M (2014). Monitoring training with heart-rate variability: how much compliance is needed for valid assessment? *Int J Sports Physiol Perform* 9(5):783-790. doi:10.1123/ijspp.2013-0455
- Plews DJ, Scott B, Altini M, Wood M, Kilding AE, Laursen PB (2017). Comparison of heart-rate-variability recording with smartphone photoplethysmography, Polar H7 chest strap, and electrocardiography. *Int J Sports Physiol Perform* 12(10):1324-1328. doi:10.1123/ijspp.2016-0668
- python-garminconnect (cyberjunky). README e docs/API_ENDPOINTS.md. https://github.com/cyberjunky/python-garminconnect
- Saw AE, Main LC, Gastin PB (2016). Monitoring the athlete training response: subjective self-reported measures trump commonly used objective measures: a systematic review. *Br J Sports Med* 50(5):281-291. doi:10.1136/bjsports-2015-094758
- Schmalenberger KM, Eisenlohr-Moul TA, Würth L, et al. (2019). A systematic review and meta-analysis of within-person changes in cardiac vagal activity across the menstrual cycle: implications for female health and future studies. *J Clin Med* 8(11):1946. doi:10.3390/jcm8111946
- Stellingwerff T, Heikura IA, Meeusen R, et al. (2021). Overtraining syndrome (OTS) and relative energy deficiency in sport (RED-S): shared pathways, symptoms and complexities. *Sports Med* 51(11):2251-2280. doi:10.1007/s40279-021-01491-0
- Vesterinen V, Nummela A, Heikura I, et al. (2016). Individual endurance training prescription with heart rate variability. *Med Sci Sports Exerc* 48(7):1347-1354. doi:10.1249/MSS.0000000000000910
- Vleck V, Millet GP, Alves FB (2014). The impact of triathlon training and racing on athletes' general health. *Sports Med* 44(12):1659-1692. doi:10.1007/s40279-014-0244-0
- Walsh NP, Halson SL, Sargent C, et al. (2021). Sleep and the athlete: narrative review and 2021 expert consensus recommendations. *Br J Sports Med* 55(7):356-368. doi:10.1136/bjsports-2020-102025
- Wang C, Vargas JT, Stokes T, Steele R, Shrier I (2020). Analyzing activity and injury: lessons learned from the acute:chronic workload ratio. *Sports Med* 50(7):1243-1254. doi:10.1007/s40279-020-01280-1
- Williams S, West S, Cross MJ, Stokes KA (2017). Better way to determine the acute:chronic workload ratio? *Br J Sports Med* 51(3):209-210. doi:10.1136/bjsports-2016-096589

*Note sulle fonti. I riferimenti sono stati riportati da conoscenza della letteratura. Durante questa sessione sono stati verificati online: README e API_ENDPOINTS di python-garminconnect, la pagina dei tipi di dati di Health Connect, l'elenco dei dati condivisi da Garmin con Health Connect e l'abstract di Hausswirth et al. (2014). Prima di una pubblicazione esterna conviene ricontrollare i DOI. Le caratteristiche dei prodotti commerciali (§7) derivano da documentazione pubblica dei produttori e possono cambiare; gli algoritmi restano proprietari.*
