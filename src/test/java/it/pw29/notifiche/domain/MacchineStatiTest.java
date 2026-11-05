package it.pw29.notifiche.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le tabelle delle transizioni sono verificate in modo esaustivo: ogni coppia (da, a) è ammessa o rifiutata. */
class MacchineStatiTest {

    @Test
    @DisplayName("Incidente: transizioni ammesse e rifiutate per ogni coppia di stati")
    void incidente() {
        Map<StatoIncidente, Set<StatoIncidente>> attese = Map.of(
                StatoIncidente.IN_VALUTAZIONE, EnumSet.of(StatoIncidente.NON_SIGNIFICATIVO, StatoIncidente.SIGNIFICATIVO),
                StatoIncidente.NON_SIGNIFICATIVO, EnumSet.of(StatoIncidente.SIGNIFICATIVO),
                StatoIncidente.SIGNIFICATIVO, EnumSet.of(StatoIncidente.NON_SIGNIFICATIVO, StatoIncidente.DECLASSATO,
                        StatoIncidente.CHIUSO),
                StatoIncidente.DECLASSATO, EnumSet.of(StatoIncidente.SIGNIFICATIVO, StatoIncidente.CHIUSO),
                StatoIncidente.CHIUSO, EnumSet.noneOf(StatoIncidente.class));
        for (StatoIncidente da : StatoIncidente.values()) {
            for (StatoIncidente a : StatoIncidente.values()) {
                assertThat(da.puoPassareA(a)).as(da + " -> " + a).isEqualTo(attese.get(da).contains(a));
            }
        }
    }

    @Test
    @DisplayName("Documento: bozza -> in approvazione -> trasmesso; trasmesso è terminale")
    void documento() {
        Map<StatoDocumento, Set<StatoDocumento>> attese = Map.of(
                StatoDocumento.BOZZA, EnumSet.of(StatoDocumento.IN_APPROVAZIONE),
                StatoDocumento.IN_APPROVAZIONE, EnumSet.of(StatoDocumento.BOZZA, StatoDocumento.TRASMESSO),
                StatoDocumento.TRASMESSO, EnumSet.noneOf(StatoDocumento.class));
        for (StatoDocumento da : StatoDocumento.values()) {
            for (StatoDocumento a : StatoDocumento.values()) {
                assertThat(da.puoPassareA(a)).as(da + " -> " + a).isEqualTo(attese.get(da).contains(a));
            }
        }
        assertThatThrownBy(() -> StatoDocumento.TRASMESSO.verificaTransizione(StatoDocumento.BOZZA))
                .isInstanceOf(TransizioneNonAmmessaException.class);
    }

    @Test
    @DisplayName("Conoscenza: registrabile una sola volta, non prima della rilevazione né nel futuro")
    void conoscenzaImmutabile() {
        Instant rilevato = Instant.parse("2026-11-02T08:00:00Z");
        Instant ora = Instant.parse("2026-11-02T09:00:00Z");
        Incidente inc = new Incidente("INC-T", null, "t", "d", rilevato, ora, "op");
        assertThatThrownBy(() -> inc.registraConoscenza(rilevato.minusSeconds(1), "m", ora, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inc.registraConoscenza(ora.plusSeconds(1), "m", ora, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inc.registraConoscenza(ora, " ", ora, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inc.cambiaStato(StatoIncidente.SIGNIFICATIVO))
                .isInstanceOf(IllegalStateException.class);

        inc.registraConoscenza(rilevato.plusSeconds(1200), "Esfiltrazione confermata dai log del WAF", ora, "op");
        assertThatThrownBy(() -> inc.registraConoscenza(rilevato.plusSeconds(1800), "altra", ora, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non è modificabile");
        assertThat(inc.getConoscenzaIl()).isEqualTo(rilevato.plusSeconds(1200));
    }

    @Test
    @DisplayName("Conclusione della gestione: registrabile una sola volta, non prima della conoscenza né nel futuro")
    void conclusioneImmutabile() {
        Instant rilevato = Instant.parse("2026-11-02T08:00:00Z");
        Instant ora = Instant.parse("2026-11-20T09:00:00Z");
        Incidente inc = new Incidente("INC-T", null, "t", "d", rilevato, rilevato, "op");
        inc.registraConoscenza(rilevato.plusSeconds(600), "Indisponibilità confermata", rilevato.plusSeconds(600), "op");
        assertThatThrownBy(() -> inc.registraConclusioneGestione(rilevato, ora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inc.registraConclusioneGestione(ora.plusSeconds(1), ora))
                .isInstanceOf(IllegalArgumentException.class);
        inc.registraConclusioneGestione(ora.minusSeconds(3600), ora);
        assertThatThrownBy(() -> inc.registraConclusioneGestione(ora, ora))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non è modificabile");
        assertThat(inc.getConclusioneGestioneIl()).isEqualTo(ora.minusSeconds(3600));
    }

    @Test
    @DisplayName("IS-4 si applica solo ai soggetti essenziali")
    void is4SoloEssenziali() {
        assertThat(CategoriaIs.IS_4.applicabileA(TipoSoggetto.ESSENZIALE)).isTrue();
        assertThat(CategoriaIs.IS_4.applicabileA(TipoSoggetto.IMPORTANTE)).isFalse();
        assertThat(CategoriaIs.IS_3.applicabileA(TipoSoggetto.IMPORTANTE)).isTrue();
    }
}
