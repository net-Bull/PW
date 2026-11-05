package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.StatoIncidente;

import java.time.Instant;

public record IncidenteSintesi(Long id, String codice, String titolo, StatoIncidente stato,
                               Instant rilevatoIl, Instant conoscenzaIl, Scadenza prossimaScadenza) {
}
