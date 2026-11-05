package it.pw29.notifiche.repository;

import it.pw29.notifiche.domain.LivelloTassonomia;
import it.pw29.notifiche.domain.TassonomiaVoce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TassonomiaVoceRepository extends JpaRepository<TassonomiaVoce, Long> {

    /**
     * Query di conteggio per livello di una versione: è la query da riportare in relazione.
     * SQL equivalente:
     * SELECT livello, COUNT(*) FROM tassonomia_voce WHERE versione_id = ? GROUP BY livello;
     */
    @Query("select v.livello as livello, count(v) as totale from TassonomiaVoce v "
            + "where v.versione.id = :versioneId group by v.livello")
    List<ConteggioLivello> contaPerLivello(@Param("versioneId") Long versioneId);

    /** Voci di una versione per codice ufficiale (es. "BC:SE_HI"), usata per classificare e per il riuso. */
    List<TassonomiaVoce> findByVersioneIdAndCodiceIn(Long versioneId, Collection<String> codici);

    /** Tutte le voci di una versione, per costruire l'albero (circa 260 righe). */
    List<TassonomiaVoce> findByVersioneIdOrderByOrdineAscIdAsc(Long versioneId);

    interface ConteggioLivello {
        LivelloTassonomia getLivello();
        long getTotale();
    }
}
