package it.pw29.notifiche.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pw29.notifiche.domain.Adempimento;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.Soggetto;
import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.StatoScadenza;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.domain.TransizioneNonAmmessaException;
import it.pw29.notifiche.domain.ValutazioneSignificativita;
import it.pw29.notifiche.dto.AdempimentoDto;
import it.pw29.notifiche.dto.ConclusioneRequest;
import it.pw29.notifiche.dto.EsitoSignificativita;
import it.pw29.notifiche.dto.IncidenteDettaglio;
import it.pw29.notifiche.dto.IncidenteSintesi;
import it.pw29.notifiche.dto.NuovoIncidenteRequest;
import it.pw29.notifiche.dto.QualificazioneRequest;
import it.pw29.notifiche.dto.RichiestaCsirtRequest;
import it.pw29.notifiche.dto.Scadenza;
import it.pw29.notifiche.repository.IncidenteRepository;
import it.pw29.notifiche.repository.SoggettoRepository;
import it.pw29.notifiche.repository.ValutazioneSignificativitaRepository;
import it.pw29.notifiche.repository.VersioneDocumentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ciclo di vita dell'incidente: registrazione, valutazione di significatività con registrazione
 * del momento della conoscenza, richieste del CSIRT Italia, relazioni mensili, conclusione della gestione.
 */
@Service
public class IncidenteService {

    private final IncidenteRepository incidenti;
    private final SoggettoRepository soggetti;
    private final ValutazioneSignificativitaRepository valutazioni;
    private final ValutazioneService valutazione;
    private final CalcoloScadenze calcoloScadenze;
    private final AuditService audit;
    private final UtenteCorrente utente;
    private final ObjectMapper json;
    private final Clock clock;
    private final VersioneDocumentoRepository versioni;
    private final CampiDiSistema campiDiSistema;
    private final ZoneId fuso;

    public IncidenteService(IncidenteRepository incidenti, SoggettoRepository soggetti,
                            ValutazioneSignificativitaRepository valutazioni, ValutazioneService valutazione,
                            CalcoloScadenze calcoloScadenze, AuditService audit, UtenteCorrente utente,
                            ObjectMapper json, Clock clock, VersioneDocumentoRepository versioni,
                            CampiDiSistema campiDiSistema,
                            @Value("${notifiche.fuso-orario:Europe/Rome}") ZoneId fuso) {
        this.incidenti = incidenti;
        this.soggetti = soggetti;
        this.valutazioni = valutazioni;
        this.valutazione = valutazione;
        this.calcoloScadenze = calcoloScadenze;
        this.audit = audit;
        this.utente = utente;
        this.json = json;
        this.clock = clock;
        this.versioni = versioni;
        this.campiDiSistema = campiDiSistema;
        this.fuso = fuso;
    }

    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public IncidenteDettaglio registra(NuovoIncidenteRequest r) {
        Instant ora = Instant.now(clock);
        if (r.rilevatoIl().isAfter(ora)) {
            throw new DatiNonValidiException("L'istante di rilevazione non può essere futuro");
        }
        Soggetto soggetto = r.soggettoId() == null
                ? soggetti.findFirstByOrderByIdAsc().orElseThrow(() -> new IllegalStateException("Nessun soggetto configurato"))
                : soggetti.findById(r.soggettoId()).orElseThrow(() -> new RisorsaNonTrovataException("Soggetto", r.soggettoId()));
        String prefisso = "INC-" + ora.atZone(fuso).getYear() + "-";
        String codice = prefisso + String.format("%04d", incidenti.countByCodiceStartingWith(prefisso) + 1);
        Incidente inc = incidenti.save(new Incidente(codice, soggetto, r.titolo(), r.descrizione(),
                r.rilevatoIl(), ora, utente.username()));
        audit.registra("INCIDENTE_REGISTRATO", "Incidente", inc.getId(), codice + " - " + r.titolo());
        return dettaglio(inc, ora);
    }

    /**
     * Valuta la significatività. Se l'esito è positivo registra, una sola volta, il momento della
     * conoscenza e fa sorgere gli obblighi di pre-notifica e notifica. Un incidente già significativo
     * non si rivaluta da qui: il cambio di qualificazione è una riclassificazione ({@link RiclassificazioneService}).
     * <p>
     * Caso particolare: un incidente qualificato significativo e poi riportato a non significativo prima di
     * ogni invio, che torna significativo per nuovi elementi. La conoscenza resta quella registrata la prima
     * volta e gli adempimenti già aperti si riprendono: i termini decorrono dalla prima evidenza (scelta
     * prudente, ADR-0007).
     */
    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public IncidenteDettaglio qualifica(Long id, QualificazioneRequest r) {
        Incidente inc = trova(id);
        if (inc.getStato() != StatoIncidente.IN_VALUTAZIONE && inc.getStato() != StatoIncidente.NON_SIGNIFICATIVO) {
            throw new TransizioneNonAmmessaException("L'incidente " + inc.getCodice() + " è " + inc.getStato()
                    + ": la modifica della qualificazione passa dalla riclassificazione");
        }
        Instant ora = Instant.now(clock);
        Instant riferimento = inc.getConoscenzaIl() != null ? inc.getConoscenzaIl()
                : r.conoscenzaIl() != null ? r.conoscenzaIl() : ora;
        ValutazioneService.Calcolo calcolo = valutazione.calcola(inc.getSoggetto(), r.valutazione(),
                LocalDate.ofInstant(riferimento, fuso));
        EsitoSignificativita esito = calcolo.esito();
        valutazioni.save(new ValutazioneSignificativita(inc, ora, utente.username(), calcolo.soglie(),
                r.valutazione().servizioCoinvolto(), scriviJson(r.valutazione()), esito.significativo(),
                unisci(esito.regoleAttivate()), unisci(esito.categorieIs())));
        audit.registra("VALUTAZIONE", "Incidente", inc.getId(), (esito.significativo() ? "Significativo" : "Non significativo")
                + "; regole " + esito.regoleAttivate() + "; categorie " + esito.categorieIs()
                + "; soglie " + esito.versioneSoglie());

        if (esito.significativo() && inc.getConoscenzaIl() != null) {
            // Riqualificazione dopo un ritorno a non significativo prima di ogni invio
            inc.cambiaStato(StatoIncidente.SIGNIFICATIVO);
            audit.registra("RIQUALIFICAZIONE", "Incidente", inc.getId(), "Di nuovo significativo: i termini decorrono "
                    + "dalla conoscenza registrata il " + inc.getConoscenzaIl());
            campiDiSistema.riallineaBozze(inc);
        } else if (esito.significativo()) {
            Instant conoscenza = r.conoscenzaIl() != null ? r.conoscenzaIl() : ora;
            try {
                inc.registraConoscenza(conoscenza, r.motivazioneConoscenza(), ora, utente.username());
            } catch (IllegalArgumentException e) {
                throw new DatiNonValidiException(e.getMessage());
            }
            inc.cambiaStato(StatoIncidente.SIGNIFICATIVO);
            inc.aggiungiAdempimento(TipoAdempimento.PRE_NOTIFICA);
            inc.aggiungiAdempimento(TipoAdempimento.NOTIFICA);
            audit.registra("CONOSCENZA_REGISTRATA", "Incidente", inc.getId(),
                    "Conoscenza il " + conoscenza + ": " + r.motivazioneConoscenza());
        } else if (inc.getStato() == StatoIncidente.IN_VALUTAZIONE) {
            inc.cambiaStato(StatoIncidente.NON_SIGNIFICATIVO);
        }
        return dettaglio(inc, ora);
    }

    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public AdempimentoDto registraRichiestaCsirt(Long id, RichiestaCsirtRequest r) {
        Incidente inc = trova(id);
        richiediSignificativo(inc);
        if (!inc.trasmesso(TipoAdempimento.PRE_NOTIFICA)) {
            throw new DatiNonValidiException("Una relazione intermedia presuppone la pre-notifica già trasmessa");
        }
        Instant ora = Instant.now(clock);
        Instant richiesta = r.richiestaIl() != null ? r.richiestaIl() : ora;
        Adempimento a = inc.aggiungiAdempimento(TipoAdempimento.RELAZIONE_INTERMEDIA);
        try {
            a.registraRichiesta(richiesta, r.scadenza(), r.riferimento());
        } catch (IllegalArgumentException e) {
            throw new DatiNonValidiException(e.getMessage());
        }
        incidenti.flush();
        audit.registra("RICHIESTA_CSIRT", "Incidente", inc.getId(),
                "Relazione intermedia n. " + a.getProgressivo() + " - " + r.riferimento());
        return Mappatura.adempimento(a);
    }

    /** Apre la prossima relazione mensile, solo se il piano delle scadenze la prevede (lett. e). */
    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public AdempimentoDto apriRelazioneMensile(Long id) {
        Incidente inc = trova(id);
        richiediSignificativo(inc);
        int prossima = (int) inc.getAdempimenti().stream()
                .filter(a -> a.getTipo() == TipoAdempimento.RELAZIONE_MENSILE).count() + 1;
        boolean prevista = scadenze(inc, Instant.now(clock)).stream()
                .anyMatch(s -> s.tipo() == TipoAdempimento.RELAZIONE_MENSILE && s.progressivo() == prossima);
        if (!prevista) {
            throw new DatiNonValidiException("La relazione mensile n. " + prossima + " non è dovuta: è richiesta solo "
                    + "se l'incidente è in corso alla data della relazione finale (art. 25, c. 5, lett. e)");
        }
        Adempimento a = inc.aggiungiAdempimento(TipoAdempimento.RELAZIONE_MENSILE);
        incidenti.flush();
        audit.registra("RELAZIONE_MENSILE_APERTA", "Incidente", inc.getId(), "Relazione mensile n. " + prossima);
        return Mappatura.adempimento(a);
    }

    /**
     * Registra, una sola volta, la conclusione della gestione: fissa il termine della relazione finale se
     * l'incidente era in corso alla data ordinaria (lett. e). Le bozze aperte la ricevono subito; con un
     * documento in approvazione è rifiutata, perché l'approvatore non trasmetta un contenuto superato.
     */
    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public IncidenteDettaglio registraConclusione(Long id, ConclusioneRequest r) {
        Incidente inc = trova(id);
        richiediSignificativo(inc);
        if (versioni.existsByAdempimentoIncidenteIdAndStato(inc.getId(), StatoDocumento.IN_APPROVAZIONE)) {
            throw new TransizioneNonAmmessaException("Un documento di " + inc.getCodice() + " è in approvazione: "
                    + "va trasmesso o respinto prima di registrare la conclusione della gestione");
        }
        Instant ora = Instant.now(clock);
        try {
            inc.registraConclusioneGestione(r.conclusaIl(), ora);
        } catch (IllegalArgumentException e) {
            throw new DatiNonValidiException(e.getMessage());
        } catch (IllegalStateException e) {
            throw new TransizioneNonAmmessaException(e.getMessage());
        }
        campiDiSistema.riallineaBozze(inc);
        audit.registra("CONCLUSIONE_GESTIONE", "Incidente", inc.getId(), "Gestione conclusa il " + r.conclusaIl());
        return dettaglio(inc, ora);
    }

    @Transactional(readOnly = true)
    public IncidenteDettaglio dettaglio(Long id) {
        return dettaglio(trova(id), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<IncidenteSintesi> elenco() {
        Instant ora = Instant.now(clock);
        return incidenti.findAllByOrderByRilevatoIlDesc().stream()
                .map(i -> new IncidenteSintesi(i.getId(), i.getCodice(), i.getTitolo(), i.getStato(),
                        i.getRilevatoIl(), i.getConoscenzaIl(), prossimaScadenza(scadenze(i, ora))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Scadenza> scadenze(Long id) {
        return scadenze(trova(id), Instant.now(clock));
    }

    // --- Supporto ------------------------------------------------------------------------------

    Incidente trova(Long id) {
        return incidenti.findById(id).orElseThrow(() -> new RisorsaNonTrovataException("Incidente", id));
    }

    /** Piano delle scadenze: vuoto se l'incidente non è (o non è più) significativo prima di ogni invio. */
    List<Scadenza> scadenze(Incidente inc, Instant ora) {
        if (inc.getStato() == StatoIncidente.IN_VALUTAZIONE || inc.getStato() == StatoIncidente.NON_SIGNIFICATIVO) {
            return List.of();
        }
        List<CalcoloScadenze.FattoAdempimento> fatti = inc.getAdempimenti().stream()
                .map(a -> new CalcoloScadenze.FattoAdempimento(a.getTipo(), a.getProgressivo(),
                        a.getTrasmessoIl(), a.getScadenzaRichiesta()))
                .toList();
        return calcoloScadenze.calcola(
                new CalcoloScadenze.Fatti(inc.getConoscenzaIl(), inc.getConclusioneGestioneIl(), fatti), ora);
    }

    private static Scadenza prossimaScadenza(List<Scadenza> piano) {
        return piano.stream()
                .filter(s -> s.trasmessaIl() == null && s.dovutaEntro() != null)
                .filter(s -> s.stato() != StatoScadenza.RISPETTATA)
                .min(Comparator.comparing(Scadenza::dovutaEntro))
                .orElse(null);
    }

    /** Gli obblighi già sorti restano anche se l'incidente è stato declassato dopo un invio (ADR-0007). */
    private static void richiediSignificativo(Incidente inc) {
        if (!inc.getStato().inGestione()) {
            throw new TransizioneNonAmmessaException("L'incidente " + inc.getCodice() + " è " + inc.getStato()
                    + ": operazione ammessa solo per incidenti in gestione (significativi o declassati)");
        }
    }

    private IncidenteDettaglio dettaglio(Incidente inc, Instant ora) {
        ValutazioneSignificativita ultima = valutazioni
                .findFirstByIncidenteIdOrderByEseguitaIlDescIdDesc(inc.getId()).orElse(null);
        return Mappatura.incidente(inc, ultima, scadenze(inc, ora));
    }

    private String scriviJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String unisci(List<? extends Enum<?>> valori) {
        return valori.stream().map(Enum::name).filter(Objects::nonNull).collect(Collectors.joining(","));
    }
}
