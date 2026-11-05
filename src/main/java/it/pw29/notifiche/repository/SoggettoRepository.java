package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.Soggetto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SoggettoRepository extends JpaRepository<Soggetto, Long> {

    /** Soggetto predefinito dell'installazione (il primo registrato). */
    Optional<Soggetto> findFirstByOrderByIdAsc();
}
