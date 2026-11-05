package it.pw29.notifiche.supporto;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Orologio dei test: fermo su un istante scelto, avanzabile a comando. */
public class OrologioRegolabile extends Clock {

    private volatile Instant adesso;
    private final ZoneId zona;

    public OrologioRegolabile(Instant iniziale, ZoneId zona) {
        this.adesso = iniziale;
        this.zona = zona;
    }

    public void imposta(Instant istante) { this.adesso = istante; }
    public void avanza(Duration d) { this.adesso = adesso.plus(d); }

    @Override public ZoneId getZone() { return zona; }
    @Override public Clock withZone(ZoneId z) { return new OrologioRegolabile(adesso, z); }
    @Override public Instant instant() { return adesso; }
}
