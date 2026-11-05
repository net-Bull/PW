package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.TipoSoggetto;

import java.time.Instant;
import java.util.List;

/**
 * Dettaglio di un incidente con adempimenti e scadenze calcolate.
 *
 * @param minutiRilevazioneConoscenza tempo trascorso fra la rilevazione e la conoscenza: reso
 *        visibile perché una qualificazione tardiva sposta in avanti tutti i termini (ADR-0003)
 */
public record IncidenteDettaglio(
        Long id, String codice, String soggetto, TipoSoggetto tipoSoggetto,
        String titolo, String descrizione, StatoIncidente stato,
        Instant rilevatoIl, Instant registratoIl, String registratoDa,
        Instant conoscenzaIl, String conoscenzaMotivazione, Instant conoscenzaRegistrataIl,
        String conoscenzaRegistrataDa, Long minutiRilevazioneConoscenza,
        Instant conclusioneGestioneIl,
        ValutazioneDto ultimaValutazione,
        List<AdempimentoDto> adempimenti,
        List<Scadenza> scadenze
) {
}
