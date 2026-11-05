package it.pw29.notifiche.dto;

import java.time.Instant;

public record AuditVoce(Long id, Instant istante, String username, String azione, String entita, Long entitaId,
                        String dettaglio) {
}
