package it.pw29.notifiche.supporto;

import it.pw29.notifiche.dto.ValutazioneRequest;

import java.math.BigDecimal;

/** Costruttore fluente di {@link ValutazioneRequest}: parte da un incidente "neutro" (nessun criterio). */
public final class Incidenti {

    private String servizio = "SOC_MDR";
    private int indisponibilita, degrado, utenti, ricorrenti;
    private boolean riservatezza, integrita, autenticita, esterno, dolo, accesso, grave, segreti, programmata;
    private BigDecimal perdita = BigDecimal.ZERO, cumulate = BigDecimal.ZERO;

    public static Incidenti neutro() { return new Incidenti(); }

    public Incidenti servizio(String s) { servizio = s; return this; }
    public Incidenti indisponibilita(int m) { indisponibilita = m; return this; }
    public Incidenti degrado(int m) { degrado = m; return this; }
    public Incidenti utenti(int n) { utenti = n; return this; }
    public Incidenti riservatezza() { riservatezza = true; return this; }
    public Incidenti integrita() { integrita = true; return this; }
    public Incidenti autenticita() { autenticita = true; return this; }
    public Incidenti esterno() { esterno = true; return this; }
    public Incidenti dolo() { dolo = true; return this; }
    public Incidenti accesso() { accesso = true; return this; }
    public Incidenti grave() { grave = true; return this; }
    public Incidenti segretiCommerciali() { segreti = true; return this; }
    public Incidenti programmata() { programmata = true; return this; }
    public Incidenti perdita(String euro) { perdita = new BigDecimal(euro); return this; }
    public Incidenti ricorrenti(int n, String euro) { ricorrenti = n; cumulate = new BigDecimal(euro); return this; }

    public ValutazioneRequest build() {
        return new ValutazioneRequest(servizio, indisponibilita, degrado, utenti, riservatezza, integrita, autenticita,
                esterno, dolo, accesso, grave, perdita, ricorrenti, cumulate, segreti, programmata);
    }
}
