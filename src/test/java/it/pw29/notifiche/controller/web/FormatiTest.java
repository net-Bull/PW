package it.pw29.notifiche.controller.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lettura dei campi data e ora dell'interfaccia (precisione del minuto). Regressione della prova d'uso della
 * Fase 5: la trasmissione registrata nello stesso minuto dell'invio in approvazione, a minuto ormai trascorso,
 * veniva rifiutata come precedente all'invio.
 */
class FormatiTest {

    private static final Instant ADESSO = Instant.parse("2026-10-23T13:55:20Z");          // 15:55:20 ora italiana
    private static final Instant INVIO = Instant.parse("2026-10-23T13:54:30Z");           // 15:54:30 ora italiana
    private final Formati fmt = new Formati(ZoneId.of("Europe/Rome"), Clock.fixed(ADESSO, ZoneOffset.UTC));

    @Test
    @DisplayName("Il minuto corrente vale adesso")
    void minutoCorrente() {
        assertThat(fmt.leggi("2026-10-23T15:55")).isEqualTo(ADESSO);
    }

    @Test
    @DisplayName("Un minuto passato vale l'inizio del minuto")
    void minutoPassato() {
        assertThat(fmt.leggi("2026-10-23T15:54")).isEqualTo(Instant.parse("2026-10-23T13:54:00Z"));
        assertThat(fmt.leggi("")).isNull();
    }

    @Test
    @DisplayName("Se l'evento di riferimento cade nel minuto indicato, l'istante è l'evento")
    void eventoNelMinuto() {
        assertThat(fmt.leggi("2026-10-23T15:54", INVIO)).isEqualTo(INVIO);
    }

    @Test
    @DisplayName("Un minuto davvero precedente all'evento resta precedente (lo rifiuta il dominio)")
    void minutoPrecedente() {
        assertThat(fmt.leggi("2026-10-23T15:53", INVIO)).isEqualTo(Instant.parse("2026-10-23T13:53:00Z"));
    }

    @Test
    @DisplayName("Un minuto successivo all'evento non cambia")
    void minutoSuccessivo() {
        assertThat(fmt.leggi("2026-10-23T15:55", INVIO)).isEqualTo(ADESSO);
        assertThat(fmt.leggi("2026-10-23T15:56", INVIO)).isEqualTo(Instant.parse("2026-10-23T13:56:00Z"));
    }
}
