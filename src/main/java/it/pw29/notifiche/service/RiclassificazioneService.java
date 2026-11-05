package it.pw29.notifiche.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pw29.notifiche.domain.Adempimento;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.LivelloTassonomia;
import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.StoricoRiclassificazione;
import it.pw29.notifiche.domain.TassonomiaVersione;
import it.pw29.notifiche.domain.TassonomiaVoce;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.domain.TransizioneNonAmmessaException;
import it.pw29.notifiche.domain.ValutazioneSignificativita;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import it.pw29.notifiche.domain.contenuto.VincoloClassificazione;
import it.pw29.notifiche.dto.EsitoSignificativita;
import it.pw29.notifiche.dto.RiclassificazioneDto;
import it.pw29.notifiche.dto.RiclassificazioneRequest;
import it.pw29.notifiche.repository.IncidenteRepository;
import it.pw29.notifiche.repository.StoricoRiclassificazioneRepository;
import it.pw29.notifiche.repository.TassonomiaVersioneRepository;
import it.pw29.notifiche.repository.TassonomiaVoceRepository;
import it.pw29.notifiche.repository.ValutazioneSignificativitaRepository;
import it.pw29.notifiche.repository.VersioneDocumentoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Riclassificazione (traccia PW 29, punto 3; ADR-0007).
 * <p>
 * <b>Prima di ogni invio</b> serve solo a riportare a non significativo un incidente qualificato per errore
 * o smentito da nuovi elementi (es. falso positivo): nuova valutazione obbligatoria con esito negativo,
 * motivazione, stato NON_SIGNIFICATIVO; nessun termine resta aperto e nessun documento si può inviare. Se in
 * seguito l'incidente torna significativo, i termini decorrono dalla conoscenza registrata la prima volta.
 * <p>
 * <b>Dopo il primo invio</b> (riclassificazione tardiva) si applica a un incidente in gestione e può cambiare:
 * <ul>
 *   <li>la significatività, con una nuova valutazione: significativo -> DECLASSATO o viceversa
 *       (il momento della conoscenza resta quello registrato);</li>
 *   <li>le categorie IS, sempre tramite la nuova valutazione;</li>
 *   <li>le voci della Tassonomia Cyber ACN.</li>
 * </ul>
 * I documenti trasmessi non cambiano. La riclassificazione è registrata nello storico con la
 * classificazione prima e dopo, il momento e la motivazione; le bozze aperte vengono riallineate e il
 * successivo documento trasmesso la comunica al CSIRT Italia (campo {@code riclassificazioni}).
 */
@Service
public class RiclassificazioneService {

    private final IncidenteRepository incidenti;
    private final VersioneDocumentoRepository versioni;
    private final StoricoRiclassificazioneRepository storico;
    private final ValutazioneSignificativitaRepository valutazioni;
    private final TassonomiaVersioneRepository tassonomie;
    private final TassonomiaVoceRepository vociTassonomia;
    private final ValutazioneService valutazione;
    private final ClassificazioneCorrente classificazione;
    private final CampiDiSistema campiDiSistema;
    private final AuditService audit;
    private final UtenteCorrente utente;
    private final ObjectMapper json;
    private final Clock clock;

    public RiclassificazioneService(IncidenteRepository incidenti, VersioneDocumentoRepository versioni,
                                    StoricoRiclassificazioneRepository storico,
                                    ValutazioneSignificativitaRepository valutazioni,
                                    TassonomiaVersioneRepository tassonomie, TassonomiaVoceRepository vociTassonomia,
                                    ValutazioneService valutazione, ClassificazioneCorrente classificazione,
                                    CampiDiSistema campiDiSistema, AuditService audit, UtenteCorrente utente,
                                    ObjectMapper json, Clock clock) {
        this.incidenti = incidenti;
        this.versioni = versioni;
        this.storico = storico;
        this.valutazioni = valutazioni;
        this.tassonomie = tassonomie;
        this.vociTassonomia = vociTassonomia;
        this.valutazione = valutazione;
        this.classificazione = classificazione;
        this.campiDiSistema = campiDiSistema;
        this.audit = audit;
        this.utente = utente;
        this.json = json;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public RiclassificazioneDto riclassifica(Long incidenteId, RiclassificazioneRequest r) {
        Incidente inc = incidenti.findById(incidenteId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Incidente", incidenteId));
        if (!inc.getStato().inGestione()) {
            throw new TransizioneNonAmmessaException("L'incidente " + inc.getCodice() + " è " + inc.getStato()
                    + ": la riclassificazione si applica a incidenti in gestione");
        }
        ClassificazioneCorrente.Stato corrente = classificazione.di(inc);
        if (versioni.existsByAdempimentoIncidenteIdAndStato(inc.getId(), StatoDocumento.IN_APPROVAZIONE)) {
            throw new TransizioneNonAmmessaException("Un documento di " + inc.getCodice() + " è in approvazione: "
                    + "va respinto prima di riclassificare, perché l'approvatore non trasmetta una classificazione superata");
        }
        if (corrente.ultimoTrasmesso().isEmpty()) {
            return primaDelPrimoInvio(inc, corrente, r);
        }
        VersioneDocumento ultimo = corrente.ultimoTrasmesso().get();
        if (r.voci() == null) {
            throw new DatiNonValidiException("Indicare le voci della nuova classificazione (anche se invariate)");
        }
        Instant ora = Instant.now(clock);
        SortedSet<String> vociDopo = verificaVoci(r.voci());

        // Significatività e categorie: dalla nuova valutazione, se presente; altrimenti invariate.
        StatoIncidente statoPrima = inc.getStato();
        StatoIncidente statoDopo = statoPrima;
        SortedSet<String> categorieDopo = corrente.categorieIs();
        ValutazioneSignificativita nuova = null;
        if (r.valutazione() != null) {
            Rivalutazione rv = nuovaValutazione(inc, r, ora);
            nuova = rv.registrata();
            statoDopo = rv.esito().significativo() ? StatoIncidente.SIGNIFICATIVO : StatoIncidente.DECLASSATO;
            categorieDopo = rv.esito().categorieIs().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        }
        if (statoDopo == statoPrima && categorieDopo.equals(corrente.categorieIs()) && vociDopo.equals(corrente.voci())) {
            throw new DatiNonValidiException("La riclassificazione non cambia né la significatività, né le categorie IS, "
                    + "né le voci della tassonomia: nulla da registrare");
        }
        if (statoDopo != statoPrima) {
            inc.cambiaStato(statoDopo);
        }

        StoricoRiclassificazione riga = storico.save(new StoricoRiclassificazione(inc, ultimo, nuova,
                new StoricoRiclassificazione.Classificazione(statoPrima, ClassificazioneCorrente.unisci(corrente.categorieIs()),
                        ClassificazioneCorrente.unisci(corrente.voci())),
                new StoricoRiclassificazione.Classificazione(statoDopo, ClassificazioneCorrente.unisci(categorieDopo),
                        ClassificazioneCorrente.unisci(vociDopo)),
                r.motivazione(), ora, utente.username()));
        audit.registra("RICLASSIFICAZIONE", "Incidente", inc.getId(), statoPrima + " -> " + statoDopo
                + "; categorie " + corrente.categorieIs() + " -> " + categorieDopo
                + "; voci " + corrente.voci() + " -> " + vociDopo
                + "; ultimo documento trasmesso: versione " + ultimo.getId() + " (" + ultimo.getRiferimentoTrasmissione()
                + "); motivazione: " + r.motivazione());

        riallineaBozze(inc, vociDopo, ora);
        return Mappatura.riclassificazione(riga);
    }

    /**
     * Ritorno a non significativo prima di ogni invio. La significatività si cambia solo così: le voci e le
     * categorie di una bozza mai inviata non sono state comunicate a nessuno e restano nello storico com'erano.
     */
    private RiclassificazioneDto primaDelPrimoInvio(Incidente inc, ClassificazioneCorrente.Stato corrente,
                                                    RiclassificazioneRequest r) {
        if (r.valutazione() == null) {
            throw new TransizioneNonAmmessaException("Nessun documento è ancora stato trasmesso per " + inc.getCodice()
                    + ": prima del primo invio la classificazione si corregge nella bozza; la riclassificazione serve "
                    + "solo a escludere la significatività, con una nuova valutazione");
        }
        Instant ora = Instant.now(clock);
        Rivalutazione rv = nuovaValutazione(inc, r, ora);
        ValutazioneSignificativita nuova = rv.registrata();
        if (rv.esito().significativo()) {
            throw new TransizioneNonAmmessaException("La nuova valutazione conferma la significatività: prima del primo "
                    + "invio categorie e voci si correggono nella bozza, i termini restano quelli in corso");
        }
        StatoIncidente prima = inc.getStato();
        inc.cambiaStato(StatoIncidente.NON_SIGNIFICATIVO);
        String voci = ClassificazioneCorrente.unisci(corrente.voci());
        StoricoRiclassificazione riga = storico.save(new StoricoRiclassificazione(inc, null, nuova,
                new StoricoRiclassificazione.Classificazione(prima, ClassificazioneCorrente.unisci(corrente.categorieIs()), voci),
                new StoricoRiclassificazione.Classificazione(StatoIncidente.NON_SIGNIFICATIVO, "", voci),
                r.motivazione(), ora, utente.username()));
        audit.registra("RICLASSIFICAZIONE", "Incidente", inc.getId(), prima + " -> NON_SIGNIFICATIVO prima di ogni invio; "
                + "categorie " + corrente.categorieIs() + " -> []; nessun termine resta aperto; motivazione: " + r.motivazione());
        campiDiSistema.riallineaBozze(inc);
        return Mappatura.riclassificazione(riga);
    }

    /** Valutazione registrata e relativo esito. */
    private record Rivalutazione(ValutazioneSignificativita registrata, EsitoSignificativita esito) {
    }

    /** Nuova valutazione con le soglie vigenti alla data della conoscenza, registrata nello storico. */
    private Rivalutazione nuovaValutazione(Incidente inc, RiclassificazioneRequest r, Instant ora) {
        ValutazioneService.Calcolo calcolo = valutazione.calcola(inc.getSoggetto(), r.valutazione(),
                LocalDate.ofInstant(inc.getConoscenzaIl(), clock.getZone()));
        EsitoSignificativita esito = calcolo.esito();
        ValutazioneSignificativita v = valutazioni.save(new ValutazioneSignificativita(inc, ora, utente.username(),
                calcolo.soglie(), r.valutazione().servizioCoinvolto(), scriviJson(r.valutazione()), esito.significativo(),
                nomi(esito.regoleAttivate()), nomi(esito.categorieIs())));
        audit.registra("VALUTAZIONE", "Incidente", inc.getId(), "Rivalutazione per riclassificazione: "
                + (esito.significativo() ? "significativo" : "non significativo") + "; regole "
                + esito.regoleAttivate() + "; categorie " + esito.categorieIs() + "; soglie " + esito.versioneSoglie());
        return new Rivalutazione(v, esito);
    }

    @Transactional(readOnly = true)
    public List<RiclassificazioneDto> storico(Long incidenteId) {
        if (!incidenti.existsById(incidenteId)) {
            throw new RisorsaNonTrovataException("Incidente", incidenteId);
        }
        return storico.findByIncidenteIdOrderByRegistrataIlAscIdAsc(incidenteId).stream()
                .map(Mappatura::riclassificazione).toList();
    }

    // --- Supporto ------------------------------------------------------------------------------

    /**
     * La nuova classificazione deve essere completa come quella di una notifica: voci esistenti nella
     * tassonomia vigente, di livello VALORE, con i vincoli della notifica (almeno una voce, una sola gravità).
     */
    private SortedSet<String> verificaVoci(List<String> richieste) {
        SortedSet<String> codici = new TreeSet<>(richieste);
        TassonomiaVersione vigente = tassonomie.findByVigenteTrue()
                .orElseThrow(() -> new IllegalStateException("Nessuna versione vigente della tassonomia"));
        List<TassonomiaVoce> voci = codici.isEmpty() ? List.of()
                : vociTassonomia.findByVersioneIdAndCodiceIn(vigente.getId(), codici);
        if (voci.size() != codici.size()) {
            List<String> sconosciuti = new ArrayList<>(codici);
            voci.forEach(v -> sconosciuti.remove(v.getCodice()));
            throw new DatiNonValidiException("Codici non presenti nella tassonomia " + vigente.getCodice() + ": " + sconosciuti);
        }
        List<String> nonValore = voci.stream().filter(v -> v.getLivello() != LivelloTassonomia.VALORE)
                .map(TassonomiaVoce::getCodice).toList();
        if (!nonValore.isEmpty()) {
            throw new DatiNonValidiException("Solo le voci di livello VALORE classificano un incidente: " + nonValore);
        }
        for (VincoloClassificazione vc : VincoloClassificazione.values()) {
            if (vc.siApplicaA(TipoAdempimento.NOTIFICA) && !vc.soddisfatto(voci)) {
                throw new DatiNonValidiException("Classificazione incompleta: " + vc.getDescrizione() + " (" + vc.getFonte() + ")");
            }
        }
        return codici;
    }

    /** Le bozze aperte adottano subito le voci nuove e i campi compilati dal sistema (categorie, storico). */
    private void riallineaBozze(Incidente inc, SortedSet<String> voci, Instant ora) {
        for (Adempimento a : inc.getAdempimenti()) {
            a.versioneAperta().filter(v -> v.getStato() == StatoDocumento.BOZZA).ifPresent(v -> {
                List<TassonomiaVoce> nuove = vociTassonomia.findByVersioneIdAndCodiceIn(v.getTassonomiaVersione().getId(), voci);
                ContenutoDocumento c = v.getContenuto();
                campiDiSistema.compila(c, a);
                v.aggiorna(c, nuove, ora, utente.username());
                audit.registra("BOZZA_RIALLINEATA", "VersioneDocumento", v.getId(),
                        a.getTipo() + " n. " + a.getProgressivo() + " v" + v.getNumero() + ": voci " + voci);
            });
        }
    }

    private String scriviJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nomi(List<? extends Enum<?>> valori) {
        return valori.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
