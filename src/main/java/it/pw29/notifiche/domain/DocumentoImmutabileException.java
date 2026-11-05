package it.pw29.notifiche.domain;

/** Tentativo di modificare o cancellare una versione di documento già trasmessa. */
public class DocumentoImmutabileException extends RuntimeException {

    public DocumentoImmutabileException(Long versioneId) {
        super("La versione " + versioneId + " è stata trasmessa al CSIRT Italia ed è immutabile");
    }
}
