package it.pw29.notifiche.service;

import it.pw29.notifiche.dto.EsitoVerifica;

/** Documento incompleto per l'adempimento: porta l'elenco dei campi mancanti (HTTP 422). */
public class DocumentoNonValidoException extends RuntimeException {

    private final transient EsitoVerifica esito;

    public DocumentoNonValidoException(EsitoVerifica esito) {
        super("Il documento non è completo per l'adempimento " + esito.adempimento()
                + ": " + esito.violazioni().size() + " problemi");
        this.esito = esito;
    }

    public EsitoVerifica getEsito() { return esito; }
}
