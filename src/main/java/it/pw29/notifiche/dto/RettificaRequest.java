package it.pw29.notifiche.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Apertura di una rettifica (nuova versione) di un adempimento già trasmesso. */
public record RettificaRequest(@NotBlank @Size(max = 2000) String motivo) {
}
