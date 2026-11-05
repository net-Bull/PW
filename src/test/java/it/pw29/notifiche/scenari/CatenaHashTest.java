package it.pw29.notifiche.scenari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pw29.notifiche.domain.CatenaImpronte;
import it.pw29.notifiche.dto.TrasmissioneRequest;
import it.pw29.notifiche.supporto.ClientApi;
import it.pw29.notifiche.supporto.OrologioRegolabile;
import it.pw29.notifiche.supporto.OrologioTestConfig;
import it.pw29.notifiche.supporto.Percorso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catena di hash SHA-256 dei documenti trasmessi (ADR-0006): struttura della catena e rilevazione di
 * manomissioni fatte direttamente sul database, aggirando l'applicazione e i suoi controlli.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OrologioTestConfig.class)
class CatenaHashTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrologioRegolabile orologio;
    @Autowired JdbcTemplate jdbc;

    ClientApi api;
    Percorso percorso;

    @BeforeEach
    void prepara() {
        api = new ClientApi(mvc, json);
        orologio.imposta(OrologioTestConfig.INIZIO);
        percorso = new Percorso(api, orologio);
    }

    private JsonNode catena(long incidente) throws Exception {
        return api.get("operatore", "/api/incidenti/" + incidente + "/catena", 200);
    }

    @Test
    @DisplayName("Catena integra: genesi dal codice, ogni anello agganciato al precedente, impronte ricalcolabili")
    void catenaIntegra() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC irraggiungibile", "CAT1");
        JsonNode c = catena(ids.incidente());
        String codice = c.get("incidente").asText();

        assertThat(c.get("integra").asBoolean()).isTrue();
        assertThat(c.get("documentiTrasmessi").asInt()).isEqualTo(2);
        assertThat(c.get("genesi").asText()).isEqualTo(CatenaImpronte.genesi(codice));
        JsonNode primo = c.get("anelli").get(0);
        JsonNode secondo = c.get("anelli").get(1);
        assertThat(primo.get("versioneId").asLong()).isEqualTo(ids.preNotifica());
        assertThat(primo.get("improntaPrecedente").asText()).isEqualTo(c.get("genesi").asText());
        assertThat(secondo.get("versioneId").asLong()).isEqualTo(ids.notifica());
        assertThat(secondo.get("improntaPrecedente").asText()).isEqualTo(primo.get("impronta").asText());
        assertThat(c.get("ultimoAnello").asText()).isEqualTo(secondo.get("impronta").asText());
        assertThat(primo.get("impronta").asText()).matches("[0-9a-f]{64}");

        // Il dettaglio del documento espone la stessa impronta
        assertThat(percorso.documento(ids.notifica()).get("improntaSha256").asText())
                .isEqualTo(secondo.get("impronta").asText());
    }

    @Test
    @DisplayName("Manomissione del contenuto via SQL: rilevata, e nessuna nuova trasmissione sulla catena rotta")
    void manomissioneContenuto() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC irraggiungibile", "CAT2");
        // La relazione finale è pronta per l'approvazione prima della manomissione
        percorso.conclusione(ids.incidente());
        long fin = percorso.bozza(percorso.adempimento(ids.incidente(), "RELAZIONE_FINALE")).get("id").asLong();
        percorso.compila(fin, Map.of("descrizioneDettagliata", "Guasto del controller di storage",
                "minacciaOCausaOriginale", "Guasto hardware", "misureAttenuazione", "Sostituzione controller"),
                Percorso.VOCI_NOTIFICA);
        api.post("operatore", "/api/documenti/" + fin + "/invio-approvazione", null, 200);

        int righe = jdbc.update("UPDATE versione_documento SET contenuto_json = REPLACE(contenuto_json, "
                + "'140 clienti', '14 clienti') WHERE id = ?", ids.notifica());
        assertThat(righe).isEqualTo(1);

        JsonNode c = catena(ids.incidente());
        assertThat(c.get("integra").asBoolean()).isFalse();
        assertThat(c.at("/anelli/0/contenutoIntegro").asBoolean()).isTrue();
        assertThat(c.at("/anelli/1/contenutoIntegro").asBoolean()).isFalse();
        assertThat(c.at("/anelli/1/improntaRicalcolata").asText()).isNotEqualTo(c.at("/anelli/1/impronta").asText());

        JsonNode rifiuto = api.post("approvatore", "/api/documenti/" + fin + "/trasmissione",
                new TrasmissioneRequest("ACN-CAT2-03", null), 409);
        assertThat(rifiuto.get("messaggio").asText()).contains("non è integra");
    }

    @Test
    @DisplayName("Manomissione con impronta ricalcolata: il collegamento con l'anello successivo si rompe")
    void manomissioneConImprontaRicalcolata() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC irraggiungibile", "CAT3");
        jdbc.update("UPDATE versione_documento SET contenuto_json = REPLACE(contenuto_json, 'NON_NOTO', 'NO') WHERE id = ?",
                ids.preNotifica());
        // L'autore della manomissione conosce l'algoritmo e riscrive anche l'impronta del documento alterato
        String nuova = catena(ids.incidente()).at("/anelli/0/improntaRicalcolata").asText();
        jdbc.update("UPDATE versione_documento SET impronta_sha256 = ? WHERE id = ?", nuova, ids.preNotifica());

        JsonNode c = catena(ids.incidente());
        assertThat(c.get("integra").asBoolean()).isFalse();
        assertThat(c.at("/anelli/0/contenutoIntegro").asBoolean()).isTrue();
        assertThat(c.get("anelli")).hasSize(1);
        assertThat(c.get("fuoriCatena").get(0).asLong()).isEqualTo(ids.notifica());
    }

    @Test
    @DisplayName("Manomissione della classificazione (voce rimossa via SQL): rilevata")
    void manomissioneClassificazione() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC irraggiungibile", "CAT4");
        int righe = jdbc.update("DELETE FROM classificazione WHERE versione_documento_id = ? AND voce_id = "
                + "(SELECT id FROM tassonomia_voce WHERE codice = 'BC:SE_ME' AND versione_id = "
                + "(SELECT id FROM tassonomia_versione WHERE vigente = TRUE))", ids.notifica());
        assertThat(righe).isEqualTo(1);
        JsonNode c = catena(ids.incidente());
        assertThat(c.get("integra").asBoolean()).isFalse();
        assertThat(c.at("/anelli/1/contenutoIntegro").asBoolean()).isFalse();
    }
}
