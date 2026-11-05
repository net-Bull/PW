package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.CatenaImpronte;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.TransizioneNonAmmessaException;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.dto.AnelloCatena;
import it.pw29.notifiche.dto.VerificaCatena;
import it.pw29.notifiche.repository.IncidenteRepository;
import it.pw29.notifiche.repository.VersioneDocumentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catena di hash SHA-256 dei documenti trasmessi (ADR-0006).
 * <p>
 * L'ordine delle trasmissioni non si ricava dalle date (l'istante di trasmissione è dichiarato e due
 * registrazioni possono avvenire nello stesso istante) ma dai collegamenti: si parte dall'impronta di
 * genesi dell'incidente e si segue, anello dopo anello, il documento che ha come precedente l'impronta
 * corrente. Lo stesso percorso serve a verificare la catena e a trovare l'anello a cui agganciare
 * il prossimo documento.
 */
@Service
public class CatenaService {

    /** Percorso della catena: anelli raggiunti in ordine e versioni trasmesse non raggiungibili. */
    record Percorso(List<VersioneDocumento> anelli, List<VersioneDocumento> fuoriCatena, String ultimaImpronta) {
    }

    private final VersioneDocumentoRepository versioni;
    private final IncidenteRepository incidenti;

    public CatenaService(VersioneDocumentoRepository versioni, IncidenteRepository incidenti) {
        this.versioni = versioni;
        this.incidenti = incidenti;
    }

    @Transactional(readOnly = true)
    public VerificaCatena verifica(Long incidenteId) {
        Incidente inc = incidenti.findById(incidenteId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Incidente", incidenteId));
        return verifica(inc);
    }

    VerificaCatena verifica(Incidente inc) {
        String genesi = CatenaImpronte.genesi(inc.getCodice());
        Percorso p = percorri(inc);
        List<AnelloCatena> anelli = new ArrayList<>();
        String atteso = genesi;
        boolean integra = p.fuoriCatena().isEmpty();
        int posizione = 1;
        for (VersioneDocumento v : p.anelli()) {
            String ricalcolata = CatenaImpronte.impronta(v, v.getImprontaPrecedente());
            boolean contenutoIntegro = ricalcolata.equals(v.getImprontaSha256());
            boolean collegato = atteso.equals(v.getImprontaPrecedente());
            integra &= contenutoIntegro && collegato;
            anelli.add(new AnelloCatena(posizione++, v.getId(), v.getAdempimento().getTipo(),
                    v.getAdempimento().getProgressivo(), v.getNumero(), CatenaImpronte.schemaDi(v), v.isRettifica(), v.getTrasmessaIl(),
                    v.getRiferimentoTrasmissione(), v.getImprontaPrecedente(), v.getImprontaSha256(),
                    ricalcolata, contenutoIntegro, collegato));
            atteso = v.getImprontaSha256();
        }
        int totale = p.anelli().size() + p.fuoriCatena().size();
        return new VerificaCatena(inc.getCodice(), "SHA-256 (" + CatenaImpronte.SCHEMA
                + "; ogni anello è verificato con lo schema con cui è stato sigillato)", integra, genesi,
                p.ultimaImpronta(), totale, anelli,
                p.fuoriCatena().stream().map(VersioneDocumento::getId).sorted().toList());
    }

    /**
     * Anello a cui agganciare il prossimo documento trasmesso. Se la catena non è integra la trasmissione
     * è rifiutata: sigillare un nuovo documento su una catena manomessa ne coprirebbe la manomissione.
     */
    String anelloPerNuovaTrasmissione(Incidente inc) {
        VerificaCatena v = verifica(inc);
        if (!v.integra()) {
            throw new TransizioneNonAmmessaException("La catena di hash dell'incidente " + inc.getCodice()
                    + " non è integra: nessuna nuova trasmissione finché non è stata verificata (GET /api/incidenti/"
                    + inc.getId() + "/catena)");
        }
        return v.ultimoAnello();
    }

    /** Documenti trasmessi dell'incidente in ordine di trasmissione (ordine della catena). */
    List<VersioneDocumento> trasmessiInOrdine(Incidente inc) {
        return percorri(inc).anelli();
    }

    Percorso percorri(Incidente inc) {
        List<VersioneDocumento> trasmessi = versioni.findByAdempimentoIncidenteIdAndStato(inc.getId(), StatoDocumento.TRASMESSO);
        Map<String, VersioneDocumento> perPrecedente = new HashMap<>();
        List<VersioneDocumento> residui = new ArrayList<>();
        for (VersioneDocumento v : trasmessi) {
            if (v.getImprontaPrecedente() == null || perPrecedente.putIfAbsent(v.getImprontaPrecedente(), v) != null) {
                residui.add(v);
            }
        }
        List<VersioneDocumento> anelli = new ArrayList<>();
        String corrente = CatenaImpronte.genesi(inc.getCodice());
        VersioneDocumento successivo;
        while ((successivo = perPrecedente.remove(corrente)) != null && successivo.getImprontaSha256() != null) {
            anelli.add(successivo);
            corrente = successivo.getImprontaSha256();
        }
        if (successivo != null) {
            residui.add(successivo);
        }
        residui.addAll(perPrecedente.values());
        residui.sort(Comparator.comparing(VersioneDocumento::getId));
        return new Percorso(List.copyOf(anelli), List.copyOf(residui), corrente);
    }
}
