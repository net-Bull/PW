# Traccia di esecuzione – Scenario completo dall'interfaccia web (milestone M4)

> File GENERATO dal test `ScenarioInterfacciaTest`: ogni riga è un modulo inviato dalle pagine Thymeleaf con il login a form e il token CSRF, come dal browser; nessuna chiamata alle API REST. Orologio simulato, database H2 in memoria, orari in Europe/Rome. Per rigenerarlo: `mvn test -Dtest=ScenarioInterfacciaTest -Dtraccia.aggiorna=true`.

Incidente **INC-2026-0002**, stato finale **CHIUSO**.

| # | Ora | Utente | Azione | Richiesta | Pagina successiva | Esito mostrato |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | 02/11/2026 09:00 | operatore | Registra l'incidente | `POST /incidenti` | `/incidenti/2/qualificazione` | Incidente INC-2026-0002 registrato. Ora valutane la significatività. |
| 2 | 02/11/2026 09:00 | operatore | Valuta la significatività | `POST /incidenti/2/qualificazione` | `/incidenti/2` | Incidente significativo (R1, IS-3): da ora decorrono i termini di pre-notifica e notifica. |
| 3 | 02/11/2026 09:00 | operatore | Crea la bozza della pre-notifica | `POST /adempimenti/3/bozza` | `/documenti/2` | Bozza creata con i dati dell'incidente e della valutazione. |
| 4 | 02/11/2026 09:00 | operatore | Compila e invia in approvazione | `POST /documenti/2` | `/documenti/2` | Bozza salvata e inviata in approvazione. |
| 5 | 02/11/2026 09:20 | operatore | Tenta di registrare la trasmissione | `POST /documenti/2/trasmissione` | `/documenti/2` | **Rifiutato:** Operazione non consentita al tuo profilo. |
| 6 | 02/11/2026 09:20 | approvatore | Registra la trasmissione | `POST /documenti/2/trasmissione` | `/documenti/2` | Pre-notifica registrata come trasmessa: documento sigillato con impronta f6921be0…590ca81d. |
| 7 | 02/11/2026 09:20 | operatore | Crea la bozza della notifica (dati ripresi dalla pre-notifica) | `POST /adempimenti/4/bozza` | `/documenti/3` | Bozza creata con i dati dell'ultimo documento trasmesso (versione 2). |
| 8 | 02/11/2026 09:20 | operatore | Invia la notifica senza valutazione iniziale | `POST /documenti/3` | `/documenti/3` | **Rifiutato:** Il documento non è completo per Notifica: compilare i campi indicati. [Valutazione iniziale (gravità e impatto)] |
| 9 | 02/11/2026 09:20 | operatore | Aggiunge la valutazione iniziale e invia | `POST /documenti/3` | `/documenti/3` | Bozza salvata e inviata in approvazione. |
| 10 | 02/11/2026 10:20 | approvatore | Registra la trasmissione della notifica | `POST /documenti/3/trasmissione` | `/documenti/3` | Notifica registrata come trasmessa: documento sigillato con impronta 7b600df6…12719a51. |
| 11 | 05/11/2026 10:20 | operatore | Riclassifica dopo l'analisi forense | `POST /incidenti/2/riclassificazione` | `/incidenti/2` | Riclassificazione registrata: il prossimo documento la comunica con la classificazione prima e dopo. |
| 12 | 15/11/2026 10:20 | operatore | Registra la conclusione senza conferma | `POST /incidenti/2/conclusione` | `/incidenti/2` | **Rifiutato:** La conclusione si registra una sola volta: spuntare la conferma |
| 13 | 15/11/2026 10:20 | operatore | Registra la conclusione della gestione | `POST /incidenti/2/conclusione` | `/incidenti/2` | Conclusione della gestione registrata: il termine della relazione finale è aggiornato. |
| 14 | 15/11/2026 10:20 | operatore | Crea la bozza della relazione finale | `POST /adempimenti/5/bozza` | `/documenti/4` | Bozza creata con i dati dell'ultimo documento trasmesso (versione 3). |
| 15 | 15/11/2026 10:20 | operatore | Compila e invia la relazione finale | `POST /documenti/4` | `/documenti/4` | Bozza salvata e inviata in approvazione. |
| 16 | 15/11/2026 12:20 | approvatore | Registra la trasmissione della finale | `POST /documenti/4/trasmissione` | `/documenti/4` | Relazione finale registrata come trasmessa: documento sigillato con impronta 606cc3b3…074f882e. |

## Termini

| Adempimento | Dovuto entro | Trasmesso | Stato |
| --- | --- | --- | --- |
| Pre-notifica | 03/11/2026 08:50 | 02/11/2026 09:20 | RISPETTATA |
| Notifica | 05/11/2026 08:50 | 02/11/2026 10:20 | RISPETTATA |
| Relazione finale | 02/12/2026 10:20 | 15/11/2026 12:20 | RISPETTATA |
