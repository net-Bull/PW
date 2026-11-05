package it.pw29.notifiche.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Traccia prima/dopo di una riclassificazione tardiva (ADR-0007): cambio di significatività,
 * di categorie IS o di voci della tassonomia avvenuto dopo che almeno un documento è stato trasmesso.
 * <p>
 * {@code documentoPrima} è l'ultimo documento trasmesso al momento della riclassificazione (ciò che
 * il CSIRT Italia conosce); {@code documentoDopo} è il primo documento trasmesso dopo la
 * riclassificazione, che la comunica. Finché è nullo, la riclassificazione non è ancora stata comunicata.
 * A parte il collegamento al documento successivo, la riga non cambia.
 */
@Entity
@Table(name = "storico_riclassificazione")
public class StoricoRiclassificazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidente_id")
    private Incidente incidente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_prima_id")
    private VersioneDocumento documentoPrima;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_dopo_id")
    private VersioneDocumento documentoDopo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valutazione_id")
    private ValutazioneSignificativita valutazione;

    @Enumerated(EnumType.STRING)
    @Column(name = "stato_incidente_prima", nullable = false, length = 20)
    private StatoIncidente statoIncidentePrima;

    @Enumerated(EnumType.STRING)
    @Column(name = "stato_incidente_dopo", nullable = false, length = 20)
    private StatoIncidente statoIncidenteDopo;

    @Column(name = "significativo_prima", nullable = false)
    private boolean significativoPrima;

    @Column(name = "significativo_dopo", nullable = false)
    private boolean significativoDopo;

    @Column(name = "categorie_is_prima", nullable = false, length = 100)
    private String categorieIsPrima;

    @Column(name = "categorie_is_dopo", nullable = false, length = 100)
    private String categorieIsDopo;

    @Column(name = "voci_prima", nullable = false, length = 2000)
    private String vociPrima;

    @Column(name = "voci_dopo", nullable = false, length = 2000)
    private String vociDopo;

    @Column(nullable = false, length = 2000)
    private String motivazione;

    @Column(name = "registrata_il", nullable = false)
    private Instant registrataIl;

    @Column(name = "registrata_da", nullable = false, length = 50)
    private String registrataDa;

    protected StoricoRiclassificazione() {
    }

    /** Fotografia di una classificazione: stato, significatività, categorie IS e voci (codici separati da virgola). */
    public record Classificazione(StatoIncidente stato, String categorieIs, String voci) {
        public boolean significativo() {
            return stato == StatoIncidente.SIGNIFICATIVO;
        }
    }

    public StoricoRiclassificazione(Incidente incidente, VersioneDocumento documentoPrima,
                                    ValutazioneSignificativita valutazione, Classificazione prima,
                                    Classificazione dopo, String motivazione, Instant registrataIl,
                                    String registrataDa) {
        if (motivazione == null || motivazione.isBlank()) {
            throw new IllegalArgumentException("La motivazione della riclassificazione è obbligatoria");
        }
        this.incidente = incidente;
        this.documentoPrima = documentoPrima;
        this.valutazione = valutazione;
        this.statoIncidentePrima = prima.stato();
        this.statoIncidenteDopo = dopo.stato();
        this.significativoPrima = prima.significativo();
        this.significativoDopo = dopo.significativo();
        this.categorieIsPrima = prima.categorieIs();
        this.categorieIsDopo = dopo.categorieIs();
        this.vociPrima = prima.voci();
        this.vociDopo = dopo.voci();
        this.motivazione = motivazione;
        this.registrataIl = registrataIl;
        this.registrataDa = registrataDa;
    }

    /** Collega il primo documento trasmesso dopo la riclassificazione. Si esegue una sola volta. */
    public void collegaDocumentoDopo(VersioneDocumento documento) {
        if (documentoDopo != null) {
            throw new IllegalStateException("La riclassificazione " + id + " è già stata comunicata con il documento "
                    + documentoDopo.getId());
        }
        this.documentoDopo = documento;
    }

    public boolean comunicata() {
        return documentoDopo != null;
    }

    public Long getId() { return id; }
    public Incidente getIncidente() { return incidente; }
    public VersioneDocumento getDocumentoPrima() { return documentoPrima; }
    public VersioneDocumento getDocumentoDopo() { return documentoDopo; }
    public ValutazioneSignificativita getValutazione() { return valutazione; }
    public StatoIncidente getStatoIncidentePrima() { return statoIncidentePrima; }
    public StatoIncidente getStatoIncidenteDopo() { return statoIncidenteDopo; }
    public boolean isSignificativoPrima() { return significativoPrima; }
    public boolean isSignificativoDopo() { return significativoDopo; }
    public String getCategorieIsPrima() { return categorieIsPrima; }
    public String getCategorieIsDopo() { return categorieIsDopo; }
    public String getVociPrima() { return vociPrima; }
    public String getVociDopo() { return vociDopo; }
    public String getMotivazione() { return motivazione; }
    public Instant getRegistrataIl() { return registrataIl; }
    public String getRegistrataDa() { return registrataDa; }
}
