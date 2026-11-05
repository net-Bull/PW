package it.pw29.notifiche.scenari;

import it.pw29.notifiche.domain.AuditLog;
import it.pw29.notifiche.domain.CatenaImpronte;
import it.pw29.notifiche.domain.DocumentoImmutabileException;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.repository.AuditLogRepository;
import it.pw29.notifiche.repository.VersioneDocumentoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Immutabilità verificata al livello della persistenza: anche aggirando i metodi di dominio
 * (scrittura diretta di un campo), una versione TRASMESSA non si aggiorna né si cancella, e il
 * registro di audit non si modifica.
 */
@SpringBootTest
class ImmutabilitaPersistenzaTest {

    @Autowired VersioneDocumentoRepository versioni;
    @Autowired AuditLogRepository audit;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    /** Crea via SQL un incidente con una versione già trasmessa, senza passare dai servizi. */
    private Long versioneTrasmessa() {
        jdbc.update("""
                INSERT INTO incidente (codice, soggetto_id, titolo, descrizione, stato, rilevato_il, registrato_il,
                                       registrato_da, conoscenza_il, conoscenza_motivazione, conoscenza_registrata_il)
                VALUES ('IMM-' || SUBSTRING(CAST(RANDOM_UUID() AS VARCHAR), 1, 12), 1, 't', 'd', 'SIGNIFICATIVO', TIMESTAMP WITH TIME ZONE '2026-11-01 10:00:00+00',
                        TIMESTAMP WITH TIME ZONE '2026-11-01 10:05:00+00', 'op', TIMESTAMP WITH TIME ZONE '2026-11-01 10:01:00+00',
                        'm', TIMESTAMP WITH TIME ZONE '2026-11-01 10:05:00+00')""");
        Long inc = jdbc.queryForObject("SELECT MAX(id) FROM incidente", Long.class);
        jdbc.update("INSERT INTO adempimento (incidente_id, tipo, progressivo, stato) VALUES (?, 'PRE_NOTIFICA', 1, 'TRASMESSO')", inc);
        Long ad = jdbc.queryForObject("SELECT MAX(id) FROM adempimento", Long.class);
        jdbc.update("""
                INSERT INTO versione_documento (adempimento_id, numero, stato, contenuto_json, tassonomia_versione_id,
                    creata_il, creata_da, modificata_il, modificata_da, approvata_da, trasmessa_il, riferimento_trasmissione,
                    trasmissione_registrata_il, impronta_sha256, impronta_precedente)
                VALUES (?, 1, 'TRASMESSO', '{}', 1, CURRENT_TIMESTAMP, 'op', CURRENT_TIMESTAMP, 'op', 'app',
                        CURRENT_TIMESTAMP, 'ACN-1', CURRENT_TIMESTAMP, ?, ?)""", ad,
                CatenaImpronte.sha256(UUID.randomUUID().toString()), CatenaImpronte.sha256(UUID.randomUUID().toString()));
        return jdbc.queryForObject("SELECT MAX(id) FROM versione_documento", Long.class);
    }

    private static boolean causataDa(Throwable t, Class<? extends Throwable> tipo) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (tipo.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("Versione trasmessa: scrittura diretta di un campo bloccata dal callback @PreUpdate")
    void aggiornamentoBloccato() {
        Long id = versioneTrasmessa();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            VersioneDocumento v = versioni.findById(id).orElseThrow();
            ReflectionTestUtils.setField(v, "riferimentoTrasmissione", "MODIFICATO");
            versioni.flush();
        })).satisfies(t -> assertThat(causataDa(t, DocumentoImmutabileException.class)).isTrue());
        assertThat(jdbc.queryForObject("SELECT riferimento_trasmissione FROM versione_documento WHERE id = ?",
                String.class, id)).isEqualTo("ACN-1");
    }

    @Test
    @DisplayName("Versione trasmessa: cancellazione bloccata dal callback @PreRemove")
    void cancellazioneBloccata() {
        Long id = versioneTrasmessa();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            versioni.delete(versioni.findById(id).orElseThrow());
            versioni.flush();
        })).satisfies(t -> assertThat(causataDa(t, DocumentoImmutabileException.class)).isTrue());
        assertThat(versioni.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("Registro di audit: una voce salvata non si modifica")
    void auditImmutabile() {
        Long id = tx.execute(s -> audit.save(new AuditLog(Instant.now(), "test", "PROVA", null, null, "originale")).getId());
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            AuditLog a = audit.findByAzioneOrderByIdAsc("PROVA").get(0);
            ReflectionTestUtils.setField(a, "dettaglio", "alterato");
        })).satisfies(t -> assertThat(causataDa(t, IllegalStateException.class)).isTrue());
        assertThat(jdbc.queryForObject("SELECT dettaglio FROM audit_log WHERE id = ?", String.class, id))
                .isEqualTo("originale");
    }
}
