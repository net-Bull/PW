package it.pw29.notifiche.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Macchina a stati della versione di un documento: bozza -> in approvazione -> trasmesso.
 * TRASMESSO è terminale: la versione non si modifica più (una correzione è una nuova versione).
 */
public enum StatoDocumento {
    BOZZA,
    IN_APPROVAZIONE,
    TRASMESSO;

    public Set<StatoDocumento> successiviAmmessi() {
        return switch (this) {
            case BOZZA -> EnumSet.of(IN_APPROVAZIONE);
            case IN_APPROVAZIONE -> EnumSet.of(BOZZA, TRASMESSO);
            case TRASMESSO -> EnumSet.noneOf(StatoDocumento.class);
        };
    }

    public boolean puoPassareA(StatoDocumento nuovo) {
        return successiviAmmessi().contains(nuovo);
    }

    public void verificaTransizione(StatoDocumento nuovo) {
        if (!puoPassareA(nuovo)) {
            throw TransizioneNonAmmessaException.da(this, nuovo, "il documento");
        }
    }
}
