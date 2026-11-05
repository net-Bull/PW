package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.StatoIncidente;

import java.time.Instant;
import java.util.List;

/**
 * Riga dello storico delle riclassificazioni: classificazione prima e dopo, con i documenti che
 * delimitano il cambiamento (ultimo trasmesso prima, primo trasmesso dopo).
 *
 * @param documentoDopoId nullo finché la riclassificazione non è comunicata con un nuovo invio
 */
public record RiclassificazioneDto(
        Long id, Long incidenteId, Instant registrataIl, String registrataDa, String motivazione,
        StatoIncidente statoPrima, StatoIncidente statoDopo,
        boolean significativoPrima, boolean significativoDopo,
        List<String> categorieIsPrima, List<String> categorieIsDopo,
        List<String> vociPrima, List<String> vociDopo,
        Long valutazioneId,
        Long documentoPrimaId, String documentoPrimaRiferimento,
        Long documentoDopoId, String documentoDopoRiferimento,
        boolean comunicata
) {
}
