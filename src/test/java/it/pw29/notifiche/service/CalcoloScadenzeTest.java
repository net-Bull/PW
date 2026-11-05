package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.StatoScadenza;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.dto.Scadenza;
import it.pw29.notifiche.service.CalcoloScadenze.FattoAdempimento;
import it.pw29.notifiche.service.CalcoloScadenze.Fatti;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static it.pw29.notifiche.domain.TipoAdempimento.NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.PRE_NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_FINALE;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_INTERMEDIA;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_MENSILE;
import static org.assertj.core.api.Assertions.assertThat;

/** Termini dell'art. 25, c. 5, D.Lgs. 138/2024, con orologio esplicito. */
class CalcoloScadenzeTest {

    private final CalcoloScadenze calcolo = new CalcoloScadenze(ZoneId.of("Europe/Rome"));

    private static final Instant CONOSCENZA = Instant.parse("2026-11-02T09:30:00Z");

    private static FattoAdempimento trasmesso(TipoAdempimento t, int n, String istante) {
        return new FattoAdempimento(t, n, Instant.parse(istante), null);
    }

    private static FattoAdempimento aperto(TipoAdempimento t, int n) {
        return new FattoAdempimento(t, n, null, null);
    }

    private List<Scadenza> piano(Instant conoscenza, Instant conclusione, Instant ora, FattoAdempimento... fatti) {
        return calcolo.calcola(new Fatti(conoscenza, conclusione, new ArrayList<>(Arrays.asList(fatti))), ora);
    }

    private static Scadenza di(List<Scadenza> piano, TipoAdempimento t, int n) {
        return piano.stream().filter(s -> s.tipo() == t && s.progressivo() == n).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Senza conoscenza dell'incidente significativo non decorre alcun termine")
    void senzaConoscenza() {
        assertThat(piano(null, null, CONOSCENZA)).isEmpty();
    }

    @Test
    @DisplayName("Pre-notifica a 24 h e notifica a 72 h dalla conoscenza; finale in attesa della notifica")
    void termini24e72() {
        List<Scadenza> p = piano(CONOSCENZA, null, CONOSCENZA, aperto(PRE_NOTIFICA, 1), aperto(NOTIFICA, 1));
        assertThat(di(p, PRE_NOTIFICA, 1).dovutaEntro()).isEqualTo(Instant.parse("2026-11-03T09:30:00Z"));
        assertThat(di(p, NOTIFICA, 1).dovutaEntro()).isEqualTo(Instant.parse("2026-11-05T09:30:00Z"));
        assertThat(di(p, RELAZIONE_FINALE, 1).dovutaEntro()).isNull();
        assertThat(di(p, RELAZIONE_FINALE, 1).stato()).isEqualTo(StatoScadenza.IN_ATTESA);
    }

    @Test
    @DisplayName("Cambio d'ora (25/10/2026): le 24 h sono ore reali, l'orario locale cambia di un'ora")
    void cambioOra() {
        Instant conoscenza = Instant.parse("2026-10-24T10:00:00Z"); // 12:00 ora legale
        Scadenza pre = di(piano(conoscenza, null, conoscenza), PRE_NOTIFICA, 1);
        assertThat(Duration.between(conoscenza, pre.dovutaEntro())).isEqualTo(Duration.ofHours(24));
        assertThat(pre.dovutaEntro().atZone(ZoneId.of("Europe/Rome")).getHour()).isEqualTo(11); // ora solare
    }

    @Test
    @DisplayName("Stati: aperta, in scadenza (entro 6 h), scaduta, rispettata, tardiva")
    void stati() {
        Instant termine = CONOSCENZA.plus(Duration.ofHours(24));
        assertThat(di(piano(CONOSCENZA, null, CONOSCENZA), PRE_NOTIFICA, 1).stato()).isEqualTo(StatoScadenza.APERTA);
        assertThat(di(piano(CONOSCENZA, null, termine.minus(Duration.ofHours(6))), PRE_NOTIFICA, 1).stato())
                .isEqualTo(StatoScadenza.IN_SCADENZA);
        assertThat(di(piano(CONOSCENZA, null, termine.plusSeconds(1)), PRE_NOTIFICA, 1).stato())
                .isEqualTo(StatoScadenza.SCADUTA);
        assertThat(di(piano(CONOSCENZA, null, termine.plusSeconds(60),
                trasmesso(PRE_NOTIFICA, 1, "2026-11-03T09:30:00Z")), PRE_NOTIFICA, 1).stato())
                .isEqualTo(StatoScadenza.RISPETTATA);
        assertThat(di(piano(CONOSCENZA, null, termine.plusSeconds(60),
                trasmesso(PRE_NOTIFICA, 1, "2026-11-03T09:30:01Z")), PRE_NOTIFICA, 1).stato())
                .isEqualTo(StatoScadenza.TARDIVA);
    }

    @Test
    @DisplayName("Relazione finale: un mese di calendario dalla trasmissione della notifica, stesso orario locale")
    void finaleUnMeseDallaNotifica() {
        Instant ora = Instant.parse("2026-11-06T10:00:00Z");
        List<Scadenza> p = piano(CONOSCENZA, null, ora, trasmesso(PRE_NOTIFICA, 1, "2026-11-02T18:00:00Z"),
                trasmesso(NOTIFICA, 1, "2026-11-04T15:20:00Z"));
        assertThat(di(p, RELAZIONE_FINALE, 1).dovutaEntro()).isEqualTo(Instant.parse("2026-12-04T15:20:00Z"));
        assertThat(p).noneMatch(s -> s.tipo() == RELAZIONE_MENSILE);
    }

    @Test
    @DisplayName("Notifica trasmessa il 31 gennaio: la finale scade l'ultimo giorno di febbraio")
    void fineMese() {
        Instant notifica = Instant.parse("2027-01-31T10:00:00Z");
        Scadenza finale = di(piano(notifica.minus(Duration.ofHours(40)), null, notifica,
                trasmesso(PRE_NOTIFICA, 1, "2027-01-30T08:00:00Z"), trasmesso(NOTIFICA, 1, "2027-01-31T10:00:00Z")),
                RELAZIONE_FINALE, 1);
        assertThat(finale.dovutaEntro()).isEqualTo(Instant.parse("2027-02-28T10:00:00Z"));
    }

    @Test
    @DisplayName("Incidente in corso alla data della finale: relazioni mensili e finale in attesa della conclusione")
    void inCorsoMensili() {
        Instant ora = Instant.parse("2027-01-10T09:00:00Z");
        List<Scadenza> p = piano(CONOSCENZA, null, ora, trasmesso(PRE_NOTIFICA, 1, "2026-11-02T18:00:00Z"),
                trasmesso(NOTIFICA, 1, "2026-11-04T15:20:00Z"), trasmesso(RELAZIONE_MENSILE, 1, "2026-12-03T10:00:00Z"));
        assertThat(di(p, RELAZIONE_MENSILE, 1).stato()).isEqualTo(StatoScadenza.RISPETTATA);
        assertThat(di(p, RELAZIONE_MENSILE, 2).dovutaEntro()).isEqualTo(Instant.parse("2027-01-04T15:20:00Z"));
        assertThat(di(p, RELAZIONE_MENSILE, 2).stato()).isEqualTo(StatoScadenza.SCADUTA);
        assertThat(di(p, RELAZIONE_MENSILE, 3).dovutaEntro()).isEqualTo(Instant.parse("2027-02-04T15:20:00Z"));
        assertThat(p).noneMatch(s -> s.tipo() == RELAZIONE_MENSILE && s.progressivo() == 4);
        assertThat(di(p, RELAZIONE_FINALE, 1).dovutaEntro()).isNull();
    }

    @Test
    @DisplayName("Gestione conclusa dopo la data della finale: mensili fino alla conclusione, finale a un mese da essa")
    void conclusioneTardiva() {
        Instant conclusione = Instant.parse("2027-01-20T12:00:00Z");
        List<Scadenza> p = piano(CONOSCENZA, conclusione, Instant.parse("2027-01-21T09:00:00Z"),
                trasmesso(PRE_NOTIFICA, 1, "2026-11-02T18:00:00Z"), trasmesso(NOTIFICA, 1, "2026-11-04T15:20:00Z"));
        assertThat(p.stream().filter(s -> s.tipo() == RELAZIONE_MENSILE)).hasSize(2); // 4/12 e 4/1
        assertThat(di(p, RELAZIONE_FINALE, 1).dovutaEntro()).isEqualTo(Instant.parse("2027-02-20T12:00:00Z"));
    }

    @Test
    @DisplayName("Gestione conclusa entro la data della finale: termine ordinario, nessuna mensile")
    void conclusioneTempestiva() {
        List<Scadenza> p = piano(CONOSCENZA, Instant.parse("2026-11-20T12:00:00Z"), Instant.parse("2026-12-10T09:00:00Z"),
                trasmesso(PRE_NOTIFICA, 1, "2026-11-02T18:00:00Z"), trasmesso(NOTIFICA, 1, "2026-11-04T15:20:00Z"));
        assertThat(di(p, RELAZIONE_FINALE, 1).dovutaEntro()).isEqualTo(Instant.parse("2026-12-04T15:20:00Z"));
        assertThat(di(p, RELAZIONE_FINALE, 1).stato()).isEqualTo(StatoScadenza.SCADUTA);
        assertThat(p).noneMatch(s -> s.tipo() == RELAZIONE_MENSILE);
    }

    @Test
    @DisplayName("Relazione intermedia: termine indicato dal CSIRT Italia, o in attesa se non indicato")
    void intermedia() {
        Instant termine = Instant.parse("2026-11-10T12:00:00Z");
        List<Scadenza> p = piano(CONOSCENZA, null, CONOSCENZA,
                new FattoAdempimento(RELAZIONE_INTERMEDIA, 1, null, termine),
                new FattoAdempimento(RELAZIONE_INTERMEDIA, 2, null, null));
        assertThat(di(p, RELAZIONE_INTERMEDIA, 1).dovutaEntro()).isEqualTo(termine);
        assertThat(di(p, RELAZIONE_INTERMEDIA, 2).stato()).isEqualTo(StatoScadenza.IN_ATTESA);
    }
}
