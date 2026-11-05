package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.SogliaSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SogliaSetRepository extends JpaRepository<SogliaSet, Long> {

    /** Versione delle soglie in vigore alla data indicata (la più recente con validità iniziata). */
    Optional<SogliaSet> findFirstByValidaDalLessThanEqualOrderByValidaDalDesc(LocalDate data);
}
