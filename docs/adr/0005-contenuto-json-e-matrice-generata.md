# ADR-0005: Contenuto dei documenti come JSON tipizzato e matrice campo/adempimento generata dal codice

- **Stato**: accettata; aggiornata dopo la revisione di fine fase 3 (campi compilati dal sistema)

## Contesto
I cinque adempimenti (pre-notifica, notifica, relazione intermedia, mensile, finale) chiedono
informazioni diverse ma in larga parte sovrapposte (art. 25, c. 5). Servono: riuso dei dati fra un
invio e il successivo, validazione lato server differenziata per adempimento, una matrice
campo/adempimento da mostrare nell'elaborato, e (Fase 3) un'impronta SHA-256 stabile dei documenti.

## Alternative valutate
1. **Una tabella per adempimento** – schema rigido e interrogabile, ma cinque tabelle quasi uguali,
   riuso dei dati con copie campo per campo e matrice da scrivere a mano.
2. **Tabella chiave/valore (EAV)** – flessibile, ma perde i tipi, complica la validazione e rende
   la matrice un dato di configurazione slegato dal codice.
3. **Un modello unico tipizzato (`ContenutoDocumento`) serializzato in JSON canonico, con metadati
   per campo in un'annotazione (`@Campo`)** – un solo modello per tutti gli invii; l'annotazione dice
   per ogni campo in quali adempimenti è obbligatorio o facoltativo e qual è la fonte normativa.

## Decisione
Alternativa 3.
- `MatriceCampi` costruisce per riflessione un registro dalle annotazioni `@Campo` e dall'enum
  `VincoloClassificazione` (vincoli sulle voci della tassonomia: almeno una voce; un solo valore di
  Severity per notifica e finale; minaccia o causa per la finale).
- Dallo stesso registro derivano la verifica di completezza (HTTP 422 con campo, etichetta e fonte di
  ciascuna mancanza), l'endpoint `GET /api/matrice-campi` e il file `docs/MATRICE-CAMPI.md`.
- `MatriceCampiDocumentazioneTest` confronta il file con il codice: se divergono la build fallisce.
- Le informazioni richieste "ove possibile"/"ove noto" sono a tre valori (`SI`, `NO`, `NON_NOTO`):
  il campo va compilato, ma "non noto" è una risposta legittima e diversa dal campo vuoto.
- Il JSON è canonico (proprietà ordinate, nulli omessi, date ISO-8601): stesso contenuto, stessi byte.
- La classificazione non sta nel JSON ma nella tabella `classificazione`, con integrità referenziale
  verso le voci della tassonomia e verso la sua versione.

## Revisione: campi compilati dal sistema
Alcuni campi non li scrive l'operatore ma il sistema, dai fatti registrati (`CampiDiSistema`): categorie IS e
criteri del Reg. 2024/2690 (ultima valutazione), riclassificazioni (storico), conclusione della gestione
(incidente), riferimento della richiesta del CSIRT (adempimento). Il valore inviato dal client è sovrascritto
alla creazione della bozza, a ogni salvataggio e all'invio in approvazione; quando i fatti cambiano
(riclassificazione, conclusione) le bozze aperte sono riallineate, e con un documento in approvazione il
cambiamento è rifiutato. La verifica segnala una versione non allineata ("Campi compilati dal sistema").
La matrice ne riporta la fonte e li tratta come gli altri campi (obbligatori o facoltativi per adempimento).

## Conseguenze
- Aggiungere un campo significa aggiungere un attributo annotato: validazione, matrice e documento
  si aggiornano insieme.
- Il contenuto non è interrogabile in SQL campo per campo; per le ricerche servono i dati dell'incidente
  (relazionali) o, se servisse, una colonna JSON nativa di PostgreSQL.
- La riflessione è confinata in `MatriceCampi` ed è coperta da test per ciascun adempimento.
