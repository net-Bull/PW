package it.pw29.notifiche.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Soggetto NIS che notifica gli incidenti, con il referente CSIRT (misura GV.RR-02). Dimensione
 * (dipendenti, totale di bilancio; il fatturato è nell'insieme di soglie) e data di inserimento
 * nell'elenco NIS motivano il tipo di soggetto e la decorrenza dell'obbligo di notifica.
 */
@Entity
@Table(name = "soggetto")
public class Soggetto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String denominazione;

    @Column(name = "codice_fiscale", nullable = false, unique = true, length = 16)
    private String codiceFiscale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSoggetto tipo;

    @Column(nullable = false)
    private String settore;

    @Column(name = "motivazione_tipo", nullable = false, length = 1000)
    private String motivazioneTipo;

    @Column(name = "referente_nome", nullable = false)
    private String referenteNome;

    @Column(name = "referente_email", nullable = false)
    private String referenteEmail;

    @Column(name = "referente_telefono", nullable = false, length = 50)
    private String referenteTelefono;

    @Column(nullable = false)
    private int dipendenti;

    @Column(name = "totale_bilancio", nullable = false)
    private BigDecimal totaleBilancio;

    @Column(name = "iscrizione_elenco_nis_il", nullable = false)
    private LocalDate iscrizioneElencoNisIl;

    protected Soggetto() {
    }

    public Soggetto(String denominazione, String codiceFiscale, TipoSoggetto tipo, String settore,
                    String motivazioneTipo, String referenteNome, String referenteEmail, String referenteTelefono,
                    int dipendenti, BigDecimal totaleBilancio, LocalDate iscrizioneElencoNisIl) {
        this.denominazione = denominazione;
        this.codiceFiscale = codiceFiscale;
        this.tipo = tipo;
        this.settore = settore;
        this.motivazioneTipo = motivazioneTipo;
        this.referenteNome = referenteNome;
        this.referenteEmail = referenteEmail;
        this.referenteTelefono = referenteTelefono;
        this.dipendenti = dipendenti;
        this.totaleBilancio = totaleBilancio;
        this.iscrizioneElencoNisIl = iscrizioneElencoNisIl;
    }

    public Long getId() { return id; }
    public String getDenominazione() { return denominazione; }
    public String getCodiceFiscale() { return codiceFiscale; }
    public TipoSoggetto getTipo() { return tipo; }
    public String getSettore() { return settore; }
    public String getMotivazioneTipo() { return motivazioneTipo; }
    public String getReferenteNome() { return referenteNome; }
    public String getReferenteEmail() { return referenteEmail; }
    public String getReferenteTelefono() { return referenteTelefono; }
    public int getDipendenti() { return dipendenti; }
    public BigDecimal getTotaleBilancio() { return totaleBilancio; }
    public LocalDate getIscrizioneElencoNisIl() { return iscrizioneElencoNisIl; }
}
