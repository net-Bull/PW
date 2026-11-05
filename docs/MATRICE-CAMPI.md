# Matrice campo/adempimento

> File GENERATO dal codice (`MatriceCampi.markdown()`), non modificare a mano.
> Fonte unica: annotazioni `@Campo` di `ContenutoDocumento` ed enum `VincoloClassificazione`.
> Il test `MatriceCampiDocumentazioneTest` fallisce se questo file non coincide con il codice;
> per rigenerarlo: `mvn test -Dtest=MatriceCampiDocumentazioneTest -Dmatrice.aggiorna=true`.

Legenda: **O** obbligatorio, F facoltativo, – non previsto. Riferimenti: art. 25, c. 5, D.Lgs. 138/2024 ("art. 25.5").

## Campi del documento

| Campo | Pre-notifica | Notifica | Relazione intermedia | Relazione mensile sui progressi | Relazione finale | Fonte |
| --- | :---: | :---: | :---: | :---: | :---: | --- |
| Descrizione sintetica dell'incidente (`descrizioneSintetica`) | **O** | **O** | **O** | **O** | **O** | Scelta progettuale: identifica l'evento in modo coerente in tutti gli invii |
| Referente CSIRT - nome (`referenteNome`) | **O** | **O** | **O** | **O** | **O** | Det. ACN 379907/2025, misura GV.RR-02 (referente CSIRT, interlocutore del CSIRT Italia) |
| Referente CSIRT - e-mail (`referenteEmail`) | **O** | **O** | **O** | **O** | **O** | Det. ACN 379907/2025, misura GV.RR-02 |
| Referente CSIRT - telefono (`referenteTelefono`) | **O** | **O** | **O** | **O** | **O** | Det. ACN 379907/2025, misura GV.RR-02 |
| Categorie di incidente significativo (IS-1..IS-4) (`categorieIs`) | F | F | F | F | F | Det. ACN 379907/2025, allegati 3 e 4. Compilato dal sistema dall'ultima valutazione di significatività: vuoto se l'incidente è significativo solo per il Reg. 2024/2690 o se è declassato |
| Criteri del Reg. (UE) 2024/2690 soddisfatti (R1-R8) (`criteriRegolamento`) | F | F | F | F | F | Reg. di esecuzione (UE) 2024/2690, artt. 3, 4 e 10. Compilato dal sistema dall'ultima valutazione: con le categorie IS motiva la significatività (ADR-0004) |
| Servizi impattati (`serviziImpattati`) | **O** | **O** | F | F | **O** | art. 25, c. 1 (impatto significativo sulla fornitura dei servizi) |
| Possibile risultato di atti illegittimi o malevoli (`attoIllegittimoOMalevolo`) | **O** | **O** | – | – | F | art. 25.5 lett. a) "ove possibile", aggiornato con lett. b): ammesso NON_NOTO |
| Impatto transfrontaliero (`impattoTransfrontaliero`) | **O** | **O** | – | – | **O** | art. 25.5 lett. a), b) e d) n. 4 "ove noto": ammesso NON_NOTO |
| Valutazione iniziale (gravità e impatto) (`valutazioneIniziale`) | – | **O** | – | – | – | art. 25.5 lett. b): valutazione iniziale comprensiva di gravità e impatto |
| Utenti impattati (`utentiImpattati`) | – | F | – | F | F | Reg. (UE) 2024/2690, art. 10 e art. 3, par. 3 (conteggio degli utenti); se non è noto, stima del massimo possibile (considerando 32) |
| Indicatori di compromissione (`indicatoriCompromissione`) | – | F | F | F | F | art. 25.5 lett. b) "ove disponibili" |
| Riferimento della richiesta del CSIRT Italia (`riferimentoRichiestaCsirt`) | – | – | **O** | – | – | art. 25.5 lett. c): relazione intermedia su richiesta del CSIRT Italia. Compilato dal sistema dalla richiesta registrata |
| Aggiornamenti sulla situazione / progressi (`aggiornamentiSituazione`) | – | – | **O** | **O** | – | art. 25.5 lett. c) (pertinenti aggiornamenti) e lett. e) (relazione mensile sui progressi) |
| Descrizione dettagliata, con gravità e impatto (`descrizioneDettagliata`) | – | – | – | – | **O** | art. 25.5 lett. d) n. 1 |
| Tipo di minaccia o causa originale (root cause) (`minacciaOCausaOriginale`) | – | – | – | – | **O** | art. 25.5 lett. d) n. 2 |
| Misure di attenuazione adottate e in corso (`misureAttenuazione`) | – | F | F | **O** | **O** | art. 25.5 lett. d) n. 3; per la mensile, lett. e) (progressi) |
| Conclusione della gestione dell'incidente (`conclusioneGestioneIl`) | – | – | – | – | **O** | art. 25.5 lett. d)-e): con l'incidente in corso la finale è sostituita dalla mensile. Compilato dal sistema dalla conclusione registrata sull'incidente (POST /api/incidenti/{id}/conclusione) |
| Riclassificazioni (classificazione precedente e nuova, momento, motivazione) (`riclassificazioni`) | F | F | F | F | F | Traccia PW 29, punto 3. Compilato dal sistema dallo storico: presente se l'incidente è stato riclassificato dopo un invio (ADR-0007) |

## Vincoli sulla classificazione (Tassonomia Cyber ACN)

Valgono sempre: solo voci di livello VALORE; tutte della versione della tassonomia del documento.

| Vincolo | Pre-notifica | Notifica | Relazione intermedia | Relazione mensile sui progressi | Relazione finale | Fonte |
| --- | :---: | :---: | :---: | :---: | :---: | --- |
| Almeno una voce della Tassonomia Cyber ACN | **O** | **O** | – | – | **O** | Traccia PW 29: classificazione vincolata alla tassonomia ACN |
| Esattamente un valore del predicato Severity (BC:SE) | – | **O** | – | – | **O** | art. 25.5 lett. b) e d) n. 1: la valutazione comprende la gravità |
| Almeno un valore di Threat Type (TT) o di Root Cause (BC:RO) | – | – | – | – | **O** | art. 25.5 lett. d) n. 2: tipo di minaccia o causa originale |
