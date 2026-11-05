package it.pw29.notifiche.service;

/** Richiesta formalmente corretta ma incoerente con lo stato o le regole del dominio (HTTP 422). */
public class DatiNonValidiException extends RuntimeException {

    public DatiNonValidiException(String messaggio) {
        super(messaggio);
    }
}
