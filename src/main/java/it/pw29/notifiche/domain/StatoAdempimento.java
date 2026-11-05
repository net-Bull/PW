package it.pw29.notifiche.domain;

/** Stato sintetico di un adempimento, derivato dalle sue versioni. */
public enum StatoAdempimento {
    /** Obbligo sorto, nessuna bozza ancora creata. */
    DA_REDIGERE,
    /** Esiste una versione in bozza o in approvazione. */
    IN_LAVORAZIONE,
    /** Una versione è stata trasmessa al CSIRT Italia. */
    TRASMESSO
}
