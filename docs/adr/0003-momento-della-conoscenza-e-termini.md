# ADR-0003: Momento della conoscenza dell'incidente significativo e computo dei termini

- **Stato**: accettata; aggiornata dopo la revisione di fine fase 3 (conclusione della gestione registrata una sola volta)

## Contesto
I termini di pre-notifica (24 h) e notifica (72 h) decorrono "da quando [i soggetti] sono venuti a
conoscenza dell'incidente significativo" (art. 25, c. 5, lett. a e b, D.Lgs. 138/2024). La relazione
finale scade "entro un mese dalla trasmissione della notifica" (lett. d) o, se l'incidente è in corso,
entro un mese dalla conclusione della gestione, con relazioni mensili nel frattempo (lett. e).

Le Linee guida NIS dell'ACN (*Definizione del processo di gestione degli incidenti di sicurezza
informatica*, v1.1, aprile 2026, par. 2.3.1, riquadro "Evidenza dell'incidente") precisano che rileva
il momento in cui il soggetto **ha evidenza** dell'incidente, cioè "dispone di elementi oggettivi dai
quali si evince che si è verificato un incidente", e che tale acquisizione "definisce il momento dal
quale decorre il termine per la trasmissione della pre-notifica (24 ore) e della notifica (72 ore)".
Il par. 2.2 distingue inoltre il rilevamento di un evento rilevante per la sicurezza dalla successiva
analisi che porta a dichiarare l'incidente.

Il considerando 31 del Reg. di esecuzione (UE) 2024/2690 dice lo stesso per i soggetti che vi rientrano: il
soggetto è "a conoscenza" dell'incidente significativo quando, dopo una valutazione iniziale, è
"ragionevolmente certo" che si sia verificato. Le FAQ ACN (ISB.G.4) parlano di elementi oggettivi acquisiti
"a valle di un'analisi anche sommaria".

In un SOC i due istanti sono spesso distanti: un allarme alle 02:10 può diventare un incidente
significativo accertato solo alle 04:30, dopo il triage.

## Alternative valutate
1. **Decorrenza dalla rilevazione (primo allarme)** – massimamente prudente, ma contraria al testo
   ("incidente significativo") e alle Linee guida; farebbe scadere termini per eventi che si rivelano
   falsi positivi e penalizzerebbe un triage accurato.
2. **Decorrenza dalla valutazione di significatività eseguita nell'applicazione** – semplice e
   automatica, ma lega il termine all'istante in cui l'operatore usa lo strumento, non a quando il
   soggetto ha avuto gli elementi oggettivi: chi registra tardi sposterebbe in avanti le scadenze.
3. **Due istanti distinti, il secondo dichiarato e motivato** – `rilevatoIl` (primo evento) e
   `conoscenzaIl` (evidenza dell'incidente significativo), inserito dall'operatore con la descrizione
   degli elementi oggettivi, vincolato a `rilevatoIl ≤ conoscenzaIl ≤ adesso`, registrato una sola volta.

## Decisione
Alternativa 3.
- `conoscenzaIl` si registra solo quando la valutazione dà esito significativo, insieme a motivazione,
  autore e istante di registrazione; il dominio rifiuta una seconda registrazione e il database
  impone che i quattro dati siano presenti tutti insieme (vincolo `ck_incidente_conoscenza_completa`).
- L'intervallo rilevazione→conoscenza è esposto nelle API (`minutiRilevazioneConoscenza`) e la
  registrazione finisce nell'audit: una qualificazione tardiva resta visibile a chi controlla.
- Termini in ore (24 h, 72 h): somma di ore reali su `Instant`, indipendente dal cambio d'ora.
- Termini in mesi (finale, mensili): mese di calendario nel fuso del soggetto (`Europe/Rome`), allo
  **stesso orario** del fatto iniziale; se il giorno manca nel mese di arrivo, ultimo giorno del mese.
  È il criterio dell'art. 2963 c.c. per il computo a mesi, applicato però in modo più prudente: il
  codice civile farebbe scadere il termine all'ultimo istante del giorno finale, con proroga se festivo;
  qui il termine cade alla stessa ora, così l'applicazione avvisa prima e non dopo.
- Mensili alla stessa ricorrenza della notifica (notifica + k mesi), calcolate dall'istante originale
  e non in cascata, così il 31 gennaio non degrada in 28 febbraio → 28 marzo.

## Conseguenze
- Le scadenze non sono memorizzate: si ricalcolano dai fatti (conoscenza, trasmissioni, conclusione)
  con `CalcoloScadenze`, funzione pura testata con orologio fisso; non possono divergere dai fatti.
- Un errore di battitura su `conoscenzaIl` non si corregge nell'applicazione, nemmeno con la riclassificazione
  (che lascia invariata la conoscenza, ADR-0007): la discrepanza si spiega nel documento successivo. È il prezzo
  dell'immutabilità, accettato perché impedisce di spostare a posteriori i termini.
- **Stessa regola per la conclusione della gestione** (dopo la revisione): dalla conclusione dipende il
  termine della relazione finale quando l'incidente era in corso (lett. e). La revisione ha mostrato che,
  registrandola una seconda volta, una finale SCADUTA tornava APERTA. Ora `Incidente.registraConclusioneGestione`
  rifiuta la seconda registrazione (HTTP 409) e il campo della finale è compilato dal sistema dall'incidente.
- **Pre-notifica e notifica restano due invii distinti**, nell'ordine. L'art. 25, c. 5, lett. a) e b) prevede due
  adempimenti; la FAQ ACN ISB.G.4 precisa che la notifica completa si trasmette "a seguito della pre-notifica"; il
  considerando 102 della direttiva NIS2 dice che il preallarme "dovrebbe essere seguito da una notifica
  dell'incidente" e le Linee guida NIS v1.1 (par. 2.3.1) descrivono la pre-notifica entro 24 ore e poi la notifica
  entro 72. Nessuna fonte pubblica consultata (FAQ, Linee guida, Vademecum NIS 2026, Det. ACN 314287/2026; verifiche
  del 23/10 e del 07/11/2026) prevede che la notifica sostituisca la pre-notifica, anche quando entrambe sono pronte
  entro 24 h: l'applicazione impone quindi l'ordine pre-notifica → notifica.
