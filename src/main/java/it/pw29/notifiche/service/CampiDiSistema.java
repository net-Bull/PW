package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.Adempimento;
import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.Regola;
import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.domain.ValutazioneSignificativita;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import it.pw29.notifiche.domain.contenuto.ContenutoJson;
import it.pw29.notifiche.repository.ValutazioneSignificativitaRepository;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Campi del documento compilati dal sistema a partire dai fatti registrati, mai dall'operatore (ADR-0005,
 * ADR-0007): categorie IS e criteri del Reg. 2024/2690 dall'ultima valutazione di significatività,
 * riclassificazioni dallo storico, conclusione della gestione dall'incidente, riferimento della richiesta
 * del CSIRT Italia dall'adempimento. Il valore inviato dal client per questi campi è sempre sovrascritto.
 * <p>
 * I campi sono ricompilati alla creazione della bozza, a ogni aggiornamento, all'invio in approvazione e
 * quando cambiano i fatti (riclassificazione, conclusione della gestione); la verifica di completezza
 * segnala una versione non allineata, che va respinta e reinviata.
 */
@Component
class CampiDiSistema {

    private final ValutazioneSignificativitaRepository valutazioni;
    private final ClassificazioneCorrente classificazione;

    CampiDiSistema(ValutazioneSignificativitaRepository valutazioni, ClassificazioneCorrente classificazione) {
        this.valutazioni = valutazioni;
        this.classificazione = classificazione;
    }

    void compila(ContenutoDocumento c, Adempimento a) {
        Incidente inc = a.getIncidente();
        Optional<ValutazioneSignificativita> ultima = valutazioni.findFirstByIncidenteIdOrderByEseguitaIlDescIdDesc(inc.getId());
        c.setCategorieIs(ultima.map(v -> Mappatura.elenco(v.getCategorieIs()).stream().map(CategoriaIs::valueOf).toList())
                .orElse(List.of()));
        c.setCriteriRegolamento(ultima.map(v -> Mappatura.elenco(v.getRegoleAttivate()).stream().map(Regola::valueOf).toList())
                .orElse(List.of()));
        c.setRiclassificazioni(classificazione.perDocumento(inc));
        c.setConclusioneGestioneIl(inc.getConclusioneGestioneIl() == null ? null
                : inc.getConclusioneGestioneIl().truncatedTo(ChronoUnit.MILLIS));
        if (a.getTipo() == TipoAdempimento.RELAZIONE_INTERMEDIA) {
            c.setRiferimentoRichiestaCsirt(a.getRiferimentoRichiesta());
        }
    }

    /** Vero se i campi di sistema della versione coincidono con quelli ricavati dai fatti correnti. */
    boolean allineati(VersioneDocumento v) {
        ContenutoDocumento atteso = v.getContenuto();
        compila(atteso, v.getAdempimento());
        return ContenutoJson.scrivi(atteso).equals(v.getContenutoJson());
    }

    /** Riallinea una bozza (non una versione in approvazione o trasmessa). */
    void riallinea(VersioneDocumento v) {
        if (v.getStato() == StatoDocumento.BOZZA) {
            ContenutoDocumento c = v.getContenuto();
            compila(c, v.getAdempimento());
            v.allineaCampiDiSistema(c);
        }
    }

    /** Riallinea le bozze aperte dell'incidente dopo un cambiamento dei fatti (es. conclusione registrata). */
    void riallineaBozze(Incidente inc) {
        for (Adempimento a : inc.getAdempimenti()) {
            a.versioneAperta().ifPresent(this::riallinea);
        }
    }
}
