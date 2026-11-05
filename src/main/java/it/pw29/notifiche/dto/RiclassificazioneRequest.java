package it.pw29.notifiche.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Riclassificazione di un incidente già qualificato (ADR-0007).
 *
 * @param valutazione nuova valutazione di significatività, se il cambio riguarda la significatività o le
 *        categorie IS; se assente, significatività e categorie restano quelle correnti. Prima del primo
 *        invio è obbligatoria e deve escludere la significatività (ritorno a non significativo)
 * @param voci nuova classificazione nella Tassonomia Cyber ACN (codici di livello VALORE): obbligatoria
 *        dopo il primo invio; prima, la classificazione si corregge nella bozza
 * @param motivazione perché la classificazione cambia: è riportata nei documenti successivi
 */
public record RiclassificazioneRequest(
        @Valid ValutazioneRequest valutazione,
        List<@NotBlank String> voci,
        @NotBlank @Size(max = 2000) String motivazione
) {
}
