package it.pw29.notifiche.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Valutazione di significatività eseguita su un incidente. Le valutazioni non si sovrascrivono:
 * restano tutte, con l'input e la versione delle soglie usati, per ricostruire la motivazione.
 */
@Entity
@Table(name = "valutazione_significativita")
public class ValutazioneSignificativita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidente_id")
    private Incidente incidente;

    @Column(name = "eseguita_il", nullable = false)
    private Instant eseguitaIl;

    @Column(name = "eseguita_da", nullable = false, length = 50)
    private String eseguitaDa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "soglia_set_id")
    private SogliaSet sogliaSet;

    @Column(nullable = false, length = 40)
    private String servizio;

    @Column(name = "input_json", nullable = false, length = 10000)
    private String inputJson;

    @Column(nullable = false)
    private boolean significativo;

    /** Regole attivate, separate da virgola (es. "R1,R4"). */
    @Column(name = "regole_attivate", nullable = false, length = 100)
    private String regoleAttivate;

    /** Categorie IS attivate, separate da virgola (es. "IS_3"). */
    @Column(name = "categorie_is", nullable = false, length = 100)
    private String categorieIs;

    protected ValutazioneSignificativita() {
    }

    public ValutazioneSignificativita(Incidente incidente, Instant eseguitaIl, String eseguitaDa, SogliaSet sogliaSet,
                                      String servizio, String inputJson, boolean significativo,
                                      String regoleAttivate, String categorieIs) {
        this.incidente = incidente;
        this.eseguitaIl = eseguitaIl;
        this.eseguitaDa = eseguitaDa;
        this.sogliaSet = sogliaSet;
        this.servizio = servizio;
        this.inputJson = inputJson;
        this.significativo = significativo;
        this.regoleAttivate = regoleAttivate;
        this.categorieIs = categorieIs;
    }

    public Long getId() { return id; }
    public Incidente getIncidente() { return incidente; }
    public Instant getEseguitaIl() { return eseguitaIl; }
    public String getEseguitaDa() { return eseguitaDa; }
    public SogliaSet getSogliaSet() { return sogliaSet; }
    public String getServizio() { return servizio; }
    public String getInputJson() { return inputJson; }
    public boolean isSignificativo() { return significativo; }
    public String getRegoleAttivate() { return regoleAttivate; }
    public String getCategorieIs() { return categorieIs; }
}
