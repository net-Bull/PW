package it.pw29.notifiche.scenari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.pw29.notifiche.dto.ConclusioneRequest;
import it.pw29.notifiche.dto.NuovoIncidenteRequest;
import it.pw29.notifiche.dto.QualificazioneRequest;
import it.pw29.notifiche.dto.RespingimentoRequest;
import it.pw29.notifiche.dto.TrasmissioneRequest;
import it.pw29.notifiche.supporto.ClientApi;
import it.pw29.notifiche.supporto.Incidenti;
import it.pw29.notifiche.supporto.OrologioRegolabile;
import it.pw29.notifiche.supporto.Percorso;
import it.pw29.notifiche.supporto.OrologioTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenari end-to-end attraverso le API, con orologio fisso: dalla registrazione dell'incidente alla
 * relazione finale, passando per valutazione, conoscenza, bozze con riuso dei dati, validazione
 * differenziata, approvazione con quattro occhi, trasmissione e immutabilità.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OrologioTestConfig.class)
class FlussoNotificaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrologioRegolabile orologio;

    ClientApi api;

    @BeforeEach
    void prepara() {
        api = new ClientApi(mvc, json);
        orologio.imposta(OrologioTestConfig.INIZIO);
    }

    private long registra(String titolo, Instant rilevato) throws Exception {
        return api.post("operatore", "/api/incidenti",
                new NuovoIncidenteRequest(titolo, "Descrizione di test", rilevato, null), 201).get("id").asLong();
    }

    private static long adempimento(JsonNode incidente, String tipo) {
        for (JsonNode a : incidente.get("adempimenti")) {
            if (a.get("tipo").asText().equals(tipo)) {
                return a.get("id").asLong();
            }
        }
        throw new AssertionError("Adempimento " + tipo + " assente");
    }

    private static JsonNode scadenza(JsonNode incidente, String tipo) {
        for (JsonNode s : incidente.get("scadenze")) {
            if (s.get("tipo").asText().equals(tipo)) {
                return s;
            }
        }
        throw new AssertionError("Scadenza " + tipo + " assente");
    }

    private static List<String> campiMancanti(JsonNode esito) {
        List<String> campi = new ArrayList<>();
        esito.get("violazioni").forEach(v -> campi.add(v.get("campo").asText()));
        return campi;
    }

    /** Sostituisce i campi indicati nel contenuto corrente della bozza e la aggiorna con le voci date. */
    private JsonNode compila(String utente, long versione, Map<String, Object> campi, List<String> voci) throws Exception {
        JsonNode doc = api.get(utente, "/api/documenti/" + versione, 200);
        ObjectNode contenuto = (ObjectNode) doc.get("contenuto");
        campi.forEach((k, v) -> contenuto.set(k, json.valueToTree(v)));
        return api.put(utente, "/api/documenti/" + versione, Map.of("contenuto", contenuto, "voci", voci), 200);
    }

    @Test
    @DisplayName("Sotto soglia: 30 minuti di indisponibilità del SOC/MDR -> non significativo, nessun adempimento")
    void sottoSoglia() throws Exception {
        long id = registra("Rallentamento SOC", OrologioTestConfig.INIZIO.minus(Duration.ofMinutes(40)));
        JsonNode inc = api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(Incidenti.neutro().indisponibilita(30).build(), null, null), 200);
        assertThat(inc.get("stato").asText()).isEqualTo("NON_SIGNIFICATIVO");
        assertThat(inc.get("conoscenzaIl").isNull()).isTrue();
        assertThat(inc.get("adempimenti")).isEmpty();
        assertThat(inc.get("scadenze")).isEmpty();
        assertThat(inc.at("/ultimaValutazione/significativo").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Significativo senza motivazione della conoscenza: rifiutato, l'incidente resta in valutazione")
    void motivazioneObbligatoria() throws Exception {
        long id = registra("Indisponibilità SOC", OrologioTestConfig.INIZIO.minus(Duration.ofMinutes(50)));
        api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(Incidenti.neutro().indisponibilita(45).build(), null, " "), 422);
        assertThat(api.get("operatore", "/api/incidenti/" + id, 200).get("stato").asText()).isEqualTo("IN_VALUTAZIONE");
    }

    @Test
    @DisplayName("Scenario completo sopra soglia: pre-notifica, notifica, relazione finale")
    void scenarioCompleto() throws Exception {
        Instant rilevato = OrologioTestConfig.INIZIO.minus(Duration.ofMinutes(50));
        Instant conoscenza = OrologioTestConfig.INIZIO.minus(Duration.ofMinutes(10));
        long id = registra("Indisponibilità della piattaforma SOC/MDR", rilevato);

        // 1. Valutazione sopra soglia: R1 (Reg. 2024/2690) e IS-3 (SL del SOC/MDR); IS-4 no, soggetto importante
        JsonNode inc = api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(Incidenti.neutro().indisponibilita(45).accesso().build(), conoscenza,
                        "Console SOC irraggiungibile da 45 minuti, confermato dal monitoraggio di disponibilità"), 200);
        assertThat(inc.get("stato").asText()).isEqualTo("SIGNIFICATIVO");
        assertThat(inc.get("conoscenzaIl").asText()).isEqualTo(conoscenza.toString());
        assertThat(inc.get("minutiRilevazioneConoscenza").asLong()).isEqualTo(40);
        assertThat(inc.at("/ultimaValutazione/regoleAttivate").toString()).isEqualTo("[\"R1\"]");
        assertThat(inc.at("/ultimaValutazione/categorieIs").toString()).isEqualTo("[\"IS_3\"]");
        assertThat(scadenza(inc, "PRE_NOTIFICA").get("dovutaEntro").asText())
                .isEqualTo(conoscenza.plus(Duration.ofHours(24)).toString());
        assertThat(scadenza(inc, "NOTIFICA").get("dovutaEntro").asText())
                .isEqualTo(conoscenza.plus(Duration.ofHours(72)).toString());

        // La qualificazione non si ripete: la conoscenza resta quella registrata
        api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(Incidenti.neutro().indisponibilita(45).build(), null, "di nuovo"), 409);

        // 2. Pre-notifica: bozza precompilata da incidente, soggetto e valutazione
        JsonNode bozza = api.post("operatore", "/api/adempimenti/" + adempimento(inc, "PRE_NOTIFICA") + "/bozza", null, 201);
        long pre = bozza.get("id").asLong();
        assertThat(bozza.at("/contenuto/referenteEmail").asText()).isEqualTo("csirt@cyberwall.example");
        assertThat(bozza.at("/contenuto/categorieIs").toString()).isEqualTo("[\"IS_3\"]");
        assertThat(bozza.at("/contenuto/serviziImpattati").toString()).isEqualTo("[\"SOC_MDR\"]");
        assertThat(campiMancanti(bozza.get("verifica"))).containsExactly("impattoTransfrontaliero", "classificazione");

        // Incompleta: rifiutata in approvazione con i campi mancanti
        JsonNode rifiuto = api.post("operatore", "/api/documenti/" + pre + "/invio-approvazione", null, 422);
        assertThat(campiMancanti(rifiuto)).containsExactly("impattoTransfrontaliero", "classificazione");

        // Voce di livello PREDICATO: rifiutata (solo VALORE classifica)
        JsonNode attuale = api.get("operatore", "/api/documenti/" + pre, 200);
        api.put("operatore", "/api/documenti/" + pre,
                Map.of("contenuto", attuale.get("contenuto"), "voci", List.of("BC:IM")), 422);

        compila("operatore", pre, Map.of("impattoTransfrontaliero", "NON_NOTO"), List.of("BC:IM_AV", "BC:RO_SY"));
        api.post("operatore", "/api/documenti/" + pre + "/invio-approvazione", null, 200);

        // Un operatore non può trasmettere; l'approvatore sì
        api.post("operatore", "/api/documenti/" + pre + "/trasmissione", new TrasmissioneRequest("ACN-0001", null), 403);
        orologio.avanza(Duration.ofHours(2));
        JsonNode trasmessa = api.post("approvatore", "/api/documenti/" + pre + "/trasmissione",
                new TrasmissioneRequest("ACN-SEG-2026-0001", null), 200);
        assertThat(trasmessa.get("stato").asText()).isEqualTo("TRASMESSO");
        assertThat(trasmessa.get("approvataDa").asText()).isEqualTo("approvatore");

        // Immutabilità: nessuna modifica dopo la trasmissione
        api.put("operatore", "/api/documenti/" + pre, Map.of("contenuto", trasmessa.get("contenuto"), "voci", List.of()), 409);

        // 3. Notifica: riparte dalla pre-notifica trasmessa (riuso dei dati e della classificazione)
        inc = api.get("operatore", "/api/incidenti/" + id, 200);
        assertThat(scadenza(inc, "PRE_NOTIFICA").get("stato").asText()).isEqualTo("RISPETTATA");
        JsonNode notifica = api.post("operatore", "/api/adempimenti/" + adempimento(inc, "NOTIFICA") + "/bozza", null, 201);
        long not = notifica.get("id").asLong();
        assertThat(notifica.get("derivataDaId").asLong()).isEqualTo(pre);
        assertThat(notifica.at("/contenuto/impattoTransfrontaliero").asText()).isEqualTo("NON_NOTO");
        assertThat(notifica.get("voci")).hasSize(2);
        assertThat(campiMancanti(notifica.get("verifica"))).containsExactly("valutazioneIniziale", "classificazione");

        compila("operatore", not, Map.of("valutazioneIniziale", Percorso.VALUTAZIONE_NOTIFICA),
                List.of("BC:IM_AV", "BC:RO_SY", "BC:SE_ME"));
        api.post("operatore", "/api/documenti/" + not + "/invio-approvazione", null, 200);
        // Respinta e corretta: torna in bozza con la nota dell'approvatore
        JsonNode respinta = api.post("approvatore", "/api/documenti/" + not + "/respingimento",
                new RespingimentoRequest("Indicare anche il numero di clienti nella valutazione"), 200);
        assertThat(respinta.get("stato").asText()).isEqualTo("BOZZA");
        compila("operatore", not, Map.of("utentiImpattati", 9000), List.of("BC:IM_AV", "BC:RO_SY", "BC:SE_ME"));
        api.post("operatore", "/api/documenti/" + not + "/invio-approvazione", null, 200);
        orologio.avanza(Duration.ofHours(20));
        Instant istanteNotifica = orologio.instant();
        api.post("approvatore", "/api/documenti/" + not + "/trasmissione", new TrasmissioneRequest("ACN-SEG-2026-0002", null), 200);

        // 4. Relazione finale: sorge con la notifica, scade un mese dopo
        inc = api.get("operatore", "/api/incidenti/" + id, 200);
        assertThat(scadenza(inc, "NOTIFICA").get("stato").asText()).isEqualTo("RISPETTATA");
        assertThat(scadenza(inc, "RELAZIONE_FINALE").get("dovutaEntro").asText())
                .isEqualTo(Instant.parse("2026-12-03T06:00:00Z").toString());
        assertThat(istanteNotifica).isEqualTo(Instant.parse("2026-11-03T06:00:00Z"));

        orologio.avanza(Duration.ofDays(10));
        api.post("operatore", "/api/incidenti/" + id + "/conclusione", new ConclusioneRequest(orologio.instant()), 200);
        // Redatta e inviata in approvazione da un approvatore: deve trasmetterla un altro (quattro occhi)
        long fin = api.post("approvatore", "/api/adempimenti/" + adempimento(inc, "RELAZIONE_FINALE") + "/bozza", null, 201)
                .get("id").asLong();
        JsonNode bozzaFinale = compila("approvatore", fin, Map.of(
                "descrizioneDettagliata", "Guasto dell'hypervisor del cluster SOC, ripristino su sito secondario",
                "minacciaOCausaOriginale", "Guasto hardware del controller di storage",
                "misureAttenuazione", "Failover automatico; sostituzione controller; test trimestrali",
                "attoIllegittimoOMalevolo", "NO"), List.of("BC:IM_AV", "BC:RO_SY", "BC:SE_ME"));
        assertThat(bozzaFinale.at("/verifica/completo").asBoolean()).isTrue();
        api.post("approvatore", "/api/documenti/" + fin + "/invio-approvazione", null, 200);
        JsonNode quattroOcchi = api.post("approvatore", "/api/documenti/" + fin + "/trasmissione",
                new TrasmissioneRequest("ACN-SEG-2026-0003", null), 403);
        assertThat(quattroOcchi.get("errore").asText()).isEqualTo("QUATTRO_OCCHI");
        api.post("approvatore2", "/api/documenti/" + fin + "/trasmissione", new TrasmissioneRequest("ACN-SEG-2026-0003", null), 200);

        inc = api.get("operatore", "/api/incidenti/" + id, 200);
        assertThat(inc.get("stato").asText()).isEqualTo("CHIUSO");
        assertThat(scadenza(inc, "RELAZIONE_FINALE").get("stato").asText()).isEqualTo("RISPETTATA");

        // 5. Audit: ogni passaggio è registrato con l'utente
        List<String> azioni = new ArrayList<>();
        api.get("operatore", "/api/incidenti/" + id + "/audit", 200).forEach(a -> azioni.add(a.get("azione").asText()));
        assertThat(azioni).contains("INCIDENTE_REGISTRATO", "VALUTAZIONE", "CONOSCENZA_REGISTRATA", "BOZZA_CREATA",
                "INVIO_APPROVAZIONE", "RESPINGIMENTO", "TRASMISSIONE", "CONCLUSIONE_GESTIONE");
        assertThat(azioni.stream().filter("TRASMISSIONE"::equals)).hasSize(3);
    }

    @Test
    @DisplayName("Ordine degli invii: la notifica non si trasmette prima della pre-notifica")
    void ordineInvii() throws Exception {
        long id = registra("Esfiltrazione dati clienti", OrologioTestConfig.INIZIO.minus(Duration.ofHours(1)));
        JsonNode inc = api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(Incidenti.neutro().riservatezza().esterno().dolo().build(), null,
                        "Dati di clienti pubblicati su un sito di leak, verificati dal team CTI"), 200);
        assertThat(inc.at("/ultimaValutazione/regoleAttivate").toString()).isEqualTo("[\"R3\"]");
        assertThat(inc.at("/ultimaValutazione/categorieIs").toString()).isEqualTo("[\"IS_1\"]");
        long not = api.post("operatore", "/api/adempimenti/" + adempimento(inc, "NOTIFICA") + "/bozza", null, 201)
                .get("id").asLong();
        compila("operatore", not, Map.of("impattoTransfrontaliero", "NO", "valutazioneIniziale", "Gravità alta"),
                List.of("BC:IM_DX", "BC:SE_HI"));
        JsonNode errore = api.post("operatore", "/api/documenti/" + not + "/invio-approvazione", null, 422);
        assertThat(errore.get("messaggio").asText()).contains("pre-notifica");
    }

    @Test
    @DisplayName("Relazione mensile: rifiutata se l'incidente non è in corso alla data della finale")
    void mensileNonDovuta() throws Exception {
        long id = registra("Degrado WAF", OrologioTestConfig.INIZIO.minus(Duration.ofHours(3)));
        api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(Incidenti.neutro().servizio("WAF_GESTITO").degrado(90).utenti(600).build(),
                        null, "Degrado confermato dalle metriche di latenza del WAF"), 200);
        api.post("operatore", "/api/incidenti/" + id + "/relazioni-mensili", null, 422);
    }

    @Test
    @DisplayName("Servizio senza livello di servizio atteso: valutazione rifiutata")
    void servizioSconosciuto() throws Exception {
        long id = registra("Evento su sistema non censito", OrologioTestConfig.INIZIO.minus(Duration.ofHours(1)));
        api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(Incidenti.neutro().servizio("SCONOSCIUTO").build(), null, null), 422);
    }
}
