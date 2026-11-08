# Notifiche CSIRT – Project Work 29

Web application in Java (Spring Boot) per redigere e gestire le notifiche di incidente
significativo al CSIRT Italia previste dal D.Lgs. 138/2024 (NIS2), con classificazione secondo
la Tassonomia Cyber ACN. Il soggetto, CyberWall S.r.l., è un MSSP fittizio: non sono coinvolti
dati o sistemi reali.

## Requisiti

- JDK 21
- Maven 3.9 o successivo
- Docker, solo per avviare l'applicazione con PostgreSQL

## Avvio

```bash
# build e test (database H2 in memoria)
mvn clean verify

# avvio con H2 e utenti demo: http://localhost:8080
mvn spring-boot:run

# applicazione e PostgreSQL 16 in contenitori
mvn clean package
docker compose up --build
```

## Utenti demo

Presenti solo nel profilo predefinito e nel profilo `demo`.

| Utente | Password | Ruolo |
| --- | --- | --- |
| `operatore` | `Operatore-Demo-2026` | redige e invia in approvazione |
| `approvatore` | `Approvatore-Demo-2026` | approva e registra la trasmissione |
| `operatore2` | `Operatore2-Demo-2026` | secondo operatore |
| `approvatore2` | `Approvatore2-Demo-2026` | secondo approvatore |

## Dove trovare i punti della traccia

| Punto | Dove |
| --- | --- |
| Tassonomia ACN allegata, con data di acquisizione | `docs/tassonomia/` (PDF e `ACQUISIZIONE.md`) |
| Caricamento a dati e query di conteggio | migrazione `V3`, `tools/genera_migrazione_tassonomia.py`, `GET /api/tassonomia/conteggio` |
| Tabella delle discrepanze | `docs/tassonomia/DISCREPANZE.md`, `docs/criteri/DISCREPANZE-CRITERI.md` |
| Riclassificazione tardiva e traccia di esecuzione | `docs/scenari/TRACCIA-RICLASSIFICAZIONE.md` e `docs/scenari/documenti/` |
| Matrice dei campi generata dal codice | `docs/MATRICE-CAMPI.md` |
| Rifiuto dei documenti incompleti | `MatriceCampiTest`, `RegressioneRevisioneTest` |
| Scenari sopra e sotto soglia | `RegoleSignificativitaTest`, `FlussoNotificaTest` |
| Architettura e deploy | `docs/ARCHITETTURA.md`, `Dockerfile`, `docker-compose.yml` |
| ER e data dictionary | `docs/ER.md`, `docs/DATA-DICTIONARY.md` |
| Registro delle decisioni | `docs/adr/` |
| Verifiche eseguite | `docs/VERIFICHE.md` |

Le tracce degli scenari, l'ER e il data dictionary si rigenerano dai test:

```bash
mvn test -Dtest=RiclassificazioneTest -Dtraccia.aggiorna=true
mvn test -Dtest=SchemaDocumentazioneTest -Dschema.aggiorna=true
```
