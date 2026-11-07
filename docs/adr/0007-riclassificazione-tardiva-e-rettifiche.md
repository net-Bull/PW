# ADR-0007: Riclassificazione tardiva, declassamento e rettifiche

- **Stato**: accettata; aggiornata dopo la revisione di fine fase 3 (ritorno a non significativo prima del primo invio, conclusione,
  rettifica della finale)

## Contesto
La qualificazione di un incidente può cambiare dopo che un documento è già stato trasmesso al CSIRT
Italia: un "guasto" si rivela un attacco, un incidente notificato come significativo risulta sotto
soglia, la classificazione nella Tassonomia Cyber ACN va corretta. La traccia chiede che i documenti
trasmessi restino immutati, che la relazione finale esponga classificazione precedente e nuova,
momento e motivazione, e che lo scenario sia tracciato con stati e identificativi di versione.

Due domande senza risposta esplicita nelle fonti consultate (art. 25 D.Lgs. 138/2024 e Linee guida
NIS ACN v1.1): come si comunica un declassamento, e come si corregge un documento già inviato. Il
portale https://segnalazioni.acn.gov.it/ non è accessibile in modo automatico e non risulta una
procedura pubblica di "annullamento" di una segnalazione.

## Alternative valutate
1. **Modificare i documenti trasmessi e la classificazione dell'incidente** – esclusa: viola
   l'immutabilità e cancella ciò che il CSIRT Italia ha effettivamente ricevuto.
2. **Classificazione come attributo dell'incidente, sovrascritta a ogni cambio** – semplice, ma il
   "prima" sopravvive solo nell'audit testuale; i documenti successivi non sanno di essere cambiati.
3. **Declassamento come annullamento degli obblighi** – l'incidente declassato esce dal flusso e le
   scadenze aperte decadono. Rischioso: se il declassamento è sbagliato, i termini sono già persi;
   e il CSIRT Italia, che ha ricevuto la pre-notifica, non riceve alcuna chiusura.
4. **Riclassificazione come evento tracciato, comunicata con l'invio successivo** – la classificazione
   resta legata alle versioni dei documenti (ADR-0005); un cambio dopo un invio è una riga dello
   storico con prima/dopo, momento, motivazione e i due documenti che lo delimitano; gli obblighi
   già sorti restano e il documento successivo comunica il cambio.

## Decisione
Alternativa 4.
- **Endpoint** `POST /api/incidenti/{id}/riclassificazione` con nuove voci (obbligatorie, complete
  come in una notifica), motivazione (obbligatoria) e, se cambia la significatività o le categorie
  IS, una nuova valutazione. La valutazione è salvata nello storico delle valutazioni con le soglie
  usate; il momento della conoscenza non cambia (ADR-0003).
- **Condizioni**: incidente in gestione; almeno un documento già trasmesso (prima del primo invio
  la classificazione si corregge nella bozza); nessun documento in approvazione (l'approvatore non
  deve trasmettere una classificazione superata); deve cambiare almeno uno fra stato, categorie, voci.
- **Storico** (`storico_riclassificazione`): stato, significatività, categorie IS e voci prima e
  dopo; `documento_prima` = ultimo documento trasmesso (ciò che il CSIRT conosce); `documento_dopo` =
  primo documento trasmesso dopo il cambio, collegato automaticamente alla trasmissione.
- **Documenti successivi**: le bozze aperte sono riallineate subito; le nuove bozze nascono con la
  classificazione corrente; il campo `riclassificazioni` del contenuto è compilato dal sistema (il
  valore inviato dal client è ignorato) e riporta tutto lo storico. Essendo nel contenuto, entra
  nell'impronta SHA-256 del documento (ADR-0006).
- **Coerenza dopo la notifica**: un documento non ancora trasmesso deve riportare le voci correnti;
  altrimenti la verifica segnala la violazione "Coerenza con la classificazione comunicata" (HTTP 422).
  Un cambio di classificazione dopo la notifica passa quindi sempre dalla riclassificazione motivata.
  Prima della notifica la classificazione si affina liberamente: la pre-notifica è preliminare per legge.
- **Declassamento**: stato `DECLASSATO`. Gli obblighi già sorti restano attivi con le loro scadenze
  (scelta prudente, coerente con ADR-0004): l'invio successivo comunica il declassamento e la
  relazione finale chiude l'incidente (`DECLASSATO -> CHIUSO`). Una nuova valutazione positiva
  riporta l'incidente a `SIGNIFICATIVO`.
- **Rettifica** di un documento trasmesso (`POST /api/adempimenti/{id}/rettifica`, motivo obbligatorio):
  nuova versione dello stesso adempimento, derivata dall'ultima versione trasmessa, con lo stesso iter
  di approvazione e un nuovo anello nella catena. L'adempimento conserva l'istante del primo invio
  (il termine è stato rispettato allora). Si rettifica solo l'ultimo documento trasmesso dell'incidente:
  un documento già superato da un invio successivo si aggiorna con il prossimo invio, non a ritroso.

## Revisione
- **Prima del primo invio**: un incidente qualificato significativo per errore (es. falso positivo della sonda)
  non aveva via d'uscita, perché la riclassificazione richiedeva un documento trasmesso. Ora la riclassificazione
  prima di ogni invio è ammessa solo per escludere la significatività: nuova valutazione obbligatoria con esito
  negativo, motivazione, stato `NON_SIGNIFICATIVO` (transizione SIGNIFICATIVO -> NON_SIGNIFICATIVO), nessun
  termine aperto e nessun invio possibile. Se nuovi elementi lo rendono di nuovo significativo, i termini
  decorrono dalla conoscenza registrata la prima volta (lettura prudente: il soggetto ne aveva già evidenza).
- **Categorie IS dei documenti dall'ultima valutazione**: dopo un declassamento la notifica non riporta più le
  categorie precedenti (prima restavano quelle della pre-notifica).
- **Conclusione della gestione** registrata una sola volta e riportata dal sistema nella finale (ADR-0003).
- **Invii dopo la chiusura**: la finale non si trasmette se un altro documento dell'incidente è in approvazione;
  dopo la chiusura nessuna bozza si invia, salvo la **rettifica della relazione finale**, ora ammessa anche a
  incidente chiuso (l'incidente resta CHIUSO e la rettifica diventa un nuovo anello della catena).
- Il campo `riclassificazioni` riporta anche un ritorno a non significativo prima del primo invio, senza
  documento precedente: è l'informazione da dare se l'incidente torna significativo.

## Conseguenze
- Scenario M3 eseguibile (`RiclassificazioneTest`): traccia generata in
  `docs/scenari/TRACCIA-RICLASSIFICAZIONE.md` con stati, identificativi di versione e impronte prima e
  dopo la riclassificazione.
- Mantenere attivi gli obblighi di un incidente declassato può produrre invii "in eccesso". È il prezzo
  della prudenza: un invio non dovuto costa meno di un termine mancato.
- **Rettifica e declassamento sul portale**: nessuna fonte pubblica consultata (Linee guida NIS v1.1, Vademecum NIS
  2026, Det. ACN 314287/2026, FAQ ACN; verifica del 23/10/2026) documenta una funzione di rettifica o di chiusura
  anticipata della segnalazione nella maschera dei Servizi NIS, visibile solo dopo l'accesso. Per questo la
  correzione è una nuova versione dell'adempimento trasmesso (rettifica) e il declassamento si comunica nel documento
  successivo (campo `riclassificazioni`). Una funzione dedicata del portale cambierebbe il canale, non il modello dei dati.

## Revisione: canale di trasmissione e domande sul portale
- La pagina del Portale Segnalazioni (https://segnalazioni.acn.gov.it/) indica che le notifiche NIS2 si
  effettuano accedendo al **Portale dei Servizi ACN** (https://portale.acn.gov.it/) e compilando la maschera
  dedicata; le Linee guida NIS v1.1 indicavano ancora il Portale Segnalazioni. Il canale nei documenti esportati
  è corretto di conseguenza; non entra nell'impronta, quindi la catena non cambia.
- La maschera è visibile solo dopo l'accesso con SPID o CIE: nessuna fonte pubblica dice se ammette rettifica,
  chiusura anticipata o allegati. Pre-notifica e notifica restano due invii distinti (art. 25, c. 5, lett. a e b;
  Direttiva (UE) 2022/2555, considerando 102; FAQ ACN ISB.G.4; Linee guida NIS v1.1, par. 2.3.1). La Det. ACN
  314287/2026 (art. 3, c. 1), che ha sostituito le precedenti determinazioni sulla piattaforma, stabilisce che i soggetti comunicano con l'ACN esclusivamente tramite i Servizi NIS:
  un PDF non può sostituire la maschera.
- Le domande restano aperte e vanno poste al Service Desk del Portale ACN o a un referente CSIRT; la risposta
  cambierebbe il canale, non il modello dei dati.
