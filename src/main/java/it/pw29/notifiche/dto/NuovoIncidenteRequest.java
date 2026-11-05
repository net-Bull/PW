package it.pw29.notifiche.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Registrazione di un incidente: rilevatoIl è l'istante del primo evento rilevante per la sicurezza. */
public record NuovoIncidenteRequest(
        @NotBlank @Size(max = 255) String titolo,
        @NotBlank @Size(max = 4000) String descrizione,
        @NotNull Instant rilevatoIl,
        Long soggettoId
) {
}
