# Tabella delle discrepanze: Tassonomia Cyber ACN v2.0 e modello adottato

Per ogni elemento ufficiale che non si mappa direttamente sul modello dati: voce di origine,
adattamento scelto, motivazione.

| # | Voce di origine | Adattamento | Motivazione |
| --- | --- | --- | --- |
| D1 | La traccia parla di "categorie e sottocategorie"; la TC-ACN ha quattro livelli: macrocategoria, predicato, sotto-predicato, valore | Albero generico `tassonomia_voce` con colonna `livello` e riferimento al padre | Un modello a due livelli perderebbe le macrocategorie e i sotto-predicati; un albero con livello esplicito li rappresenta tutti con una sola tabella e una sola query di conteggio |
| D2 | Le 4 macrocategorie (BC, TT, TA, AC) esistono solo nel PDF, non nel JSON MISP | Caricate da `struttura-pdf-v2.0.json`, dati estratti dal PDF | Senza macrocategorie la classificazione perde il raggruppamento ufficiale usato nel documento |
| D3 | I 19 sotto-predicati di Involved Asset sono solo nel PDF; nel JSON sono un prefisso dello slug (es. `hardware-network-device_router`) | Nodi `SOTTOPREDICATO` fra predicato e valore; nome del valore senza prefisso (es. "Router") | Rende navigabile un predicato da 98 valori e rispecchia la struttura ufficiale |
| D4 | I codici ufficiali dei valori (es. `TT:MA_RA`) sono solo nel PDF; il JSON ha solo slug | Il codice ufficiale è la chiave univoca (`codice`); lo slug MISP è conservato in `slug_misp` | Gli slug non sono univoci: `other` ricorre 15 volte e `italy` 2 volte |
| D5 | Cinque slug di valore coincidono con nomi di predicato (`availability`, `active-scanning`, `brand-abuse`, `data-exposure`, `social-engineering`) | Nessuna identificazione per slug; il codice distingue `BC:IM_AV` (valore) da `TT:AV` (predicato) | Evita ambiguità fra un valore e un predicato con lo stesso nome |
| D6 | Codici anomali nel PDF: `TT:VU_0-` (0-Day Vulnerability) e `TT:VU_N-` (n-Day Vulnerability) | Codici riportati esattamente come nel documento | Il codice ufficiale non va corretto arbitrariamente; l'anomalia è segnalata e il valore resta univoco |
| D7 | Suffisso "Other" non uniforme: `OT` nei predicati, `OTH` nei sotto-predicati, e `AC:IN_OT_OT` per il sotto-predicato Other | Codici mantenuti come nel documento | Coerenza con la fonte ufficiale; nessun impatto sull'univocità |
| D8 | Nel JSON lo slug e il nome di `AC:IN_SW-AP_SSH` hanno uno spazio finale | Nome normalizzato senza spazio; slug conservato tal quale | Il nome è mostrato all'operatore; lo slug serve a confrontarsi con la fonte MISP |
| D9 | Refuso nella fonte: "Intelligent Elettronic Device" (`AC:IN_HW-OT_IED`) | Mantenuto come nel documento | Il nome ufficiale non va corretto; segnalato qui |
| D10 | Descrizioni: in italiano nel PDF, in inglese nel JSON | Caricate le descrizioni inglesi del JSON | Sono l'unica fonte strutturata; trascrivere 219 descrizioni dal PDF introdurrebbe errori |
| D11 | Il JSON dichiara `"version": 1`, il PDF è la versione 2.0 | Versione registrata come 2.0, presa dal documento | Il campo del JSON è la versione del formato MISP, non della tassonomia |
| D12 | Due sotto-predicati hanno il solo valore Other (`HW-VI`, `SW-IT`) | Caricati comunque come sotto-predicati | Rispecchiano la struttura ufficiale; restano disponibili per future estensioni ACN |
| D13 | La pagina ACN indica 31/07/2024, il PDF allegato è la v2.0 di novembre 2025 | Versione e data presi dal documento e dai suoi metadati | Vedi `ACQUISIZIONE.md` |
