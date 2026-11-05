package it.pw29.notifiche.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pw29.notifiche.domain.Adempimento;
import it.pw29.notifiche.domain.Esito3;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.Soggetto;
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
import it.pw29.notifiche.domain.contenuto.ContenutoJson;
import it.pw29.notifiche.domain.contenuto.Intestazione;
import it.pw29.notifiche.dto.AggiornaDocumentoRequest;
import it.pw29.notifiche.dto.DocumentoDettaglio;
import it.pw29.notifiche.dto.EsitoVerifica;
import it.pw29.notifiche.dto.TrasmissioneRequest;
import it.pw29.notifiche.dto.ValutazioneRequest;
import it.pw29.notifiche.dto.Violazione;
import it.pw29.notifiche.repository.AdempimentoRepository;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Redazione dei documenti: bozza con riuso dei dati, aggiornamento, verifica di completezza
 * differenziata per adempimento, invio in approvazione, respingimento, trasmissione.
 * <p>
 * Riuso dei dati: la bozza di un adempimento parte dal contenuto e dalla classificazione
 * dell'ultimo documento trasmesso per lo stesso incidente (es. la notifica dalla pre-notifica,
 * la relazione finale dalla notifica); il collegamento resta in {@code derivataDa}. Il primo
 * documento parte invece dai dati dell'incidente, del soggetto e dell'ultima valutazione.
 * La classificazione riportata è quella corrente: se dopo l'ultimo invio l'incidente è stato
 * riclassificato, la bozza nasce già con la classificazione nuova (ADR-0007). I campi ricavati dai fatti
 * (categorie, criteri, riclassificazioni, conclusione, riferimento della richiesta) li compila il sistema
 * ({@link CampiDiSistema}).
 * <p>
 * Trasmissione: la versione è sigillata nella catena di hash dell'incidente con l'intestazione del
 * momento (ADR-0006) e collega le riclassificazioni non ancora comunicate. Rettifica: nuova versione di un
 * adempimento trasmesso; per la relazione finale è ammessa anche a incidente chiuso.
 */
@Service
public class DocumentoService {

    private final AdempimentoRepository adempimenti;
    private final VersioneDocumentoRepository versioni;
    private final TassonomiaVersioneRepository tassonomie;
    private final TassonomiaVoceRepository vociTassonomia;
    private final ValutazioneSignificativitaRepository valutazioni;
    private final MatriceCampi matrice;
    private final CatenaService catena;
    private final ClassificazioneCorrente classificazione;
    private final CampiDiSistema campiDiSistema;
    private final StoricoRiclassificazioneRepository storico;
    private final AuditService audit;
    private final UtenteCorrente utente;
    private final ObjectMapper json;
    private final Clock clock;

    public DocumentoService(AdempimentoRepository adempimenti, VersioneDocumentoRepository versioni,
                            TassonomiaVersioneRepository tassonomie, TassonomiaVoceRepository vociTassonomia,
                            ValutazioneSignificativitaRepository valutazioni, MatriceCampi matrice,
                            CatenaService catena, ClassificazioneCorrente classificazione,
                            CampiDiSistema campiDiSistema, StoricoRiclassificazioneRepository storico,
                            AuditService audit, UtenteCorrente utente, ObjectMapper json, Clock clock) {
        this.adempimenti = adempimenti;
        this.versioni = versioni;
        this.tassonomie = tassonomie;
        this.vociTassonomia = vociTassonomia;
        this.valutazioni = valutazioni;
        this.matrice = matrice;
        this.catena = catena;
        this.classificazione = classificazione;
        this.campiDiSistema = campiDiSistema;
        this.storico = storico;
        this.audit = audit;
        this.utente = utente;
        this.json = json;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public DocumentoDettaglio creaBozza(Long adempimentoId) {
        Adempimento a = adempimenti.findById(adempimentoId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Adempimento", adempimentoId));
        Incidente inc = a.getIncidente();
        richiediInGestione(inc);
        Instant ora = Instant.now(clock);
        TassonomiaVersione vigente = vigente();

        ClassificazioneCorrente.Stato corrente = classificazione.di(inc);
        Optional<VersioneDocumento> fonte = corrente.ultimoTrasmesso();
        ContenutoDocumento contenuto = fonte.map(VersioneDocumento::getContenuto).orElseGet(() -> iniziale(inc));

        VersioneDocumento v;
        try {
            v = a.nuovaVersione(fonte.orElse(null), vigente, ora, utente.username());
        } catch (IllegalStateException e) {
            throw new TransizioneNonAmmessaException(e.getMessage());
        }
        List<String> nonRiportate = compilaDaCorrente(v, contenuto, corrente, ora);
        versioni.save(v);
        audit.registra("BOZZA_CREATA", "VersioneDocumento", v.getId(), a.getTipo() + " n. " + a.getProgressivo()
                + " v" + v.getNumero() + fonte.map(f -> ", dati ripresi dalla versione " + f.getId()).orElse(""));
        return dettaglio(v, nonRiportate);
    }

    /**
     * Apre la rettifica di un adempimento già trasmesso: nuova versione con i dati dell'ultima versione
     * trasmessa, che resta immutata. Si rettifica solo l'ultimo documento trasmesso dell'incidente:
     * un documento già superato da un invio successivo si aggiorna con il prossimo invio, non a ritroso.
     * La relazione finale si rettifica anche a incidente chiuso, perché dopo di essa non ci sono altri invii.
     */
    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public DocumentoDettaglio rettifica(Long adempimentoId, String motivo) {
        Adempimento a = adempimenti.findById(adempimentoId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Adempimento", adempimentoId));
        Incidente inc = a.getIncidente();
        boolean finaleChiusa = inc.getStato() == StatoIncidente.CHIUSO && a.getTipo() == TipoAdempimento.RELAZIONE_FINALE;
        if (!finaleChiusa) {
            richiediInGestione(inc);
        }
        ClassificazioneCorrente.Stato corrente = classificazione.di(inc);
        VersioneDocumento daCorreggere = a.ultimaTrasmessa().orElseThrow(() -> new TransizioneNonAmmessaException(
                a.getTipo().getEtichetta() + " non è ancora stata trasmessa: si corregge la bozza"));
        if (corrente.ultimoTrasmesso().map(u -> !u.getId().equals(daCorreggere.getId())).orElse(true)) {
            throw new TransizioneNonAmmessaException("Si rettifica solo l'ultimo documento trasmesso dell'incidente "
                    + "(versione " + corrente.ultimoTrasmesso().map(VersioneDocumento::getId).orElse(null)
                    + "): le informazioni aggiornate vanno nel prossimo invio");
        }
        Instant ora = Instant.now(clock);
        VersioneDocumento v;
        try {
            v = a.rettifica(motivo, vigente(), ora, utente.username());
        } catch (IllegalStateException e) {
            throw new TransizioneNonAmmessaException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new DatiNonValidiException(e.getMessage());
        }
        List<String> nonRiportate = compilaDaCorrente(v, daCorreggere.getContenuto(), corrente, ora);
        versioni.save(v);
        audit.registra("RETTIFICA_APERTA", "VersioneDocumento", v.getId(), a.getTipo() + " n. " + a.getProgressivo()
                + " v" + v.getNumero() + " rettifica la versione " + daCorreggere.getId() + ": " + motivo);
        return dettaglio(v, nonRiportate);
    }

    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public DocumentoDettaglio aggiorna(Long versioneId, AggiornaDocumentoRequest r) {
        VersioneDocumento v = trova(versioneId);
        richiediAmmessa(v);
        List<String> codici = r.voci() == null ? List.of() : r.voci().stream().distinct().toList();
        List<TassonomiaVoce> voci = risolviVoci(v, codici);
        // I campi ricavati dai fatti li compila il sistema: i valori inviati dal client sono ignorati.
        ContenutoDocumento contenuto = r.contenuto();
        campiDiSistema.compila(contenuto, v.getAdempimento());
        try {
            v.aggiorna(contenuto, voci, Instant.now(clock), utente.username());
        } catch (IllegalArgumentException e) {
            throw new DatiNonValidiException(e.getMessage());
        }
        audit.registra("BOZZA_AGGIORNATA", "VersioneDocumento", v.getId(), "Voci: " + codici);
        return dettaglio(v, List.of());
    }

    @Transactional(readOnly = true)
    public EsitoVerifica verifica(Long versioneId) {
        return verifica(trova(versioneId));
    }

    /**
     * Verifica di completezza di un contenuto <b>non salvato</b>: l'editor dell'interfaccia la chiama mentre
     * l'operatore compila, così i campi mancanti per l'adempimento si vedono prima del salvataggio. Nessun
     * effetto sul database; i campi compilati dal sistema sono ricavati dai fatti come al salvataggio.
     */
    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional(readOnly = true)
    public EsitoVerifica verificaProvvisoria(Long versioneId, AggiornaDocumentoRequest r) {
        VersioneDocumento v = trova(versioneId);
        if (v.getStato() != StatoDocumento.BOZZA) {
            return verifica(v);
        }
        List<TassonomiaVoce> voci = risolviVoci(v, r.voci());
        ContenutoDocumento contenuto = r.contenuto();
        campiDiSistema.compila(contenuto, v.getAdempimento());
        return verificaAperta(v, contenuto, voci, true);
    }

    @Transactional(readOnly = true)
    public DocumentoDettaglio dettaglio(Long versioneId) {
        return dettaglio(trova(versioneId), List.of());
    }

    /** Un documento incompleto per il proprio adempimento è rifiutato (HTTP 422 con i campi mancanti). */
    @PreAuthorize("hasRole('OPERATORE')")
    @Transactional
    public DocumentoDettaglio inviaInApprovazione(Long versioneId) {
        VersioneDocumento v = trova(versioneId);
        richiediAmmessa(v);
        campiDiSistema.riallinea(v);
        richiediCompleto(v);
        richiediPrecedenti(v.getAdempimento());
        v.inviaInApprovazione(Instant.now(clock), utente.username());
        audit.registra("INVIO_APPROVAZIONE", "VersioneDocumento", v.getId(), v.getAdempimento().getTipo().name());
        return dettaglio(v, List.of());
    }

    @PreAuthorize("hasRole('APPROVATORE')")
    @Transactional
    public DocumentoDettaglio respingi(Long versioneId, String nota) {
        VersioneDocumento v = trova(versioneId);
        v.respingi(nota, Instant.now(clock), utente.username());
        audit.registra("RESPINGIMENTO", "VersioneDocumento", v.getId(), nota);
        return dettaglio(v, List.of());
    }

    /**
     * Registra la trasmissione al CSIRT Italia. Effetti: la versione diventa immutabile e viene sigillata con
     * l'intestazione del momento; la notifica fa sorgere la relazione finale; la relazione finale chiude
     * l'incidente. La finale non si trasmette se un altro documento dell'incidente è in approvazione: dopo la
     * chiusura nessun altro invio è ammesso, salvo la rettifica della finale stessa.
     */
    @PreAuthorize("hasRole('APPROVATORE')")
    @Transactional
    public DocumentoDettaglio trasmetti(Long versioneId, TrasmissioneRequest r) {
        VersioneDocumento v = trova(versioneId);
        Adempimento a = v.getAdempimento();
        Incidente inc = a.getIncidente();
        richiediAmmessa(v);
        richiediCompleto(v);
        richiediPrecedenti(a);
        if (a.getTipo() == TipoAdempimento.RELAZIONE_FINALE
                && versioni.existsByAdempimentoIncidenteIdAndStatoAndIdNot(inc.getId(), StatoDocumento.IN_APPROVAZIONE, v.getId())) {
            throw new TransizioneNonAmmessaException("Un altro documento di " + inc.getCodice() + " è in approvazione: "
                    + "la relazione finale chiude l'incidente, quindi va prima trasmesso o respinto");
        }
        Instant ora = Instant.now(clock);
        Instant trasmessaIl = r.trasmessaIl() != null ? r.trasmessaIl() : ora;
        boolean primoInvio = a.getTrasmessoIl() == null;
        String anello = catena.anelloPerNuovaTrasmissione(inc);
        String intestazione = ContenutoJson.scrivi(Intestazione.di(inc));
        try {
            v.trasmetti(r.riferimento(), trasmessaIl, ora, utente.username(), intestazione, anello);
        } catch (IllegalArgumentException e) {
            throw new DatiNonValidiException(e.getMessage());
        }
        // Le riclassificazioni registrate dopo l'ultimo invio sono comunicate da questo documento.
        List<StoricoRiclassificazione> comunicate = storico
                .findByIncidenteIdAndDocumentoDopoIsNullOrderByRegistrataIlAscIdAsc(inc.getId());
        comunicate.forEach(rc -> rc.collegaDocumentoDopo(v));
        if (primoInvio && a.getTipo() == TipoAdempimento.NOTIFICA) {
            inc.aggiungiAdempimento(TipoAdempimento.RELAZIONE_FINALE);
        }
        if (a.getTipo() == TipoAdempimento.RELAZIONE_FINALE && inc.getStato() != StatoIncidente.CHIUSO) {
            inc.cambiaStato(StatoIncidente.CHIUSO);
        }
        adempimenti.flush();
        audit.registra("TRASMISSIONE", "VersioneDocumento", v.getId(), a.getTipo() + " n. " + a.getProgressivo()
                + " v" + v.getNumero() + (v.isRettifica() ? " (rettifica)" : "")
                + " trasmessa il " + trasmessaIl + ", riferimento portale " + r.riferimento()
                + ", impronta " + v.getImprontaSha256()
                + (comunicate.isEmpty() ? "" : ", comunica le riclassificazioni "
                        + comunicate.stream().map(rc -> String.valueOf(rc.getId())).collect(Collectors.joining(","))));
        return dettaglio(v, List.of());
    }

    // --- Supporto ------------------------------------------------------------------------------

    private VersioneDocumento trova(Long id) {
        return versioni.findById(id).orElseThrow(() -> new RisorsaNonTrovataException("Versione del documento", id));
    }

    /**
     * Completezza per adempimento (matrice dei campi), allineamento dei campi compilati dal sistema e, dopo la
     * notifica, coerenza della classificazione: un documento non ancora trasmesso deve riportare le voci
     * correnti. Cambiare la classificazione dopo la notifica richiede una riclassificazione motivata, che resta
     * tracciata (ADR-0007). Prima della notifica la classificazione si affina liberamente: la pre-notifica è
     * per sua natura preliminare.
     */
    private EsitoVerifica verifica(VersioneDocumento v) {
        if (v.getStato() == StatoDocumento.TRASMESSO) {
            return matrice.verifica(v.getAdempimento().getTipo(), v.getContenuto(), v.getVoci());
        }
        return verificaAperta(v, v.getContenuto(), v.getVoci(), campiDiSistema.allineati(v));
    }

    /** Verifica di un contenuto non ancora salvato, o salvato, per una versione non trasmessa. */
    private EsitoVerifica verificaAperta(VersioneDocumento v, ContenutoDocumento contenuto,
                                         Collection<TassonomiaVoce> vociDocumento, boolean allineata) {
        EsitoVerifica base = matrice.verifica(v.getAdempimento().getTipo(), contenuto, vociDocumento);
        List<Violazione> violazioni = new ArrayList<>(base.violazioni());
        if (!allineata) {
            violazioni.add(new Violazione("campiDiSistema", "Campi compilati dal sistema",
                    "Categorie, criteri, riclassificazioni o conclusione sono cambiati dopo l'ultimo aggiornamento: "
                            + "salvare di nuovo la bozza (se in approvazione, va prima respinta)",
                    "ADR-0005, ADR-0007"));
        }
        Incidente inc = v.getAdempimento().getIncidente();
        if (inc.trasmesso(TipoAdempimento.NOTIFICA)) {
            SortedSet<String> correnti = classificazione.di(inc).voci();
            SortedSet<String> documento = vociDocumento.stream().map(TassonomiaVoce::getCodice)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (!correnti.equals(documento)) {
                violazioni.add(new Violazione("classificazione", "Coerenza con la classificazione comunicata",
                        "Le voci " + documento + " differiscono dalla classificazione corrente " + correnti
                                + ": dopo la notifica un cambio di classificazione passa dalla riclassificazione motivata",
                        "Traccia PW 29, punto 3 (tracciabilità della riclassificazione); ADR-0007"));
            }
        }
        return EsitoVerifica.di(base.adempimento(), violazioni);
    }

    private void richiediCompleto(VersioneDocumento v) {
        EsitoVerifica esito = verifica(v);
        if (!esito.completo()) {
            throw new DocumentoNonValidoException(esito);
        }
    }

    private static void richiediInGestione(Incidente inc) {
        if (!inc.getStato().inGestione()) {
            throw new TransizioneNonAmmessaException("L'incidente " + inc.getCodice() + " è " + inc.getStato()
                    + ": si redigono documenti solo per incidenti in gestione (significativi o declassati)");
        }
    }

    /**
     * Operazioni su una versione (modifica, invio, trasmissione): ammesse se l'incidente è in gestione, oppure
     * per la rettifica della relazione finale di un incidente chiuso. Così una bozza rimasta aperta non si
     * invia dopo la chiusura, e un incidente riportato a non significativo prima di ogni invio non produce invii.
     */
    private static void richiediAmmessa(VersioneDocumento v) {
        Incidente inc = v.getAdempimento().getIncidente();
        boolean rettificaFinale = inc.getStato() == StatoIncidente.CHIUSO && v.isRettifica()
                && v.getAdempimento().getTipo() == TipoAdempimento.RELAZIONE_FINALE;
        if (!inc.getStato().inGestione() && !rettificaFinale) {
            throw new TransizioneNonAmmessaException("L'incidente " + inc.getCodice() + " è " + inc.getStato()
                    + ": la versione " + v.getId() + " non si modifica né si invia (ammessa solo la rettifica della "
                    + "relazione finale di un incidente chiuso)");
        }
    }

    /** Voci della tassonomia della versione per codice (il livello VALORE è verificato dal dominio al salvataggio). */
    private List<TassonomiaVoce> risolviVoci(VersioneDocumento v, List<String> richiesti) {
        List<String> codici = richiesti == null ? List.of() : richiesti.stream().distinct().toList();
        List<TassonomiaVoce> voci = codici.isEmpty() ? List.of()
                : vociTassonomia.findByVersioneIdAndCodiceIn(v.getTassonomiaVersione().getId(), codici);
        if (voci.size() != codici.size()) {
            List<String> sconosciuti = new ArrayList<>(codici);
            voci.forEach(t -> sconosciuti.remove(t.getCodice()));
            throw new DatiNonValidiException("Codici non presenti nella tassonomia "
                    + v.getTassonomiaVersione().getCodice() + ": " + sconosciuti);
        }
        return voci;
    }

    private TassonomiaVersione vigente() {
        return tassonomie.findByVigenteTrue()
                .orElseThrow(() -> new IllegalStateException("Nessuna versione vigente della tassonomia"));
    }

    /**
     * Completa una nuova versione: voci della classificazione corrente riportate per codice nella tassonomia
     * della versione (i codici non più presenti sono segnalati e non copiati) e campi compilati dal sistema.
     */
    private List<String> compilaDaCorrente(VersioneDocumento v, ContenutoDocumento contenuto,
                                           ClassificazioneCorrente.Stato corrente, Instant ora) {
        SortedSet<String> codici = corrente.voci();
        List<TassonomiaVoce> voci = codici.isEmpty() ? List.of()
                : vociTassonomia.findByVersioneIdAndCodiceIn(v.getTassonomiaVersione().getId(), codici);
        List<String> nonRiportate = new ArrayList<>(codici);
        voci.forEach(t -> nonRiportate.remove(t.getCodice()));
        campiDiSistema.compila(contenuto, v.getAdempimento());
        v.aggiorna(contenuto, voci, ora, utente.username());
        return nonRiportate;
    }

    /**
     * Ordine degli invii: la notifica aggiorna la pre-notifica (lett. b), quindi la presuppone;
     * relazioni intermedie, mensili e finale presuppongono rispettivamente pre-notifica e notifica.
     */
    private static void richiediPrecedenti(Adempimento a) {
        Incidente inc = a.getIncidente();
        TipoAdempimento richiesto = switch (a.getTipo()) {
            case PRE_NOTIFICA -> null;
            case NOTIFICA, RELAZIONE_INTERMEDIA -> TipoAdempimento.PRE_NOTIFICA;
            case RELAZIONE_MENSILE, RELAZIONE_FINALE -> TipoAdempimento.NOTIFICA;
        };
        if (richiesto != null && !inc.trasmesso(richiesto)) {
            throw new DatiNonValidiException(a.getTipo().getEtichetta() + " richiede che "
                    + richiesto.getEtichetta().toLowerCase() + " sia già stata trasmessa");
        }
    }

    private DocumentoDettaglio dettaglio(VersioneDocumento v, List<String> nonRiportate) {
        return Mappatura.documento(v, matrice.campiPrevisti(v.getAdempimento().getTipo()), verifica(v), nonRiportate);
    }

    /** Contenuto di partenza del primo documento: dati dell'incidente, del soggetto e dell'ultima valutazione. */
    private ContenutoDocumento iniziale(Incidente inc) {
        Soggetto s = inc.getSoggetto();
        ContenutoDocumento c = new ContenutoDocumento();
        c.setDescrizioneSintetica(inc.getTitolo());
        c.setReferenteNome(s.getReferenteNome());
        c.setReferenteEmail(s.getReferenteEmail());
        c.setReferenteTelefono(s.getReferenteTelefono());
        valutazioni.findFirstByIncidenteIdOrderByEseguitaIlDescIdDesc(inc.getId()).ifPresent(val -> {
            c.setServiziImpattati(List.of(val.getServizio()));
            ValutazioneRequest in = leggiInput(val);
            c.setUtentiImpattati(in.utentiImpattati());
            // Il sospetto di dolo è un indizio, non una conclusione: SI se sospettato, altrimenti NON_NOTO.
            c.setAttoIllegittimoOMalevolo(Boolean.TRUE.equals(in.sospettoDoloso()) ? Esito3.SI : Esito3.NON_NOTO);
        });
        return c;
    }

    private ValutazioneRequest leggiInput(ValutazioneSignificativita v) {
        try {
            return json.readValue(v.getInputJson(), ValutazioneRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Input della valutazione " + v.getId() + " non leggibile", e);
        }
    }
}
