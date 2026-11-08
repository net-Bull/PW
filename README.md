# Notifiche CSIRT – Project Work 29

Web application full stack in Java per la redazione e la gestione delle notifiche di incidente
significativo (NIS2, D.Lgs. 138/2024) verso il CSIRT Italia, secondo la tassonomia ACN.

Contesto organizzativo: **CyberWall S.r.l.**, MSSP interamente fittizio. Nessun dato,
sistema o cliente reale è coinvolto.

## Funzionalità principali

- Registrazione dell'incidente e valutazione di significatività con le regole R1-R8 (Reg. di esecuzione (UE)
  2024/2690) e le categorie IS-1..IS-4 dell'ACN; soglie e livelli di servizio salvati come dati versionati.
- Momento della conoscenza registrato una sola volta e distinto dalla rilevazione; scadenze di pre-notifica (24 h),
  notifica (72 h), relazione finale e relazioni mensili calcolate dai fatti.
- Tassonomia Cyber ACN v2.0 caricata da migrazione (4 / 21 / 19 / 219) con query di conteggio.
- Compilazione guidata dei cinque adempimenti, con bozze, riuso dei dati e controllo dei campi obbligatori per
  adempimento; la matrice dei campi è generata dal codice (`docs/MATRICE-CAMPI.md`).
- Ruoli operatore e approvatore con il principio dei quattro occhi e registro di audit.
- Documenti in JSON e in PDF/A-2b generati dagli stessi dati.
- Documenti trasmessi immutabili, con catena di hash SHA-256 per incidente che rende visibile ogni manomissione.
- Riclassificazione tardiva, declassamento e rettifiche, con storico prima/dopo riportato nella relazione finale.
- Interfaccia web Thymeleaf e API REST che usano gli stessi servizi.

## Requisiti

- JDK 21
- Maven 3.9+
- Docker (solo per il profilo PostgreSQL)

## Avvio

```bash
# Build e test (H2 in memoria)
mvn clean verify

# Avvio con H2 in memoria e utenti demo: interfaccia su http://localhost:8080
mvn spring-boot:run

# Applicazione e PostgreSQL in contenitori, con utenti demo
mvn clean package
docker compose up --build

# Solo PostgreSQL in contenitore, applicazione da Maven
docker compose up -d db
mvn spring-boot:run -Dspring-boot.run.profiles=postgres,demo   # con utenti demo
mvn spring-boot:run -Dspring-boot.run.profiles=postgres        # senza utenti: li crea l'amministratore
```

Su PostgreSQL la migrazione `db/vendor/postgresql/V6_1` aggiunge i trigger che impediscono di modificare o
cancellare i documenti trasmessi e il registro di audit anche in SQL (ADR-0006). Dettagli del deploy in
`docs/ARCHITETTURA.md`.

## Verifiche

Oltre a `mvn clean verify`, la conformità dei PDF allo standard PDF/A si controlla con veraPDF, in un profilo
Maven separato:

```bash
mvn -Pverapdf -Dtest=VerificaPdfaTest test
```

Gli esiti delle verifiche eseguite sono descritti in `docs/VERIFICHE.md`.

## Interfaccia web

Accesso con login a form su `http://localhost:8080` (credenziali demo nella pagina di accesso, solo nei profili
che caricano gli utenti demo). Percorso tipico:

1. **Registra incidente** (rilevazione) e **valuta la significatività**: servizio coinvolto, minuti di
   indisponibilità e degrado, utenti, compromissioni, perdite. Se è significativo si registra, una sola volta,
   il momento della conoscenza con l'evidenza che lo fonda.
2. Nel dettaglio dell'incidente la **sequenza dei termini** (conoscenza, 24 h, 72 h, finale) mostra il prossimo
   termine con il conto alla rovescia; sotto, adempimenti, documenti e azioni.
3. **Crea bozza**: l'editor mostra solo i campi previsti per l'adempimento, con gli obbligatori segnati e i campi
   compilati dal sistema in sola lettura; il pannello a destra dice cosa manca mentre si scrive. Le voci della
   tassonomia si cercano per codice o nome.
4. **Salva e invia in approvazione** (operatore); l'**approvatore** registra la trasmissione con il riferimento
   del portale ACN, oppure respinge con una nota. Chi ha scritto o inviato la versione non può trasmetterla.
5. Dopo la notifica: **riclassificazione** (prima/dopo, motivazione), **conclusione della gestione**, richieste del
   CSIRT, relazioni mensili, **relazione finale** che chiude l'incidente. Da ogni documento: anteprima di stampa,
   PDF/A e JSON.

## Utenti dimostrativi

Caricati dalla location Flyway `db/demo` solo nel profilo predefinito (H2) e nel profilo `demo`. Le API usano
HTTP Basic senza sessione; l'interfaccia web usa il login a form con CSRF (ADR-0009).

| Utente | Password | Ruolo |
| --- | --- | --- |
| `operatore` | `Operatore-Demo-2026` | OPERATORE |
| `operatore2` | `Operatore2-Demo-2026` | OPERATORE |
| `approvatore` | `Approvatore-Demo-2026` | APPROVATORE (include i permessi di operatore) |
| `approvatore2` | `Approvatore2-Demo-2026` | APPROVATORE |

Quattro occhi: non può registrare la trasmissione di una versione chi l'ha inviata in approvazione né chi ne
ha scritto contenuto o classificazione (gli autori, elencati nel dettaglio del documento).

## API principali

| Metodo e percorso | Ruolo | Funzione |
| --- | --- | --- |
| `POST /api/significativita/valutazione` | autenticato | Simulazione R1-R8 + IS-1..IS-4, senza registrare nulla |
| `POST /api/incidenti` | operatore | Registra un incidente (`rilevatoIl` = primo evento) |
| `POST /api/incidenti/{id}/qualificazione` | operatore | Valuta con le soglie vigenti alla data della conoscenza; se significativo registra la conoscenza (una volta) e apre pre-notifica e notifica |
| `GET /api/incidenti/{id}` e `/scadenze` | autenticato | Dettaglio con adempimenti e scadenze calcolate |
| `POST /api/incidenti/{id}/richieste-csirt` | operatore | Richiesta di relazione intermedia del CSIRT Italia |
| `POST /api/incidenti/{id}/relazioni-mensili` | operatore | Apre la relazione mensile dovuta (incidente in corso) |
| `POST /api/incidenti/{id}/conclusione` | operatore | Conclusione della gestione: una sola volta (409 alla seconda), rifiutata con un documento in approvazione |
| `POST /api/adempimenti/{id}/bozza` | operatore | Nuova bozza con i dati dell'ultimo documento trasmesso |
| `PUT /api/documenti/{id}` | operatore | Contenuto e voci della tassonomia (solo in bozza); categorie IS, criteri R, riclassificazioni, conclusione e riferimento della richiesta li compila il sistema |
| `GET /api/documenti/{id}/verifica` | autenticato | Campi mancanti per l'adempimento, con la fonte |
| `POST /api/documenti/{id}/invio-approvazione` | operatore | Rifiutato con 422 se incompleto |
| `POST /api/documenti/{id}/respingimento` | approvatore | Torna in bozza con una nota |
| `POST /api/documenti/{id}/trasmissione` | approvatore | Registra l'invio sul portale ACN, sigilla la versione con l'intestazione del momento nella catena di hash; da qui è immutabile. La finale chiude l'incidente e non parte con altri documenti in approvazione |
| `GET /api/documenti/{id}/json` | autenticato | Documento strutturato con i soli campi previsti per l'adempimento |
| `GET /api/documenti/{id}/pdf` | autenticato | Documento stampabile PDF/A-2b, stessa rappresentazione del JSON |
| `POST /api/adempimenti/{id}/rettifica` | operatore | Nuova versione di un adempimento già trasmesso (solo l'ultimo invio; la finale anche a incidente chiuso), motivo obbligatorio |
| `POST /api/incidenti/{id}/riclassificazione` | operatore | Dopo un invio: nuove voci e/o nuova valutazione, con motivazione; `DECLASSATO` se non più significativo. Prima di ogni invio: solo nuova valutazione non significativa, `NON_SIGNIFICATIVO` |
| `GET /api/incidenti/{id}/riclassificazioni` | autenticato | Storico prima/dopo con i documenti che delimitano ogni cambio |
| `GET /api/incidenti/{id}/catena` | autenticato | Verifica della catena di hash: impronte ricalcolate, collegamenti, documenti fuori catena |
| `GET /api/matrice-campi` (`/markdown`) | autenticato | Matrice campo/adempimento generata dal codice |
| `GET /api/incidenti/{id}/audit` | autenticato | Registro di audit dell'incidente e dei suoi documenti |
| `GET /api/tassonomia/conteggio` | autenticato | Conteggio delle voci della tassonomia vigente |

## Prova rapida

```bash
# Simulazione: 31 minuti di indisponibilità del SOC/MDR -> R1 e IS-3
curl -s -u operatore:Operatore-Demo-2026 -X POST localhost:8080/api/significativita/valutazione \
  -H 'Content-Type: application/json' \
  -d '{"servizioCoinvolto":"SOC_MDR","minutiIndisponibilitaTotale":31,"minutiDegrado":0,"utentiImpattati":0,"perditaRiservatezza":false,"perditaIntegrita":false,"perditaAutenticita":false,"impattoEsterno":false,"sospettoDoloso":false,"accessoNonAutorizzato":false,"gravePerturbazione":false,"perditaDiretta":0,"incidentiStessaCausaInFinestra":0,"perditeCumulateInFinestra":0}'

# Conteggio delle voci della tassonomia vigente per livello
curl -s -u operatore:Operatore-Demo-2026 localhost:8080/api/tassonomia/conteggio
```

Lo scenario completo (pre-notifica, notifica, finale) è eseguito dalle API da `FlussoNotificaTest` e
dall'interfaccia web da `ScenarioInterfacciaTest` (milestone M4), che produce `docs/scenari/TRACCIA-INTERFACCIA.md`.
Lo scenario di riclassificazione tardiva (milestone M3) da `RiclassificazioneTest`, che produce la traccia
di esecuzione e i documenti JSON/PDF in `docs/scenari/`:

```bash
mvn test -Dtest=RiclassificazioneTest -Dtraccia.aggiorna=true
mvn test -Dtest=ScenarioInterfacciaTest -Dtraccia.aggiorna=true
```

ER e data dictionary si rigenerano dal DDL (dopo una nuova migrazione, con i `COMMENT ON` delle colonne nuove):

```bash
mvn test -Dtest=SchemaDocumentazioneTest -Dschema.aggiorna=true
python tools/rendi_diagrammi.py docs/ER.md docs/figure/er.svg   # figura per la relazione (vedi lo script)
```

## Aggiornare la tassonomia ACN

La tassonomia si carica da dati, senza modificare il codice. Per una nuova versione ACN:

1. salvare in `docs/tassonomia/` il nuovo JSON MISP, i codici estratti dal PDF e la struttura
   (macrocategorie e sotto-predicati), e aggiornare `ACQUISIZIONE.md`;
2. generare una nuova migrazione con un numero successivo:

```bash
python3 tools/genera_migrazione_tassonomia.py \
  --json docs/tassonomia/acn-machinetag-vX.json \
  --codici docs/tassonomia/codici-pdf-vX.txt \
  --struttura docs/tassonomia/struttura-pdf-vX.json \
  --versione X --data-acquisizione AAAA-MM-GG \
  --pdf-url URL --pdf-sha256 HASH --json-url URL --id-versione 2 \
  --out src/main/resources/db/migration/V<n>__tassonomia_acn_vX.sql
```

3. nella stessa migrazione, o in una successiva, impostare `vigente = FALSE` sulla versione precedente.
   Le classificazioni già registrate restano legate alla versione con cui sono state fatte.

## Regole di significatività

Un incidente è significativo se scatta almeno una regola del Reg. di esecuzione (UE) 2024/2690 o una categoria
IS dell'ACN (ADR-0004). Soglie dai parametri del contesto, per servizio (art. 3, par. 3, e art. 10):

| Servizio | Servizio gestito (art. 10) | Utenti UE | Soglia utenti (5%) | SL (IS-3) |
| --- | --- | ---: | ---: | --- |
| SOC/MDR 24/7 | sì | 9.000 | 450 | 30 min / 60 min |
| WAF gestito | sì | 5.400 | 270 | 30 min / 60 min |
| Portale clienti e ticketing | no | 9.000 | – | 120 min / 240 min |

Soglia economica: min(500.000 EUR; 5% di 7.000.000 EUR) = **350.000 EUR**.

| Regola | Condizione | Fonte |
| --- | --- | --- |
| R1 | Servizio gestito completamente indisponibile > 30 min (non per interruzioni programmate) | Reg. (UE) 2024/2690, art. 10, lett. a) |
| R2 | Servizio gestito degradato > 60 min per più del 5% dei suoi utenti (non per interruzioni programmate) | art. 10, lett. b) |
| R3 | Dati del servizio gestito compromessi con sospetta azione malevola | art. 10, lett. c) |
| R4 | Dati del servizio gestito compromessi per più del 5% dei suoi utenti | art. 10, lett. d) |
| R5 | Perdita finanziaria diretta, anche potenziale, > 350.000 EUR | art. 3, par. 1, lett. a) |
| R6 | Accesso non autorizzato, sospetto malevolo, con gravi perturbazioni | art. 3, par. 1, lett. e) |
| R7 | ≥ 2 incidenti in 6 mesi con stessa causa e perdite cumulate > 350.000 EUR | art. 4 |
| R8 | Esfiltrazione, anche potenziale, di segreti commerciali | art. 3, par. 1, lett. b) |

Categorie ACN (Det. 379907/2025, allegati 3-4): IS-1 riservatezza verso l'esterno, IS-2 integrità con impatto
esterno, IS-3 superamento dei livelli di servizio attesi del servizio coinvolto (non per interruzioni
programmate), IS-4 accesso non autorizzato (solo soggetti essenziali). Criteri esclusi e motivazioni:
`docs/criteri/DISCREPANZE-CRITERI.md`.

## Struttura

```
src/main/java/it/pw29/notifiche/
  controller/       API REST
  controller/web/   pagine dell'interfaccia (Thymeleaf), moduli, azioni
  service/          logica applicativa e motore delle regole
  repository/       accesso ai dati (Spring Data JPA)
  domain/           entità JPA ed enum di dominio
  dto/              oggetti di ingresso/uscita
  config/           configurazione di Spring Security
src/main/resources/db/migration/   migrazioni Flyway (schema, dati, commenti dello schema)
src/main/resources/db/vendor/      migrazioni specifiche del database (trigger di immutabilità su PostgreSQL)
src/main/resources/db/demo/        utenti dimostrativi (solo sviluppo, test e profilo demo)
src/main/resources/templates/      pagine dell'interfaccia (interfaccia/) e template di stampa (documenti/)
src/main/resources/static/         foglio di stile, script e font dell'interfaccia
src/main/resources/stampa/         font e profilo colore per il PDF/A, con licenze
docs/ARCHITETTURA.md               componenti, flusso, stati, deploy
docs/ER.md, docs/DATA-DICTIONARY.md  generati dal DDL e verificati da test
docs/figure/                       figure SVG e PNG per la relazione
docs/adr/                          registro delle decisioni architetturali (ADR-0001..0010)
docs/criteri/                      criteri di significatività ufficiali e loro traduzione in regole
docs/MATRICE-CAMPI.md              matrice campo/adempimento, generata dal codice e verificata da test
docs/scenari/                      tracce degli scenari M3 e M4 e documenti JSON/PDF prodotti (generati dai test)
docs/fonti/                        registro delle fonti normative consultate (URL, data, SHA-256)
docs/tassonomia/                   PDF ufficiale e fonti della tassonomia ACN, registro di acquisizione, discrepanze
tools/                             generazione delle migrazioni della tassonomia e delle figure
Dockerfile, docker-compose.yml     contenitori dell'applicazione e di PostgreSQL
```
