package it.pw29.notifiche.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.domain.TipoSoggetto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Documento strutturato di un adempimento verso il CSIRT Italia (formato JSON scaricabile). È la stessa
 * rappresentazione da cui si genera il PDF: le due uscite non possono divergere.
 * <p>
 * {@code contenuto} contiene solo i campi previsti per l'adempimento dalla matrice campo/adempimento
 * (ADR-0005), nell'ordine della matrice; i campi facoltativi vuoti sono omessi.
 * <p>
 * Versione 2 del formato: per un documento trasmesso, {@code soggetto} e {@code incidente} sono l'intestazione
 * sigillata alla trasmissione (stato e date di quel momento), non i dati correnti; il contenuto riporta anche
 * i criteri del Reg. 2024/2690 che motivano la significatività.
 *
 * @param schema identificativo e versione del formato
 * @param catena posizione del documento nella catena di hash (ADR-0006)
 */
@JsonPropertyOrder({"schema", "destinatario", "adempimento", "soggetto", "incidente", "versione",
        "classificazione", "contenuto", "trasmissione", "catena", "generatoIl", "generatoDa"})
public record DocumentoEsportato(
        String schema,
        Destinatario destinatario,
        Adempimento adempimento,
        Soggetto soggetto,
        Incidente incidente,
        Versione versione,
        Classificazione classificazione,
        Map<String, Object> contenuto,
        Trasmissione trasmissione,
        Catena catena,
        Instant generatoIl,
        String generatoDa
) {

    public static final String SCHEMA = "pw29-documento-csirt/2";

    public record Destinatario(String nome, String canale) {
    }

    public record Adempimento(TipoAdempimento tipo, String etichetta, String riferimentoNormativo, int progressivo) {
    }

    public record Soggetto(String denominazione, String codiceFiscale, TipoSoggetto tipo, String settore) {
    }

    public record Incidente(String codice, String titolo, StatoIncidente stato, Instant rilevatoIl, Instant conoscenzaIl,
                            Instant conclusioneGestioneIl) {
    }

    /**
     * @param rettificaDellaVersione identificativo della versione trasmessa che questa versione corregge
     */
    public record Versione(Long id, int numero, StatoDocumento stato, Long derivataDa, Long rettificaDellaVersione,
                           String motivoRettifica, Instant creataIl, String creataDa,
                           String inviataApprovazioneDa, String approvataDa) {
    }

    /**
     * @param voci valori della Tassonomia Cyber ACN con il percorso macrocategoria > predicato > valore
     */
    public record Classificazione(String tassonomia, String versioneDocumento, LocalDate dataAcquisizione,
                                  List<Voce> voci) {
    }

    public record Voce(String codice, String nome, String percorso) {
    }

    public record Trasmissione(Instant trasmessaIl, String riferimentoPortale, String approvataDa,
                               Instant registrataIl) {
    }

    /**
     * @param improntaPrecedente per un documento trasmesso, l'anello a cui è agganciato; per uno non ancora
     *        trasmesso, l'impronta dell'ultimo documento trasmesso dell'incidente al momento dell'esportazione
     *        (ancora esterna degli anelli precedenti)
     * @param impronta impronta SHA-256 del documento, solo se trasmesso
     */
    public record Catena(String algoritmo, String improntaPrecedente, String impronta) {
    }
}
