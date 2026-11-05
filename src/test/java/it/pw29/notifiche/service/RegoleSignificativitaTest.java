package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.LivelloServizio;
import it.pw29.notifiche.domain.Regola;
import it.pw29.notifiche.domain.SogliaSet;
import it.pw29.notifiche.dto.EsitoSignificativita;
import it.pw29.notifiche.dto.ValutazioneRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per ogni regola: un caso immediatamente sopra soglia (attiva il flusso) e uno
 * immediatamente sotto, cioè esattamente sul valore limite (non lo attiva).
 */
class RegoleSignificativitaTest {

    private final RegoleSignificativita regole = new RegoleSignificativita();

    private final SogliaSet soglie = new SogliaSet(1L, "TEST", LocalDate.of(2024, 10, 18), "test",
            new BigDecimal("7000000"), 30, 60, new BigDecimal("0.05"), 1_000_000,
            new BigDecimal("500000"), new BigDecimal("0.05"), 6, 2);

    /** Servizi del contesto (V6): SOC/MDR e WAF sono servizi di sicurezza gestiti, il portale no. */
    private final LivelloServizio soc = new LivelloServizio(null, "SOC_MDR", "SOC / MDR 24/7", 30, 60, "test", 9000, true);
    private final LivelloServizio waf = new LivelloServizio(null, "WAF_GESTITO", "WAF gestito", 30, 60, "test", 5400, true);
    private final LivelloServizio portale = new LivelloServizio(null, "PORTALE_TICKETING", "Portale", 120, 240, "test", 9000, false);

    /** Adatta la vecchia firma (dati compromessi = perdita di riservatezza) al nuovo ValutazioneRequest. */
    private static ValutazioneRequest base() {
        return con(0, 0, 0, false, false, false, false, "0", 0, "0");
    }

    private static ValutazioneRequest con(int indisp, int degrado, int utenti, boolean dati, boolean dolo,
                                          boolean accesso, boolean grave, String perdita,
                                          int ricorrenti, String perditeCumulate) {
        return new ValutazioneRequest("SOC_MDR", indisp, degrado, utenti, dati, false, false, false, dolo, accesso, grave,
                new BigDecimal(perdita), ricorrenti, new BigDecimal(perditeCumulate), null, null);
    }

    @Test
    @DisplayName("R3/R4: anche la sola perdita di integrità o di autenticità conta come dati compromessi")
    void integritaEAutenticita() {
        ValutazioneRequest integrita = new ValutazioneRequest("SOC_MDR", 0, 0, 451, false, true, false, false,
                true, false, false, BigDecimal.ZERO, 0, BigDecimal.ZERO, null, null);
        ValutazioneRequest autenticita = new ValutazioneRequest("SOC_MDR", 0, 0, 0, false, false, true, false,
                true, false, false, BigDecimal.ZERO, 0, BigDecimal.ZERO, null, null);
        assertThat(regole.valuta(integrita, soglie, soc).regoleAttivate()).containsExactly(Regola.R3, Regola.R4);
        assertThat(regole.valuta(autenticita, soglie, soc).regoleAttivate()).containsExactly(Regola.R3);
    }

    @Test
    @DisplayName("Soglie calcolate dai parametri del contesto: 450 utenti del SOC/MDR, 270 del WAF, 350.000 EUR")
    void soglieCalcolate() {
        assertThat(soglie.sogliaUtenti(soc.getUtentiUe())).isEqualTo(450);
        assertThat(soglie.sogliaUtenti(waf.getUtentiUe())).isEqualTo(270);
        assertThat(soglie.sogliaUtenti(40_000_000)).isEqualTo(1_000_000);
        assertThat(soglie.sogliaEconomica()).isEqualByComparingTo("350000");
    }

    @Test
    @DisplayName("Incidente neutro: nessuna regola, flusso non attivato")
    void neutro() {
        EsitoSignificativita esito = regole.valuta(base(), soglie, soc);
        assertThat(esito.significativo()).isFalse();
        assertThat(esito.regoleAttivate()).isEmpty();
    }

    @Test
    @DisplayName("R1: 31 minuti di indisponibilità attiva, 30 no")
    void r1() {
        assertThat(regole.valuta(con(31, 0, 0, false, false, false, false, "0", 0, "0"), soglie, soc).regoleAttivate())
                .containsExactly(Regola.R1);
        assertThat(regole.valuta(con(30, 0, 0, false, false, false, false, "0", 0, "0"), soglie, soc).significativo())
                .isFalse();
    }

    @Test
    @DisplayName("R2: 61 minuti e 451 utenti attiva; 60 minuti o 450 utenti no")
    void r2() {
        assertThat(regole.valuta(con(0, 61, 451, false, false, false, false, "0", 0, "0"), soglie, soc).regoleAttivate())
                .containsExactly(Regola.R2);
        assertThat(regole.valuta(con(0, 60, 451, false, false, false, false, "0", 0, "0"), soglie, soc).significativo())
                .isFalse();
        assertThat(regole.valuta(con(0, 61, 450, false, false, false, false, "0", 0, "0"), soglie, soc).significativo())
                .isFalse();
    }

    @Test
    @DisplayName("R3: dati compromessi con sospetto doloso attiva, senza dolo no")
    void r3() {
        assertThat(regole.valuta(con(0, 0, 0, true, true, false, false, "0", 0, "0"), soglie, soc).regoleAttivate())
                .containsExactly(Regola.R3);
        assertThat(regole.valuta(con(0, 0, 0, true, false, false, false, "0", 0, "0"), soglie, soc).significativo())
                .isFalse();
    }

    @Test
    @DisplayName("R4: dati compromessi su 451 utenti attiva, su 450 no")
    void r4() {
        assertThat(regole.valuta(con(0, 0, 451, true, false, false, false, "0", 0, "0"), soglie, soc).regoleAttivate())
                .containsExactly(Regola.R4);
        assertThat(regole.valuta(con(0, 0, 450, true, false, false, false, "0", 0, "0"), soglie, soc).significativo())
                .isFalse();
    }

    @Test
    @DisplayName("R5: perdita di 350.000,01 EUR attiva, 350.000 no")
    void r5() {
        assertThat(regole.valuta(con(0, 0, 0, false, false, false, false, "350000.01", 0, "0"), soglie, soc)
                .regoleAttivate()).containsExactly(Regola.R5);
        assertThat(regole.valuta(con(0, 0, 0, false, false, false, false, "350000", 0, "0"), soglie, soc)
                .significativo()).isFalse();
    }

    @Test
    @DisplayName("R6: accesso non autorizzato doloso con grave perturbazione attiva, senza perturbazione no")
    void r6() {
        assertThat(regole.valuta(con(0, 0, 0, false, true, true, true, "0", 0, "0"), soglie, soc).regoleAttivate())
                .containsExactly(Regola.R6);
        assertThat(regole.valuta(con(0, 0, 0, false, true, true, false, "0", 0, "0"), soglie, soc).significativo())
                .isFalse();
    }

    @Test
    @DisplayName("R7: 2 incidenti ricorrenti con 350.000,01 EUR cumulati attiva; 350.000 o 1 incidente no")
    void r7() {
        assertThat(regole.valuta(con(0, 0, 0, false, false, false, false, "0", 2, "350000.01"), soglie, soc)
                .regoleAttivate()).containsExactly(Regola.R7);
        assertThat(regole.valuta(con(0, 0, 0, false, false, false, false, "0", 2, "350000"), soglie, soc)
                .significativo()).isFalse();
        assertThat(regole.valuta(con(0, 0, 0, false, false, false, false, "0", 1, "900000"), soglie, soc)
                .significativo()).isFalse();
    }

    @Test
    @DisplayName("Più regole possono scattare insieme e vengono tutte registrate")
    void regoleMultiple() {
        EsitoSignificativita esito = regole.valuta(con(45, 0, 600, true, true, false, false, "0", 0, "0"), soglie, soc);
        assertThat(esito.regoleAttivate()).containsExactly(Regola.R1, Regola.R3, Regola.R4);
        assertThat(esito.versioneSoglie()).isEqualTo("TEST");
    }

    @Test
    @DisplayName("Art. 10: soglia sugli utenti del servizio coinvolto (WAF: 271 sì, 270 no)")
    void sogliaPerServizio() {
        ValutazioneRequest dati271 = new ValutazioneRequest("WAF_GESTITO", 0, 0, 271, true, false, false, false,
                false, false, false, BigDecimal.ZERO, 0, BigDecimal.ZERO, null, null);
        ValutazioneRequest dati270 = new ValutazioneRequest("WAF_GESTITO", 0, 0, 270, true, false, false, false,
                false, false, false, BigDecimal.ZERO, 0, BigDecimal.ZERO, null, null);
        assertThat(regole.valuta(dati271, soglie, waf).regoleAttivate()).containsExactly(Regola.R4);
        assertThat(regole.valuta(dati270, soglie, waf).significativo()).isFalse();
    }

    @Test
    @DisplayName("Art. 10 solo per i servizi gestiti: il portale indisponibile 31 minuti non attiva R1")
    void servizioNonGestito() {
        assertThat(regole.valuta(con(31, 0, 0, false, false, false, false, "0", 0, "0"), soglie, portale).significativo())
                .isFalse();
        // I criteri orizzontali degli artt. 3 e 4 valgono anche per il portale
        assertThat(regole.valuta(con(0, 0, 0, false, false, false, false, "350000.01", 0, "0"), soglie, portale)
                .regoleAttivate()).containsExactly(Regola.R5);
    }

    @Test
    @DisplayName("R8: esfiltrazione di segreti commerciali, anche senza dolo e su qualunque servizio")
    void segretiCommerciali() {
        ValutazioneRequest segreti = new ValutazioneRequest("PORTALE_TICKETING", 0, 0, 0, true, false, false, false,
                false, false, false, BigDecimal.ZERO, 0, BigDecimal.ZERO, true, null);
        assertThat(regole.valuta(segreti, soglie, portale).regoleAttivate()).containsExactly(Regola.R8);
        assertThat(regole.valuta(base(), soglie, portale).significativo()).isFalse();
    }

    @Test
    @DisplayName("Interruzione programmata (art. 3, par. 2): 45 minuti di indisponibilità non attivano R1 né R2")
    void interruzioneProgrammata() {
        ValutazioneRequest programmata = new ValutazioneRequest("SOC_MDR", 45, 90, 9000, false, false, false, false,
                false, false, false, BigDecimal.ZERO, 0, BigDecimal.ZERO, null, true);
        assertThat(regole.valuta(programmata, soglie, soc).significativo()).isFalse();
        ValutazioneRequest nonProgrammata = new ValutazioneRequest("SOC_MDR", 45, 90, 9000, false, false, false, false,
                false, false, false, BigDecimal.ZERO, 0, BigDecimal.ZERO, null, false);
        assertThat(regole.valuta(nonProgrammata, soglie, soc).regoleAttivate()).containsExactly(Regola.R1, Regola.R2);
    }
}
