package it.pw29.notifiche.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adempimento verso il CSIRT Italia (pre-notifica, notifica, relazioni). Le scadenze non sono
 * memorizzate ma calcolate dai fatti dell'incidente ({@code CalcoloScadenze}); fa eccezione la
 * scadenza eventualmente indicata dal CSIRT Italia per una relazione intermedia.
 */
@Entity
@Table(name = "adempimento")
public class Adempimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidente_id")
    private Incidente incidente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAdempimento tipo;

    @Column(nullable = false)
    private int progressivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoAdempimento stato;

    @Column(name = "richiesto_il")
    private Instant richiestoIl;

    @Column(name = "scadenza_richiesta")
    private Instant scadenzaRichiesta;

    @Column(name = "riferimento_richiesta")
    private String riferimentoRichiesta;

    @Column(name = "trasmesso_il")
    private Instant trasmessoIl;

    @OneToMany(mappedBy = "adempimento", cascade = CascadeType.ALL)
    @OrderBy("numero")
    private List<VersioneDocumento> versioni = new ArrayList<>();

    protected Adempimento() {
    }

    Adempimento(Incidente incidente, TipoAdempimento tipo, int progressivo) {
        this.incidente = incidente;
        this.tipo = tipo;
        this.progressivo = progressivo;
        this.stato = StatoAdempimento.DA_REDIGERE;
    }

    /** Dati della richiesta del CSIRT Italia che origina una relazione intermedia. */
    public void registraRichiesta(Instant richiestoIl, Instant scadenza, String riferimento) {
        if (tipo != TipoAdempimento.RELAZIONE_INTERMEDIA) {
            throw new IllegalStateException("Solo la relazione intermedia nasce da una richiesta del CSIRT Italia");
        }
        if (scadenza != null && scadenza.isBefore(richiestoIl)) {
            throw new IllegalArgumentException("La scadenza indicata dal CSIRT non può precedere la richiesta");
        }
        this.richiestoIl = richiestoIl;
        this.scadenzaRichiesta = scadenza;
        this.riferimentoRichiesta = riferimento;
    }

    public VersioneDocumento nuovaVersione(VersioneDocumento derivataDa, TassonomiaVersione tassonomia,
                                           Instant ora, String utente) {
        if (stato == StatoAdempimento.TRASMESSO) {
            throw new IllegalStateException("L'adempimento " + tipo + " è già stato trasmesso");
        }
        if (versioneAperta().isPresent()) {
            throw new IllegalStateException("Esiste già una versione in lavorazione per " + tipo);
        }
        VersioneDocumento v = new VersioneDocumento(this, versioni.size() + 1, derivataDa, null, tassonomia, ora, utente);
        versioni.add(v);
        stato = StatoAdempimento.IN_LAVORAZIONE;
        return v;
    }

    /**
     * Apre una rettifica: nuova versione di un adempimento già trasmesso, derivata dall'ultima versione
     * trasmessa. La versione trasmessa resta immutata; l'adempimento resta TRASMESSO (il termine è
     * stato rispettato dal primo invio) e la rettifica segue lo stesso iter di approvazione.
     */
    public VersioneDocumento rettifica(String motivo, TassonomiaVersione tassonomia, Instant ora, String utente) {
        if (stato != StatoAdempimento.TRASMESSO) {
            throw new IllegalStateException("Si rettifica solo un adempimento già trasmesso: " + tipo + " è " + stato);
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Indicare il motivo della rettifica");
        }
        if (versioneAperta().isPresent()) {
            throw new IllegalStateException("Esiste già una versione in lavorazione per " + tipo);
        }
        VersioneDocumento base = ultimaTrasmessa().orElseThrow();
        VersioneDocumento v = new VersioneDocumento(this, versioni.size() + 1, base, motivo, tassonomia, ora, utente);
        versioni.add(v);
        return v;
    }

    /** Chiamato dalla versione quando viene trasmessa. Il primo invio fissa il rispetto del termine. */
    void segnaTrasmesso(Instant istante) {
        this.stato = StatoAdempimento.TRASMESSO;
        if (this.trasmessoIl == null) {
            this.trasmessoIl = istante;
        }
    }

    public Optional<VersioneDocumento> versioneAperta() {
        return versioni.stream().filter(v -> v.getStato() != StatoDocumento.TRASMESSO).findFirst();
    }

    /** Ultima versione trasmessa: in presenza di rettifiche è quella che il CSIRT Italia ha ricevuto per ultima. */
    public Optional<VersioneDocumento> ultimaTrasmessa() {
        return versioni.stream().filter(v -> v.getStato() == StatoDocumento.TRASMESSO)
                .reduce((prima, dopo) -> dopo);
    }

    public Long getId() { return id; }
    public Incidente getIncidente() { return incidente; }
    public TipoAdempimento getTipo() { return tipo; }
    public int getProgressivo() { return progressivo; }
    public StatoAdempimento getStato() { return stato; }
    public Instant getRichiestoIl() { return richiestoIl; }
    public Instant getScadenzaRichiesta() { return scadenzaRichiesta; }
    public String getRiferimentoRichiesta() { return riferimentoRichiesta; }
    public Instant getTrasmessoIl() { return trasmessoIl; }
    public List<VersioneDocumento> getVersioni() { return versioni; }
}
