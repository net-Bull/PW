package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.TipoAdempimento;

import java.util.List;

/** Esito della verifica di completezza di un documento per uno specifico adempimento. */
public record EsitoVerifica(TipoAdempimento adempimento, boolean completo, List<Violazione> violazioni) {

    public static EsitoVerifica di(TipoAdempimento tipo, List<Violazione> violazioni) {
        return new EsitoVerifica(tipo, violazioni.isEmpty(), List.copyOf(violazioni));
    }
}
