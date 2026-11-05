package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.StatoScadenza;
import it.pw29.notifiche.domain.TipoAdempimento;

import java.time.Instant;

/**
 * Scadenza di un adempimento, calcolata dai fatti dell'incidente.
 *
 * @param dovutaEntro termine; nullo se non ancora determinabile
 * @param nota spiegazione del calcolo, con il riferimento normativo
 */
public record Scadenza(
        TipoAdempimento tipo,
        int progressivo,
        Instant dovutaEntro,
        Instant trasmessaIl,
        StatoScadenza stato,
        String nota
) {
}
