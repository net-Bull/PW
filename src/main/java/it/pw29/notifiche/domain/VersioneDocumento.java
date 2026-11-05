package it.pw29.notifiche.domain;

import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import it.pw29.notifiche.domain.contenuto.ContenutoJson;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Versione di un documento destinato al CSIRT Italia. Il contenuto è un documento JSON
 * ({@link ContenutoDocumento}) e la classificazione è un insieme di voci della tassonomia ACN
 * di livello VALORE, legate alla versione della tassonomia vigente alla creazione.
 * <p>
 * Immutabilità: una versione TRASMESSA non si modifica né si cancella. Il vincolo è imposto due
 * volte: dai metodi di dominio (che rifiutano la modifica) e dai callback JPA {@code @PreUpdate}
 * e {@code @PreRemove}, che bloccano anche scritture fatte aggirando i metodi. Alla trasmissione la
 * versione viene sigillata nella catena di hash dell'incidente ({@link CatenaImpronte}, ADR-0006).
 * <p>
 * Rettifica: un documento trasmesso non si corregge, si sostituisce con una nuova versione dello
 * stesso adempimento, con motivo obbligatorio e collegamento alla versione corretta ({@code derivataDa}).
 * <p>
 * Quattro occhi: la versione registra i propri <b>autori</b> (chi ne ha scritto contenuto o classificazione).
 * Né gli autori né chi l'ha inviata in approvazione possono registrarne la trasmissione.
 */
@Entity
@Table(name = "versione_documento")
public class VersioneDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adempimento_id")
    private Adempimento adempimento;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoDocumento stato;

    @Column(name = "contenuto_json", nullable = false, length = 200000)
    private String contenutoJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tassonomia_versione_id")
    private TassonomiaVersione tassonomiaVersione;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "derivata_da_id")
    private VersioneDocumento derivataDa;

    @ManyToMany
    @JoinTable(name = "classificazione",
            joinColumns = @JoinColumn(name = "versione_documento_id"),
            inverseJoinColumns = @JoinColumn(name = "voce_id"))
    private Set<TassonomiaVoce> voci = new LinkedHashSet<>();

    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;

    @Column(name = "creata_da", nullable = false, length = 50)
    private String creataDa;

    @Column(name = "modificata_il", nullable = false)
    private Instant modificataIl;

    @Column(name = "modificata_da", nullable = false, length = 50)
    private String modificataDa;

    @Column(name = "inviata_approvazione_il")
    private Instant inviataApprovazioneIl;

    @Column(name = "inviata_approvazione_da", length = 50)
    private String inviataApprovazioneDa;

    @Column(name = "nota_respingimento", length = 2000)
    private String notaRespingimento;

    @Column(name = "approvata_da", length = 50)
    private String approvataDa;

    @Column(name = "trasmessa_il")
    private Instant trasmessaIl;

    @Column(name = "riferimento_trasmissione")
    private String riferimentoTrasmissione;

    @Column(name = "impronta_sha256", length = 64)
    private String improntaSha256;

    @Column(name = "impronta_precedente", length = 64)
    private String improntaPrecedente;

    @Column(name = "motivo_rettifica", length = 2000)
    private String motivoRettifica;

    @Column(name = "trasmissione_registrata_il")
    private Instant trasmissioneRegistrataIl;

    /** Soggetto e incidente come erano alla trasmissione (JSON canonico di {@code Intestazione}). */
    @Column(name = "intestazione_json", length = 4000)
    private String intestazioneJson;

    /** Schema dell'impronta con cui la versione è stata sigillata (pw29-catena-v1 per le versioni precedenti). */
    @Column(name = "schema_impronta", length = 20)
    private String schemaImpronta;

    @ElementCollection
    @CollectionTable(name = "versione_autore", joinColumns = @JoinColumn(name = "versione_documento_id"))
    @Column(name = "username", nullable = false, length = 50)
    private Set<String> autori = new TreeSet<>();

    @Version
    private long versione;

    @Transient
    private StatoDocumento statoAlCaricamento;

    protected VersioneDocumento() {
    }

    VersioneDocumento(Adempimento adempimento, int numero, VersioneDocumento derivataDa, String motivoRettifica,
                      TassonomiaVersione tassonomia, Instant ora, String utente) {
        this.adempimento = adempimento;
        this.numero = numero;
        this.derivataDa = derivataDa;
        this.motivoRettifica = motivoRettifica;
        this.tassonomiaVersione = tassonomia;
        this.stato = StatoDocumento.BOZZA;
        this.contenutoJson = ContenutoJson.scrivi(new ContenutoDocumento());
        this.creataIl = ora;
        this.creataDa = utente;
        this.modificataIl = ora;
        this.modificataDa = utente;
    }

    // --- Lettura del contenuto ------------------------------------------------------------

    /** Restituisce una copia del contenuto: modificarla non altera la versione. */
    public ContenutoDocumento getContenuto() {
        return ContenutoJson.leggi(contenutoJson);
    }

    // --- Operazioni ammesse solo in bozza ---------------------------------------------------

    public void aggiorna(ContenutoDocumento contenuto, Collection<TassonomiaVoce> nuoveVoci, Instant ora, String utente) {
        richiediBozza();
        for (TassonomiaVoce v : nuoveVoci) {
            if (v.getLivello() != LivelloTassonomia.VALORE) {
                throw new IllegalArgumentException("Solo le voci di livello VALORE classificano un incidente: "
                        + v.getCodice() + " è di livello " + v.getLivello());
            }
            if (!v.getVersione().getId().equals(tassonomiaVersione.getId())) {
                throw new IllegalArgumentException("La voce " + v.getCodice()
                        + " non appartiene alla versione della tassonomia del documento");
            }
        }
        this.contenutoJson = ContenutoJson.scrivi(contenuto);
        this.voci.clear();
        this.voci.addAll(nuoveVoci);
        this.modificataIl = ora;
        this.modificataDa = utente;
        this.autori.add(utente);
    }

    /**
     * Riallinea i campi compilati dal sistema (categorie, criteri, riclassificazioni, conclusione): non è
     * scrittura dell'operatore, quindi non aggiunge autori e non cambia l'ultima modifica.
     */
    public void allineaCampiDiSistema(ContenutoDocumento contenuto) {
        richiediBozza();
        this.contenutoJson = ContenutoJson.scrivi(contenuto);
    }

    public void inviaInApprovazione(Instant ora, String utente) {
        stato.verificaTransizione(StatoDocumento.IN_APPROVAZIONE);
        this.stato = StatoDocumento.IN_APPROVAZIONE;
        this.inviataApprovazioneIl = ora;
        this.inviataApprovazioneDa = utente;
        this.notaRespingimento = null;
    }

    public void respingi(String nota, Instant ora, String utente) {
        stato.verificaTransizione(StatoDocumento.BOZZA);
        this.stato = StatoDocumento.BOZZA;
        this.notaRespingimento = nota;
        this.modificataIl = ora;
        this.modificataDa = utente;
    }

    /**
     * Registra la trasmissione al CSIRT Italia (portale segnalazioni ACN) e sigilla la versione nella
     * catena di hash agganciandola all'anello indicato. Da qui la versione è immutabile.
     * Principio dei quattro occhi: chi ha inviato in approvazione o ha scritto la versione non può approvarla.
     * Gli istanti sono troncati al millisecondo, la precisione conservata dal database: l'impronta
     * ricalcolata dopo una rilettura deve coincidere con quella calcolata qui.
     */
    public void trasmetti(String riferimento, Instant trasmessaIl, Instant ora, String approvatore,
                          String intestazione, String improntaPrecedente) {
        if (improntaPrecedente == null || improntaPrecedente.length() != 64) {
            throw new IllegalArgumentException("Anello precedente della catena di hash mancante o non valido");
        }
        if (intestazione == null || intestazione.isBlank()) {
            throw new IllegalArgumentException("Intestazione del documento mancante");
        }
        stato.verificaTransizione(StatoDocumento.TRASMESSO);
        if (approvatore.equals(inviataApprovazioneDa) || autori.contains(approvatore)) {
            throw new QuattroOcchiException(approvatore);
        }
        if (trasmessaIl.isAfter(ora)) {
            throw new IllegalArgumentException("L'istante di trasmissione non può essere futuro");
        }
        if (trasmessaIl.isBefore(inviataApprovazioneIl)) {
            throw new IllegalArgumentException("La trasmissione non può precedere l'invio in approvazione");
        }
        trasmessaIl = trasmessaIl.truncatedTo(ChronoUnit.MILLIS);
        ora = ora.truncatedTo(ChronoUnit.MILLIS);
        this.stato = StatoDocumento.TRASMESSO;
        this.approvataDa = approvatore;
        this.trasmessaIl = trasmessaIl;
        this.riferimentoTrasmissione = riferimento;
        this.trasmissioneRegistrataIl = ora;
        this.intestazioneJson = intestazione;
        this.schemaImpronta = CatenaImpronte.SCHEMA;
        this.improntaPrecedente = improntaPrecedente;
        this.improntaSha256 = CatenaImpronte.impronta(this, improntaPrecedente);
        adempimento.segnaTrasmesso(trasmessaIl);
    }

    /** Vero se la versione sostituisce una versione già trasmessa dello stesso adempimento. */
    public boolean isRettifica() {
        return motivoRettifica != null;
    }

    private void richiediBozza() {
        if (stato == StatoDocumento.TRASMESSO) {
            throw new DocumentoImmutabileException(id);
        }
        if (stato != StatoDocumento.BOZZA) {
            throw new TransizioneNonAmmessaException("Il documento è " + stato
                    + ": si modifica solo in BOZZA (va prima respinto dall'approvatore)");
        }
    }

    // --- Guardie di persistenza ---------------------------------------------------------------

    @PostLoad
    void ricordaStatoCaricato() {
        this.statoAlCaricamento = stato;
    }

    @PreUpdate
    void impedisciModificaSeTrasmessa() {
        if (statoAlCaricamento == StatoDocumento.TRASMESSO) {
            throw new DocumentoImmutabileException(id);
        }
    }

    @PreRemove
    void impedisciCancellazioneSeTrasmessa() {
        if (stato == StatoDocumento.TRASMESSO || statoAlCaricamento == StatoDocumento.TRASMESSO) {
            throw new DocumentoImmutabileException(id);
        }
    }

    public Long getId() { return id; }
    public Adempimento getAdempimento() { return adempimento; }
    public int getNumero() { return numero; }
    public StatoDocumento getStato() { return stato; }
    public String getContenutoJson() { return contenutoJson; }
    public TassonomiaVersione getTassonomiaVersione() { return tassonomiaVersione; }
    public VersioneDocumento getDerivataDa() { return derivataDa; }
    public Set<TassonomiaVoce> getVoci() { return Set.copyOf(voci); }
    public Instant getCreataIl() { return creataIl; }
    public String getCreataDa() { return creataDa; }
    public Instant getModificataIl() { return modificataIl; }
    public String getModificataDa() { return modificataDa; }
    public Instant getInviataApprovazioneIl() { return inviataApprovazioneIl; }
    public String getInviataApprovazioneDa() { return inviataApprovazioneDa; }
    public String getNotaRespingimento() { return notaRespingimento; }
    public String getApprovataDa() { return approvataDa; }
    public Instant getTrasmessaIl() { return trasmessaIl; }
    public String getRiferimentoTrasmissione() { return riferimentoTrasmissione; }
    public String getImprontaSha256() { return improntaSha256; }
    public String getImprontaPrecedente() { return improntaPrecedente; }
    public String getMotivoRettifica() { return motivoRettifica; }
    public Instant getTrasmissioneRegistrataIl() { return trasmissioneRegistrataIl; }
    public String getIntestazioneJson() { return intestazioneJson; }
    public String getSchemaImpronta() { return schemaImpronta; }
    public SortedSet<String> getAutori() { return new TreeSet<>(autori); }
}
