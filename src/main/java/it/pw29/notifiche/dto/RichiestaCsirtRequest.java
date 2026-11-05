package it.pw29.notifiche.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Richiesta di relazione intermedia ricevuta dal CSIRT Italia (art. 25, c. 5, lett. c). */
public record RichiestaCsirtRequest(
        @NotBlank @Size(max = 255) String riferimento,
        Instant richiestaIl,
        Instant scadenza
) {
}
