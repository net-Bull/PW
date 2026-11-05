package it.pw29.notifiche.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Insieme versionato di soglie di significatività e parametri del contesto organizzativo.
 * Le soglie sono dati (caricati da migrazione), non costanti nel codice: un cambio normativo
 * si gestisce con una nuova versione, senza modificare il motore delle regole.
 */
@Entity
@Table(name = "soglia_set")
public class SogliaSet {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String versione;

    @Column(name = "valida_dal", nullable = false)
    private LocalDate validaDal;

    @Column(nullable = false)
    private String fonte;

    // Parametro del contesto organizzativo (MSSP fittizio): fatturato dell'esercizio precedente.
    // Gli utenti sono per servizio (LivelloServizio.utentiUe): l'art. 10 conta gli "utenti di tale servizio".
    @Column(name = "fatturato_annuo", nullable = false)
    private BigDecimal fatturatoAnnuo;

    // Soglie normative (Reg. di esecuzione (UE) 2024/2690, artt. 3, 4, 10)
    @Column(name = "minuti_indisponibilita", nullable = false)
    private int minutiIndisponibilita;

    @Column(name = "minuti_degrado", nullable = false)
    private int minutiDegrado;

    @Column(name = "quota_utenti", nullable = false)
    private BigDecimal quotaUtenti;

    @Column(name = "tetto_utenti", nullable = false)
    private int tettoUtenti;

    @Column(name = "perdita_assoluta", nullable = false)
    private BigDecimal perditaAssoluta;

    @Column(name = "quota_fatturato", nullable = false)
    private BigDecimal quotaFatturato;

    @Column(name = "finestra_ricorrenza_mesi", nullable = false)
    private int finestraRicorrenzaMesi;

    @Column(name = "min_incidenti_ricorrenza", nullable = false)
    private int minIncidentiRicorrenza;

    protected SogliaSet() {
    }

    public SogliaSet(Long id, String versione, LocalDate validaDal, String fonte,
                     BigDecimal fatturatoAnnuo, int minutiIndisponibilita, int minutiDegrado,
                     BigDecimal quotaUtenti, int tettoUtenti, BigDecimal perditaAssoluta,
                     BigDecimal quotaFatturato, int finestraRicorrenzaMesi, int minIncidentiRicorrenza) {
        this.id = id;
        this.versione = versione;
        this.validaDal = validaDal;
        this.fonte = fonte;
        this.fatturatoAnnuo = fatturatoAnnuo;
        this.minutiIndisponibilita = minutiIndisponibilita;
        this.minutiDegrado = minutiDegrado;
        this.quotaUtenti = quotaUtenti;
        this.tettoUtenti = tettoUtenti;
        this.perditaAssoluta = perditaAssoluta;
        this.quotaFatturato = quotaFatturato;
        this.finestraRicorrenzaMesi = finestraRicorrenzaMesi;
        this.minIncidentiRicorrenza = minIncidentiRicorrenza;
    }

    /**
     * min(quota × utenti del servizio nell'UE; tetto assoluto): art. 10, lett. b) e d), Reg. (UE) 2024/2690
     * ("oltre il 5 % degli utenti di tale servizio nell'Unione o oltre 1 milione [...], a seconda di quale
     * valore sia inferiore"). Gli utenti si contano come prevede l'art. 3, par. 3.
     */
    public int sogliaUtenti(int utentiServizio) {
        int quota = quotaUtenti.multiply(BigDecimal.valueOf(utentiServizio)).intValue();
        return Math.min(quota, tettoUtenti);
    }

    /** min(perdita assoluta; quota × fatturato) — art. 3, par. 1, lett. a. */
    public BigDecimal sogliaEconomica() {
        return perditaAssoluta.min(quotaFatturato.multiply(fatturatoAnnuo));
    }

    public Long getId() { return id; }
    public String getVersione() { return versione; }
    public LocalDate getValidaDal() { return validaDal; }
    public String getFonte() { return fonte; }
    public BigDecimal getFatturatoAnnuo() { return fatturatoAnnuo; }
    public int getMinutiIndisponibilita() { return minutiIndisponibilita; }
    public int getMinutiDegrado() { return minutiDegrado; }
    public BigDecimal getQuotaUtenti() { return quotaUtenti; }
    public int getTettoUtenti() { return tettoUtenti; }
    public BigDecimal getPerditaAssoluta() { return perditaAssoluta; }
    public BigDecimal getQuotaFatturato() { return quotaFatturato; }
    public int getFinestraRicorrenzaMesi() { return finestraRicorrenzaMesi; }
    public int getMinIncidentiRicorrenza() { return minIncidentiRicorrenza; }
}
