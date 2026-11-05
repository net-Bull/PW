package it.pw29.notifiche.domain.contenuto;

/** Indicatore di compromissione (IoC), es. tipo "ipv4" e valore "203.0.113.7". */
public record IndicatoreCompromissione(String tipo, String valore, String nota) {
}
