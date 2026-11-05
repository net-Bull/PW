package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.LivelloServizio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LivelloServizioRepository extends JpaRepository<LivelloServizio, Long> {

    Optional<LivelloServizio> findBySoggettoIdAndServizio(Long soggettoId, String servizio);

    List<LivelloServizio> findBySoggettoIdOrderByServizio(Long soggettoId);
}
