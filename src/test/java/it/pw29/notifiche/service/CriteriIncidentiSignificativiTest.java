package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.LivelloServizio;
import it.pw29.notifiche.domain.TipoSoggetto;
import it.pw29.notifiche.supporto.Incidenti;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static it.pw29.notifiche.domain.TipoSoggetto.ESSENZIALE;
import static it.pw29.notifiche.domain.TipoSoggetto.IMPORTANTE;
import static org.assertj.core.api.Assertions.assertThat;

/** Categorie IS-1..IS-4: un caso che le attiva e uno immediatamente sotto, per ciascuna. */
class CriteriIncidentiSignificativiTest {

    private final CriteriIncidentiSignificativi criteri = new CriteriIncidentiSignificativi();

    /** SL del SOC/MDR del contesto: 30 minuti di indisponibilità, 60 di degrado. */
    private final LivelloServizio sl = new LivelloServizio(null, "SOC_MDR", "SOC / MDR 24/7", 30, 60, "test", 9000, true);

    private java.util.List<CategoriaIs> valuta(Incidenti i, TipoSoggetto tipo) {
        return criteri.valuta(i.build(), tipo, sl);
    }

    @Test
    @DisplayName("Incidente neutro: nessuna categoria")
    void neutro() {
        assertThat(valuta(Incidenti.neutro(), ESSENZIALE)).isEmpty();
    }

    @Test
    @DisplayName("IS-1: perdita di riservatezza verso l'esterno sì, senza effetti esterni no")
    void is1() {
        assertThat(valuta(Incidenti.neutro().riservatezza().esterno(), IMPORTANTE)).containsExactly(CategoriaIs.IS_1);
        assertThat(valuta(Incidenti.neutro().riservatezza(), IMPORTANTE)).isEmpty();
    }

    @Test
    @DisplayName("IS-2: perdita di integrità con impatto esterno sì, interna no")
    void is2() {
        assertThat(valuta(Incidenti.neutro().integrita().esterno(), IMPORTANTE)).containsExactly(CategoriaIs.IS_2);
        assertThat(valuta(Incidenti.neutro().integrita(), IMPORTANTE)).isEmpty();
    }

    @Test
    @DisplayName("IS-3: SL superato (31 min indisponibilità o 61 min degrado) sì; sul limite no")
    void is3() {
        assertThat(valuta(Incidenti.neutro().indisponibilita(31), IMPORTANTE)).containsExactly(CategoriaIs.IS_3);
        assertThat(valuta(Incidenti.neutro().degrado(61), IMPORTANTE)).containsExactly(CategoriaIs.IS_3);
        assertThat(valuta(Incidenti.neutro().indisponibilita(30).degrado(60), IMPORTANTE)).isEmpty();
    }

    @Test
    @DisplayName("IS-4: accesso non autorizzato solo per i soggetti essenziali")
    void is4() {
        assertThat(valuta(Incidenti.neutro().accesso(), ESSENZIALE)).containsExactly(CategoriaIs.IS_4);
        assertThat(valuta(Incidenti.neutro().accesso(), IMPORTANTE)).isEmpty();
    }

    @Test
    @DisplayName("IS-3 usa lo SL del servizio coinvolto, non una soglia fissa")
    void slDelServizio() {
        LivelloServizio portale = new LivelloServizio(null, "PORTALE_TICKETING", "Portale", 120, 240, "test", 9000, false);
        assertThat(criteri.valuta(Incidenti.neutro().servizio("PORTALE_TICKETING").indisponibilita(90).build(),
                IMPORTANTE, portale)).isEmpty();
        assertThat(criteri.valuta(Incidenti.neutro().servizio("PORTALE_TICKETING").indisponibilita(121).build(),
                IMPORTANTE, portale)).containsExactly(CategoriaIs.IS_3);
    }

    @Test
    @DisplayName("IS-3: un'interruzione programmata non viola gli SL")
    void is3Programmata() {
        assertThat(valuta(Incidenti.neutro().indisponibilita(45).programmata(), IMPORTANTE)).isEmpty();
    }
}
