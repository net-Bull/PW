package it.pw29.notifiche.service;

public class RisorsaNonTrovataException extends RuntimeException {

    public RisorsaNonTrovataException(String risorsa, Object id) {
        super(risorsa + " " + id + " non trovato");
    }
}
