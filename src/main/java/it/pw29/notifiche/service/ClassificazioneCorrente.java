package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.StoricoRiclassificazione;
import it.pw29.notifiche.domain.TassonomiaVoce;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.domain.contenuto.Riclassificazione;
import it.pw29.notifiche.repository.StoricoRiclassificazioneRepository;
import it.pw29.notifiche.repository.ValutazioneSignificativitaRepository;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Classificazione corrente di un incidente (ADR-0007):
 * <ul>
 *   <li>voci della tassonomia: quelle che il CSIRT Italia conosce dall'ultimo documento trasmesso, oppure, se
 *       dopo quell'invio è stata registrata una riclassificazione non ancora comunicata, quelle nuove;</li>
 *   <li>categorie IS: quelle dell'ultima valutazione di significatività, che è l'unica fonte delle
 *       categorie (qualificazione o riclassificazione con nuova valutazione).</li>
 * </ul>
 * È il riferimento per il riuso dei dati nelle bozze e per il controllo di coerenza dei documenti
 * successivi alla notifica.
 */
@Component
class ClassificazioneCorrente {

    /** Voci e categorie IS correnti, con l'ultimo documento trasmesso (se esiste). */
    record Stato(SortedSet<String> voci, SortedSet<String> categorieIs, Optional<VersioneDocumento> ultimoTrasmesso) {
    }

    private final CatenaService catena;
    private final StoricoRiclassificazioneRepository storico;
    private final ValutazioneSignificativitaRepository valutazioni;

    ClassificazioneCorrente(CatenaService catena, StoricoRiclassificazioneRepository storico,
                            ValutazioneSignificativitaRepository valutazioni) {
        this.catena = catena;
        this.storico = storico;
        this.valutazioni = valutazioni;
    }

    Stato di(Incidente inc) {
        List<VersioneDocumento> trasmessi = catena.trasmessiInOrdine(inc);
        Optional<VersioneDocumento> ultimo = trasmessi.isEmpty() ? Optional.empty()
                : Optional.of(trasmessi.get(trasmessi.size() - 1));
        SortedSet<String> categorie = valutazioni.findFirstByIncidenteIdOrderByEseguitaIlDescIdDesc(inc.getId())
                .map(v -> insieme(v.getCategorieIs())).orElseGet(TreeSet::new);
        List<StoricoRiclassificazione> pendenti = storico
                .findByIncidenteIdAndDocumentoDopoIsNullOrderByRegistrataIlAscIdAsc(inc.getId());
        if (!pendenti.isEmpty()) {
            StoricoRiclassificazione r = pendenti.get(pendenti.size() - 1);
            return new Stato(insieme(r.getVociDopo()), categorie, ultimo);
        }
        SortedSet<String> voci = ultimo.map(v -> v.getVoci().stream().map(TassonomiaVoce::getCodice)
                .collect(Collectors.toCollection(TreeSet::new))).orElseGet(TreeSet::new);
        return new Stato(voci, categorie, ultimo);
    }

    /**
     * Storico completo, nella forma esposta dai documenti (campo {@code riclassificazioni}). Gli istanti sono
     * al millisecondo, così il contenuto compilato coincide con quello ricalcolato dopo una rilettura.
     */
    List<Riclassificazione> perDocumento(Incidente inc) {
        return storico.findByIncidenteIdOrderByRegistrataIlAscIdAsc(inc.getId()).stream()
                .map(r -> new Riclassificazione(r.getRegistrataIl().truncatedTo(ChronoUnit.MILLIS), r.getMotivazione(),
                        r.isSignificativoPrima(), r.isSignificativoDopo(),
                        List.copyOf(insieme(r.getCategorieIsPrima())), List.copyOf(insieme(r.getCategorieIsDopo())),
                        List.copyOf(insieme(r.getVociPrima())), List.copyOf(insieme(r.getVociDopo())),
                        r.getDocumentoPrima() == null ? null : r.getDocumentoPrima().getRiferimentoTrasmissione()))
                .toList();
    }

    static SortedSet<String> insieme(String elenco) {
        return Mappatura.elenco(elenco).stream().map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static String unisci(java.util.Collection<String> valori) {
        return new TreeSet<>(valori).stream().collect(Collectors.joining(","));
    }
}
