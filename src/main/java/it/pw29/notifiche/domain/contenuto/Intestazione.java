package it.pw29.notifiche.domain.contenuto;

import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.TipoSoggetto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Intestazione sigillata di un documento trasmesso: soggetto notificante e incidente come erano al momento
 * della trasmissione. Si salva nella versione e entra nell'impronta SHA-256 (schema pw29-catena-v2): la
 * copia conforme di un documento trasmesso non cambia se in seguito l'incidente evolve (es. CHIUSO) o se
 * cambiano i dati anagrafici del soggetto (ADR-0006, ADR-0008).
 */
public record Intestazione(Soggetto soggetto, Incidente incidente) {

    public record Soggetto(String denominazione, String codiceFiscale, TipoSoggetto tipo, String settore) {
    }

    /** @param stato stato dell'incidente al momento della trasmissione, prima dei suoi effetti */
    public record Incidente(String codice, String titolo, StatoIncidente stato, Instant rilevatoIl,
                            Instant conoscenzaIl, Instant conclusioneGestioneIl) {
    }

    /** Fotografia dell'incidente e del suo soggetto, con istanti al millisecondo (precisione del database). */
    public static Intestazione di(it.pw29.notifiche.domain.Incidente inc) {
        var s = inc.getSoggetto();
        return new Intestazione(
                new Soggetto(s.getDenominazione(), s.getCodiceFiscale(), s.getTipo(), s.getSettore()),
                new Incidente(inc.getCodice(), inc.getTitolo(), inc.getStato(), ms(inc.getRilevatoIl()),
                        ms(inc.getConoscenzaIl()), ms(inc.getConclusioneGestioneIl())));
    }

    private static Instant ms(Instant i) {
        return i == null ? null : i.truncatedTo(ChronoUnit.MILLIS);
    }
}
