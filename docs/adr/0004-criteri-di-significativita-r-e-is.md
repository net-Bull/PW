# ADR-0004: Criteri di significatività – regole R1-R7 e categorie IS-1..IS-4

- **Stato**: accettata; aggiornata dopo la revisione di fine fase 3 sui testi ufficiali (EUR-Lex, FAQ ACN): vedi "Revisione"

## Contesto
Per l'MSSP del contesto valgono due fonti che descrivono quando un incidente è significativo:
- il **Reg. di esecuzione (UE) 2024/2690**, che per i fornitori di servizi gestiti e di sicurezza
  gestiti fissa criteri quantitativi (artt. 3, 4 e 10), tradotti nelle regole R1-R7 (ADR-0001);
- la **Determinazione ACN n. 379907/2025**, i cui allegati 3 (soggetti importanti) e 4 (essenziali)
  definiscono gli incidenti significativi di base IS-1..IS-4. Testo ripreso dalle Linee guida NIS ACN
  v1.1 (aprile 2026), par. 2.3.1: IS-1 perdita di riservatezza verso l'esterno; IS-2 perdita di
  integrità con impatto verso l'esterno; IS-3 violazione dei livelli di servizio attesi (SL) definiti
  ai sensi della misura DE.CM-01; IS-4 accesso non autorizzato o con abuso dei privilegi, solo per i
  soggetti essenziali.

Il rapporto fra le due fonti per un MSSP italiano non è chiarito nei documenti consultati.

## Alternative valutate
1. **Solo il Reg. 2024/2690** – fonte di rango superiore e specifica per gli MSSP, ma ignora le
   categorie che l'ACN chiede di indicare e che il portale di notifica si aspetta.
2. **Solo le categorie IS dell'ACN** – aderente alla prassi nazionale, ma perde le soglie economiche
   (R5, R7) e di utenti (R2, R4) del regolamento.
3. **Unione dei due insiemi** – significativo se scatta almeno una regola R o una categoria IS;
   entrambi gli esiti sono registrati e motivati.

## Decisione
Alternativa 3, perché l'errore asimmetrico pesa a favore della prudenza: una notifica non dovuta
costa un invio, una notifica omessa espone alle sanzioni del decreto. Dettagli:
- IS-3 si valuta sugli SL del servizio coinvolto (`livello_servizio`), definiti in autonomia dal
  soggetto e non coincidenti per forza con gli SLA contrattuali (Linee guida, nota 21). Per l'MSSP:
  SOC/MDR e WAF 30 min di indisponibilità / 60 di degrado; portale e ticketing 120 / 240.
- IS-4 scatta solo se il soggetto è essenziale (`soggetto.tipo`). CyberWall è
  configurato come **importante** (media impresa in un settore dell'Allegato I): IS-4 è implementata
  e testata, ma nel caso di studio non si attiva.
- Confronti strettamente maggiori anche per gli SL, come per le soglie del regolamento.
- Ogni valutazione è conservata (`valutazione_significativita`) con input, versione delle soglie,
  regole e categorie attivate: la motivazione resta ricostruibile.

## Revisione
Verifica sul testo italiano del Reg. 2024/2690 (EUR-Lex) e sulle FAQ ufficiali ACN:
- **Rapporto fra le due fonti**: le FAQ ACN (ISB.G.1) chiedono a tutti i soggetti NIS di notificare le
  fattispecie IS, senza eccezioni per chi rientra nel regolamento; il considerando 30 del regolamento dichiara
  esaustivi i suoi criteri. La dottrina consultata (ICT Security Magazine) legge le categorie IS come soglia
  nazionale anticipata, aggiuntiva ai criteri unionali; la tesi contraria vede nel regolamento una disciplina
  speciale che esonera questi soggetti. L'unione resta la scelta prudente e documentata.
- **Art. 10 solo per i servizi gestiti** (`livello_servizio.servizio_gestito`): il portale clienti/ticketing
  non è il servizio di sicurezza gestito, quindi R1-R4 non si applicano a esso. Prima, 31 minuti di
  indisponibilità del portale (SL 120) bastavano per R1.
- **Utenti per servizio** (`livello_servizio.utenti_ue`, art. 3, par. 3): l'art. 10 calcola il 5% sugli
  "utenti di tale servizio". SOC/MDR 9.000 utenti (soglia 450), WAF gestito 5.400 (soglia 270).
- **R8**: esfiltrazione di segreti commerciali (art. 3, par. 1, lett. b); **interruzioni programmate**
  (art. 3, par. 2) escluse da R1, R2 e IS-3. Decesso e salute (lett. c, d) restano esclusi con motivazione in
  `docs/criteri/DISCREPANZE-CRITERI.md`.
- **Soglie alla data della conoscenza**: una rivalutazione successiva usa le soglie vigenti quando
  l'incidente è stato conosciuto, non quelle del giorno in cui si rivaluta.
- **Categorie e criteri nei documenti**: le categorie IS e i criteri R attivati li compila il sistema
  dall'ultima valutazione (campi `categorieIs` e `criteriRegolamento`): l'operatore non può indicare IS-4
  per un soggetto importante né cambiare le categorie senza una nuova valutazione. Le categorie IS sono
  facoltative nella matrice perché un incidente può essere significativo solo per il regolamento (es. R5) o
  essere declassato.
- Le categorie IS restano un enum nel codice, a differenza di soglie e tassonomia: sono logica di decisione
  (ogni categoria ha la sua condizione), non un elenco di valori; una nuova categoria ACN richiede comunque
  una nuova condizione e quindi codice e test.

## Conseguenze
- La bozza della pre-notifica riceve le categorie IS e i criteri R attivati, compilati dal sistema.
- Se la verifica aperta stabilisse che per gli MSSP vale solo il regolamento, basta escludere le
  categorie dal calcolo di `significativo` in `EsitoSignificativita.conCategorie`: una riga.
- **Testo delle categorie**: IS-1..IS-4 sono riportate come negli allegati 3 e 4 della Det. ACN 379907/2025
  (applicabile dal 15/01/2026, FAQ ACN DSB.1), nel testo delle Linee guida NIS v1.1, par. 2.3.1.
- **Rapporto con il Reg. 2024/2690**: nessuna fonte ufficiale lo chiarisce per i soggetti che rientrano nel
  regolamento; l'unione resta la lettura prudente, motivata nella sezione Revisione.
- **Tipo di soggetto**: CyberWall è un soggetto **importante**: media impresa ai sensi della Racc. 2003/361/CE
  (85 dipendenti, fatturato 7,0 M EUR, totale di bilancio 5,2 M EUR), fornitore di servizi di sicurezza gestiti
  (Allegato I, settore 9), art. 3, c. 2, e art. 6 del D.Lgs. 138/2024 (migrazione V6, `soggetto.motivazione_tipo`).
