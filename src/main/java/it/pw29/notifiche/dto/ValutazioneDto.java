package it.pw29.notifiche.dto;

import java.time.Instant;
import java.util.List;

public record ValutazioneDto(Long id, Instant eseguitaIl, String eseguitaDa, String versioneSoglie, String servizio,
                             boolean significativo, List<String> regoleAttivate, List<String> categorieIs) {
}
