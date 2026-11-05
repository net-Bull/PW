package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;

import java.time.Instant;
import java.util.List;

/**
 * Versione di un documento con contenuto, classificazione e verifica di completezza.
 *
 * @param campiPrevisti campi pertinenti per l'adempimento (dalla matrice): guida l'interfaccia
 * @param derivataDaId versione trasmessa da cui la bozza ha ripreso i dati (riuso)
 * @param vociNonRiportate codici della versione d'origine non presenti nella tassonomia vigente
 * @param motivoRettifica valorizzato se la versione sostituisce una versione già trasmessa
 * @param improntaSha256 impronta della versione nella catena di hash (solo se trasmessa)
 * @param autori chi ha scritto contenuto o classificazione: nessuno di loro può registrarne la trasmissione
 */
public record DocumentoDettaglio(
        Long id, Long incidenteId, String incidenteCodice, Long adempimentoId,
        TipoAdempimento tipo, int progressivo, int numero, StatoDocumento stato,
        String tassonomiaVersione, Long derivataDaId,
        ContenutoDocumento contenuto, List<VoceDto> voci, List<String> campiPrevisti,
        Instant creataIl, String creataDa, Instant modificataIl, String modificataDa,
        Instant inviataApprovazioneIl, String inviataApprovazioneDa, String notaRespingimento,
        String approvataDa, Instant trasmessaIl, String riferimentoTrasmissione,
        EsitoVerifica verifica, List<String> vociNonRiportate,
        String motivoRettifica, Instant trasmissioneRegistrataIl, String improntaPrecedente, String improntaSha256,
        List<String> autori
) {
}
