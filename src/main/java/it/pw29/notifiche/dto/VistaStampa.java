package it.pw29.notifiche.dto;

import java.util.List;

/**
 * Modello del template di stampa: il documento strutturato più i valori già formattati per la lettura
 * (date in ora italiana, esiti a tre valori in parole, categorie IS con la loro descrizione).
 *
 * @param banda testo della fascia di stato in testa al documento (bozza, in approvazione, trasmesso)
 * @param classeBanda classe CSS della fascia
 */
public record VistaStampa(
        String titolo,
        String banda,
        String classeBanda,
        DocumentoEsportato doc,
        String rilevatoIl,
        String conoscenzaIl,
        String statoIncidente,
        String trasmessaIl,
        String generatoIl,
        List<Riga> campi,
        List<RiclassificazioneStampa> riclassificazioni
) {

    /** Campo del documento: etichetta, righe di testo, obbligatorietà per l'adempimento. */
    public record Riga(String etichetta, List<String> linee, boolean obbligatorio) {
    }

    public record RiclassificazioneStampa(String registrataIl, String significativita, String categorie,
                                          String vociPrima, String vociDopo, String riferimentoDocumentoPrima,
                                          String motivazione) {
    }
}
