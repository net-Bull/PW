package it.pw29.notifiche.controller.web;

import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.dto.AdempimentoDto;
import it.pw29.notifiche.dto.ConclusioneRequest;
import it.pw29.notifiche.dto.DocumentoDettaglio;
import it.pw29.notifiche.dto.IncidenteDettaglio;
import it.pw29.notifiche.dto.NuovoIncidenteRequest;
import it.pw29.notifiche.dto.QualificazioneRequest;
import it.pw29.notifiche.dto.RettificaRequest;
import it.pw29.notifiche.dto.RichiestaCsirtRequest;
import it.pw29.notifiche.dto.RiclassificazioneRequest;
import it.pw29.notifiche.dto.Scadenza;
import it.pw29.notifiche.dto.VersioneSintesi;
import it.pw29.notifiche.dto.VoceDto;
import it.pw29.notifiche.service.AuditConsultazioneService;
import it.pw29.notifiche.service.CatenaService;
import it.pw29.notifiche.service.DocumentoService;
import it.pw29.notifiche.service.IncidenteService;
import it.pw29.notifiche.service.RiclassificazioneService;
import it.pw29.notifiche.service.TassonomiaService;
import it.pw29.notifiche.service.ValutazioneService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pagine degli incidenti: elenco con la prossima scadenza, registrazione, dettaglio con la sequenza dei termini,
 * qualificazione, conclusione della gestione, richieste del CSIRT, riclassificazione. Ogni azione chiama gli
 * stessi servizi delle API REST: permessi ({@code @PreAuthorize}), regole di dominio e audit sono gli stessi.
 */
@Controller
public class PagineIncidenti {

    /** Adempimento con le azioni ammesse, ricavate dalle sue versioni. */
    public record RigaAdempimento(AdempimentoDto a, VersioneSintesi aperta, VersioneSintesi ultimaTrasmessa,
                                  boolean creabile, boolean rettificabile) {
    }

    private final IncidenteService incidenti;
    private final DocumentoService documenti;
    private final RiclassificazioneService riclassificazione;
    private final CatenaService catena;
    private final AuditConsultazioneService audit;
    private final ValutazioneService valutazione;
    private final TassonomiaService tassonomia;
    private final Azioni azioni;
    private final Formati fmt;

    public PagineIncidenti(IncidenteService incidenti, DocumentoService documenti, RiclassificazioneService riclassificazione,
                           CatenaService catena, AuditConsultazioneService audit, ValutazioneService valutazione,
                           TassonomiaService tassonomia, Azioni azioni, Formati fmt) {
        this.incidenti = incidenti;
        this.documenti = documenti;
        this.riclassificazione = riclassificazione;
        this.catena = catena;
        this.audit = audit;
        this.valutazione = valutazione;
        this.tassonomia = tassonomia;
        this.azioni = azioni;
        this.fmt = fmt;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/incidenti";
    }

    @GetMapping("/incidenti")
    public String elenco(Model m) {
        m.addAttribute("incidenti", incidenti.elenco());
        return "interfaccia/incidenti";
    }

    @GetMapping("/incidenti/nuovo")
    public String nuovo(Model m) {
        m.addAttribute("adesso", fmt.campoAdesso());
        return "interfaccia/nuovo-incidente";
    }

    @PostMapping("/incidenti")
    public String registra(@RequestParam(required = false) String titolo, @RequestParam(required = false) String descrizione,
                           @RequestParam(required = false) String rilevatoIl, RedirectAttributes ra) {
        ra.addFlashAttribute("titolo", titolo);
        ra.addFlashAttribute("descrizione", descrizione);
        return azioni.esegui(ra, "/incidenti/nuovo", () -> {
            IncidenteDettaglio d = incidenti.registra(azioni.valida(
                    new NuovoIncidenteRequest(titolo, descrizione, fmt.leggi(rilevatoIl), null)));
            return new Azioni.Esito("/incidenti/" + d.id() + "/qualificazione",
                    "Incidente " + d.codice() + " registrato. Ora valutane la significatività.");
        });
    }

    @GetMapping("/incidenti/{id}")
    public String dettaglio(@PathVariable Long id, Model m) {
        IncidenteDettaglio inc = incidenti.dettaglio(id);
        Optional<VersioneSintesi> ultimoInvio = inc.adempimenti().stream()
                .flatMap(a -> a.versioni().stream())
                .filter(v -> v.stato() == StatoDocumento.TRASMESSO)
                .max(Comparator.comparing(VersioneSintesi::trasmessaIl).thenComparing(VersioneSintesi::id));
        boolean inGestione = inc.stato().inGestione();
        List<RigaAdempimento> righe = inc.adempimenti().stream().map(a -> {
            VersioneSintesi aperta = a.versioni().stream().filter(v -> v.stato() != StatoDocumento.TRASMESSO)
                    .max(Comparator.comparing(VersioneSintesi::numero)).orElse(null);
            VersioneSintesi trasmessa = a.versioni().stream().filter(v -> v.stato() == StatoDocumento.TRASMESSO)
                    .max(Comparator.comparing(VersioneSintesi::numero)).orElse(null);
            boolean finaleChiusa = inc.stato() == StatoIncidente.CHIUSO && a.tipo() == TipoAdempimento.RELAZIONE_FINALE;
            boolean rettificabile = aperta == null && trasmessa != null && (inGestione || finaleChiusa)
                    && ultimoInvio.map(u -> u.id().equals(trasmessa.id())).orElse(false);
            return new RigaAdempimento(a, aperta, trasmessa, inGestione && aperta == null && trasmessa == null, rettificabile);
        }).toList();

        Set<Integer> mensiliAperte = inc.adempimenti().stream().filter(a -> a.tipo() == TipoAdempimento.RELAZIONE_MENSILE)
                .map(AdempimentoDto::progressivo).collect(Collectors.toSet());
        boolean mensileDovuta = inGestione && inc.scadenze().stream()
                .anyMatch(s -> s.tipo() == TipoAdempimento.RELAZIONE_MENSILE && !mensiliAperte.contains(s.progressivo()));

        m.addAttribute("inc", inc);
        m.addAttribute("passi", passi(inc));
        m.addAttribute("righe", righe);
        m.addAttribute("inGestione", inGestione);
        m.addAttribute("nessunInvio", ultimoInvio.isEmpty());
        m.addAttribute("preNotificaTrasmessa", inc.adempimenti().stream()
                .anyMatch(a -> a.tipo() == TipoAdempimento.PRE_NOTIFICA && a.trasmessoIl() != null));
        m.addAttribute("mensileDovuta", mensileDovuta);
        m.addAttribute("riclassificazioni", riclassificazione.storico(id));
        m.addAttribute("catena", catena.verifica(id));
        m.addAttribute("audit", audit.perIncidente(id));
        m.addAttribute("adesso", fmt.campoAdesso());
        return "interfaccia/incidente";
    }

    /**
     * Passi della sequenza dei termini, nell'ordine in cui scadono; il primo non ancora trasmesso è quello
     * "corrente", evidenziato con il conto alla rovescia.
     */
    public record Passo(String etichetta, String regola, Scadenza scadenza, boolean corrente) {
    }

    private List<Passo> passi(IncidenteDettaglio inc) {
        List<Scadenza> ordinate = inc.scadenze().stream()
                .sorted(Comparator.comparing(Scadenza::dovutaEntro, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Scadenza corrente = ordinate.stream().filter(s -> s.trasmessaIl() == null && s.dovutaEntro() != null)
                .findFirst().orElse(null);
        return ordinate.stream().map(s -> new Passo(
                s.tipo().getEtichetta() + (s.tipo().ripetibile() ? " n. " + s.progressivo() : ""),
                regola(s, inc), s, s == corrente)).toList();
    }

    private static String regola(Scadenza s, IncidenteDettaglio inc) {
        return switch (s.tipo()) {
            case PRE_NOTIFICA -> "24 h dalla conoscenza";
            case NOTIFICA -> "72 h dalla conoscenza";
            case RELAZIONE_FINALE -> inc.conclusioneGestioneIl() != null ? "1 mese dalla conclusione o dalla notifica"
                    : "1 mese dalla notifica";
            case RELAZIONE_MENSILE -> "mensile, incidente in corso";
            case RELAZIONE_INTERMEDIA -> "su richiesta del CSIRT";
        };
    }

    // --- Qualificazione ----------------------------------------------------------------------

    @GetMapping("/incidenti/{id}/qualificazione")
    public String qualificazione(@PathVariable Long id, Model m) {
        IncidenteDettaglio inc = incidenti.dettaglio(id);
        m.addAttribute("inc", inc);
        m.addAttribute("servizi", valutazione.servizi(null));
        if (!m.containsAttribute("valutazione")) {
            m.addAttribute("valutazione", new ModuloValutazione());
        }
        m.addAttribute("adesso", fmt.campoAdesso());
        return "interfaccia/qualificazione";
    }

    @PostMapping("/incidenti/{id}/qualificazione")
    public String qualifica(@PathVariable Long id, @ModelAttribute("valutazione") ModuloValutazione v,
                            @RequestParam(required = false) String conoscenzaIl,
                            @RequestParam(required = false) String motivazioneConoscenza, RedirectAttributes ra) {
        ra.addFlashAttribute("valutazione", v);
        ra.addFlashAttribute("motivazioneConoscenza", motivazioneConoscenza);
        return azioni.esegui(ra, "/incidenti/" + id + "/qualificazione", () -> {
            IncidenteDettaglio d = incidenti.qualifica(id, azioni.valida(new QualificazioneRequest(v.richiesta(),
                    fmt.leggi(conoscenzaIl), motivazioneConoscenza)));
            String esito = d.ultimaValutazione() == null ? "" : spiegazione(d);
            return new Azioni.Esito("/incidenti/" + id, d.stato() == StatoIncidente.SIGNIFICATIVO
                    ? "Incidente significativo (" + esito + "): da ora decorrono i termini di pre-notifica e notifica."
                    : "Incidente non significativo: nessun criterio è soddisfatto, nessun obbligo di notifica.");
        });
    }

    private static String spiegazione(IncidenteDettaglio d) {
        return Stream.concat(d.ultimaValutazione().regoleAttivate().stream(),
                        d.ultimaValutazione().categorieIs().stream().map(c -> c.replace('_', '-')))
                .collect(Collectors.joining(", "));
    }

    // --- Azioni sull'incidente -----------------------------------------------------------------

    @PostMapping("/incidenti/{id}/conclusione")
    public String conclusione(@PathVariable Long id, @RequestParam(required = false) String conclusaIl,
                              @RequestParam(defaultValue = "false") boolean conferma, RedirectAttributes ra) {
        return azioni.esegui(ra, "/incidenti/" + id, () -> {
            if (!conferma) {
                throw new ModuloNonValidoException("La conclusione si registra una sola volta: spuntare la conferma");
            }
            incidenti.registraConclusione(id, azioni.valida(new ConclusioneRequest(fmt.leggi(conclusaIl))));
            return new Azioni.Esito("/incidenti/" + id, "Conclusione della gestione registrata: il termine della "
                    + "relazione finale è aggiornato.");
        });
    }

    @PostMapping("/incidenti/{id}/richieste-csirt")
    public String richiestaCsirt(@PathVariable Long id, @RequestParam(required = false) String riferimento,
                                 @RequestParam(required = false) String richiestaIl,
                                 @RequestParam(required = false) String scadenza, RedirectAttributes ra) {
        return azioni.esegui(ra, "/incidenti/" + id, () -> {
            AdempimentoDto a = incidenti.registraRichiestaCsirt(id, azioni.valida(
                    new RichiestaCsirtRequest(riferimento, fmt.leggi(richiestaIl), fmt.leggi(scadenza))));
            return new Azioni.Esito("/incidenti/" + id, "Richiesta del CSIRT registrata: relazione intermedia n. "
                    + a.progressivo() + " da redigere.");
        });
    }

    @PostMapping("/incidenti/{id}/relazioni-mensili")
    public String relazioneMensile(@PathVariable Long id, RedirectAttributes ra) {
        return azioni.esegui(ra, "/incidenti/" + id, () -> {
            AdempimentoDto a = incidenti.apriRelazioneMensile(id);
            return new Azioni.Esito("/incidenti/" + id, "Relazione mensile n. " + a.progressivo() + " aperta.");
        });
    }

    // --- Documenti degli adempimenti -----------------------------------------------------------

    @PostMapping("/adempimenti/{id}/bozza")
    public String bozza(@PathVariable Long id, @RequestParam Long incidente, RedirectAttributes ra) {
        return azioni.esegui(ra, "/incidenti/" + incidente, () -> {
            DocumentoDettaglio d = documenti.creaBozza(id);
            return new Azioni.Esito("/documenti/" + d.id(), d.derivataDaId() == null
                    ? "Bozza creata con i dati dell'incidente e della valutazione."
                    : "Bozza creata con i dati dell'ultimo documento trasmesso (versione " + d.derivataDaId() + ").");
        });
    }

    @PostMapping("/adempimenti/{id}/rettifica")
    public String rettifica(@PathVariable Long id, @RequestParam Long incidente,
                            @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        return azioni.esegui(ra, "/incidenti/" + incidente, () -> {
            DocumentoDettaglio d = documenti.rettifica(id, azioni.valida(new RettificaRequest(motivo)).motivo());
            return new Azioni.Esito("/documenti/" + d.id(), "Rettifica aperta: la versione trasmessa resta immutata.");
        });
    }

    // --- Riclassificazione ---------------------------------------------------------------------

    @GetMapping("/incidenti/{id}/riclassificazione")
    public String riclassificazione(@PathVariable Long id, Model m) {
        IncidenteDettaglio inc = incidenti.dettaglio(id);
        Optional<VersioneSintesi> ultimo = inc.adempimenti().stream().flatMap(a -> a.versioni().stream())
                .filter(v -> v.stato() == StatoDocumento.TRASMESSO)
                .max(Comparator.comparing(VersioneSintesi::trasmessaIl).thenComparing(VersioneSintesi::id));
        List<String> correnti = ultimo.map(u -> documenti.dettaglio(u.id()).voci().stream().map(VoceDto::codice).toList())
                .orElse(List.of());
        m.addAttribute("inc", inc);
        m.addAttribute("ultimo", ultimo.orElse(null));
        m.addAttribute("vociCorrenti", correnti);
        if (!m.containsAttribute("selezionate")) {
            m.addAttribute("selezionate", correnti);
        }
        m.addAttribute("valori", tassonomia.valoriVigenti());
        m.addAttribute("servizi", valutazione.servizi(null));
        if (!m.containsAttribute("valutazione")) {
            m.addAttribute("valutazione", new ModuloValutazione());
        }
        return "interfaccia/riclassificazione";
    }

    @PostMapping("/incidenti/{id}/riclassificazione")
    public String riclassifica(@PathVariable Long id, @ModelAttribute("valutazione") ModuloValutazione v,
                               @RequestParam(defaultValue = "false") boolean nuovaValutazione,
                               @RequestParam(required = false) List<String> voci,
                               @RequestParam(required = false) String motivazione,
                               @RequestParam(defaultValue = "false") boolean primaDelPrimoInvio, RedirectAttributes ra) {
        ra.addFlashAttribute("valutazione", v);
        ra.addFlashAttribute("selezionate", voci == null ? List.of() : voci);
        ra.addFlashAttribute("motivazione", motivazione);
        ra.addFlashAttribute("nuovaValutazione", nuovaValutazione);
        return azioni.esegui(ra, "/incidenti/" + id + "/riclassificazione", () -> {
            boolean conValutazione = nuovaValutazione || primaDelPrimoInvio;
            riclassificazione.riclassifica(id, azioni.valida(new RiclassificazioneRequest(
                    conValutazione ? v.richiesta() : null,
                    primaDelPrimoInvio ? null : (voci == null ? List.of() : voci), motivazione)));
            IncidenteDettaglio dopo = incidenti.dettaglio(id);
            return new Azioni.Esito("/incidenti/" + id, switch (dopo.stato()) {
                case NON_SIGNIFICATIVO -> "Incidente riportato a non significativo prima di ogni invio: nessun termine resta aperto.";
                case DECLASSATO -> "Incidente declassato: gli obblighi già sorti restano, il prossimo invio comunica il declassamento.";
                default -> "Riclassificazione registrata: il prossimo documento la comunica con la classificazione prima e dopo.";
            });
        });
    }
}
