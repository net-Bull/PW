# ADR-0008: Documenti in JSON strutturato e PDF/A da un'unica rappresentazione

- **Stato**: accettata; aggiornata dopo la revisione di fine fase 3 (formato `pw29-documento-csirt/2`)

## Contesto
La traccia chiede la generazione dei documenti di pre-notifica, notifica e relazione finale in formato
stampabile (PDF) e strutturato (JSON o XML). Le due uscite devono dire la stessa cosa: un PDF che
riporta un campo assente dal JSON, o viceversa, ricreerebbe proprio le incoerenze fra invii che il
progetto vuole eliminare. Il PDF di un documento trasmesso è anche la copia da conservare.

Vincoli: build offline con la cache Maven del PC dello studente (ogni dipendenza nuova va scaricata a
mano), licenze compatibili con un repository pubblico, testo italiano con lettere accentate.

## Alternative valutate
1. **Apache FOP (XSL-FO)** – standard maturo, PDF/A supportato; ma richiede un secondo linguaggio di
   template (XSLT/XSL-FO) e una trasformazione del JSON in XML: più codice e meno leggibile.
2. **JasperReports** – potente per report tabellari; dipendenze pesanti, editor esterno per i template
   (`.jrxml`) e impaginazione pensata per elenchi, non per un documento a sezioni.
3. **OpenPDF con API di disegno** – nessun template, controllo totale; ma l'impaginazione (righe che
   vanno a capo, salti pagina, intestazioni) va scritta a mano e ogni modifica grafica è codice Java.
4. **Thymeleaf (già nello stack per l'interfaccia) + openhtmltopdf 1.1.97 su PDFBox 3** – template
   XHTML con CSS di stampa (pagina A4, numeri di pagina, piè di pagina ripetuto); lo stesso template
   può servire da anteprima HTML nell'interfaccia; licenza LGPL, PDF/A supportato.

Per il formato strutturato: **XML** con XSD avrebbe una validazione formale più matura, ma il contenuto
è già JSON canonico (ADR-0005) e il CSIRT Italia non pubblica uno schema di interscambio: si sceglie
**JSON**, con un identificativo di formato versionato (`schema: pw29-documento-csirt/1`).

## Decisione
Alternativa 4, con JSON come formato strutturato.
- `EsportazioneService` costruisce un unico `DocumentoEsportato`: destinatario e canale, adempimento e
  riferimento normativo, soggetto, incidente, versione (con eventuale rettifica), classificazione
  (voci con percorso macrocategoria > predicato > valore e versione della tassonomia), contenuto,
  trasmissione e posizione nella catena di hash.
- Il **contenuto** contiene solo i campi previsti per l'adempimento da `MatriceCampi.valoriPrevisti`,
  nell'ordine della matrice: matrice, validazione, JSON e PDF hanno una sola fonte (ADR-0005).
- Il **JSON** (`GET /api/documenti/{id}/json`) è la serializzazione diretta; il **PDF**
  (`GET /api/documenti/{id}/pdf`) è il template `templates/documenti/documento.html` applicato alla
  stessa rappresentazione, con i valori formattati (date in ora italiana, esiti in parole, categorie IS
  con descrizione, servizi con il nome del livello di servizio).
- **PDF/A-2b** (archiviazione a lungo termine): caratteri DejaVu Sans incorporati, profilo colore sRGB
  come OutputIntent, titolo e autore nei metadati. Risorse e licenze in `src/main/resources/stampa/`.
- Una fascia in testa dichiara lo stato: *bozza, non trasmettere*; *in approvazione*; *copia del
  documento trasmesso il … con riferimento …*. Il documento trasmesso riporta impronta e anello
  precedente; quello non ancora trasmesso riporta l'impronta dell'ultimo documento trasmesso, che
  diventa un'ancora esterna della catena quando il documento arriva al CSIRT Italia (ADR-0006).
- Il nome del file identifica incidente, adempimento, progressivo, versione e stato
  (es. `INC-2026-0002_NOTIFICA_1_v1.pdf`, `..._v1_BOZZA.pdf`). Ogni esportazione è registrata nell'audit.

## Conseguenze
- `EsportazioneTest` verifica per pre-notifica, notifica e relazione finale che il JSON contenga tutti
  gli obbligatori e nessun campo non previsto, e sul PDF (letto con PDFBox) testo, font incorporati,
  OutputIntent e identificazione PDF/A-2b nei metadati XMP; la relazione finale espone la
  riclassificazione. Gli esempi generati sono in `target/esempi/`.
- La conformità PDF/A è dichiarata dalla libreria e verificata dai test solo nei suoi elementi
  principali: **da validare con veraPDF** prima della consegna.
- Il template deve restare XHTML ben formato (openhtmltopdf lo legge come XML): niente entità HTML.
- Il JSON non ha ancora uno JSON Schema pubblicato: se servisse, si può generare dai record Java.

## Revisione: formato `pw29-documento-csirt/2`
- Per un documento trasmesso `soggetto` e `incidente` vengono dall'intestazione sigillata (ADR-0006): stato e
  date sono quelli del momento dell'invio, e il PDF lo dichiara ("Stato dell'incidente all'invio"). Una bozza
  mostra i dati correnti. I documenti sigillati con lo schema v1 non avevano l'intestazione e mostrano i dati
  correnti: limite noto, limitato ai dati creati prima della migrazione V6.
- Il contenuto riporta anche i criteri del Reg. 2024/2690 attivati (`criteriRegolamento`), così la motivazione
  della significatività è nel documento insieme alle categorie IS.
- CSS: `word-wrap: break-word` al posto di `word-break` (non supportato da openhtmltopdf); numeri con il
  separatore delle migliaia.
- Controllo strutturale dei PDF rigenerati (pikepdf): `pdfaid` 2/B, OutputIntent, font incorporati.
  **La validazione completa con veraPDF resta da eseguire** sul computer di lavoro.

## Revisione: impaginazione da modulo amministrativo
- **Motivo**: la prima impaginazione (titoli blu con filetto, fascia di stato colorata, tabelle etichetta/valore,
  note grigie) seguiva lo schema ricorrente dei documenti prodotti con strumenti automatici, non quello dei
  documenti indirizzati a un'autorità. Sono state confrontate tre alternative, stampate con lo stesso motore
  sui dati dello scenario M3: modulo amministrativo, lettera protocollata, bollettino tecnico a spaziatura
  fissa. Scelto il **modulo**: è il più compatto (la pre-notifica sta in un foglio) e ogni campo ha un numero
  di casella, utile per riferirsi a un dato in una rettifica o in una telefonata con il CSIRT.
- **Struttura**: testata a tre riquadri (soggetto notificante; modello con riferimento normativo e codice
  `Mod. PW29/PN`, `NT`, `RI`, `RM`, `RF`; spazio riservato al destinatario con il riferimento del portale);
  quadri A-F (soggetto, incidente, contenuto, classificazione, riclassificazioni, redazione e integrità) con
  caselle numerate (A1, C7, D4...); i campi obbligatori per l'adempimento hanno "OBBL." nella casella del
  numero; impronte SHA-256 in gruppi di 8 caratteri; avvertenze in fondo; piede con il codice del modulo e
  "Foglio n di m".
- **Stato**: caselle di spunta (trasmesso, in approvazione, bozza) al posto della fascia colorata, con il testo
  dello stato sotto.
- **Colore**: bianco e nero. Il rosso compare solo sui documenti non trasmessi: filigrana diagonale "BOZZA"
  (o "NON TRASMESSO" per un documento in approvazione) e timbro "NON TRASMETTERE". Lo stato non dipende dal
  colore: casella e scritta restano leggibili anche in una stampa o fotocopia in bianco e nero.
- **Caratteri**: Liberation Sans e Liberation Mono 2.1.5 (SIL OFL 1.1; metriche di Arial e Courier New, consuete
  nella modulistica), con DejaVu Sans come riserva per i glifi non coperti; tutti incorporati (PDF/A-2b).
- **Invariati**: contenuto, ordine dei campi, JSON e catena di hash. L'impronta è calcolata sul documento
  strutturato e non sul PDF, quindi i documenti già trasmessi mantengono la loro impronta; cambia solo la stampa.
- **Test**: il testo del PDF si confronta senza distinguere maiuscole e minuscole, perché etichette e titoli dei
  quadri sono in maiuscolo.
