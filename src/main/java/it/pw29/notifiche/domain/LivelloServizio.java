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

/**
 * Servizio critico del soggetto con il suo livello di servizio atteso (SL), definito in autonomia
 * (misura DE.CM-01). Il superamento di uno dei due limiti configura la categoria IS-3.
 * <p>
 * {@code utentiUe} sono gli utenti del servizio nell'Unione contati come prevede l'art. 3, par. 3, del
 * Reg. (UE) 2024/2690 (clienti con contratto e persone associate ai clienti business): l'art. 10 calcola
 * la soglia del 5% sugli "utenti di tale servizio". {@code servizioGestito} dice se il servizio è un
 * servizio gestito o di sicurezza gestito, a cui si applicano i criteri dell'art. 10 (regole R1-R4).
 */
@Entity
@Table(name = "livello_servizio")
public class LivelloServizio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "soggetto_id")
    private Soggetto soggetto;

    @Column(nullable = false, length = 40)
    private String servizio;

    @Column(nullable = false)
    private String descrizione;

    @Column(name = "max_indisponibilita_minuti", nullable = false)
    private int maxIndisponibilitaMinuti;

    @Column(name = "max_degrado_minuti", nullable = false)
    private int maxDegradoMinuti;

    @Column(nullable = false, length = 500)
    private String fonte;

    @Column(name = "utenti_ue", nullable = false)
    private int utentiUe;

    @Column(name = "servizio_gestito", nullable = false)
    private boolean servizioGestito;

    protected LivelloServizio() {
    }

    public LivelloServizio(Soggetto soggetto, String servizio, String descrizione,
                           int maxIndisponibilitaMinuti, int maxDegradoMinuti, String fonte,
                           int utentiUe, boolean servizioGestito) {
        this.soggetto = soggetto;
        this.servizio = servizio;
        this.descrizione = descrizione;
        this.maxIndisponibilitaMinuti = maxIndisponibilitaMinuti;
        this.maxDegradoMinuti = maxDegradoMinuti;
        this.fonte = fonte;
        this.utentiUe = utentiUe;
        this.servizioGestito = servizioGestito;
    }

    public Long getId() { return id; }
    public Soggetto getSoggetto() { return soggetto; }
    public String getServizio() { return servizio; }
    public String getDescrizione() { return descrizione; }
    public int getMaxIndisponibilitaMinuti() { return maxIndisponibilitaMinuti; }
    public int getMaxDegradoMinuti() { return maxDegradoMinuti; }
    public String getFonte() { return fonte; }
    public int getUtentiUe() { return utentiUe; }
    public boolean isServizioGestito() { return servizioGestito; }
}
