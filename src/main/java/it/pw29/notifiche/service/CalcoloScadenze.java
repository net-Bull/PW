package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.StatoScadenza;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.dto.Scadenza;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Calcolo delle scadenze dell'art. 25, c. 5, D.Lgs. 138/2024. Funzione pura: riceve i fatti
 * dell'incidente e l'istante corrente (dall'orologio iniettato), restituisce il piano delle scadenze.
 * <ul>
 *   <li>pre-notifica: conoscenza + 24 h (lett. a); notifica: conoscenza + 72 h (lett. b).
 *       Ore reali, calcolate su {@link Instant}: il cambio d'ora non sposta il termine;</li>
 *   <li>relazione finale: trasmissione della notifica + 1 mese (lett. d). Il mese si conta sul
 *       calendario del fuso del soggetto (Europe/Rome), allo stesso orario; se il giorno manca nel
 *       mese di arrivo (es. 31 gennaio) il termine cade l'ultimo giorno del mese (ADR-0003);</li>
 *   <li>incidente in corso a quella data (lett. e): relazioni mensili alla stessa ricorrenza
 *       (notifica + k mesi) finché la gestione non si conclude, poi finale entro un mese dalla conclusione;</li>
 *   <li>relazione intermedia (lett. c): solo su richiesta, con la scadenza indicata dal CSIRT Italia.</li>
 * </ul>
 */
@Component
public class CalcoloScadenze {

    /** Fatti dell'incidente rilevanti per i termini. */
    public record Fatti(Instant conoscenzaIl, Instant conclusioneGestioneIl, List<FattoAdempimento> adempimenti) {
    }

    /** Stato di trasmissione di un adempimento esistente. */
    public record FattoAdempimento(TipoAdempimento tipo, int progressivo, Instant trasmessoIl, Instant scadenzaRichiesta) {
    }

    private static final int MAX_MENSILI = 120;

    private final ZoneId fuso;

    public CalcoloScadenze(@Value("${notifiche.fuso-orario:Europe/Rome}") ZoneId fuso) {
        this.fuso = fuso;
    }

    public List<Scadenza> calcola(Fatti fatti, Instant ora) {
        List<Scadenza> piano = new ArrayList<>();
        Instant conoscenza = fatti.conoscenzaIl();
        if (conoscenza == null) {
            return piano;
        }

        piano.add(scadenza(TipoAdempimento.PRE_NOTIFICA, 1, conoscenza.plus(Duration.ofHours(24)), fatti, ora,
                "Entro 24 ore dalla conoscenza dell'incidente significativo (art. 25, c. 5, lett. a)"));
        piano.add(scadenza(TipoAdempimento.NOTIFICA, 1, conoscenza.plus(Duration.ofHours(72)), fatti, ora,
                "Entro 72 ore dalla conoscenza dell'incidente significativo (art. 25, c. 5, lett. b)"));

        fatti.adempimenti().stream()
                .filter(a -> a.tipo() == TipoAdempimento.RELAZIONE_INTERMEDIA)
                .forEach(a -> piano.add(scadenza(a.tipo(), a.progressivo(), a.scadenzaRichiesta(), fatti, ora,
                        a.scadenzaRichiesta() == null
                                ? "Su richiesta del CSIRT Italia, senza termine indicato (art. 25, c. 5, lett. c)"
                                : "Termine indicato dal CSIRT Italia nella richiesta (art. 25, c. 5, lett. c)")));

        Optional<Instant> notifica = trasmissione(fatti, TipoAdempimento.NOTIFICA, 1);
        if (notifica.isEmpty()) {
            piano.add(scadenza(TipoAdempimento.RELAZIONE_FINALE, 1, null, fatti, ora,
                    "Entro un mese dalla trasmissione della notifica, non ancora avvenuta (art. 25, c. 5, lett. d)"));
            return piano;
        }

        Instant n = notifica.get();
        Instant termineOrdinario = piuMesi(n, 1);
        Instant conclusione = fatti.conclusioneGestioneIl();

        if (conclusione != null && !conclusione.isAfter(termineOrdinario)) {
            piano.add(scadenza(TipoAdempimento.RELAZIONE_FINALE, 1, termineOrdinario, fatti, ora,
                    "Entro un mese dalla trasmissione della notifica (art. 25, c. 5, lett. d)"));
            return piano;
        }
        if (conclusione == null && ora.isBefore(termineOrdinario)) {
            piano.add(scadenza(TipoAdempimento.RELAZIONE_FINALE, 1, termineOrdinario, fatti, ora,
                    "Entro un mese dalla trasmissione della notifica (art. 25, c. 5, lett. d). Se a quella data "
                            + "la gestione è ancora in corso, è dovuta una relazione mensile e la finale slitta a un "
                            + "mese dalla conclusione (lett. e)"));
            return piano;
        }

        // Incidente in corso alla data della relazione finale: relazioni mensili sui progressi (lett. e)
        for (int k = 1; k <= MAX_MENSILI; k++) {
            Instant termine = piuMesi(n, k);
            if (conclusione != null && !termine.isBefore(conclusione)) {
                break;
            }
            piano.add(scadenza(TipoAdempimento.RELAZIONE_MENSILE, k, termine, fatti, ora,
                    "Relazione mensile n. " + k + " sui progressi: incidente in corso (art. 25, c. 5, lett. e)"));
            if (conclusione == null && termine.isAfter(ora)) {
                break;
            }
        }
        if (conclusione != null) {
            piano.add(scadenza(TipoAdempimento.RELAZIONE_FINALE, 1, piuMesi(conclusione, 1), fatti, ora,
                    "Entro un mese dalla conclusione della gestione dell'incidente (art. 25, c. 5, lett. e)"));
        } else {
            piano.add(scadenza(TipoAdempimento.RELAZIONE_FINALE, 1, null, fatti, ora,
                    "Entro un mese dalla conclusione della gestione, non ancora registrata (art. 25, c. 5, lett. e)"));
        }
        return piano;
    }

    /** Somma di mesi di calendario nel fuso del soggetto (java.time porta il 31 all'ultimo giorno del mese). */
    Instant piuMesi(Instant istante, int mesi) {
        return istante.atZone(fuso).plusMonths(mesi).toInstant();
    }

    private Scadenza scadenza(TipoAdempimento tipo, int progressivo, Instant termine, Fatti fatti,
                              Instant ora, String nota) {
        Instant trasmesso = trasmissione(fatti, tipo, progressivo).orElse(null);
        return new Scadenza(tipo, progressivo, termine, trasmesso, stato(tipo, termine, trasmesso, ora), nota);
    }

    private static Optional<Instant> trasmissione(Fatti fatti, TipoAdempimento tipo, int progressivo) {
        return fatti.adempimenti().stream()
                .filter(a -> a.tipo() == tipo && a.progressivo() == progressivo)
                .map(FattoAdempimento::trasmessoIl)
                .filter(Objects::nonNull)
                .findFirst();
    }

    static StatoScadenza stato(TipoAdempimento tipo, Instant termine, Instant trasmesso, Instant ora) {
        if (trasmesso != null) {
            return termine == null || !trasmesso.isAfter(termine) ? StatoScadenza.RISPETTATA : StatoScadenza.TARDIVA;
        }
        if (termine == null) {
            return StatoScadenza.IN_ATTESA;
        }
        if (ora.isAfter(termine)) {
            return StatoScadenza.SCADUTA;
        }
        return Duration.between(ora, termine).compareTo(preavviso(tipo)) <= 0
                ? StatoScadenza.IN_SCADENZA : StatoScadenza.APERTA;
    }

    /** Anticipo con cui una scadenza è segnalata come imminente. */
    static Duration preavviso(TipoAdempimento tipo) {
        return switch (tipo) {
            case PRE_NOTIFICA -> Duration.ofHours(6);
            case NOTIFICA -> Duration.ofHours(12);
            default -> Duration.ofDays(3);
        };
    }
}
