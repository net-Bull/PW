package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.LivelloServizio;
import it.pw29.notifiche.domain.TipoSoggetto;
import it.pw29.notifiche.dto.ValutazioneRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Categorie di incidente significativo di base IS-1..IS-4 (Det. ACN 379907/2025, allegati 3 e 4).
 * Logica pura come {@link RegoleSignificativita}: riceve le grandezze, il tipo di soggetto e il
 * livello di servizio atteso del servizio coinvolto.
 * <ul>
 *   <li>IS-1: perdita di riservatezza con effetti verso l'esterno;</li>
 *   <li>IS-2: perdita di integrità con impatto verso l'esterno;</li>
 *   <li>IS-3: superamento dei livelli di servizio attesi (SL) definiti dal soggetto (DE.CM-01);
 *       confronto strettamente maggiore, come per le soglie del regolamento. Un'interruzione programmata
 *       non viola gli SL (scelta coerente con l'art. 3, par. 2, del Reg. 2024/2690);</li>
 *   <li>IS-4: accesso non autorizzato o con abuso di privilegi, solo per i soggetti essenziali.</li>
 * </ul>
 */
@Component
public class CriteriIncidentiSignificativi {

    public List<CategoriaIs> valuta(ValutazioneRequest in, TipoSoggetto tipo, LivelloServizio sl) {
        List<CategoriaIs> attivate = new ArrayList<>();
        if (in.perditaRiservatezza() && in.impattoEsterno()) {
            attivate.add(CategoriaIs.IS_1);
        }
        if (in.perditaIntegrita() && in.impattoEsterno()) {
            attivate.add(CategoriaIs.IS_2);
        }
        if (!in.programmata() && (in.minutiIndisponibilitaTotale() > sl.getMaxIndisponibilitaMinuti()
                || in.minutiDegrado() > sl.getMaxDegradoMinuti())) {
            attivate.add(CategoriaIs.IS_3);
        }
        if (in.accessoNonAutorizzato() && CategoriaIs.IS_4.applicabileA(tipo)) {
            attivate.add(CategoriaIs.IS_4);
        }
        return List.copyOf(attivate);
    }
}
