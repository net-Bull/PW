package it.pw29.notifiche.dto;

/** Campo mancante o vincolo non rispettato, con la fonte che lo rende obbligatorio. */
public record Violazione(String campo, String etichetta, String messaggio, String fonte) {
}
