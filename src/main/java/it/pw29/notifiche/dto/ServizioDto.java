package it.pw29.notifiche.dto;

/**
 * Servizio del soggetto con i livelli di servizio attesi (SL, misura DE.CM-01) e gli utenti nell'Unione su cui
 * si calcola la quota del 5% dell'art. 10 del Reg. 2024/2690. Proposto dall'interfaccia nella qualificazione.
 */
public record ServizioDto(String servizio, String descrizione, int maxIndisponibilitaMinuti, int maxDegradoMinuti,
                          int utentiUe, boolean servizioGestito) {
}
