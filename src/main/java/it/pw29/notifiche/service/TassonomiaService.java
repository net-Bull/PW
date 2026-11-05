package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.LivelloTassonomia;
import it.pw29.notifiche.domain.TassonomiaVersione;
import it.pw29.notifiche.repository.TassonomiaVersioneRepository;
import it.pw29.notifiche.repository.TassonomiaVoceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.pw29.notifiche.domain.TassonomiaVoce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TassonomiaService {

    public record Conteggio(String versione, String versioneDocumento, String dataAcquisizione,
                            String sha256Documento, Map<LivelloTassonomia, Long> perLivello) {
    }

    /**
     * Valore classificabile della versione vigente con il suo percorso nell'albero: è l'unico livello che un
     * incidente può referenziare (ADR-0002), quindi l'unico che l'interfaccia propone.
     *
     * @param predicato predicato, seguito dal sotto-predicato quando c'è (es. "Involved Asset &gt; Hardware")
     */
    public record ValoreSelezionabile(String codice, String nome, String descrizione, String macrocategoria,
                                      String predicato) {
    }

    private final TassonomiaVersioneRepository versioni;
    private final TassonomiaVoceRepository voci;

    public TassonomiaService(TassonomiaVersioneRepository versioni, TassonomiaVoceRepository voci) {
        this.versioni = versioni;
        this.voci = voci;
    }

    @Transactional(readOnly = true)
    public Optional<Conteggio> conteggioVigente() {
        return versioni.findByVigenteTrue().map(this::conta);
    }

    /** Valori della versione vigente nell'ordine dell'albero: macrocategoria, predicato, valore. */
    @Transactional(readOnly = true)
    public List<ValoreSelezionabile> valoriVigenti() {
        TassonomiaVersione v = versioni.findByVigenteTrue()
                .orElseThrow(() -> new IllegalStateException("Nessuna versione vigente della tassonomia"));
        record Riga(List<Integer> chiave, ValoreSelezionabile valore) {
        }
        List<Riga> righe = new ArrayList<>();
        for (TassonomiaVoce t : voci.findByVersioneIdOrderByOrdineAscIdAsc(v.getId())) {
            if (t.getLivello() != LivelloTassonomia.VALORE) {
                continue;
            }
            List<TassonomiaVoce> antenati = new ArrayList<>();
            for (TassonomiaVoce p = t.getPadre(); p != null; p = p.getPadre()) {
                antenati.add(0, p);
            }
            String macro = antenati.isEmpty() ? "" : antenati.get(0).getNome();
            String predicato = String.join(" > ", antenati.stream().skip(1).map(TassonomiaVoce::getNome).toList());
            List<Integer> chiave = new ArrayList<>(antenati.stream().map(TassonomiaVoce::getOrdine).toList());
            chiave.add(t.getOrdine());
            righe.add(new Riga(chiave, new ValoreSelezionabile(t.getCodice(), t.getNome(), t.getDescrizione(), macro, predicato)));
        }
        Comparator<List<Integer>> perChiave = (a, b) -> {
            for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                int c = Integer.compare(a.get(i), b.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(a.size(), b.size());
        };
        return righe.stream().sorted(Comparator.comparing(Riga::chiave, perChiave)).map(Riga::valore).toList();
    }

    private Conteggio conta(TassonomiaVersione v) {
        Map<LivelloTassonomia, Long> perLivello = new EnumMap<>(LivelloTassonomia.class);
        for (LivelloTassonomia l : LivelloTassonomia.values()) {
            perLivello.put(l, 0L);
        }
        voci.contaPerLivello(v.getId()).forEach(c -> perLivello.put(c.getLivello(), c.getTotale()));
        return new Conteggio(v.getCodice(), v.getVersioneDocumento(), v.getDataAcquisizione().toString(),
                v.getSha256Documento(), perLivello);
    }
}
