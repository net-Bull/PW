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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Incidente di sicurezza. Distingue due istanti (ADR-0003):
 * <ul>
 *   <li>{@code rilevatoIl}: primo evento rilevante per la sicurezza (allarme, segnalazione);</li>
 *   <li>{@code conoscenzaIl}: momento in cui il soggetto ha evidenza, cioè elementi oggettivi, di un
 *       incidente significativo; da qui decorrono i termini di 24 h e 72 h. Si registra una sola volta.</li>
 * </ul>
 */
@Entity
@Table(name = "incidente")
public class Incidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "soggetto_id")
    private Soggetto soggetto;

    @Column(nullable = false)
    private String titolo;

    @Column(nullable = false, length = 4000)
    private String descrizione;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoIncidente stato;

    @Column(name = "rilevato_il", nullable = false)
    private Instant rilevatoIl;

    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;

    @Column(name = "registrato_da", nullable = false, length = 50)
    private String registratoDa;

    @Column(name = "conoscenza_il")
    private Instant conoscenzaIl;

    @Column(name = "conoscenza_motivazione", length = 2000)
    private String conoscenzaMotivazione;

    @Column(name = "conoscenza_registrata_il")
    private Instant conoscenzaRegistrataIl;

    @Column(name = "conoscenza_registrata_da", length = 50)
    private String conoscenzaRegistrataDa;

    @Column(name = "conclusione_gestione_il")
    private Instant conclusioneGestioneIl;

    @Version
    private long versione;

    @OneToMany(mappedBy = "incidente", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<Adempimento> adempimenti = new ArrayList<>();

    protected Incidente() {
    }

    public Incidente(String codice, Soggetto soggetto, String titolo, String descrizione,
                     Instant rilevatoIl, Instant registratoIl, String registratoDa) {
        if (rilevatoIl.isAfter(registratoIl)) {
            throw new IllegalArgumentException("L'istante di rilevazione non può essere futuro");
        }
        this.codice = codice;
        this.soggetto = soggetto;
        this.titolo = titolo;
        this.descrizione = descrizione;
        this.rilevatoIl = rilevatoIl;
        this.registratoIl = registratoIl;
        this.registratoDa = registratoDa;
        this.stato = StatoIncidente.IN_VALUTAZIONE;
    }

    /**
     * Registra il momento della conoscenza dell'incidente significativo. Operazione eseguibile una
     * sola volta: i termini di 24 h e 72 h dipendono da questo istante, e consentirne la modifica
     * permetterebbe di spostare a posteriori le scadenze.
     */
    public void registraConoscenza(Instant conoscenza, String motivazione, Instant ora, String utente) {
        if (conoscenzaIl != null) {
            throw new IllegalStateException("Il momento della conoscenza dell'incidente " + codice
                    + " è già stato registrato e non è modificabile");
        }
        if (conoscenza.isBefore(rilevatoIl)) {
            throw new IllegalArgumentException("La conoscenza non può precedere la rilevazione dell'evento");
        }
        if (conoscenza.isAfter(ora)) {
            throw new IllegalArgumentException("La conoscenza non può essere un istante futuro");
        }
        if (motivazione == null || motivazione.isBlank()) {
            throw new IllegalArgumentException("Indicare gli elementi oggettivi che fondano la conoscenza");
        }
        this.conoscenzaIl = conoscenza;
        this.conoscenzaMotivazione = motivazione;
        this.conoscenzaRegistrataIl = ora;
        this.conoscenzaRegistrataDa = utente;
    }

    public void cambiaStato(StatoIncidente nuovo) {
        stato.verificaTransizione(nuovo);
        if (nuovo == StatoIncidente.SIGNIFICATIVO && conoscenzaIl == null) {
            throw new IllegalStateException("Un incidente significativo richiede il momento della conoscenza");
        }
        this.stato = nuovo;
    }

    /**
     * Registra la conclusione della gestione. Come la conoscenza, si registra una sola volta: dalla conclusione
     * dipende il termine della relazione finale (art. 25, c. 5, lett. e), e consentirne la modifica
     * permetterebbe di spostare a posteriori una scadenza già superata (ADR-0003, ADR-0007).
     */
    public void registraConclusioneGestione(Instant conclusione, Instant ora) {
        if (conclusioneGestioneIl != null) {
            throw new IllegalStateException("La conclusione della gestione dell'incidente " + codice
                    + " è già stata registrata e non è modificabile");
        }
        if (conoscenzaIl != null && conclusione.isBefore(conoscenzaIl)) {
            throw new IllegalArgumentException("La conclusione della gestione non può precedere la conoscenza");
        }
        if (conclusione.isAfter(ora)) {
            throw new IllegalArgumentException("La conclusione della gestione non può essere un istante futuro");
        }
        this.conclusioneGestioneIl = conclusione;
    }

    public Adempimento aggiungiAdempimento(TipoAdempimento tipo) {
        int progressivo = (int) adempimenti.stream().filter(a -> a.getTipo() == tipo).count() + 1;
        if (!tipo.ripetibile() && progressivo > 1) {
            throw new IllegalStateException("L'adempimento " + tipo + " esiste già per l'incidente " + codice);
        }
        Adempimento a = new Adempimento(this, tipo, progressivo);
        adempimenti.add(a);
        return a;
    }

    public Optional<Adempimento> adempimento(TipoAdempimento tipo, int progressivo) {
        return adempimenti.stream()
                .filter(a -> a.getTipo() == tipo && a.getProgressivo() == progressivo)
                .findFirst();
    }

    public boolean trasmesso(TipoAdempimento tipo) {
        return adempimenti.stream().anyMatch(a -> a.getTipo() == tipo && a.getTrasmessoIl() != null);
    }

    public Long getId() { return id; }
    public String getCodice() { return codice; }
    public Soggetto getSoggetto() { return soggetto; }
    public String getTitolo() { return titolo; }
    public String getDescrizione() { return descrizione; }
    public StatoIncidente getStato() { return stato; }
    public Instant getRilevatoIl() { return rilevatoIl; }
    public Instant getRegistratoIl() { return registratoIl; }
    public String getRegistratoDa() { return registratoDa; }
    public Instant getConoscenzaIl() { return conoscenzaIl; }
    public String getConoscenzaMotivazione() { return conoscenzaMotivazione; }
    public Instant getConoscenzaRegistrataIl() { return conoscenzaRegistrataIl; }
    public String getConoscenzaRegistrataDa() { return conoscenzaRegistrataDa; }
    public Instant getConclusioneGestioneIl() { return conclusioneGestioneIl; }
    public List<Adempimento> getAdempimenti() { return adempimenti; }
}
