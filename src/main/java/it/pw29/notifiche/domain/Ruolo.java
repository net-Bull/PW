package it.pw29.notifiche.domain;

/**
 * Ruoli applicativi. L'approvatore eredita i permessi dell'operatore (gerarchia dei ruoli),
 * ma non può approvare una versione che ha inviato lui stesso in approvazione (principio dei
 * quattro occhi).
 */
public enum Ruolo {
    /** Registra incidenti, redige le bozze e le invia in approvazione. */
    OPERATORE,
    /** Approva e registra la trasmissione al CSIRT Italia, oppure respinge la bozza. */
    APPROVATORE;

    public String authority() {
        return "ROLE_" + name();
    }
}
