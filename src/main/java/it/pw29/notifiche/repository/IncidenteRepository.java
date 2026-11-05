package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.Incidente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {

    long countByCodiceStartingWith(String prefisso);

    List<Incidente> findAllByOrderByRilevatoIlDesc();
}
