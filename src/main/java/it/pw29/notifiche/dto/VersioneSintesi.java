package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.StatoDocumento;

import java.time.Instant;

public record VersioneSintesi(Long id, int numero, StatoDocumento stato, String creataDa, Instant trasmessaIl,
                              String motivoRettifica, String improntaSha256) {
}
