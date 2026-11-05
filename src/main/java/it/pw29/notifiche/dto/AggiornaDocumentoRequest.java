package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Sostituzione integrale di contenuto e classificazione di una bozza (voci per codice ufficiale ACN). */
public record AggiornaDocumentoRequest(@NotNull ContenutoDocumento contenuto, List<String> voci) {
}
