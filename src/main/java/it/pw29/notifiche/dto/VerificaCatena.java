package it.pw29.notifiche.dto;

import java.util.List;

/**
 * Esito della verifica della catena di hash dei documenti trasmessi di un incidente (ADR-0006).
 *
 * @param integra tutti gli anelli sono integri e collegati, e nessun documento trasmesso è fuori catena
 * @param genesi impronta di partenza, derivata dal codice dell'incidente
 * @param ultimoAnello impronta a cui si aggancerà il prossimo documento trasmesso
 * @param fuoriCatena versioni trasmesse non raggiungibili seguendo i collegamenti (indizio di manomissione)
 */
public record VerificaCatena(String incidente, String algoritmo, boolean integra, String genesi, String ultimoAnello,
                             int documentiTrasmessi, List<AnelloCatena> anelli, List<Long> fuoriCatena) {
}
