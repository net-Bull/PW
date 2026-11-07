# Architettura

Applicazione Spring Boot monolitica a livelli, con due punti d'ingresso (interfaccia web e API REST) sopra gli
stessi servizi. Le figure sono in Mermaid; le versioni SVG e PNG per la relazione sono in `docs/figure/`.

## Componenti e livelli

```mermaid
flowchart TB
    subgraph Client["Client"]
        B["Browser<br/>operatore e approvatore"]
        C["Client API<br/>script, test, integrazioni"]
    end

    subgraph Sicurezza["Spring Security, due catene di filtri (ADR-0009)"]
        F2["Interfaccia<br/>login a form, sessione, CSRF, SameSite=Strict"]
        F1["/api/**<br/>HTTP Basic senza sessione"]
    end

    subgraph Controller["Livello controller"]
        W["controller.web<br/>pagine Thymeleaf: incidenti, documenti, riclassificazione"]
        R["controller<br/>API REST JSON"]
    end

    subgraph Servizi["Livello service: permessi @PreAuthorize, transazioni, audit"]
        S1["IncidenteService<br/>qualificazione, conoscenza, conclusione, scadenze"]
        S2["DocumentoService<br/>bozze, riuso, verifica, approvazione, trasmissione"]
        S3["RiclassificazioneService<br/>prima/dopo, declassamento"]
        S4["ValutazioneService<br/>regole R1-R8 e categorie IS"]
        S5["CatenaService<br/>catena SHA-256"]
        S6["EsportazioneService e StampaPdf<br/>JSON e PDF/A-2b"]
        S7["MatriceCampi<br/>matrice campo/adempimento da @Campo"]
        direction LR
        S1 ~~~ S2 ~~~ S3 ~~~ S4
        S5 ~~~ S6 ~~~ S7
    end

    subgraph Dominio["Dominio"]
        direction LR
        D["Entità JPA e macchine a stati<br/>Incidente, Adempimento, VersioneDocumento, StoricoRiclassificazione"]
    end

    subgraph Dati["Persistenza"]
        RP["repository<br/>Spring Data JPA"]
        DB[("H2 (sviluppo e test)<br/>PostgreSQL 16 (profilo postgres)")]
        FW["Flyway<br/>schema, tassonomia, soglie, commenti, trigger"]
    end

    B --> F2 --> W
    C --> F1 --> R
    W --> Servizi
    R --> Servizi
    Servizi --> D
    Servizi --> RP --> DB
    FW --> DB
```

I controller, web e REST, usano solo i servizi; i repository sono usati solo dai servizi. Le pagine dell'interfaccia e le API chiamano gli
**stessi** metodi di servizio, quindi permessi, regole di dominio e audit sono gli stessi (ADR-0010).

| Package | Ruolo |
| --- | --- |
| `config` | Spring Security: catene di filtri, gerarchia dei ruoli, codifica delle password |
| `controller` | API REST e traduzione delle eccezioni in codici HTTP (`GestioneErrori`) |
| `controller.web` | Pagine Thymeleaf, moduli, esecuzione delle azioni con POST-redirect-GET (`Azioni`), formati |
| `service` | Logica applicativa: valutazione, scadenze, documenti, catena, esportazioni, matrice dei campi |
| `domain` | Entità JPA, enum con le transizioni ammesse, contenuto dei documenti con le annotazioni `@Campo` |
| `repository` | Accesso ai dati con Spring Data JPA, query di conteggio della tassonomia |
| `dto` | Oggetti di ingresso e uscita |

## Flusso principale: dalla rilevazione alla relazione finale

```mermaid
sequenceDiagram
    autonumber
    actor O as Operatore
    actor A as Approvatore
    participant UI as Interfaccia web
    participant S as Servizi
    participant DB as Database

    O->>UI: Registra incidente (rilevazione)
    UI->>S: IncidenteService.registra
    O->>UI: Valutazione di significatività
    UI->>S: qualifica: soglie alla data della conoscenza, R1-R8 e IS-1..IS-4
    S->>DB: valutazione, conoscenza (una volta), adempimenti pre-notifica e notifica
    Note over S: termini 24 h e 72 h calcolati dalla conoscenza
    O->>UI: Crea bozza, compila (verifica mentre scrive), invia in approvazione
    UI->>S: DocumentoService: campi di sistema, completezza per adempimento
    A->>UI: Registra trasmissione (riferimento del portale ACN)
    UI->>S: trasmetti: quattro occhi, intestazione sigillata, impronta SHA-256
    S->>DB: versione TRASMESSO, anello della catena (immutabile)
    Note over O,S: la notifica riusa i dati della pre-notifica trasmessa e fa sorgere la relazione finale
    O->>UI: Riclassificazione tardiva (voci, eventuale nuova valutazione, motivazione)
    UI->>S: RiclassificazioneService: storico prima/dopo, bozze riallineate
    O->>UI: Conclusione della gestione (una volta)
    O->>UI: Relazione finale con le riclassificazioni compilate dal sistema
    A->>UI: Registra trasmissione della finale
    S->>DB: incidente CHIUSO
```

Lo stesso percorso è eseguito per intero dall'interfaccia in `ScenarioInterfacciaTest` (milestone M4), che
produce `docs/scenari/TRACCIA-INTERFACCIA.md`; dalle API in `RiclassificazioneTest` (milestone M3).

## Stati

```mermaid
stateDiagram-v2
    direction LR
    state "Incidente" as I {
        [*] --> IN_VALUTAZIONE
        IN_VALUTAZIONE --> NON_SIGNIFICATIVO
        IN_VALUTAZIONE --> SIGNIFICATIVO
        NON_SIGNIFICATIVO --> SIGNIFICATIVO
        SIGNIFICATIVO --> NON_SIGNIFICATIVO: prima di ogni invio
        SIGNIFICATIVO --> DECLASSATO: riclassificazione dopo un invio
        DECLASSATO --> SIGNIFICATIVO
        SIGNIFICATIVO --> CHIUSO: relazione finale trasmessa
        DECLASSATO --> CHIUSO: relazione finale trasmessa
    }
    state "Versione del documento" as V {
        [*] --> BOZZA
        BOZZA --> IN_APPROVAZIONE: completa per l'adempimento
        IN_APPROVAZIONE --> BOZZA: respinta
        IN_APPROVAZIONE --> TRASMESSO: quattro occhi
        TRASMESSO --> [*]
    }
```

Le transizioni sono dichiarate in un solo punto (`StatoIncidente`, `StatoDocumento`) e verificate da
`MacchineStatiTest`. Una versione `TRASMESSO` non si modifica: una correzione è una nuova versione (rettifica).

## Deploy

```mermaid
flowchart LR
    U["Browser"] -- "HTTP 8080" --> APP
    subgraph Host["Docker Compose"]
        APP["app<br/>eclipse-temurin:21-jre<br/>notifiche-csirt.jar<br/>profili postgres,demo"]
        DB[("db<br/>postgres:16<br/>volume dbdata")]
        APP -- "JDBC 5432" --> DB
    end
```

| Modalità | Comando | Database | Utenti |
| --- | --- | --- | --- |
| Sviluppo | `mvn spring-boot:run` | H2 in memoria | demo |
| Prove su PostgreSQL | `docker compose up -d db` e `mvn spring-boot:run -Dspring-boot.run.profiles=postgres,demo` | PostgreSQL 16 | demo |
| Contenitori | `mvn clean package` e `docker compose up --build` | PostgreSQL 16 | demo |
| Installazione reale | jar con il profilo `postgres` e `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL | creati dall'amministratore |

All'avvio Flyway applica le migrazioni: schema, tassonomia, soglie, commenti dello schema e, solo su PostgreSQL,
i trigger di immutabilità (`db/vendor/postgresql`). Gli istanti sono salvati come `TIMESTAMP WITH TIME ZONE`;
il fuso del soggetto (`notifiche.fuso-orario`, predefinito `Europe/Rome`) vale per i termini a mesi, per le date
dei documenti e per l'interfaccia.

## Documentazione generata e verificata dalla build

| Documento | Generato da | Verifica |
| --- | --- | --- |
| `docs/MATRICE-CAMPI.md` | annotazioni `@Campo` e vincoli di classificazione | `MatriceCampiDocumentazioneTest` |
| `docs/ER.md`, `docs/DATA-DICTIONARY.md` | DDL delle migrazioni (`INFORMATION_SCHEMA`, commenti V8) | `SchemaDocumentazioneTest` |
| `docs/scenari/TRACCIA-RICLASSIFICAZIONE.md` e documenti | scenario M3 dalle API | `RiclassificazioneTest` |
| `docs/scenari/TRACCIA-INTERFACCIA.md` | scenario M4 dall'interfaccia | `ScenarioInterfacciaTest` |
| `docs/figure/*.svg`, `*.png` | blocchi Mermaid di `ER.md` e di questo file | `tools/rendi_diagrammi.py` |
