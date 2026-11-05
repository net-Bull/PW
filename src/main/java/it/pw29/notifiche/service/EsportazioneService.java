package it.pw29.notifiche.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.pw29.notifiche.domain.Adempimento;
import it.pw29.notifiche.domain.CatenaImpronte;
import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.Regola;
import it.pw29.notifiche.domain.Esito3;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.TassonomiaVersione;
import it.pw29.notifiche.domain.TassonomiaVoce;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.domain.contenuto.ContenutoJson;
import it.pw29.notifiche.domain.contenuto.IndicatoreCompromissione;
import it.pw29.notifiche.domain.contenuto.Intestazione;
import it.pw29.notifiche.domain.contenuto.Riclassificazione;
import it.pw29.notifiche.dto.DocumentoEsportato;
import it.pw29.notifiche.dto.RigaMatrice.Requisito;
import it.pw29.notifiche.dto.VistaStampa;
import it.pw29.notifiche.repository.LivelloServizioRepository;
import it.pw29.notifiche.repository.VersioneDocumentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Esportazione dei documenti verso il CSIRT Italia nei due formati richiesti: strutturato (JSON) e
 * stampabile (PDF/A). Entrambi derivano dalla stessa rappresentazione ({@link DocumentoEsportato}),
 * che contiene solo i campi previsti per l'adempimento dalla matrice campo/adempimento.
 * <p>
 * L'esportazione di un documento non ancora trasmesso è la copia da caricare sul portale ACN; quella
 * di un documento trasmesso è la copia conforme archiviata, con la sua impronta nella catena.
 * Ogni esportazione è registrata nell'audit.
 */
@Service
public class EsportazioneService {

    /** File esportato: nome suggerito per il download e contenuto. */
    public record FileEsportato(String nome, String tipo, byte[] dati) {
    }

    private final DateTimeFormatter dataOra;
    private static final String DESTINATARIO = "CSIRT Italia - Agenzia per la cybersicurezza nazionale";
    private static final String CANALE = "Servizi NIS del Portale dei Servizi ACN, https://portale.acn.gov.it/";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private final VersioneDocumentoRepository versioni;
    private final LivelloServizioRepository livelli;
    private final MatriceCampi matrice;
    private final CatenaService catena;
    private final StampaPdf stampa;
    private final AuditService audit;
    private final UtenteCorrente utente;
    private final Clock clock;

    public EsportazioneService(VersioneDocumentoRepository versioni, LivelloServizioRepository livelli,
                               MatriceCampi matrice, CatenaService catena, StampaPdf stampa,
                               AuditService audit, UtenteCorrente utente, Clock clock,
                               @Value("${notifiche.fuso-orario:Europe/Rome}") ZoneId fuso) {
        this.dataOra = DateTimeFormatter.ofPattern("dd/MM/yyyy 'ore' HH:mm").withZone(fuso);
        this.versioni = versioni;
        this.livelli = livelli;
        this.matrice = matrice;
        this.catena = catena;
        this.stampa = stampa;
        this.audit = audit;
        this.utente = utente;
        this.clock = clock;
    }

    @Transactional
    public FileEsportato json(Long versioneId) {
        VersioneDocumento v = trova(versioneId);
        DocumentoEsportato doc = documento(v);
        audit.registra("ESPORTAZIONE_JSON", "VersioneDocumento", v.getId(), nome(v, "json"));
        try {
            return new FileEsportato(nome(v, "json"), "application/json", JSON.writeValueAsBytes(doc));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Documento non serializzabile", e);
        }
    }

    @Transactional
    public FileEsportato pdf(Long versioneId) {
        VersioneDocumento v = trova(versioneId);
        byte[] pdf = stampa.pdf(vista(v, documento(v)));
        audit.registra("ESPORTAZIONE_PDF", "VersioneDocumento", v.getId(), nome(v, "pdf"));
        return new FileEsportato(nome(v, "pdf"), "application/pdf", pdf);
    }

    /**
     * Anteprima HTML del documento stampabile: lo stesso template e la stessa vista del PDF, per controllare il
     * documento nel browser prima dell'invio. Non è un'esportazione e non è registrata nell'audit.
     */
    @Transactional(readOnly = true)
    public String anteprimaHtml(Long versioneId) {
        VersioneDocumento v = trova(versioneId);
        return stampa.html(vista(v, documento(v)));
    }

    /** Rappresentazione strutturata, usata anche dai test e dall'interfaccia. */
    @Transactional(readOnly = true)
    public DocumentoEsportato documento(Long versioneId) {
        return documento(trova(versioneId));
    }

    // --- Costruzione del documento --------------------------------------------------------------

    /**
     * Per un documento trasmesso con lo schema corrente, soggetto e incidente vengono dall'intestazione
     * sigillata alla trasmissione: la copia conforme non cambia se l'incidente evolve (ADR-0008). Per una bozza,
     * o per un documento sigillato con lo schema v1 (che non conservava l'intestazione), sono i dati correnti.
     */
    DocumentoEsportato documento(VersioneDocumento v) {
        Adempimento a = v.getAdempimento();
        Incidente inc = a.getIncidente();
        TassonomiaVersione t = v.getTassonomiaVersione();
        Intestazione intestazione = v.getIntestazioneJson() != null
                ? ContenutoJson.leggi(v.getIntestazioneJson(), Intestazione.class)
                : Intestazione.di(inc);
        Intestazione.Soggetto s = intestazione.soggetto();
        Intestazione.Incidente i = intestazione.incidente();

        Map<String, Object> contenuto = new LinkedHashMap<>();
        for (MatriceCampi.ValoreCampo c : matrice.valoriPrevisti(a.getTipo(), v.getContenuto())) {
            if (!c.vuoto()) {
                contenuto.put(c.nome(), c.valore());
            }
        }
        boolean trasmessa = v.getStato() == StatoDocumento.TRASMESSO;
        DocumentoEsportato.Trasmissione trasmissione = trasmessa
                ? new DocumentoEsportato.Trasmissione(v.getTrasmessaIl(), v.getRiferimentoTrasmissione(),
                        v.getApprovataDa(), v.getTrasmissioneRegistrataIl())
                : null;
        DocumentoEsportato.Catena anello = trasmessa
                ? new DocumentoEsportato.Catena("SHA-256 (" + CatenaImpronte.schemaDi(v) + ")", v.getImprontaPrecedente(),
                        v.getImprontaSha256())
                : new DocumentoEsportato.Catena("SHA-256 (" + CatenaImpronte.SCHEMA + ")",
                        catena.percorri(inc).ultimaImpronta(), null);

        return new DocumentoEsportato(
                DocumentoEsportato.SCHEMA,
                new DocumentoEsportato.Destinatario(DESTINATARIO, CANALE),
                new DocumentoEsportato.Adempimento(a.getTipo(), a.getTipo().getEtichetta(),
                        a.getTipo().getRiferimento() + " D.Lgs. 138/2024", a.getProgressivo()),
                new DocumentoEsportato.Soggetto(s.denominazione(), s.codiceFiscale(), s.tipo(), s.settore()),
                new DocumentoEsportato.Incidente(i.codice(), i.titolo(), i.stato(), i.rilevatoIl(),
                        i.conoscenzaIl(), i.conclusioneGestioneIl()),
                new DocumentoEsportato.Versione(v.getId(), v.getNumero(), v.getStato(),
                        v.getDerivataDa() == null ? null : v.getDerivataDa().getId(),
                        v.isRettifica() ? v.getDerivataDa().getId() : null, v.getMotivoRettifica(),
                        v.getCreataIl(), v.getCreataDa(), v.getInviataApprovazioneDa(), v.getApprovataDa()),
                new DocumentoEsportato.Classificazione(t.getCodice(), t.getVersioneDocumento(), t.getDataAcquisizione(),
                        v.getVoci().stream().sorted(Comparator.comparing(TassonomiaVoce::getCodice))
                                .map(x -> new DocumentoEsportato.Voce(x.getCodice(), x.getNome(), percorso(x))).toList()),
                contenuto,
                trasmissione,
                anello,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                utente.username());
    }

    // --- Vista di stampa ------------------------------------------------------------------------

    VistaStampa vista(VersioneDocumento v, DocumentoEsportato doc) {
        Adempimento a = v.getAdempimento();
        Incidente inc = a.getIncidente();
        DocumentoEsportato.Incidente intestazione = doc.incidente();
        List<VistaStampa.Riga> righe = new ArrayList<>();
        List<VistaStampa.RiclassificazioneStampa> riclassificazioni = new ArrayList<>();
        for (MatriceCampi.ValoreCampo c : matrice.valoriPrevisti(a.getTipo(), v.getContenuto())) {
            if (c.valore() instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Riclassificazione) {
                l.forEach(r -> riclassificazioni.add(riclassificazione((Riclassificazione) r)));
                continue;
            }
            if (c.vuoto() && c.requisito() == Requisito.FACOLTATIVO) {
                continue;
            }
            righe.add(new VistaStampa.Riga(c.etichetta(), testo(c.valore(), inc.getSoggetto().getId()),
                    c.requisito() == Requisito.OBBLIGATORIO));
        }
        String titolo = a.getTipo().getEtichetta() + (a.getTipo().ripetibile() ? " n. " + a.getProgressivo() : "")
                + " - " + intestazione.codice();
        String banda;
        String classe;
        switch (v.getStato()) {
            case TRASMESSO -> {
                banda = "Copia del documento trasmesso al CSIRT Italia il " + dataOra.format(v.getTrasmessaIl())
                        + " - riferimento portale " + v.getRiferimentoTrasmissione();
                classe = "trasmesso";
            }
            case IN_APPROVAZIONE -> {
                banda = "In approvazione - documento da caricare sul portale ACN dopo l'approvazione";
                classe = "approvazione";
            }
            default -> {
                banda = "BOZZA - non approvata, non trasmettere";
                classe = "bozza";
            }
        }
        return new VistaStampa(titolo, banda, classe, doc,
                data(intestazione.rilevatoIl()), data(intestazione.conoscenzaIl()), stato(intestazione.stato()),
                data(v.getTrasmessaIl()),
                data(doc.generatoIl()), righe, riclassificazioni);
    }

    private VistaStampa.RiclassificazioneStampa riclassificazione(Riclassificazione r) {
        return new VistaStampa.RiclassificazioneStampa(
                data(r.registrataIl()),
                (r.significativoPrima() ? "Significativo" : "Non significativo") + " -> "
                        + (r.significativoDopo() ? "significativo" : "non significativo (declassato)"),
                categorie(r.categorieIsPrima()) + " -> " + categorie(r.categorieIsDopo()),
                String.join(", ", r.vociPrima()),
                String.join(", ", r.vociDopo()),
                r.riferimentoDocumentoPrima() == null ? "-" : r.riferimentoDocumentoPrima(),
                r.motivazione());
    }

    /** Valore di un campo in righe di testo leggibili. */
    private List<String> testo(Object valore, Long soggettoId) {
        if (valore == null || (valore instanceof String s && s.isBlank())
                || (valore instanceof Collection<?> c && c.isEmpty())) {
            return List.of("(non compilato)");
        }
        if (valore instanceof Instant i) {
            return List.of(data(i));
        }
        if (valore instanceof Integer n) {
            return List.of(String.format(java.util.Locale.ITALY, "%,d", n));
        }
        if (valore instanceof Esito3 e) {
            return List.of(switch (e) {
                case SI -> "Sì";
                case NO -> "No";
                case NON_NOTO -> "Non noto";
            });
        }
        if (valore instanceof Collection<?> c) {
            List<String> linee = new ArrayList<>();
            for (Object o : c) {
                linee.add(switch (o) {
                    case CategoriaIs cat -> cat.getCodice() + " - " + cat.getDescrizione();
                    case Regola reg -> reg.name() + " - " + reg.getDescrizione() + " (" + reg.getFonte() + ")";
                    case IndicatoreCompromissione ioc -> ioc.tipo() + ": " + ioc.valore()
                            + (ioc.nota() == null || ioc.nota().isBlank() ? "" : " (" + ioc.nota() + ")");
                    case String servizio -> livelli.findBySoggettoIdAndServizio(soggettoId, servizio)
                            .map(l -> l.getDescrizione() + " (" + servizio + ")").orElse(servizio);
                    default -> String.valueOf(o);
                });
            }
            return linee;
        }
        return List.of(String.valueOf(valore).split("\\R"));
    }

    private static String stato(StatoIncidente s) {
        return switch (s) {
            case IN_VALUTAZIONE -> "In valutazione";
            case NON_SIGNIFICATIVO -> "Non significativo";
            case SIGNIFICATIVO -> "Significativo";
            case DECLASSATO -> "Declassato: non più significativo dopo un invio (obblighi già sorti in corso)";
            case CHIUSO -> "Chiuso";
        };
    }

    /** Codici delle categorie IS nella forma ufficiale (IS-1) anziché come nome dell'enum (IS_1). */
    private static String categorie(List<String> codici) {
        return codici.isEmpty() ? "nessuna" : String.join(", ", codici.stream().map(c -> c.replace('_', '-')).toList());
    }

    private static String percorso(TassonomiaVoce v) {
        List<String> nomi = new ArrayList<>();
        for (TassonomiaVoce x = v; x != null; x = x.getPadre()) {
            nomi.add(0, x.getNome());
        }
        return String.join(" > ", nomi);
    }

    String data(Instant i) {
        return i == null ? "-" : dataOra.format(i) + " (ora italiana)";
    }

    private static String nome(VersioneDocumento v, String estensione) {
        Adempimento a = v.getAdempimento();
        return a.getIncidente().getCodice() + "_" + a.getTipo().name() + "_" + a.getProgressivo()
                + "_v" + v.getNumero() + (v.getStato() == StatoDocumento.TRASMESSO ? "" : "_" + v.getStato().name())
                + "." + estensione;
    }

    private VersioneDocumento trova(Long id) {
        return versioni.findById(id).orElseThrow(() -> new RisorsaNonTrovataException("Versione del documento", id));
    }
}
