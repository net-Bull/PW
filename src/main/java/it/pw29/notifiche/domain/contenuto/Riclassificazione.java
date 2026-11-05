package it.pw29.notifiche.domain.contenuto;

import java.time.Instant;
import java.util.List;

/**
 * Riclassificazione esposta nei documenti successivi (traccia PW 29, punto 3): classificazione
 * precedente e nuova, momento e motivazione del cambiamento. È compilata dal sistema a partire
 * dallo storico delle riclassificazioni, non dall'operatore: così non può divergere da esso.
 *
 * @param riferimentoDocumentoPrima riferimento di trasmissione (portale ACN) dell'ultimo documento
 *        inviato con la classificazione precedente
 */
public record Riclassificazione(
        Instant registrataIl,
        String motivazione,
        boolean significativoPrima,
        boolean significativoDopo,
        List<String> categorieIsPrima,
        List<String> categorieIsDopo,
        List<String> vociPrima,
        List<String> vociDopo,
        String riferimentoDocumentoPrima
) {
}
