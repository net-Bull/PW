# ADR-0010: Interfaccia web resa dal server con Thymeleaf, sugli stessi servizi delle API

- **Stato**: accettata

## Contesto
La traccia chiede un'interfaccia web per la compilazione guidata dei tre adempimenti, con salvataggio in bozza e
riuso dei dati. Gli utenti sono pochi (operatori del SOC e responsabili della sicurezza), lavorano sotto la
pressione dei termini di 24 e 72 ore e devono vedere subito: quale termine scade per primo, quali campi servono
per l'adempimento che stanno redigendo, chi può fare cosa. Le regole (permessi, quattro occhi, completezza,
coerenza della classificazione, immutabilità) sono già tutte nei servizi e coperte da test; l'interfaccia non
deve duplicarle né aggirarle. Vincoli pratici: build riproducibile offline (nessun download durante la build,
né npm né CDN), consegna entro novembre, un solo sviluppatore.

## Alternative valutate
1. **Single page application (React o Angular) sulle API REST** – interazione ricca e separazione netta fra
   client e server. Contro: una seconda toolchain (Node, bundler) e una seconda base di codice; la build smette
   di essere solo Maven e non funziona offline; la sessione del browser andrebbe portata sulle API, che oggi sono
   senza stato e senza CSRF proprio per scelta (ADR-0009); molte regole (campi previsti, azioni ammesse per stato
   e ruolo) andrebbero replicate nel client o esposte con altre API.
2. **Interfaccia generata o di amministrazione (es. pannello CRUD sulle entità)** – rapida da ottenere. Contro:
   espone le tabelle invece del processo; permette modifiche che il dominio vieta (stati, versioni trasmesse);
   non guida l'operatore lungo pre-notifica, notifica e relazione finale come chiede la traccia.
3. **Pagine rese dal server con Thymeleaf, controller web che chiamano gli stessi servizi delle API** – una sola
   toolchain (Maven), nessuna dipendenza esterna a runtime (font e script serviti dall'applicazione), sessione e
   CSRF gestiti da Spring Security nella catena dell'interfaccia. Contro: interazione meno ricca, un ricaricamento
   di pagina per ogni azione; servono poche righe di JavaScript per il conto alla rovescia e per la verifica
   mentre si compila.

## Decisione
Alternativa 3. I controller dell'interfaccia (`controller.web`) stanno nel livello controller e chiamano
esclusivamente i servizi: `@PreAuthorize`, macchine a stati, quattro occhi, campi compilati
dal sistema e audit valgono identici per interfaccia e API. Ogni azione segue lo schema POST-redirect-GET
(`Azioni`): le eccezioni di dominio diventano messaggi per l'operatore con lo stesso significato dei codici HTTP
delle API (422, 409, 403), e i DTO costruiti dai moduli passano dalla stessa Bean Validation dei corpi JSON.

L'editor dei documenti è guidato dalla matrice dei campi generata dal codice (`MatriceCampi.campiModulo`): mostra
solo i campi previsti per l'adempimento, segna gli obbligatori, presenta in sola lettura i campi compilati dal
sistema (`@Campo(compilatoDalSistema = true)`). La verifica mentre si compila usa
`DocumentoService.verificaProvvisoria`, che applica le stesse regole del salvataggio senza scrivere nulla. Il
selettore della tassonomia propone solo i valori (livello VALORE). Lo script della pagina è un miglioramento:
senza JavaScript le pagine funzionano, la verifica avviene al salvataggio.

## Conseguenze
- Una sola build Maven, offline; nessun CDN: le pagine funzionano anche in una rete isolata.
- Le regole non sono duplicate nel client: un'azione non ammessa nell'interfaccia lo è anche nelle API, e
  viceversa. Lo scenario completo è percorso dall'interfaccia in `ScenarioInterfacciaTest` (login a form, CSRF,
  nessuna chiamata alle API).
- L'interfaccia è meno reattiva di una SPA; per gli utenti e il volume previsti è accettabile.
- La catena di filtri dell'interfaccia resta separata da quella delle API (ADR-0009): le API restano senza
  sessione, l'interfaccia ha CSRF e cookie `SameSite=Strict`.
- I campi data e ora hanno la precisione del minuto: il minuto corrente vale "adesso", così un istante proposto
  dal modulo non precede di qualche secondo un evento appena registrato (ad esempio la trasmissione subito dopo
  l'invio in approvazione).
