package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.TipoAdempimento;

import java.time.Instant;

/**
 * Anello della catena di hash di un incidente, con l'esito della verifica.
 *
 * @param schema schema dell'input con cui la versione è stata sigillata (pw29-catena-v1 o v2)
 * @param improntaRicalcolata SHA-256 ricalcolato ora dai dati presenti nel database
 * @param contenutoIntegro l'impronta ricalcolata coincide con quella registrata alla trasmissione
 * @param collegato l'anello precedente registrato coincide con l'impronta dell'anello che lo precede
 */
public record AnelloCatena(int posizione, Long versioneId, TipoAdempimento tipo, int progressivo, int numero, String schema,
                           boolean rettifica, Instant trasmessaIl, String riferimentoTrasmissione,
                           String improntaPrecedente, String impronta, String improntaRicalcolata,
                           boolean contenutoIntegro, boolean collegato) {
}
