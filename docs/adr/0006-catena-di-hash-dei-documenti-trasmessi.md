# ADR-0006: Catena di hash SHA-256 dei documenti trasmessi

- **Stato**: accettata; aggiornata dopo la revisione di fine fase 3 (schema `pw29-catena-v2`, trigger su PostgreSQL)

## Contesto
La traccia chiede che i documenti già trasmessi al CSIRT Italia restino immutati e che la
riclassificazione sia tracciabile. L'immutabilità è già imposta dall'applicazione (metodi di dominio e
callback JPA `@PreUpdate`/`@PreRemove`, ADR-0003 e Fase 2), ma non protegge da chi scrive direttamente
sul database (SQL, strumenti di amministrazione, ripristini parziali). Serve un modo per **rilevare**
una manomissione, non solo per impedirla dall'applicazione. È l'unica funzionalità aggiuntiva scelta
per l'originalità del progetto.

## Alternative valutate
1. **Solo immutabilità applicativa** (stato attuale) – semplice, ma una modifica fatta fuori
   dall'applicazione non lascia traccia: il documento alterato è indistinguibile dall'originale.
2. **Impronta SHA-256 indipendente per ogni documento** – rileva la modifica del contenuto, ma chi
   altera un documento può ricalcolarne e riscriverne l'impronta; non rileva nemmeno la cancellazione
   di un documento intero.
3. **Firma digitale qualificata o marca temporale (RFC 3161) di ogni documento** – valore probatorio
   pieno, ma richiede un certificato, un servizio esterno di marcatura e la gestione delle chiavi:
   fuori dal perimetro di un progetto che deve girare senza sistemi reali.
4. **Catena di hash per incidente** – ogni documento trasmesso include nel calcolo l'impronta del
   documento trasmesso prima; il primo si aggancia a un'impronta di genesi derivata dal codice
   dell'incidente.

## Decisione
Alternativa 4.
- **Input dell'impronta** (`CatenaImpronte.inputCanonico`): JSON canonico con chiavi in ordine
  alfabetico che contiene versione dell'algoritmo (`pw29-catena-v1`), codice incidente, adempimento,
  progressivo, identificativo e numero di versione, eventuale motivo di rettifica, versione della
  tassonomia, **voci di classificazione ordinate**, **contenuto JSON canonico** (ADR-0005), utente che
  ha inviato in approvazione, approvatore, istante di trasmissione dichiarato, istante di registrazione,
  riferimento del portale ACN e **impronta dell'anello precedente**. Impronta = SHA-256 in esadecimale.
- **Quando**: alla registrazione della trasmissione, dentro `VersioneDocumento.trasmetti`: il sigillo
  non si può dimenticare. Gli istanti sono troncati al millisecondo, la precisione conservata dal
  database, così l'impronta ricalcolata dopo una rilettura coincide.
- **Ordine della catena**: non per data (l'istante di trasmissione è dichiarato e due registrazioni
  possono cadere nello stesso istante) ma seguendo i collegamenti dalla genesi. Lo stesso percorso
  trova l'anello per il prossimo documento e verifica la catena.
- **Nessuna biforcazione**: indici univoci su `impronta_sha256` e `impronta_precedente`; due
  trasmissioni concorrenti dello stesso incidente non possono agganciarsi allo stesso anello (la
  seconda riceve HTTP 409). Un vincolo `CHECK` impone che ogni documento trasmesso sia sigillato.
- **Fail-safe**: se la catena non è integra, nuove trasmissioni dell'incidente sono rifiutate (409):
  sigillare un documento su una catena manomessa ne coprirebbe la manomissione.
- **Verifica**: `GET /api/incidenti/{id}/catena` ricalcola ogni impronta e riporta per ogni anello
  contenuto integro sì/no, collegamento sì/no, e i documenti trasmessi fuori catena.

## Conseguenze
- Rilevati dai test (`CatenaHashTest`): modifica del contenuto via SQL; modifica del contenuto con
  impronta ricalcolata e riscritta (il collegamento con l'anello successivo si rompe e il documento
  successivo risulta fuori catena); rimozione di una voce di classificazione via SQL.
- **Limite**: chi ha accesso in scrittura al database e conosce l'algoritmo può riscrivere l'intera
  catena fino all'ultimo anello. Mitigazioni: i trigger di PostgreSQL (revisione sotto) impediscono la
  scrittura all'utente applicativo; i documenti esportati riportano l'impronta dell'ultimo documento
  trasmesso, che diventa un'ancora esterna **solo se** quel PDF o JSON viene allegato all'invio sul portale
  ACN (la maschera di notifica dei Servizi NIS è un modulo web, visibile solo dopo l'accesso, e nessuna fonte
  pubblica consultata ne documenta gli allegati); per l'ultimo anello resta aperta l'ipotesi di una marca
  temporale RFC 3161 o di un registro esterno (fuori ambito).
- La correzione di un documento trasmesso non tocca la catena esistente: è una nuova versione
  (rettifica, ADR-0007) che diventa un nuovo anello.
- Cambiare il formato dell'input richiede una nuova versione dello schema e una verifica che sappia
  riconoscere entrambe.

## Revisione: schema `pw29-catena-v2`
- **Intestazione sigillata**: alla trasmissione si salvano soggetto e incidente come sono in quel momento
  (`versione_documento.intestazione_json`, record `Intestazione`) e l'intestazione entra nell'impronta. La revisione
  aveva mostrato che la copia di una pre-notifica trasmessa riportava, dopo la chiusura, lo stato "CHIUSO".
- **Autori** della versione nell'input (ADR-0009); **niente chiavi surrogate**: `versioneId` è sostituito
  dall'identificazione logica già presente (incidente, adempimento, progressivo, numero), così la catena
  sopravvive a un export/import che rinumera gli id.
- **Schema per riga** (`versione_documento.schema_impronta`): le versioni sigillate prima della migrazione V6
  restano `pw29-catena-v1` e si verificano con il loro algoritmo; la genesi non dipende dallo schema, quindi
  catene miste v1/v2 restano collegate. Verificato su PostgreSQL 16: dati creati con la versione precedente,
  migrazione V6, nuova trasmissione v2, catena integra.
- **Immutabilità anche nel database** (solo PostgreSQL, `db/vendor/postgresql/V6_1`): trigger che rifiutano
  UPDATE e DELETE di una versione trasmessa, qualunque modifica della sua classificazione e dei suoi autori,
  UPDATE e DELETE del registro di audit. Prevenzione nel database e rilevazione con la catena si completano:
  il proprietario delle tabelle può disattivare i trigger, ma non senza rompere la catena.

## Revisione: canale
- Le notifiche NIS si inviano dalla maschera dei Servizi NIS sul Portale dei Servizi ACN (ADR-0007, revisione
  del 23/10/2026). Nessuna fonte pubblica dice se la maschera accetta allegati: l'ancora esterna resta
  condizionata all'allegato del PDF o del JSON, e in alternativa alla trascrizione dell'impronta in un campo
  di testo libero della notifica, se presente.
