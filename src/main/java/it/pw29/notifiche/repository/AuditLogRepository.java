package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.AuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Repository del registro di audit. Estende {@link Repository} e non {@code JpaRepository}:
 * espone solo inserimento e lettura, nessun metodo di modifica o cancellazione.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog voce);

    List<AuditLog> findByEntitaAndEntitaIdOrderByIdAsc(String entita, Long entitaId);

    List<AuditLog> findByAzioneOrderByIdAsc(String azione);
}
