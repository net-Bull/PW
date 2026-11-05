package it.pw29.notifiche.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Nodo dell'albero della Tassonomia Cyber ACN. Ogni voce appartiene a una sola versione,
 * così una classificazione resta legata alla versione vigente quando è stata fatta.
 * Il codice è quello ufficiale del documento ACN (es. "TT:MA_RA"), univoco nella versione;
 * lo slug MISP non lo è (es. "other" ricorre in molti predicati) e serve solo all'interoperabilità.
 */
@Entity
@Table(name = "tassonomia_voce")
public class TassonomiaVoce {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "versione_id")
    private TassonomiaVersione versione;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id")
    private TassonomiaVoce padre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LivelloTassonomia livello;

    @Column(nullable = false, length = 40)
    private String codice;

    @Column(name = "slug_misp", length = 120)
    private String slugMisp;

    @Column(nullable = false)
    private String nome;

    @Column(length = 2000)
    private String descrizione;

    @Column(length = 20)
    private String sezione;

    @Column(nullable = false)
    private int ordine;

    protected TassonomiaVoce() {
    }

    public Long getId() { return id; }
    public TassonomiaVersione getVersione() { return versione; }
    public TassonomiaVoce getPadre() { return padre; }
    public LivelloTassonomia getLivello() { return livello; }
    public String getCodice() { return codice; }
    public String getSlugMisp() { return slugMisp; }
    public String getNome() { return nome; }
    public String getDescrizione() { return descrizione; }
    public String getSezione() { return sezione; }
    public int getOrdine() { return ordine; }
}
