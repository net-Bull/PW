package it.pw29.notifiche.controller.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Formati dell'interfaccia, disponibili nei template come {@code @fmt}. Tutti gli istanti sono mostrati e letti
 * nel fuso del soggetto ({@code notifiche.fuso-orario}), lo stesso usato per i termini a mesi e per i documenti:
 * l'operatore inserisce l'ora che legge sull'orologio, il sistema la registra come istante.
 */
@Component("fmt")
public class Formati {

    private static final DateTimeFormatter DATA_ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALY);
    private static final DateTimeFormatter DATA_ORA_SECONDI = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ITALY);
    private static final DateTimeFormatter CAMPO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ZoneId fuso;
    private final Clock clock;

    public Formati(@Value("${notifiche.fuso-orario:Europe/Rome}") ZoneId fuso, Clock clock) {
        this.fuso = fuso;
        this.clock = clock;
    }

    public ZoneId fuso() {
        return fuso;
    }

    public Instant adesso() {
        return Instant.now(clock);
    }

    /** "23/10/2026 14:05", vuoto se assente. */
    public String data(Instant i) {
        return i == null ? "" : DATA_ORA.format(i.atZone(fuso));
    }

    public String dataSecondi(Instant i) {
        return i == null ? "" : DATA_ORA_SECONDI.format(i.atZone(fuso));
    }

    /** Valore per un campo {@code datetime-local}. */
    public String campo(Instant i) {
        return i == null ? "" : CAMPO.format(i.atZone(fuso).truncatedTo(ChronoUnit.MINUTES));
    }

    /** Ora corrente per un campo {@code datetime-local}. */
    public String campoAdesso() {
        return campo(adesso());
    }

    /**
     * Lettura di un campo {@code datetime-local}; null se vuoto. Il campo ha la precisione del minuto: se indica il
     * minuto corrente vale "adesso", così un istante proposto dal modulo non precede di qualche secondo un evento
     * appena registrato.
     */
    public Instant leggi(String valore) {
        return leggi(valore, null);
    }

    /**
     * Come {@link #leggi(String)}, con un evento già registrato al secondo che l'istante letto non può precedere
     * (ad esempio l'invio in approvazione prima della trasmissione). Il campo indica un minuto, cioè un intervallo
     * di 60 secondi: se l'evento cade dentro quel minuto, l'istante letto è l'evento stesso. Senza questa regola la
     * trasmissione registrata nello stesso minuto dell'invio in approvazione, ma a minuto ormai trascorso, sarebbe
     * rifiutata perché le ore e i minuti del modulo valgono l'inizio del minuto. Un minuto davvero precedente
     * all'evento resta precedente e il dominio lo rifiuta.
     */
    public Instant leggi(String valore, Instant nonPrimaDi) {
        if (valore == null || valore.isBlank()) {
            return null;
        }
        Instant letto;
        try {
            letto = LocalDateTime.parse(valore.trim(), CAMPO).atZone(fuso).toInstant();
        } catch (DateTimeParseException e) {
            throw new ModuloNonValidoException("Data e ora non valide: " + valore);
        }
        Instant adesso = adesso();
        Instant risultato = letto.equals(adesso.atZone(fuso).truncatedTo(ChronoUnit.MINUTES).toInstant()) ? adesso : letto;
        if (nonPrimaDi != null && risultato.isBefore(nonPrimaDi) && nonPrimaDi.isBefore(letto.plus(1, ChronoUnit.MINUTES))) {
            return nonPrimaDi;
        }
        return risultato;
    }

    /**
     * Tempo che manca a un termine, o da cui è superato, rispetto all'orologio del sistema: "mancano 3 h 12 min",
     * "scaduta da 2 g 4 h". Il testo iniziale è calcolato qui, così coincide con quello che vedono i test; nel
     * browser lo aggiorna uno script ogni 30 secondi.
     */
    public String residuo(Instant termine) {
        if (termine == null) {
            return "";
        }
        Duration d = Duration.between(adesso(), termine);
        return d.isNegative() ? "scaduto da " + durata(d.negated()) : "mancano " + durata(d);
    }

    public boolean superato(Instant termine) {
        return termine != null && adesso().isAfter(termine);
    }

    static String durata(Duration d) {
        long minuti = d.toMinutes();
        long giorni = minuti / (60 * 24);
        long ore = (minuti / 60) % 24;
        long min = minuti % 60;
        if (giorni > 0) {
            return giorni + " g " + ore + " h";
        }
        if (ore > 0) {
            return ore + " h " + min + " min";
        }
        return min + " min";
    }

    /** Impronta abbreviata per le tabelle: primi e ultimi 8 caratteri. */
    public String impronta(String sha) {
        if (sha == null || sha.length() < 20) {
            return sha == null ? "" : sha;
        }
        return sha.substring(0, 8) + "…" + sha.substring(sha.length() - 8);
    }
}
