# Verifiche

Verifiche eseguite il 06/11/2026 sul PC Windows dello studente, con i comandi indicati in ciascuna sezione.

## Ambiente

| Voce | Valore |
| --- | --- |
| Sistema | Windows 11 |
| Java | OpenJDK 21.0.12.1 LTS |
| Maven | Apache Maven 3.9.11 |
| Profilo dell'applicazione | predefinito: H2 in memoria, utenti demo |
| Browser della prova d'uso | browser basato su Chromium, su `http://localhost:8080` |

## 1. Build riproducibile

`mvn -B clean verify` da repository pulito: **108 test, 0 fallimenti, 0 errori**. Jar avviato con `java -jar`:
applicazione pronta in circa 5 secondi, migrazioni V1–V8 e utenti demo applicati da Flyway su H2.
`GET /api/tassonomia/conteggio` restituisce TC-ACN-2.0 con 4 / 21 / 19 / 219, lo stesso conteggio del documento
ACN.

## 2. Conformità PDF/A con veraPDF

Validatore di riferimento veraPDF **1.30.2** (motore greenfield, `org.verapdf:validation-model-jakarta`), usato
come libreria in un test del profilo Maven `verapdf`, fuori dalla build predefinita (che resta compilabile offline):

```
mvn -Pverapdf -Dtest=VerificaPdfaTest test [-Dverapdf.cartella=<pdf scaricati>] [-Dverapdf.rapporto=<file.md>]
```

| File | Origine | Profilo dichiarato | Conforme a PDF/A-2b |
| --- | --- | --- | --- |
| `INC-2026-0001_PRE_NOTIFICA_1_v1.pdf` | scenario M3 (`docs/scenari/documenti`) | 2b | sì, nessuna regola violata |
| `INC-2026-0001_NOTIFICA_1_v1.pdf` | scenario M3 | 2b | sì, nessuna regola violata |
| `INC-2026-0001_RELAZIONE_FINALE_1_v1.pdf` | scenario M3 | 2b | sì, nessuna regola violata |
| `INC-2026-0001_PRE_NOTIFICA_1_v1.pdf` | scaricato dall'interfaccia dopo la trasmissione (punto 3) | 2b | sì, nessuna regola violata |

**Controllo negativo**: nello stesso test un PDF di una pagina creato con PDFBox senza requisiti PDF/A viene
rifiutato (clausola 6.6.2.1: manca lo stream dei metadati XMP nel catalogo). Il test dimostra che il validatore
distingue davvero un file conforme da uno che non lo è.

## 3. Prova d'uso dall'interfaccia

Scenario fittizio coerente con il contesto: degrado del WAF gestito. La prova usa i due ruoli, con due accessi
distinti.

| Passo | Utente | Esito osservato |
| --- | --- | --- |
| Accesso con login a form | operatore | elenco incidenti vuoto, profilo operatore |
| Registrazione: "Prova degrado servizio web", primo evento 06/11/2026 16:40 | operatore | INC-2026-0001 registrato, rimanda alla valutazione |
| Valutazione sul servizio WAF gestito, conoscenza 06/11/2026 16:57 | operatore | significativo per **R2** e **IS-3**; pre-notifica entro 07/11 16:57, notifica entro 09/11 16:57 |
| Bozza della pre-notifica | operatore | referente CSIRT e servizio già compilati; categorie IS e criteri R compilati dal sistema in sola lettura |
| Classificazione e completamento | operatore | BC:IM_AV, BC:RO_SY, BC:SE_ME, TT:VU_SE; atto malevolo "non noto", impatto transfrontaliero "no" |
| Invio in approvazione | operatore | stato "In approvazione" |
| Trasmissione con riferimento fittizio `PROVA-0001` | approvatore | registrata alle 17:06, versione sigillata con impronta SHA-256 `3e066765…91feff80` collegata all'anello iniziale della catena; documento immutabile |
| Download del PDF/A | approvatore | modulo con stato "TRASMESSO", autori (redatta e inviata da operatore, trasmessa da approvatore), impronta e anello precedente; conforme a PDF/A-2b (punto 2) |

## 4. Difetti trovati provando l'interfaccia e corretti

Provando l'interfaccia dal browser sono emersi due difetti che i test automatici non coprivano. Entrambi sono
corretti e coperti da test di regressione.

### 4.1 Istante di trasmissione nello stesso minuto dell'invio in approvazione

**Sintomo.** L'approvatore apre la pagina nello stesso minuto in cui l'operatore ha inviato in approvazione; il
modulo propone quel minuto. Se registra la trasmissione dopo che il minuto è trascorso, viene rifiutata con "La
trasmissione non può precedere l'invio in approvazione".

**Causa.** Il campo `datetime-local` ha la precisione del minuto e veniva letto come inizio del minuto, che precede
l'invio in approvazione registrato al secondo.

**Correzione.** `Formati.leggi(valore, nonPrimaDi)`: un minuto è un intervallo di 60 secondi; se l'evento di
riferimento (l'invio in approvazione) cade dentro il minuto indicato, l'istante letto è l'evento stesso. Un
minuto davvero precedente resta precedente e il dominio continua a rifiutarlo; le API, che ricevono istanti al
secondo, non cambiano. Regressione: `FormatiTest`.

### 4.2 Modulo inviato dopo la scadenza della sessione

**Sintomo.** Una pagina rimasta aperta oltre la durata della sessione, al momento dell'invio del modulo, mostrava
la pagina d'errore predefinita di Spring Boot ("Whitelabel Error Page", 403 Forbidden).

**Causa.** La sessione dell'interfaccia scade dopo 30 minuti di inattività (valore predefinito); il modulo
portava il token CSRF della sessione scaduta e il filtro CSRF lo ha giustamente rifiutato. Il rifiuto avviene nei
filtri di sicurezza, prima dei controller, quindi la pagina d'errore dell'interfaccia (`ErroriInterfaccia`) non
interveniva.

**Correzione.** Nella catena di filtri dell'interfaccia un `AccessDeniedHandler` rimanda a `/login?scaduta`
quando il rifiuto è CSRF e la richiesta indica una sessione non più valida; la pagina di accesso spiega che il
modulo non è stato salvato. Ogni altro rifiuto (token mancante o errato in una sessione attiva, richiesta
forgiata) resta un 403. Pagina `templates/error.html` per gli errori fuori dai controller, senza dettagli
tecnici. Regressione: `SessioneScadutaTest`. La protezione CSRF non cambia (ADR-0009).

## 5. Docker Compose

Non eseguito: Docker Desktop non è installato sul PC. La stessa configurazione (jar con profili `postgres,demo`
su PostgreSQL 16) è stata provata senza contenitori. Da eseguire se si installa Docker Desktop:
`docker compose up --build`.

## 6. Tassonomia vigente

Verificata di nuovo il 07/11/2026 e l'08/11/2026, prima del caricamento dell'elaborato: TC-ACN v2.0 ancora
vigente, impronte identiche a quelle registrate (`docs/tassonomia/ACQUISIZIONE.md`).

## Note

- La ricerca delle voci della tassonomia confronta codice e nome ufficiale (in inglese, come nel documento ACN),
  non la descrizione: "degrado" non trova nulla, "availability" o "BC:IM" sì.
