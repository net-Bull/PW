package it.pw29.notifiche;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Orologio iniettabile nel fuso del soggetto ({@code notifiche.fuso-orario}): i termini 24h/72h/1 mese
 * dipendono dal tempo, e nei test l'orologio va fissato per rendere gli scenari riproducibili. Il fuso è
 * quello dell'orologio, non quello del sistema operativo: in un container il sistema è spesso in UTC.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${notifiche.fuso-orario:Europe/Rome}") ZoneId fuso) {
        return Clock.system(fuso);
    }
}
