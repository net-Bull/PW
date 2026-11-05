package it.pw29.notifiche.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Registrazione della trasmissione al CSIRT Italia, effettuata dal referente CSIRT nella maschera dei Servizi NIS
 * del Portale dei Servizi ACN (https://portale.acn.gov.it/), come indica il Portale Segnalazioni (verifica del
 * 23/10/2026). Le Linee guida NIS ACN v1.1 (par. 2.3.1) indicavano ancora https://segnalazioni.acn.gov.it/.
 *
 * @param riferimento identificativo della segnalazione restituito dal portale
 * @param trasmessaIl istante di invio sul portale (predefinito: adesso)
 */
public record TrasmissioneRequest(@NotBlank @Size(max = 255) String riferimento, Instant trasmessaIl) {
}
