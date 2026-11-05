package it.pw29.notifiche.controller;

import it.pw29.notifiche.domain.DocumentoImmutabileException;
import it.pw29.notifiche.domain.QuattroOcchiException;
import it.pw29.notifiche.domain.TransizioneNonAmmessaException;
import it.pw29.notifiche.dto.EsitoVerifica;
import it.pw29.notifiche.service.DatiNonValidiException;
import it.pw29.notifiche.service.DocumentoNonValidoException;
import it.pw29.notifiche.service.RisorsaNonTrovataException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduzione delle eccezioni di dominio in risposte HTTP:
 * 404 risorsa assente; 409 stato incompatibile (transizione non ammessa, documento immutabile,
 * modifica concorrente); 422 dati incoerenti o documento incompleto (con i campi mancanti);
 * 403 violazione del principio dei quattro occhi. Gli errori di formato restano 400 (Bean Validation).
 */
@RestControllerAdvice(annotations = RestController.class)
public class GestioneErrori {

    public record Errore(String errore, String messaggio) {
    }

    @ExceptionHandler(RisorsaNonTrovataException.class)
    public ResponseEntity<Errore> nonTrovata(RisorsaNonTrovataException e) {
        return risposta(HttpStatus.NOT_FOUND, "RISORSA_NON_TROVATA", e);
    }

    @ExceptionHandler(DocumentoNonValidoException.class)
    public ResponseEntity<EsitoVerifica> documentoIncompleto(DocumentoNonValidoException e) {
        return ResponseEntity.unprocessableEntity().body(e.getEsito());
    }

    @ExceptionHandler(DatiNonValidiException.class)
    public ResponseEntity<Errore> datiNonValidi(DatiNonValidiException e) {
        return risposta(HttpStatus.UNPROCESSABLE_ENTITY, "DATI_NON_VALIDI", e);
    }

    @ExceptionHandler(TransizioneNonAmmessaException.class)
    public ResponseEntity<Errore> transizione(TransizioneNonAmmessaException e) {
        return risposta(HttpStatus.CONFLICT, "TRANSIZIONE_NON_AMMESSA", e);
    }

    @ExceptionHandler(DocumentoImmutabileException.class)
    public ResponseEntity<Errore> immutabile(DocumentoImmutabileException e) {
        return risposta(HttpStatus.CONFLICT, "DOCUMENTO_IMMUTABILE", e);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Errore> concorrenza(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Errore("MODIFICA_CONCORRENTE", "Il dato è stato modificato da un altro utente: ricaricare"));
    }

    /**
     * Violazione di un vincolo del database: tipicamente due trasmissioni concorrenti dello stesso incidente
     * che tentano di agganciarsi allo stesso anello della catena di hash (indice univoco).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Errore> integrita(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Errore("CONFLITTO_DATI", "Operazione in conflitto con un'altra appena eseguita: ricaricare e riprovare"));
    }

    @ExceptionHandler(QuattroOcchiException.class)
    public ResponseEntity<Errore> quattroOcchi(QuattroOcchiException e) {
        return risposta(HttpStatus.FORBIDDEN, "QUATTRO_OCCHI", e);
    }

    private static ResponseEntity<Errore> risposta(HttpStatus stato, String codice, RuntimeException e) {
        return ResponseEntity.status(stato).body(new Errore(codice, e.getMessage()));
    }
}
