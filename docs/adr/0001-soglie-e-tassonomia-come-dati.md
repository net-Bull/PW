# ADR-0001: Soglie di significatività e tassonomia ACN come dati versionati

- **Stato**: accettata

## Contesto
La traccia chiede che la tassonomia ACN sia aggiornabile senza modifiche al codice e che le
soglie di significatività siano regole implementabili e giustificate. Sia la tassonomia sia le
soglie del Reg. di esecuzione (UE) 2024/2690 possono cambiare nel tempo, e una classificazione già
trasmessa deve restare legata alla versione vigente quando è stata fatta.

## Alternative valutate
1. **Costanti nel codice (enum e `static final`)** – semplice e tipizzato, ma ogni aggiornamento
   normativo richiede una nuova release e non conserva lo storico delle versioni. Scartata.
2. **File di configurazione (YAML/properties)** – modificabile senza ricompilare, ma senza storico,
   senza integrità referenziale verso gli incidenti e senza una query di conteggio. Scartata.
3. **Tabelle versionate nel database, popolate da migrazioni Flyway** – ogni versione è un dato con
   data di validità; le classificazioni referenziano la versione; il conteggio è una query SQL.

## Decisione
Alternativa 3. Soglie (`soglia_set`) e tassonomia (`tassonomia_versione`, `tassonomia_voce`)
sono tabelle versionate caricate da migrazioni; il motore delle regole riceve le soglie come
parametro e non contiene numeri normativi.

## Conseguenze
- Un aggiornamento normativo si gestisce con una nuova migrazione, senza toccare il codice.
- I documenti già trasmessi restano coerenti con la versione usata al momento dell'invio.
- Va mantenuta la disciplina di non modificare migrazioni già applicate (Flyway lo verifica col checksum).
