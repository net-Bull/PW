# Criteri di significatività: fonti ufficiali e regole adottate

Per ogni criterio delle fonti ufficiali: come è tradotto nel modello, oppure perché non lo è.
Fonti: Reg. di esecuzione (UE) 2024/2690, testo italiano su EUR-Lex (artt. 3, 4, 10; considerando 30-34);
Det. ACN 379907/2025, allegati 3 e 4, nel testo delle Linee guida NIS v1.1, par. 2.3.1. Le soglie sono dati
(`soglia_set`, `livello_servizio`), le regole sono in `RegoleSignificativita` e `CriteriIncidentiSignificativi`.

## Reg. (UE) 2024/2690

| Criterio ufficiale | Traduzione nel modello | Motivazione |
| --- | --- | --- |
| Art. 3, par. 1, lett. a): perdita finanziaria diretta, anche potenziale, > min(500.000 EUR; 5% del fatturato dell'esercizio precedente) | **R5**, soglia 350.000 EUR | Il campo `perditaDiretta` comprende la perdita stimata ("in grado di causare") |
| Art. 3, par. 1, lett. b): esfiltrazione, anche potenziale, di segreti commerciali (dir. 2016/943) | **R8**, indicatore `esfiltrazioneSegretiCommerciali` | Non richiede dolo né soglie, quindi non è assorbibile in R3 (che vale solo per i dati dei servizi gestiti e richiede la sospetta azione malevola) |
| Art. 3, par. 1, lett. c) e d): decesso o danni considerevoli alla salute di una persona fisica | **Non tradotti in regole** | I servizi di un MSSP non hanno effetti diretti sull'integrità fisica; l'effetto indiretto (es. cliente sanitario) ricade negli obblighi del cliente (FAQ ACN ISB.F.2). Se il contesto cambiasse si aggiungerebbero come indicatori, come R8 |
| Art. 3, par. 1, lett. e): accesso non autorizzato, che si sospetta malevolo, in grado di causare gravi perturbazioni operative | **R6** | Tre indicatori in AND: accesso, sospetto doloso, grave perturbazione |
| Art. 3, par. 1, lett. f) e art. 4: incidenti ricorrenti (almeno due in sei mesi, stessa causa apparente, insieme oltre la soglia della lett. a) | **R7** | La ricorrenza è un dato dichiarato dall'operatore; il collegamento automatico fra incidenti è uno sviluppo futuro |
| Art. 3, par. 2: interruzioni e manutenzioni programmate non sono incidenti significativi | Indicatore `interruzioneProgrammata`: esclude **R1**, **R2** e **IS-3** | Considerando 33. Non esclude i criteri sui dati (una compromissione durante una manutenzione non ne è una "conseguenza prevista") |
| Art. 3, par. 3: utenti = clienti con contratto + persone associate ai clienti business | `livello_servizio.utenti_ue` per servizio | 140 clienti business; 9.000 utenti del SOC/MDR, 5.400 del WAF gestito. Se il numero non è noto si indica la stima del massimo (considerando 32) |
| Art. 10, lett. a): servizio gestito o di sicurezza gestito completamente indisponibile per più di 30 minuti | **R1**, solo per servizi con `servizio_gestito` | Il portale clienti/ticketing è un canale di comunicazione, non il servizio di sicurezza gestito: per esso valgono IS-3 (SL 120/240) e i criteri orizzontali |
| Art. 10, lett. b): disponibilità limitata per più di un'ora per oltre il 5% degli utenti **di tale servizio** (o oltre 1 milione) | **R2**, soglia per servizio: SOC/MDR 450, WAF 270 | Prima dell'audit la soglia era unica (9.000 utenti): corretta perché il testo si riferisce agli utenti del servizio |
| Art. 10, lett. c): dati del servizio compromessi da un'azione che si sospetta malevola | **R3** | – |
| Art. 10, lett. d): dati del servizio compromessi per oltre il 5% degli utenti del servizio (o oltre 1 milione) | **R4**, soglia per servizio | – |
| Considerando 30: i criteri del regolamento sono esaustivi | Unione con le categorie IS (ADR-0004) | Le FAQ ACN chiedono di notificare le fattispecie IS senza distinguere i soggetti del regolamento; l'unione è la lettura prudente. La tesi contraria (solo il regolamento per i soggetti che vi rientrano) è discussa nell'ADR-0004 |

## Det. ACN 379907/2025 (allegati 3 e 4)

| Criterio ufficiale | Traduzione nel modello | Motivazione |
| --- | --- | --- |
| IS-1: perdita di riservatezza, verso l'esterno, di dati digitali | **IS-1**: `perditaRiservatezza` e `impattoEsterno` | – |
| IS-2: perdita di integrità, con impatto verso l'esterno | **IS-2**: `perditaIntegrita` e `impattoEsterno` | – |
| IS-3: violazione dei livelli di servizio attesi (SL, misura DE.CM-01) | **IS-3**: superamento degli SL del servizio coinvolto (`livello_servizio`) | SL definiti in autonomia dal soggetto; un'interruzione programmata non li viola |
| IS-4: accesso non autorizzato o con abuso dei privilegi (solo soggetti essenziali) | **IS-4** solo se `soggetto.tipo = ESSENZIALE` | CyberWall è un soggetto importante: IS-4 è implementata e testata ma non si attiva |
| Evidenza dell'incidente (Linee guida NIS v1.1; FAQ ISB.G.4) | `conoscenzaIl` registrata una volta, con motivazione (ADR-0003) | Coerente con il considerando 31 del Reg. 2024/2690 ("ragionevolmente certo") |
