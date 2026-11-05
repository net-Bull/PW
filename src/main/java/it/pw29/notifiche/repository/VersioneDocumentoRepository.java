package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.VersioneDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersioneDocumentoRepository extends JpaRepository<VersioneDocumento, Long> {

    /**
     * Versioni di un incidente in un dato stato. Per lo stato TRASMESSO sono gli anelli della catena
     * di hash: l'ordine di trasmissione si ricostruisce seguendo i collegamenti, non per data (ADR-0006).
     */
    List<VersioneDocumento> findByAdempimentoIncidenteIdAndStato(Long incidenteId, StatoDocumento stato);

    boolean existsByAdempimentoIncidenteIdAndStato(Long incidenteId, StatoDocumento stato);

    /** Altre versioni dell'incidente nello stato indicato (es. in approvazione quando si trasmette la finale). */
    boolean existsByAdempimentoIncidenteIdAndStatoAndIdNot(Long incidenteId, StatoDocumento stato, Long esclusa);
}
