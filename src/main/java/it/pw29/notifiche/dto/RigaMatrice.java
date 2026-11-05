package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.TipoAdempimento;

import java.util.Map;

/** Riga della matrice campo/adempimento: per ogni adempimento, OBBLIGATORIO, FACOLTATIVO o NON_PREVISTO. */
public record RigaMatrice(String campo, String etichetta, Map<TipoAdempimento, Requisito> requisiti, String fonte) {

    public enum Requisito { OBBLIGATORIO, FACOLTATIVO, NON_PREVISTO }
}
