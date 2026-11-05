package it.pw29.notifiche.service;

import it.pw29.notifiche.dto.ServizioDto;
import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.LivelloServizio;
import it.pw29.notifiche.domain.Soggetto;
import it.pw29.notifiche.domain.SogliaSet;
import it.pw29.notifiche.dto.EsitoSignificativita;
import it.pw29.notifiche.dto.ValutazioneRequest;
import it.pw29.notifiche.repository.LivelloServizioRepository;
import it.pw29.notifiche.repository.SoggettoRepository;
import it.pw29.notifiche.repository.SogliaSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Valutazione di significatività: regole R1-R8 sulle soglie del Reg. (UE) 2024/2690 vigenti alla data di
 * riferimento (la data della conoscenza dell'incidente, o oggi per una simulazione), più categorie
 * IS-1..IS-4 dell'ACN sul tipo di soggetto e sul livello di servizio atteso del servizio coinvolto.
 * L'incidente è significativo se scatta almeno un criterio (ADR-0004).
 */
@Service
public class ValutazioneService {

    /** Esito con l'insieme di soglie usato, da memorizzare nello storico delle valutazioni. */
    public record Calcolo(EsitoSignificativita esito, SogliaSet soglie) {
    }

    private final SogliaSetRepository soglie;
    private final SoggettoRepository soggetti;
    private final LivelloServizioRepository livelli;
    private final RegoleSignificativita regole;
    private final CriteriIncidentiSignificativi criteri;
    private final Clock clock;

    public ValutazioneService(SogliaSetRepository soglie, SoggettoRepository soggetti,
                              LivelloServizioRepository livelli, RegoleSignificativita regole,
                              CriteriIncidentiSignificativi criteri, Clock clock) {
        this.soglie = soglie;
        this.soggetti = soggetti;
        this.livelli = livelli;
        this.regole = regole;
        this.criteri = criteri;
        this.clock = clock;
    }

    /** Servizi del soggetto indicato o di quello predefinito, con i livelli di servizio attesi. */
    @Transactional(readOnly = true)
    public List<ServizioDto> servizi(Long soggettoId) {
        Soggetto s = soggettoId == null
                ? soggetti.findFirstByOrderByIdAsc().orElseThrow(() -> new IllegalStateException("Nessun soggetto configurato"))
                : soggetti.findById(soggettoId).orElseThrow(() -> new RisorsaNonTrovataException("Soggetto", soggettoId));
        return livelli.findBySoggettoIdOrderByServizio(s.getId()).stream()
                .map(l -> new ServizioDto(l.getServizio(), l.getDescrizione(),
                        l.getMaxIndisponibilitaMinuti(), l.getMaxDegradoMinuti(), l.getUtentiUe(), l.isServizioGestito()))
                .toList();
    }

    /** Simulazione senza effetti, per il soggetto indicato o per quello predefinito. */
    @Transactional(readOnly = true)
    public EsitoSignificativita valuta(ValutazioneRequest richiesta, Long soggettoId) {
        Soggetto s = soggettoId == null
                ? soggetti.findFirstByOrderByIdAsc().orElseThrow(() -> new IllegalStateException("Nessun soggetto configurato"))
                : soggetti.findById(soggettoId).orElseThrow(() -> new RisorsaNonTrovataException("Soggetto", soggettoId));
        return calcola(s, richiesta, LocalDate.now(clock)).esito();
    }

    /**
     * @param riferimento data a cui cercare le soglie vigenti: per un incidente, la data in cui il soggetto
     *        ne ha avuto conoscenza, così una rivalutazione successiva usa le stesse regole del tempo
     */
    @Transactional(readOnly = true)
    public Calcolo calcola(Soggetto soggetto, ValutazioneRequest richiesta, LocalDate riferimento) {
        SogliaSet vigente = soglie.findFirstByValidaDalLessThanEqualOrderByValidaDalDesc(riferimento)
                .orElseThrow(() -> new IllegalStateException("Nessun insieme di soglie vigente al " + riferimento));
        LivelloServizio sl = livelli.findBySoggettoIdAndServizio(soggetto.getId(), richiesta.servizioCoinvolto())
                .orElseThrow(() -> new DatiNonValidiException("Servizio '" + richiesta.servizioCoinvolto()
                        + "' senza livello di servizio atteso per il soggetto " + soggetto.getDenominazione()));
        List<CategoriaIs> categorie = criteri.valuta(richiesta, soggetto.getTipo(), sl);
        return new Calcolo(regole.valuta(richiesta, vigente, sl).conCategorie(categorie), vigente);
    }
}
