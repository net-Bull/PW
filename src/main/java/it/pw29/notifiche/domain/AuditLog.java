package it.pw29.notifiche.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Voce del registro di audit. Solo inserimento: aggiornamento e cancellazione sono bloccati. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant istante;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 50)
    private String azione;

    @Column(length = 50)
    private String entita;

    @Column(name = "entita_id")
    private Long entitaId;

    @Column(length = 2000)
    private String dettaglio;

    protected AuditLog() {
    }

    public AuditLog(Instant istante, String username, String azione, String entita, Long entitaId, String dettaglio) {
        this.istante = istante;
        this.username = username;
        this.azione = azione;
        this.entita = entita;
        this.entitaId = entitaId;
        this.dettaglio = dettaglio == null || dettaglio.length() <= 2000 ? dettaglio : dettaglio.substring(0, 2000);
    }

    @PreUpdate
    @PreRemove
    void soloInserimento() {
        throw new IllegalStateException("Il registro di audit non è modificabile");
    }

    public Long getId() { return id; }
    public Instant getIstante() { return istante; }
    public String getUsername() { return username; }
    public String getAzione() { return azione; }
    public String getEntita() { return entita; }
    public Long getEntitaId() { return entitaId; }
    public String getDettaglio() { return dettaglio; }
}
