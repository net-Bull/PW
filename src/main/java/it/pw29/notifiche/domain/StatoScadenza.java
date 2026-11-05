package it.pw29.notifiche.domain;

/** Stato di una scadenza rispetto all'istante corrente. */
public enum StatoScadenza {
    /** Trasmesso entro il termine. */
    RISPETTATA,
    /** Trasmesso oltre il termine. */
    TARDIVA,
    /** Termine superato senza trasmissione. */
    SCADUTA,
    /** Termine vicino (entro il preavviso dell'adempimento). */
    IN_SCADENZA,
    /** Termine futuro. */
    APERTA,
    /** Termine non ancora determinabile (es. finale prima della notifica, intermedia senza scadenza). */
    IN_ATTESA
}
