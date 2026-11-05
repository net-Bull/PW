package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.ValutazioneSignificativita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ValutazioneSignificativitaRepository extends JpaRepository<ValutazioneSignificativita, Long> {

    List<ValutazioneSignificativita> findByIncidenteIdOrderByEseguitaIlAscIdAsc(Long incidenteId);

    Optional<ValutazioneSignificativita> findFirstByIncidenteIdOrderByEseguitaIlDescIdDesc(Long incidenteId);
}
