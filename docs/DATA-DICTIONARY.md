# Data dictionary

> File GENERATO da `SchemaDocumentazioneTest` a partire dal DDL: migrazioni Flyway `src/main/resources/db/migration` applicate su H2 in modalità PostgreSQL e lette da `INFORMATION_SCHEMA`, commenti compresi (`COMMENT ON`, migrazione V8). La build fallisce se il DDL cambia e questo file no. Rigenerazione: `mvn test -Dtest=SchemaDocumentazioneTest -Dschema.aggiorna=true`.
> Escluse la tabella tecnica di Flyway e la migrazione `db/vendor/postgresql` (trigger di immutabilità, solo PostgreSQL: non cambia tabelle né colonne).

Legenda: **PK** chiave primaria, **FK** chiave esterna, **UK** valore univoco; "Null" indica se la colonna ammette valori assenti.

## Indice

- [`adempimento`](#adempimento) – Adempimento verso il CSIRT Italia (pre-notifica, notifica, relazione intermedia, mensile o finale)
- [`audit_log`](#audit_log) – Registro delle operazioni: solo inserimenti; su PostgreSQL un trigger impedisce modifiche e cancellazioni
- [`classificazione`](#classificazione) – Voci della tassonomia (solo valori) associate a una versione del documento: ogni invio conserva la propria classificazione
- [`incidente`](#incidente) – Incidente registrato, con il momento della rilevazione, quello della conoscenza da cui decorrono i termini e la conclusione della gestione (ADR-0003)
- [`livello_servizio`](#livello_servizio) – Servizio critico del soggetto con i livelli di servizio attesi (SL) definiti in autonomia (misura DE.CM-01): base della categoria IS-3 e, per i servizi gestiti, delle regole R1-R4
- [`soggetto`](#soggetto) – Soggetto NIS che notifica (qui l'MSSP fittizio CyberWall S.r.l.), con i dati che ne motivano la qualifica e il referente CSIRT
- [`soglia_set`](#soglia_set) – Insieme versionato delle soglie del Reg
- [`storico_riclassificazione`](#storico_riclassificazione) – Traccia di ogni riclassificazione: classificazione prima e dopo, momento, motivazione, documento trasmesso con la classificazione precedente e documento che la comunica (traccia PW 29, punto 3; ADR-0007)
- [`tassonomia_versione`](#tassonomia_versione) – Versione della Tassonomia Cyber ACN caricata a dati, con la fonte e l'impronta del documento ufficiale acquisito
- [`tassonomia_voce`](#tassonomia_voce) – Nodo dell'albero della tassonomia: macrocategoria, predicato, sotto-predicato (solo sotto Involved Asset) e valore
- [`utente`](#utente) – Utente dell'applicazione con il suo profilo (ADR-0009)
- [`valutazione_significativita`](#valutazione_significativita) – Storico delle valutazioni di significatività: input, soglie usate ed esito restano anche dopo una riclassificazione
- [`versione_autore`](#versione_autore) – Autori di una versione, cioè chi ne ha scritto contenuto o classificazione: nessuno di loro può registrarne la trasmissione (quattro occhi, ADR-0009)
- [`versione_documento`](#versione_documento) – Versione del documento di un adempimento: bozza, in approvazione o trasmessa

## adempimento

Adempimento verso il CSIRT Italia (pre-notifica, notifica, relazione intermedia, mensile o finale). Le scadenze non sono memorizzate: si calcolano dai fatti.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `incidente_id` | BIGINT | no | FK → `incidente.id` |  | Incidente a cui si riferisce. |
| `tipo` | VARCHAR(30) | no |  |  | PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE o RELAZIONE_FINALE. |
| `progressivo` | INTEGER | no |  |  | Numero d'ordine fra gli adempimenti dello stesso tipo (intermedie e mensili). |
| `stato` | VARCHAR(20) | no |  |  | DA_REDIGERE, IN_LAVORAZIONE o TRASMESSO, derivato dalle versioni. |
| `richiesto_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Ricezione della richiesta del CSIRT (solo relazione intermedia). |
| `scadenza_richiesta` | TIMESTAMP WITH TIME ZONE | sì |  |  | Termine indicato dal CSIRT nella richiesta, se presente. |
| `riferimento_richiesta` | VARCHAR(255) | sì |  |  | Riferimento della richiesta del CSIRT. |
| `trasmesso_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Istante del primo invio; resta anche dopo una rettifica. |

Vincoli:

- univocità di (`incidente_id`, `tipo`, `progressivo`)
- controllo `"stato" IN('DA_REDIGERE', 'IN_LAVORAZIONE', 'TRASMESSO')`
- controllo `"tipo" IN('PRE_NOTIFICA', 'NOTIFICA', 'RELAZIONE_INTERMEDIA', 'RELAZIONE_MENSILE', 'RELAZIONE_FINALE')`

## audit_log

Registro delle operazioni: solo inserimenti; su PostgreSQL un trigger impedisce modifiche e cancellazioni.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `istante` | TIMESTAMP WITH TIME ZONE | no |  |  | Istante dell'operazione. |
| `username` | VARCHAR(50) | no |  |  | Utente che ha eseguito l'operazione (o "sistema"). |
| `azione` | VARCHAR(50) | no |  |  | Codice dell'operazione (es. TRASMISSIONE, RICLASSIFICAZIONE, ACCESSO_FALLITO). |
| `entita` | VARCHAR(50) | sì |  |  | Tipo di entità coinvolta. |
| `entita_id` | BIGINT | sì |  |  | Identificativo dell'entità coinvolta. |
| `dettaglio` | VARCHAR(2000) | sì |  |  | Descrizione leggibile dell'operazione. |

## classificazione

Voci della tassonomia (solo valori) associate a una versione del documento: ogni invio conserva la propria classificazione.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `versione_documento_id` | BIGINT | no | PK, FK → `versione_documento.id` |  | Versione del documento classificata. |
| `voce_id` | BIGINT | no | PK, FK → `tassonomia_voce.id` |  | Valore della tassonomia. |

## incidente

Incidente registrato, con il momento della rilevazione, quello della conoscenza da cui decorrono i termini e la conclusione della gestione (ADR-0003).

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `codice` | VARCHAR(20) | no | UK |  | Codice leggibile, INC-anno-progressivo. |
| `soggetto_id` | BIGINT | no | FK → `soggetto.id` |  | Soggetto a cui è occorso l'incidente. |
| `titolo` | VARCHAR(255) | no |  |  | Titolo breve. |
| `descrizione` | VARCHAR(4000) | no |  |  | Descrizione dell'evento osservato. |
| `stato` | VARCHAR(20) | no |  |  | IN_VALUTAZIONE, NON_SIGNIFICATIVO, SIGNIFICATIVO, DECLASSATO o CHIUSO (macchina a stati StatoIncidente). |
| `rilevato_il` | TIMESTAMP WITH TIME ZONE | no |  |  | Primo evento rilevante osservato. |
| `registrato_il` | TIMESTAMP WITH TIME ZONE | no |  |  | Istante di registrazione nel sistema. |
| `registrato_da` | VARCHAR(50) | no |  |  | Utente che ha registrato l'incidente. |
| `conoscenza_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Evidenza di incidente significativo: da qui le 24 h e le 72 h (art. 25, c. 5). Registrata una sola volta. |
| `conoscenza_motivazione` | VARCHAR(2000) | sì |  |  | Elementi oggettivi che fondano la conoscenza. |
| `conoscenza_registrata_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Istante in cui la conoscenza è stata registrata. |
| `conoscenza_registrata_da` | VARCHAR(50) | sì |  |  | Utente che ha registrato la conoscenza. |
| `conclusione_gestione_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Conclusione della gestione, registrata una sola volta: fissa il termine della relazione finale se l'incidente era in corso (lett. e). |
| `versione` | BIGINT | no |  | `0` | Contatore per il controllo ottimistico delle modifiche concorrenti. |

Vincoli:

- controllo `"stato" IN('IN_VALUTAZIONE', 'NON_SIGNIFICATIVO', 'SIGNIFICATIVO', 'DECLASSATO', 'CHIUSO')`
- controllo `("conoscenza_il" IS NULL) OR ("conoscenza_il" >= "rilevato_il")`
- controllo `(("conoscenza_il" IS NULL) AND ("conoscenza_motivazione" IS NULL) AND ("conoscenza_registrata_il" IS NULL)) OR (("conoscenza_il" IS NOT NULL) AND ("conoscenza_motivazione" IS NOT NULL) AND ("conoscenza_registrata_il" IS NOT NULL))`

## livello_servizio

Servizio critico del soggetto con i livelli di servizio attesi (SL) definiti in autonomia (misura DE.CM-01): base della categoria IS-3 e, per i servizi gestiti, delle regole R1-R4.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `soggetto_id` | BIGINT | no | FK → `soggetto.id` |  | Soggetto che eroga il servizio. |
| `servizio` | VARCHAR(40) | no |  |  | Codice del servizio (es. SOC_MDR), univoco per soggetto. |
| `descrizione` | VARCHAR(255) | no |  |  | Descrizione del servizio. |
| `max_indisponibilita_minuti` | INTEGER | no |  |  | Indisponibilità completa massima attesa, in minuti: oltre scatta IS-3. |
| `max_degrado_minuti` | INTEGER | no |  |  | Degrado continuativo massimo atteso, in minuti: oltre scatta IS-3. |
| `fonte` | VARCHAR(500) | no |  |  | Origine del livello di servizio atteso. |
| `utenti_ue` | INTEGER | no |  |  | Utenti del servizio nell'Unione (art. 3, par. 3, del regolamento), base della quota del 5%. |
| `servizio_gestito` | BOOLEAN | no |  |  | Vero per i servizi gestiti o di sicurezza gestiti, a cui si applicano le regole R1-R4 (art. 10). |

Vincoli:

- univocità di (`soggetto_id`, `servizio`)
- controllo `"utenti_ue" >= 0`

## soggetto

Soggetto NIS che notifica (qui l'MSSP fittizio CyberWall S.r.l.), con i dati che ne motivano la qualifica e il referente CSIRT.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `denominazione` | VARCHAR(255) | no |  |  | Ragione sociale. |
| `codice_fiscale` | VARCHAR(16) | no | UK |  | Codice fiscale (fittizio nel contesto del progetto). |
| `tipo` | VARCHAR(20) | no |  |  | ESSENZIALE o IMPORTANTE (art. 6 D.Lgs. 138/2024): decide se si applica la categoria IS-4. |
| `settore` | VARCHAR(255) | no |  |  | Settore e allegato del D.Lgs. 138/2024. |
| `motivazione_tipo` | VARCHAR(1000) | no |  |  | Motivazione della qualifica con i riferimenti normativi. |
| `referente_nome` | VARCHAR(255) | no |  |  | Nome del referente CSIRT, interlocutore del CSIRT Italia (misura GV.RR-02). |
| `referente_email` | VARCHAR(255) | no |  |  | E-mail del referente CSIRT, proposta nelle bozze. |
| `referente_telefono` | VARCHAR(50) | no |  |  | Telefono del referente CSIRT, proposto nelle bozze. |
| `dipendenti` | INTEGER | no |  |  | Effettivi, per la dimensione d'impresa (Racc. 2003/361/CE, richiamata dall'art. 3, c. 2). |
| `totale_bilancio` | NUMERIC(15, 2) | no |  |  | Totale di bilancio annuo (euro), per la dimensione d'impresa. |
| `iscrizione_elenco_nis_il` | DATE | no |  |  | Data di inserimento nell'elenco dei soggetti NIS, da cui decorre l'obbligo di notifica (FAQ ACN ISB.G.3). |

Vincoli:

- controllo `"tipo" IN('ESSENZIALE', 'IMPORTANTE')`

## soglia_set

Insieme versionato delle soglie del Reg. di esecuzione (UE) 2024/2690 per il soggetto, con data di validità: una valutazione usa le soglie vigenti alla data della conoscenza.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `versione` | VARCHAR(50) | no | UK |  | Codice della versione delle soglie, riportato in ogni valutazione. |
| `valida_dal` | DATE | no |  |  | Data da cui le soglie si applicano. |
| `fonte` | VARCHAR(500) | no |  |  | Riferimento normativo delle soglie. |
| `fatturato_annuo` | NUMERIC(15, 2) | no |  |  | Fatturato annuo del soggetto (euro), base della soglia economica relativa. |
| `minuti_indisponibilita` | INTEGER | no |  |  | Durata oltre la quale l'indisponibilità completa di un servizio gestito è significativa (art. 10, lett. a). |
| `minuti_degrado` | INTEGER | no |  |  | Durata oltre la quale la disponibilità limitata è significativa se supera la quota di utenti (art. 10, lett. b). |
| `quota_utenti` | NUMERIC(5, 4) | no |  |  | Quota degli utenti del servizio (0,05 = 5%) oltre la quale scattano le regole R2 e R4. |
| `tetto_utenti` | INTEGER | no |  |  | Soglia assoluta di utenti (art. 10, lett. b e d): vale la minore fra questa e la quota degli utenti del servizio. |
| `perdita_assoluta` | NUMERIC(15, 2) | no |  |  | Soglia assoluta della perdita finanziaria diretta (euro, art. 3, par. 1, lett. a). |
| `quota_fatturato` | NUMERIC(5, 4) | no |  |  | Quota del fatturato annuo per la soglia economica relativa; vale la minore fra le due. |
| `finestra_ricorrenza_mesi` | INTEGER | no |  |  | Finestra, in mesi, in cui si contano gli incidenti ricorrenti (art. 4). |
| `min_incidenti_ricorrenza` | INTEGER | no |  |  | Numero minimo di incidenti con la stessa causa apparente nella finestra (art. 4). |

## storico_riclassificazione

Traccia di ogni riclassificazione: classificazione prima e dopo, momento, motivazione, documento trasmesso con la classificazione precedente e documento che la comunica (traccia PW 29, punto 3; ADR-0007).

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `incidente_id` | BIGINT | no | FK → `incidente.id` |  | Incidente riclassificato. |
| `documento_prima_id` | BIGINT | sì | FK → `versione_documento.id` |  | Ultimo documento trasmesso con la classificazione precedente; vuoto per il ritorno a non significativo prima di ogni invio. |
| `documento_dopo_id` | BIGINT | sì | FK → `versione_documento.id` |  | Primo documento trasmesso che comunica la riclassificazione; vuoto finché non parte. |
| `significativo_prima` | BOOLEAN | no |  |  | Significatività prima del cambiamento. |
| `significativo_dopo` | BOOLEAN | no |  |  | Significatività dopo il cambiamento. |
| `voci_prima` | VARCHAR(2000) | no |  |  | Codici delle voci prima, separati da virgola. |
| `voci_dopo` | VARCHAR(2000) | no |  |  | Codici delle voci dopo, separati da virgola. |
| `motivazione` | VARCHAR(2000) | no |  |  | Motivazione del cambiamento. |
| `registrata_il` | TIMESTAMP WITH TIME ZONE | no |  |  | Momento della riclassificazione. |
| `registrata_da` | VARCHAR(50) | no |  |  | Utente che l'ha registrata. |
| `categorie_is_prima` | VARCHAR(100) | no |  | `''` | Categorie IS prima, separate da virgola. |
| `categorie_is_dopo` | VARCHAR(100) | no |  | `''` | Categorie IS dopo, separate da virgola. |
| `valutazione_id` | BIGINT | sì | FK → `valutazione_significativita.id` |  | Nuova valutazione che ha motivato il cambio di significatività, se c'è stata. |
| `stato_incidente_prima` | VARCHAR(20) | no |  | `'SIGNIFICATIVO'` | Stato dell'incidente prima. |
| `stato_incidente_dopo` | VARCHAR(20) | no |  | `'SIGNIFICATIVO'` | Stato dell'incidente dopo (es. DECLASSATO). |

## tassonomia_versione

Versione della Tassonomia Cyber ACN caricata a dati, con la fonte e l'impronta del documento ufficiale acquisito. Una sola versione è vigente; le classificazioni restano legate alla versione con cui sono state fatte (ADR-0001, ADR-0002).

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `codice` | VARCHAR(50) | no | UK |  | Codice della versione nel sistema, es. TC-ACN-2.0. |
| `versione_documento` | VARCHAR(20) | no |  |  | Versione dichiarata nel documento ACN. |
| `data_acquisizione` | DATE | no |  |  | Data in cui il documento ufficiale è stato scaricato e verificato. |
| `fonte_url` | VARCHAR(500) | no |  |  | Pagina ufficiale ACN da cui è stato acquisito il documento. |
| `fonte_strutturata` | VARCHAR(500) | no |  |  | Fonte strutturata usata per l'albero (JSON MISP acn/machinetag). |
| `sha256_documento` | VARCHAR(64) | no |  |  | Impronta SHA-256 del PDF ufficiale allegato (docs/tassonomia). |
| `vigente` | BOOLEAN | no |  | `FALSE` | Vero per la versione usata dalle nuove bozze. |

## tassonomia_voce

Nodo dell'albero della tassonomia: macrocategoria, predicato, sotto-predicato (solo sotto Involved Asset) e valore. Solo i valori classificano un incidente.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `versione_id` | BIGINT | no | FK → `tassonomia_versione.id` |  | Versione della tassonomia a cui appartiene la voce. |
| `padre_id` | BIGINT | sì | FK → `tassonomia_voce.id` |  | Voce di livello superiore; vuota per le macrocategorie. |
| `livello` | VARCHAR(20) | no |  |  | MACROCATEGORIA, PREDICATO, SOTTOPREDICATO o VALORE. |
| `codice` | VARCHAR(40) | no |  |  | Codice ufficiale del documento ACN (es. TT:MA_RA), univoco nella versione. |
| `slug_misp` | VARCHAR(120) | sì |  |  | Etichetta del machinetag MISP, per l'interoperabilità; non univoca. |
| `nome` | VARCHAR(255) | no |  |  | Nome della voce come nel documento ufficiale. |
| `descrizione` | VARCHAR(2000) | sì |  |  | Definizione della voce, se presente nella fonte. |
| `sezione` | VARCHAR(20) | sì |  |  | Sezione del documento ACN in cui compare il codice. |
| `ordine` | INTEGER | no |  |  | Posizione fra le voci sorelle, per presentare l'albero nell'ordine del documento. |

Vincoli:

- univocità di (`versione_id`, `codice`)
- controllo `"livello" IN('MACROCATEGORIA', 'PREDICATO', 'SOTTOPREDICATO', 'VALORE')`

## utente

Utente dell'applicazione con il suo profilo (ADR-0009).

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `username` | VARCHAR(50) | no | UK |  | Nome utente, usato negli autori dei documenti e nell'audit. |
| `password_hash` | VARCHAR(100) | no |  |  | Hash della password (BCrypt con prefisso {bcrypt}). |
| `nome_completo` | VARCHAR(255) | no |  |  | Nome e cognome o funzione. |
| `ruolo` | VARCHAR(20) | no |  |  | OPERATORE (redige e invia in approvazione) o APPROVATORE (respinge e registra la trasmissione). |
| `attivo` | BOOLEAN | no |  | `TRUE` | Falso per un utente disabilitato. |

Vincoli:

- controllo `"ruolo" IN('OPERATORE', 'APPROVATORE')`

## valutazione_significativita

Storico delle valutazioni di significatività: input, soglie usate ed esito restano anche dopo una riclassificazione.

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `incidente_id` | BIGINT | no | FK → `incidente.id` |  | Incidente valutato. |
| `eseguita_il` | TIMESTAMP WITH TIME ZONE | no |  |  | Istante della valutazione. |
| `eseguita_da` | VARCHAR(50) | no |  |  | Utente che ha eseguito la valutazione. |
| `soglia_set_id` | BIGINT | no | FK → `soglia_set.id` |  | Insieme di soglie vigente alla data della conoscenza. |
| `servizio` | VARCHAR(40) | no |  |  | Servizio coinvolto, di cui si usano livelli di servizio e utenti. |
| `input_json` | VARCHAR(10000) | no |  |  | Grandezze inserite (minuti, utenti, perdite, compromissioni) in JSON. |
| `significativo` | BOOLEAN | no |  |  | Esito: vero se scatta almeno una regola R o una categoria IS (ADR-0004). |
| `regole_attivate` | VARCHAR(100) | no |  |  | Regole R1-R8 del regolamento soddisfatte, separate da virgola. |
| `categorie_is` | VARCHAR(100) | no |  |  | Categorie IS-1..IS-4 dell'ACN soddisfatte, separate da virgola. |

## versione_autore

Autori di una versione, cioè chi ne ha scritto contenuto o classificazione: nessuno di loro può registrarne la trasmissione (quattro occhi, ADR-0009).

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `versione_documento_id` | BIGINT | no | PK, FK → `versione_documento.id` |  | Versione del documento. |
| `username` | VARCHAR(50) | no | PK |  | Utente autore. |

## versione_documento

Versione del documento di un adempimento: bozza, in approvazione o trasmessa. Una versione trasmessa è immutabile e sigillata nella catena di hash dell'incidente (ADR-0006, ADR-0007).

| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | no | PK |  | Identificativo. |
| `adempimento_id` | BIGINT | no | FK → `adempimento.id` |  | Adempimento a cui appartiene la versione. |
| `numero` | INTEGER | no |  |  | Numero della versione nell'adempimento. |
| `stato` | VARCHAR(20) | no |  |  | BOZZA, IN_APPROVAZIONE o TRASMESSO (macchina a stati StatoDocumento). |
| `contenuto_json` | VARCHAR(200000) | no |  |  | Contenuto del documento in JSON canonico (ContenutoDocumento); i campi previsti dipendono dall'adempimento. |
| `tassonomia_versione_id` | BIGINT | no | FK → `tassonomia_versione.id` |  | Versione della tassonomia vigente alla creazione della versione. |
| `derivata_da_id` | BIGINT | sì | FK → `versione_documento.id` |  | Versione trasmessa da cui sono stati ripresi i dati, o che la rettifica corregge. |
| `creata_il` | TIMESTAMP WITH TIME ZONE | no |  |  | Istante di creazione. |
| `creata_da` | VARCHAR(50) | no |  |  | Utente che ha creato la versione. |
| `modificata_il` | TIMESTAMP WITH TIME ZONE | no |  |  | Ultima modifica. |
| `modificata_da` | VARCHAR(50) | no |  |  | Autore dell'ultima modifica. |
| `inviata_approvazione_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Ultimo invio in approvazione. |
| `inviata_approvazione_da` | VARCHAR(50) | sì |  |  | Chi ha inviato in approvazione: non può registrarne la trasmissione. |
| `nota_respingimento` | VARCHAR(2000) | sì |  |  | Nota dell'approvatore all'ultimo respingimento. |
| `approvata_da` | VARCHAR(50) | sì |  |  | Approvatore che ha registrato la trasmissione. |
| `trasmessa_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Istante di invio sul portale ACN dichiarato dall'approvatore. |
| `riferimento_trasmissione` | VARCHAR(255) | sì |  |  | Riferimento ricevuto dal portale ACN. |
| `impronta_sha256` | VARCHAR(64) | sì | UK |  | Impronta SHA-256 del documento trasmesso, anello della catena. |
| `impronta_precedente` | VARCHAR(64) | sì | UK |  | Impronta dell'anello precedente (o genesi dal codice incidente): univoca, la catena non si biforca. |
| `versione` | BIGINT | no |  | `0` | Contatore per il controllo ottimistico delle modifiche concorrenti. |
| `motivo_rettifica` | VARCHAR(2000) | sì |  |  | Motivo della rettifica; valorizzato solo per le versioni che correggono un documento trasmesso. |
| `trasmissione_registrata_il` | TIMESTAMP WITH TIME ZONE | sì |  |  | Istante, dall'orologio del server, in cui la trasmissione è stata registrata: fissa l'ordine della catena. |
| `intestazione_json` | VARCHAR(4000) | sì |  |  | Soggetto e incidente come erano all'invio, sigillati nell'impronta (copia conforme). |
| `schema_impronta` | VARCHAR(20) | sì |  |  | Schema dell'input dell'impronta (pw29-catena-v1 o pw29-catena-v2), per verificare ogni anello con le sue regole. |

Vincoli:

- univocità di (`adempimento_id`, `numero`)
- controllo `"stato" IN('BOZZA', 'IN_APPROVAZIONE', 'TRASMESSO')`
- controllo `("stato" <> 'TRASMESSO') OR ("schema_impronta" = 'pw29-catena-v1') OR (("schema_impronta" = 'pw29-catena-v2') AND ("intestazione_json" IS NOT NULL))`
- controllo `("stato" <> 'TRASMESSO') OR (("impronta_sha256" IS NOT NULL) AND ("impronta_precedente" IS NOT NULL) AND ("trasmissione_registrata_il" IS NOT NULL))`
- controllo `("stato" <> 'TRASMESSO') OR (("trasmessa_il" IS NOT NULL) AND ("approvata_da" IS NOT NULL) AND ("riferimento_trasmissione" IS NOT NULL))`
