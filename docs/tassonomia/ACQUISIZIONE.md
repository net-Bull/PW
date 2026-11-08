# Registro di acquisizione della Tassonomia Cyber ACN

| Campo | Valore |
| --- | --- |
| Documento | La Tassonomia Cyber dell'ACN (TC-ACN), guida operativa, TLP:CLEAR |
| Versione | 2.0, novembre 2025 (prima emissione 1.0: novembre 2024) |
| Pagina di pubblicazione | https://www.acn.gov.it/portale/w/la-tassonomia-cyber-dellacn |
| URL del PDF | https://www.acn.gov.it/portale/documents/20119/552690/ACN_Tassonomia_Cyber_CLEAR.pdf/9595cc35-1c0b-4007-07b2-8f0468e5b82e |
| Data di acquisizione | 21 ottobre 2026 |
| Dimensione | 4.528.595 byte, 77 pagine |
| SHA-256 | `13ba8bc27aaa56737f823392a3246461d5393f1b4f325e8109506267451e682b` |
| Last-Modified (server) | Tue, 04 Nov 2025 15:23:00 GMT |
| Metadati PDF | CreationDate e ModDate 2025-11-03 16:39:19 +01:00 |
| Formato strutturato | JSON MISP indicato dal PDF (p. 5): https://github.com/MISP/misp-taxonomies/blob/main/acn/machinetag.json |
| SHA-256 del JSON scaricato | `bb976c578e54e82cd41c8ac7f3cb778a2d8fd3c07ee9f0fa96bf58cf7306153c` |
| Copia allegata | `docs/tassonomia/ACN_Tassonomia_Cyber_CLEAR.pdf` (stessa impronta SHA-256) |

## Verifiche di vigenza

La traccia chiede la tassonomia vigente alla data di consegna: la verifica si ripete a ridosso del caricamento.

| Data | Verifica | Esito |
| --- | --- | --- |
| 21/10/2026 | Acquisizione del PDF e del JSON MISP | v2.0, impronte registrate sopra |
| 21/10/2026 | Controllo: PDF riscaricato dalla pagina ACN (browser), JSON MISP riscaricato da GitHub | Impronte identiche, Last-Modified invariato: v2.0 vigente |
| 23/10/2026 | Copia allegata al repository dalla cartella di download | SHA-256 identico al registro |
| 07/11/2026 | Verifica prima del caricamento: PDF riscaricato dalla pagina ACN (4.528.595 byte, Last-Modified 04/11/2025), JSON MISP riscaricato da GitHub (219 valori, 21 predicati) | Impronte identiche al registro: v2.0 vigente |
| 08/11/2026 | Stessa verifica, ripetuta | Esito invariato: v2.0 vigente |

## Contenuto dichiarato dal documento (cap. 3, p. 11)

219 valori, ognuno appartenente a uno dei 21 predicati, organizzati in 4 macrocategorie;
il solo predicato Involved Asset è suddiviso in 19 sotto-predicati.

## Verifica di coerenza

| Livello | Documento ACN | JSON MISP | Database (query di conteggio) |
| --- | --- | --- | --- |
| Macrocategorie | 4 | non presenti | 4 |
| Predicati | 21 | 21 | 21 |
| Sotto-predicati | 19 | non presenti (nel prefisso dello slug) | 19 |
| Valori | 219 | 219 | 219 |

Query di conteggio:

```sql
SELECT livello, COUNT(*) AS totale
FROM tassonomia_voce
WHERE versione_id = (SELECT id FROM tassonomia_versione WHERE vigente)
GROUP BY livello;
```

Esposta anche da `GET /api/tassonomia/conteggio` e verificata dal test `TassonomiaApiTest`.

## Nota sulla pagina di pubblicazione

La pagina ACN riporta pubblicazione e ultimo aggiornamento al 31/07/2024, ma il PDF allegato è la
versione 2.0 di novembre 2025 (data del file sul server: 4 novembre 2025). La versione vigente va
quindi ricavata dal documento, non dalla data della pagina.
