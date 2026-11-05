package it.pw29.notifiche.controller.web;

/** Dato di un modulo dell'interfaccia non interpretabile (es. data o numero scritti male). */
public class ModuloNonValidoException extends RuntimeException {

    public ModuloNonValidoException(String messaggio) {
        super(messaggio);
    }
}
