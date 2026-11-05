package it.pw29.notifiche.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Valutazione di significatività di un incidente registrato. Se l'esito è positivo, conoscenzaIl
 * (predefinito: adesso) e la motivazione diventano il momento della conoscenza, non più modificabile.
 *
 * @param motivazioneConoscenza elementi oggettivi da cui si evince l'incidente significativo
 */
public record QualificazioneRequest(
        @NotNull @Valid ValutazioneRequest valutazione,
        Instant conoscenzaIl,
        @Size(max = 2000) String motivazioneConoscenza
) {
}
