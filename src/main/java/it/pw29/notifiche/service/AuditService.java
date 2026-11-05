package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.AuditLog;
import it.pw29.notifiche.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Registro di audit delle operazioni rilevanti (registrazione, valutazione, conoscenza, bozze,
 * approvazioni, trasmissioni, accessi falliti). Ogni voce riporta utente, istante, entità e dettaglio.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final UtenteCorrente utente;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, UtenteCorrente utente, Clock clock) {
        this.repository = repository;
        this.utente = utente;
        this.clock = clock;
    }

    /** Registra nella transazione corrente: se l'operazione fallisce, la voce non resta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registra(String azione, String entita, Long entitaId, String dettaglio) {
        repository.save(new AuditLog(Instant.now(clock), utente.username(), azione, entita, entitaId, dettaglio));
    }

    /** Registra in una transazione propria: usato per eventi esterni alle operazioni (es. login fallito). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registraEvento(String username, String azione, String dettaglio) {
        repository.save(new AuditLog(Instant.now(clock), username, azione, null, null, dettaglio));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> perEntita(String entita, Long id) {
        return repository.findByEntitaAndEntitaIdOrderByIdAsc(entita, id);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> perAzione(String azione) {
        return repository.findByAzioneOrderByIdAsc(azione);
    }
}
