package it.pw29.notifiche.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Macchina a stati dell'incidente. Le transizioni ammesse sono dichiarate qui, in un solo punto,
 * e verificate da test; ogni cambio di stato passa da {@link #verificaTransizione}.
 */
public enum StatoIncidente {
    /** Registrato, significatività non ancora valutata. */
    IN_VALUTAZIONE,
    /**
     * Valutato non significativo: nessun obbligo di notifica. Rivalutabile se emergono nuovi elementi.
     * Vi si arriva anche da SIGNIFICATIVO se, prima di qualunque invio, una nuova valutazione motivata
     * esclude la significatività (riclassificazione prima del primo invio, ADR-0007).
     */
    NON_SIGNIFICATIVO,
    /** Significativo: momento della conoscenza registrato, adempimenti attivi. */
    SIGNIFICATIVO,
    /**
     * Riqualificato non significativo dopo almeno una trasmissione (riclassificazione tardiva).
     * Gli obblighi già sorti restano: il declassamento si comunica con l'invio successivo e la
     * relazione finale chiude l'incidente (ADR-0007).
     */
    DECLASSATO,
    /** Relazione finale trasmessa e gestione conclusa. Stato terminale. */
    CHIUSO;

    public Set<StatoIncidente> successiviAmmessi() {
        return switch (this) {
            case IN_VALUTAZIONE -> EnumSet.of(NON_SIGNIFICATIVO, SIGNIFICATIVO);
            case NON_SIGNIFICATIVO -> EnumSet.of(SIGNIFICATIVO);
            case SIGNIFICATIVO -> EnumSet.of(NON_SIGNIFICATIVO, DECLASSATO, CHIUSO);
            case DECLASSATO -> EnumSet.of(SIGNIFICATIVO, CHIUSO);
            case CHIUSO -> EnumSet.noneOf(StatoIncidente.class);
        };
    }

    /** Incidente con adempimenti attivi: significativo oppure declassato dopo un invio. */
    public boolean inGestione() {
        return this == SIGNIFICATIVO || this == DECLASSATO;
    }

    public boolean puoPassareA(StatoIncidente nuovo) {
        return successiviAmmessi().contains(nuovo);
    }

    public void verificaTransizione(StatoIncidente nuovo) {
        if (!puoPassareA(nuovo)) {
            throw TransizioneNonAmmessaException.da(this, nuovo, "l'incidente");
        }
    }
}
