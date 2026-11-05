package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.Adempimento;
import it.pw29.notifiche.domain.AuditLog;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.StoricoRiclassificazione;
import it.pw29.notifiche.domain.TassonomiaVoce;
import it.pw29.notifiche.domain.ValutazioneSignificativita;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.dto.AdempimentoDto;
import it.pw29.notifiche.dto.AuditVoce;
import it.pw29.notifiche.dto.DocumentoDettaglio;
import it.pw29.notifiche.dto.EsitoVerifica;
import it.pw29.notifiche.dto.IncidenteDettaglio;
import it.pw29.notifiche.dto.RiclassificazioneDto;
import it.pw29.notifiche.dto.Scadenza;
import it.pw29.notifiche.dto.ValutazioneDto;
import it.pw29.notifiche.dto.VersioneSintesi;
import it.pw29.notifiche.dto.VoceDto;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Conversione delle entità nei DTO delle API, eseguita dentro la transazione del servizio. */
final class Mappatura {

    private Mappatura() {
    }

    static IncidenteDettaglio incidente(Incidente i, ValutazioneSignificativita ultima, List<Scadenza> scadenze) {
        Long ritardo = i.getConoscenzaIl() == null ? null
                : Duration.between(i.getRilevatoIl(), i.getConoscenzaIl()).toMinutes();
        return new IncidenteDettaglio(i.getId(), i.getCodice(), i.getSoggetto().getDenominazione(),
                i.getSoggetto().getTipo(), i.getTitolo(), i.getDescrizione(), i.getStato(),
                i.getRilevatoIl(), i.getRegistratoIl(), i.getRegistratoDa(),
                i.getConoscenzaIl(), i.getConoscenzaMotivazione(), i.getConoscenzaRegistrataIl(),
                i.getConoscenzaRegistrataDa(), ritardo, i.getConclusioneGestioneIl(),
                ultima == null ? null : valutazione(ultima),
                i.getAdempimenti().stream().map(Mappatura::adempimento).toList(),
                scadenze);
    }

    static ValutazioneDto valutazione(ValutazioneSignificativita v) {
        return new ValutazioneDto(v.getId(), v.getEseguitaIl(), v.getEseguitaDa(), v.getSogliaSet().getVersione(),
                v.getServizio(), v.isSignificativo(), elenco(v.getRegoleAttivate()), elenco(v.getCategorieIs()));
    }

    static AdempimentoDto adempimento(Adempimento a) {
        return new AdempimentoDto(a.getId(), a.getTipo(), a.getProgressivo(), a.getStato(), a.getRichiestoIl(),
                a.getScadenzaRichiesta(), a.getRiferimentoRichiesta(), a.getTrasmessoIl(),
                a.getVersioni().stream()
                        .map(v -> new VersioneSintesi(v.getId(), v.getNumero(), v.getStato(), v.getCreataDa(), v.getTrasmessaIl(),
                                v.getMotivoRettifica(), v.getImprontaSha256()))
                        .toList());
    }

    static DocumentoDettaglio documento(VersioneDocumento v, List<String> campiPrevisti, EsitoVerifica verifica,
                                        List<String> vociNonRiportate) {
        Adempimento a = v.getAdempimento();
        Incidente i = a.getIncidente();
        List<VoceDto> voci = v.getVoci().stream()
                .sorted(Comparator.comparing(TassonomiaVoce::getId))
                .map(t -> new VoceDto(t.getCodice(), t.getNome(), t.getPadre() == null ? null : t.getPadre().getCodice()))
                .toList();
        return new DocumentoDettaglio(v.getId(), i.getId(), i.getCodice(), a.getId(), a.getTipo(), a.getProgressivo(),
                v.getNumero(), v.getStato(), v.getTassonomiaVersione().getCodice(),
                v.getDerivataDa() == null ? null : v.getDerivataDa().getId(),
                v.getContenuto(), voci, campiPrevisti,
                v.getCreataIl(), v.getCreataDa(), v.getModificataIl(), v.getModificataDa(),
                v.getInviataApprovazioneIl(), v.getInviataApprovazioneDa(), v.getNotaRespingimento(),
                v.getApprovataDa(), v.getTrasmessaIl(), v.getRiferimentoTrasmissione(),
                verifica, vociNonRiportate,
                v.getMotivoRettifica(), v.getTrasmissioneRegistrataIl(), v.getImprontaPrecedente(), v.getImprontaSha256(),
                List.copyOf(v.getAutori()));
    }

    static RiclassificazioneDto riclassificazione(StoricoRiclassificazione r) {
        return new RiclassificazioneDto(r.getId(), r.getIncidente().getId(), r.getRegistrataIl(), r.getRegistrataDa(),
                r.getMotivazione(), r.getStatoIncidentePrima(), r.getStatoIncidenteDopo(),
                r.isSignificativoPrima(), r.isSignificativoDopo(),
                List.copyOf(ClassificazioneCorrente.insieme(r.getCategorieIsPrima())),
                List.copyOf(ClassificazioneCorrente.insieme(r.getCategorieIsDopo())),
                List.copyOf(ClassificazioneCorrente.insieme(r.getVociPrima())),
                List.copyOf(ClassificazioneCorrente.insieme(r.getVociDopo())),
                r.getValutazione() == null ? null : r.getValutazione().getId(),
                r.getDocumentoPrima() == null ? null : r.getDocumentoPrima().getId(),
                r.getDocumentoPrima() == null ? null : r.getDocumentoPrima().getRiferimentoTrasmissione(),
                r.getDocumentoDopo() == null ? null : r.getDocumentoDopo().getId(),
                r.getDocumentoDopo() == null ? null : r.getDocumentoDopo().getRiferimentoTrasmissione(),
                r.comunicata());
    }

    static AuditVoce audit(AuditLog a) {
        return new AuditVoce(a.getId(), a.getIstante(), a.getUsername(), a.getAzione(), a.getEntita(),
                a.getEntitaId(), a.getDettaglio());
    }

    static List<String> elenco(String valori) {
        return valori == null || valori.isBlank() ? List.of() : Arrays.asList(valori.split(","));
    }
}
