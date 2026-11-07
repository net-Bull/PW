# ADR-0009: Autenticazione separata per API e interfaccia, quattro occhi sugli autori

- **Stato**: accettata (introdotta con la revisione di fine fase 3)

## Contesto
L'applicazione ha due punti d'ingresso: le API REST, chiamate con HTTP Basic, e l'interfaccia web della
Fase 4, con login a form e sessione. Nella prima versione una sola catena di filtri gestiva entrambi e
disattivava il CSRF per percorso (`/api/**`): la revisione ha mostrato che, dopo il login a form, una
`POST /api/incidenti` con il solo cookie di sessione veniva accettata (HTTP 201). Un sito terzo poteva
quindi far eseguire a un utente collegato operazioni con effetti, trasmissione compresa.

La stessa revisione ha mostrato che il principio dei quattro occhi si aggirava: l'approvatore, che eredita i
permessi dell'operatore, poteva scrivere il testo di una bozza, farla inviare in approvazione da un
operatore e poi trasmetterla lui stesso, perché il controllo guardava solo chi l'aveva inviata.

## Alternative valutate
1. **CSRF attivo anche sulle API** – protegge, ma obbliga i client delle API (script, test, integrazioni)
   a ottenere e rimandare un token pensato per i browser; complica senza motivo un'interfaccia senza stato.
2. **Solo cookie `SameSite`** – i browser moderni non inviano il cookie su richieste originate da altri siti,
   ma il comportamento varia (es. eccezioni temporanee per i cookie senza attributo esplicito) e resta
   affidato al client: non è una protezione lato server.
3. **Due catene di filtri** – API solo HTTP Basic e senza sessione (il contesto non si legge né si salva in
   sessione, la risposta 401 non contiene `WWW-Authenticate` così il browser non memorizza credenziali);
   interfaccia con form, sessione, CSRF e cookie `SameSite=Strict`.

Per i quattro occhi: (a) vietare all'approvatore di scrivere bozze, rompendo la gerarchia dei ruoli;
(b) confrontare l'approvatore con l'ultimo autore della modifica; (c) registrare **tutti** gli autori della
versione e vietare la trasmissione a ciascuno di loro e a chi l'ha inviata in approvazione.

## Decisione
Alternativa 3 per l'autenticazione, (c) per i quattro occhi.
- `SicurezzaConfig`: catena `@Order(1)` con `securityMatcher("/api/**")`, `SessionCreationPolicy.STATELESS`,
  `RequestAttributeSecurityContextRepository`, CSRF disattivato; catena `@Order(2)` per tutto il resto con
  login a form e CSRF attivo. Cookie di sessione `SameSite=Strict` in `application.yml`.
- Tabella `versione_autore` (V6): ogni scrittura dell'operatore su contenuto o classificazione aggiunge l'utente
  agli autori; il riallineamento dei campi compilati dal sistema non lo fa. `VersioneDocumento.trasmetti`
  rifiuta (HTTP 403, `QUATTRO_OCCHI`) un approvatore che sia autore o mittente. Gli autori entrano
  nell'impronta della catena (schema `pw29-catena-v2`, ADR-0006).
- Utenti dimostrativi solo nel profilo predefinito (H2) e nel profilo `demo`: il profilo `postgres` da solo
  non li carica.

## Conseguenze
- Test di regressione `RegressioneRevisioneTest`: la sessione da form non autentica le API (401);
  un approvatore autore riceve 403, un secondo approvatore trasmette.
- Chi riclassifica un incidente diventa autore delle bozze riallineate (ne ha deciso la classificazione):
  se è un approvatore, quelle bozze le trasmette un altro.
- Le API restano utilizzabili da qualunque client con credenziali, senza token aggiuntivi.
