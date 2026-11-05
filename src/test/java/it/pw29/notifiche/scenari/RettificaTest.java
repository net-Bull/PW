package it.pw29.notifiche.scenari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pw29.notifiche.dto.RettificaRequest;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correzione di un documento già trasmesso: non si modifica, si trasmette una nuova versione dello
 * stesso adempimento (rettifica), con motivo e collegamento alla versione corretta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OrologioTestConfig.class)
class RettificaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrologioRegolabile orologio;

    ClientApi api;
    Percorso percorso;

    @BeforeEach
    void prepara() {
        api = new ClientApi(mvc, json);
        orologio.imposta(OrologioTestConfig.INIZIO);
        percorso = new Percorso(api, orologio);
    }

    private static JsonNode adempimento(JsonNode incidente, String tipo) {
        for (JsonNode a : incidente.get("adempimenti")) {
            if (a.get("tipo").asText().equals(tipo)) {
                return a;
            }
        }
        throw new AssertionError(tipo);
    }

    private static JsonNode scadenza(JsonNode incidente, String tipo) {
        for (JsonNode s : incidente.get("scadenze")) {
            if (s.get("tipo").asText().equals(tipo)) {
                return s;
            }
        }
        throw new AssertionError(tipo);
    }

    @Test
    @DisplayName("Rettifica della notifica: nuova versione trasmessa, la prima resta immutata e il termine resta rispettato")
    void rettificaNotifica() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC irraggiungibile", "RET1");
        JsonNode prima = percorso.documento(ids.notifica());
        long adempimentoNotifica = prima.get("adempimentoId").asLong();
        String trasmessoIlPrimo = adempimento(percorso.incidente(ids.incidente()), "NOTIFICA").get("trasmessoIl").asText();

        // Senza motivo: rifiutata dalla validazione
        api.post("operatore", "/api/adempimenti/" + adempimentoNotifica + "/rettifica", new RettificaRequest(" "), 400);

        JsonNode rett = api.post("operatore", "/api/adempimenti/" + adempimentoNotifica + "/rettifica",
                new RettificaRequest("Utenti impattati sovrastimati: 2.600 utenti sono stati serviti dal sito secondario, "
                        + "quelli rimasti senza monitoraggio sono 6.400"), 201);
        long v2 = rett.get("id").asLong();
        assertThat(rett.get("numero").asInt()).isEqualTo(2);
        assertThat(rett.get("stato").asText()).isEqualTo("BOZZA");
        assertThat(rett.get("derivataDaId").asLong()).isEqualTo(ids.notifica());
        assertThat(rett.get("motivoRettifica").asText()).contains("6.400");
        assertThat(rett.at("/contenuto/valutazioneIniziale").asText()).isEqualTo(prima.at("/contenuto/valutazioneIniziale").asText());

        // Una sola rettifica aperta per volta
        api.post("operatore", "/api/adempimenti/" + adempimentoNotifica + "/rettifica", new RettificaRequest("altra"), 409);

        percorso.compila(v2, Map.of("utentiImpattati", 6400), Percorso.VOCI_NOTIFICA);
        JsonNode trasmessa = percorso.approvaETrasmetti(v2, "ACN-RET1-02R");
        assertThat(trasmessa.get("improntaPrecedente").asText()).isEqualTo(prima.get("improntaSha256").asText());

        // La versione 1 è invariata, impronta compresa
        JsonNode primaDopo = percorso.documento(ids.notifica());
        assertThat(primaDopo.get("stato").asText()).isEqualTo("TRASMESSO");
        assertThat(primaDopo.at("/contenuto/utentiImpattati").asInt()).isEqualTo(9000);
        assertThat(primaDopo.get("improntaSha256").asText()).isEqualTo(prima.get("improntaSha256").asText());

        // L'adempimento conserva il primo invio (termine rispettato); nessuna seconda relazione finale
        JsonNode inc = percorso.incidente(ids.incidente());
        JsonNode notifica = adempimento(inc, "NOTIFICA");
        assertThat(notifica.get("trasmessoIl").asText()).isEqualTo(trasmessoIlPrimo);
        assertThat(notifica.get("versioni")).hasSize(2);
        assertThat(notifica.at("/versioni/1/motivoRettifica").asText()).contains("6.400");
        assertThat(scadenza(inc, "NOTIFICA").get("stato").asText()).isEqualTo("RISPETTATA");
        assertThat(inc.get("adempimenti").findValuesAsText("tipo").stream().filter("RELAZIONE_FINALE"::equals)).hasSize(1);

        // La catena ha tre anelli, l'ultimo è la rettifica
        JsonNode catena = api.get("operatore", "/api/incidenti/" + ids.incidente() + "/catena", 200);
        assertThat(catena.get("integra").asBoolean()).isTrue();
        assertThat(catena.get("anelli")).hasSize(3);
        assertThat(catena.at("/anelli/2/rettifica").asBoolean()).isTrue();

        // La relazione finale riparte dalla rettifica, non dalla versione corretta
        JsonNode finale = percorso.bozza(adempimento(inc, "RELAZIONE_FINALE").get("id").asLong());
        assertThat(finale.get("derivataDaId").asLong()).isEqualTo(v2);
        assertThat(finale.at("/contenuto/utentiImpattati").asInt()).isEqualTo(6400);
    }

    @Test
    @DisplayName("Rettifica di un documento superato da un invio successivo: rifiutata")
    void rettificaDocumentoSuperato() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC irraggiungibile", "RET2");
        long adempimentoPre = percorso.documento(ids.preNotifica()).get("adempimentoId").asLong();
        JsonNode errore = api.post("operatore", "/api/adempimenti/" + adempimentoPre + "/rettifica",
                new RettificaRequest("Correggere il servizio impattato"), 409);
        assertThat(errore.get("messaggio").asText()).contains("ultimo documento trasmesso");
    }

    @Test
    @DisplayName("Rettifica di un adempimento non ancora trasmesso: rifiutata, si corregge la bozza")
    void rettificaNonTrasmesso() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC irraggiungibile", "RET3");
        long fin = percorso.adempimento(ids.incidente(), "RELAZIONE_FINALE");
        api.post("operatore", "/api/adempimenti/" + fin + "/rettifica", new RettificaRequest("prova"), 409);
    }
}
