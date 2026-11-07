# Traccia di esecuzione – Riclassificazione tardiva dopo la notifica (milestone M3)

> File GENERATO dal test `RiclassificazioneTest` con le chiamate API reali dello scenario, orologio simulato e database H2 in memoria. Gli identificativi dipendono dall'ordine di esecuzione dei test; per rigenerarlo: `mvn test -Dtest=RiclassificazioneTest -Dtraccia.aggiorna=true`.
> Orari in Europe/Rome. Impronte SHA-256 abbreviate ai primi 16 caratteri.

## Passi

| # | Istante | Attore | Operazione |
| ---: | --- | --- | --- |
| 1 | 02/11/2026 11:00 | operatore + approvatore | Pre-notifica e notifica trasmesse, ipotesi di guasto del sistema: voci [BC:IM_AV, BC:RO_SY, TT:AV_OU, BC:SE_ME] |

## Prima della riclassificazione

Incidente **INC-2026-0001**, stato **SIGNIFICATIVO**, categorie IS dell'ultima valutazione: ["IS_3"]

| Versione (id) | Adempimento | N. | Stato | Derivata da | Rettifica | Voci | Impronta |
| ---: | --- | ---: | --- | ---: | --- | --- | --- |
| 1 | PRE_NOTIFICA | 1 | TRASMESSO | – | no | BC:IM_AV, BC:RO_SY, TT:AV_OU | d949c633e2bb6501 |
| 2 | NOTIFICA | 1 | TRASMESSO | 1 | no | BC:IM_AV, BC:RO_SY, BC:SE_ME, TT:AV_OU | bdfe597fbd39451f |

## Passi

| # | Istante | Attore | Operazione |
| ---: | --- | --- | --- |
| 2 | 05/11/2026 11:00 | operatore | Bozza della relazione finale (versione 3) creata con la classificazione della notifica |
| 3 | 07/11/2026 11:00 | operatore | Riclassificazione 1: voci [BC:IM_AV, BC:RO_SY, BC:SE_ME, TT:AV_OU] -> [BC:IM_AV, BC:IM_DX, BC:RO_MA, BC:SE_HI, TT:AV_DA, TT:MA_RA]; categorie [IS_3] -> [IS_1, IS_3]. Motivazione: L'analisi forense dell'hypervisor ha accertato la cifratura dei datastore da parte di un ransomware e l'esfiltrazione preventiva degli archivi di log di 38 dei 140 clienti (circa 2.400 utenti del servizio): non si tratta di un guasto |

## Dopo la riclassificazione, prima della relazione finale

Incidente **INC-2026-0001**, stato **SIGNIFICATIVO**, categorie IS dell'ultima valutazione: ["IS_1","IS_3"]

| Versione (id) | Adempimento | N. | Stato | Derivata da | Rettifica | Voci | Impronta |
| ---: | --- | ---: | --- | ---: | --- | --- | --- |
| 1 | PRE_NOTIFICA | 1 | TRASMESSO | – | no | BC:IM_AV, BC:RO_SY, TT:AV_OU | d949c633e2bb6501 |
| 2 | NOTIFICA | 1 | TRASMESSO | 1 | no | BC:IM_AV, BC:RO_SY, BC:SE_ME, TT:AV_OU | bdfe597fbd39451f |
| 3 | RELAZIONE_FINALE | 1 | BOZZA | 2 | no | BC:IM_AV, BC:IM_DX, BC:RO_MA, BC:SE_HI, TT:AV_DA, TT:MA_RA | – |

## Passi

| # | Istante | Attore | Operazione |
| ---: | --- | --- | --- |
| 4 | 07/11/2026 11:00 | operatore | Tentativo di inviare la finale con le voci precedenti: rifiutato (HTTP 422, coerenza con la classificazione comunicata) |
| 5 | 22/11/2026 11:00 | operatore | Conclusione della gestione registrata dopo bonifica e ripristino |
| 6 | 22/11/2026 12:00 | approvatore | Relazione finale (versione 3) trasmessa con la classificazione nuova e la sezione riclassificazioni |

## Dopo la relazione finale

Incidente **INC-2026-0001**, stato **CHIUSO**, categorie IS dell'ultima valutazione: ["IS_1","IS_3"]

| Versione (id) | Adempimento | N. | Stato | Derivata da | Rettifica | Voci | Impronta |
| ---: | --- | ---: | --- | ---: | --- | --- | --- |
| 1 | PRE_NOTIFICA | 1 | TRASMESSO | – | no | BC:IM_AV, BC:RO_SY, TT:AV_OU | d949c633e2bb6501 |
| 2 | NOTIFICA | 1 | TRASMESSO | 1 | no | BC:IM_AV, BC:RO_SY, BC:SE_ME, TT:AV_OU | bdfe597fbd39451f |
| 3 | RELAZIONE_FINALE | 1 | TRASMESSO | 2 | no | BC:IM_AV, BC:IM_DX, BC:RO_MA, BC:SE_HI, TT:AV_DA, TT:MA_RA | 256cd3cbf9e19c88 |

## Storico delle riclassificazioni

| Id | Registrata | Da | Stato prima → dopo | Categorie IS prima → dopo | Voci prima | Voci dopo | Ultimo documento prima (id, rif.) | Documento che la comunica (id, rif.) | Motivazione |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 07/11/2026 11:00 | operatore | SIGNIFICATIVO → SIGNIFICATIVO | IS_3 → IS_1, IS_3 | BC:IM_AV, BC:RO_SY, BC:SE_ME, TT:AV_OU | BC:IM_AV, BC:IM_DX, BC:RO_MA, BC:SE_HI, TT:AV_DA, TT:MA_RA | 2, ACN-M3-02 | 3, ACN-M3-03 | L'analisi forense dell'hypervisor ha accertato la cifratura dei datastore da parte di un ransomware e l'esfiltrazione preventiva degli archivi di log di 38 dei 140 clienti (circa 2.400 utenti del servizio): non si tratta di un guasto |

## Catena di hash

Algoritmo SHA-256 (pw29-catena-v2; ogni anello è verificato con lo schema con cui è stato sigillato), catena **integra**, genesi `9161c51d62ad53d6`.

| Pos. | Versione (id) | Adempimento | N. | Rif. portale | Anello precedente | Impronta | Integro |
| ---: | ---: | --- | ---: | --- | --- | --- | --- |
| 1 | 1 | PRE_NOTIFICA | 1 | ACN-M3-01 | 9161c51d62ad53d6 | d949c633e2bb6501 | sì |
| 2 | 2 | NOTIFICA | 1 | ACN-M3-02 | d949c633e2bb6501 | bdfe597fbd39451f | sì |
| 3 | 3 | RELAZIONE_FINALE | 1 | ACN-M3-03 | bdfe597fbd39451f | 256cd3cbf9e19c88 | sì |

## Documenti prodotti dallo scenario

- [`INC-2026-0001_PRE_NOTIFICA_1_v1.json`](documenti/INC-2026-0001_PRE_NOTIFICA_1_v1.json)
- [`INC-2026-0001_PRE_NOTIFICA_1_v1.pdf`](documenti/INC-2026-0001_PRE_NOTIFICA_1_v1.pdf)
- [`INC-2026-0001_NOTIFICA_1_v1.json`](documenti/INC-2026-0001_NOTIFICA_1_v1.json)
- [`INC-2026-0001_NOTIFICA_1_v1.pdf`](documenti/INC-2026-0001_NOTIFICA_1_v1.pdf)
- [`INC-2026-0001_RELAZIONE_FINALE_1_v1.json`](documenti/INC-2026-0001_RELAZIONE_FINALE_1_v1.json)
- [`INC-2026-0001_RELAZIONE_FINALE_1_v1.pdf`](documenti/INC-2026-0001_RELAZIONE_FINALE_1_v1.pdf)

