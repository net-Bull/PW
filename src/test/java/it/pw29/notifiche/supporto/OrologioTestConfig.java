package it.pw29.notifiche.supporto;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.ZoneId;

/** Sostituisce l'orologio di sistema con {@link OrologioRegolabile} (ClockConfig lo rende iniettabile). */
@TestConfiguration
public class OrologioTestConfig {

    public static final Instant INIZIO = Instant.parse("2026-11-02T08:00:00Z");

    @Bean
    @Primary
    public OrologioRegolabile orologioRegolabile() {
        return new OrologioRegolabile(INIZIO, ZoneId.of("Europe/Rome"));
    }
}
