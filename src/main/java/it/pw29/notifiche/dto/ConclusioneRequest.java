package it.pw29.notifiche.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Conclusione della gestione dell'incidente: sposta il termine della relazione finale (lett. e). */
public record ConclusioneRequest(@NotNull Instant conclusaIl) {
}
