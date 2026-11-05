package it.pw29.notifiche.domain;

/** Violazione del principio dei quattro occhi: chi ha scritto o inviato in approvazione non può approvare. */
public class QuattroOcchiException extends RuntimeException {

    public QuattroOcchiException(String utente) {
        super("L'utente " + utente + " ha redatto il documento o l'ha inviato in approvazione e non può approvarlo: "
                + "serve un approvatore che non ne sia autore");
    }
}
