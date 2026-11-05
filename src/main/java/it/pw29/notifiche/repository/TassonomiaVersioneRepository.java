package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.TassonomiaVersione;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TassonomiaVersioneRepository extends JpaRepository<TassonomiaVersione, Long> {

    Optional<TassonomiaVersione> findByVigenteTrue();
}
