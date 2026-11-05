package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.StoricoRiclassificazione;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoricoRiclassificazioneRepository extends JpaRepository<StoricoRiclassificazione, Long> {

    List<StoricoRiclassificazione> findByIncidenteIdOrderByRegistrataIlAscIdAsc(Long incidenteId);

    /** Riclassificazioni non ancora comunicate al CSIRT Italia con un documento trasmesso. */
    List<StoricoRiclassificazione> findByIncidenteIdAndDocumentoDopoIsNullOrderByRegistrataIlAscIdAsc(Long incidenteId);
}
