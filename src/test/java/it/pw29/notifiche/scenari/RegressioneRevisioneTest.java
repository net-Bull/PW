package it.pw29.notifiche.scenari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.pw29.notifiche.dto.ConclusioneRequest;
import it.pw29.notifiche.dto.QualificazioneRequest;
import it.pw29.notifiche.dto.RichiestaCsirtRequest;
import it.pw29.notifiche.dto.RiclassificazioneRequest;
import it.pw29.notifiche.dto.TrasmissioneRequest;
import it.pw29.notifiche.supporto.ClientApi;
import it.pw29.notifiche.supporto.Incidenti;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regressioni della revisione di fine fase 3. Ogni test riproduce un caso che aveva mostrato un errore
 * e verifica il comportamento corretto; gli ultimi coprono i casi introdotti dalle correzioni.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OrologioTestConfig.class)
class RegressioneRevisioneTest {

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

    private long finaleCompilata(long incidente) throws Exception {
        long fin = percorso.bozza(percorso.adempimento(incidente, "RELAZIONE_FINALE")).get("id").asLong();
        percorso.compila(fin, Map.of("descrizioneDettagliata", "Guasto del controller di storage del cluster SOC",
                "minacciaOCausaOriginale", "Guasto hardware", "misureAttenuazione", "Sostituzione del controller",
                "attoIllegittimoOMalevolo", "NO"), Percorso.VOCI_NOTIFICA);
        return fin;
    }

    @Test
    @DisplayName("le API non accettano il cookie di sessione del login a form, quindi niente CSRF")
    void apiSenzaSessione() throws Exception {
        MvcResult login = mvc.perform(post("/login").with(csrf())
                .param("username", "operatore").param("password", "Operatore-Demo-2026")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(302);
        assertThat(login.getResponse().getRedirectedUrl()).doesNotContain("error");
        MockHttpSession sessione = (MockHttpSession) login.getRequest().getSession(false);
        int stato = mvc.perform(post("/api/incidenti").session(sessione).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titolo\":\"csrf\",\"descrizione\":\"x\",\"rilevatoIl\":\"2026-11-02T07:00:00Z\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(stato).isEqualTo(401);
    }

    @Test
    @DisplayName("chi ha scritto la versione non può trasmetterla, anche se l'ha inviata un altro")
    void quattroOcchiSugliAutori() throws Exception {
        long id = percorso.registra("Quattro occhi", orologio.instant().minus(Duration.ofMinutes(50)));
        percorso.qualifica(id, Incidenti.neutro().indisponibilita(45).build(), orologio.instant().minus(Duration.ofMinutes(10)), "m");
        long pre = percorso.bozza(percorso.adempimento(id, "PRE_NOTIFICA")).get("id").asLong();
        ObjectNode c = (ObjectNode) percorso.documento(pre).get("contenuto");
        c.put("impattoTransfrontaliero", "NO");
        c.put("descrizioneSintetica", "Testo scritto dall'approvatore");
        api.put("approvatore", "/api/documenti/" + pre, Map.of("contenuto", c, "voci", Percorso.VOCI_PRE_NOTIFICA), 200);
        api.post("operatore", "/api/documenti/" + pre + "/invio-approvazione", null, 200);
        JsonNode errore = api.post("approvatore", "/api/documenti/" + pre + "/trasmissione", new TrasmissioneRequest("R", null), 403);
        assertThat(errore.get("errore").asText()).isEqualTo("QUATTRO_OCCHI");
        assertThat(percorso.documento(pre).get("autori").toString()).contains("approvatore", "operatore");
        api.post("approvatore2", "/api/documenti/" + pre + "/trasmissione", new TrasmissioneRequest("R", null), 200);
    }

    @Test
    @DisplayName("la finale non parte con un altro documento in approvazione; dopo la chiusura nessun invio")
    void nessunInvioDopoLaChiusura() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Dopo la chiusura", "REV3");
        long id = ids.incidente();
        api.post("operatore", "/api/incidenti/" + id + "/richieste-csirt", new RichiestaCsirtRequest("RIC-1", null, null), 201);
        long inter = percorso.bozza(percorso.adempimento(id, "RELAZIONE_INTERMEDIA")).get("id").asLong();
        percorso.compila(inter, Map.of("aggiornamentiSituazione", "Contenimento in corso"), Percorso.VOCI_NOTIFICA);
        assertThat(percorso.documento(inter).at("/contenuto/riferimentoRichiestaCsirt").asText()).isEqualTo("RIC-1");
        percorso.conclusione(id);
        long fin = finaleCompilata(id);
        // La conclusione è registrata prima di mettere in approvazione l'intermedia (dopo sarebbe rifiutata)
        api.post("operatore", "/api/documenti/" + inter + "/invio-approvazione", null, 200);
        api.post("operatore", "/api/documenti/" + fin + "/invio-approvazione", null, 200);
        orologio.avanza(Duration.ofHours(1));
        JsonNode errore = api.post("approvatore", "/api/documenti/" + fin + "/trasmissione", new TrasmissioneRequest("REV3-FIN", null), 409);
        assertThat(errore.get("messaggio").asText()).contains("in approvazione");
        // Respinta l'intermedia, la finale chiude l'incidente; l'intermedia non si invia più
        api.post("approvatore", "/api/documenti/" + inter + "/respingimento", Map.of("nota", "Non più necessaria"), 200);
        api.post("approvatore", "/api/documenti/" + fin + "/trasmissione", new TrasmissioneRequest("REV3-FIN", null), 200);
        assertThat(percorso.incidente(id).get("stato").asText()).isEqualTo("CHIUSO");
        api.post("operatore", "/api/documenti/" + inter + "/invio-approvazione", null, 409);
    }

    @Test
    @DisplayName("la conclusione si registra una sola volta e la finale riporta quella dell'incidente")
    void conclusioneUnica() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Conclusione", "REV4");
        long id = ids.incidente();
        api.post("operatore", "/api/incidenti/" + id + "/conclusione",
                new ConclusioneRequest(orologio.instant().minus(Duration.ofMinutes(5))), 200);
        orologio.avanza(Duration.ofDays(40));
        api.post("operatore", "/api/incidenti/" + id + "/conclusione",
                new ConclusioneRequest(orologio.instant().minus(Duration.ofHours(1))), 409);
        JsonNode inc = percorso.incidente(id);
        for (JsonNode s : inc.get("scadenze")) {
            if (s.get("tipo").asText().equals("RELAZIONE_FINALE")) {
                assertThat(s.get("stato").asText()).isEqualTo("SCADUTA");
            }
        }
        long fin = percorso.bozza(percorso.adempimento(id, "RELAZIONE_FINALE")).get("id").asLong();
        Instant falsa = orologio.instant().minus(Duration.ofDays(20));
        percorso.compila(fin, Map.of("descrizioneDettagliata", "d", "minacciaOCausaOriginale", "c",
                "misureAttenuazione", "m", "conclusioneGestioneIl", falsa), Percorso.VOCI_NOTIFICA);
        JsonNode trasmessa = percorso.approvaETrasmetti(fin, "REV4-FIN");
        assertThat(trasmessa.at("/contenuto/conclusioneGestioneIl").asText()).isEqualTo(inc.get("conclusioneGestioneIl").asText());
    }

    @Test
    @DisplayName("la copia conforme di un documento trasmesso conserva l'intestazione del momento dell'invio")
    void intestazioneSigillata() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Copia conforme", "REV5");
        JsonNode prima = json.readTree(api.scarica("operatore", "/api/documenti/" + ids.preNotifica() + "/json", 200).dati());
        percorso.conclusione(ids.incidente());
        percorso.approvaETrasmetti(finaleCompilata(ids.incidente()), "REV5-FIN");
        JsonNode dopo = json.readTree(api.scarica("operatore", "/api/documenti/" + ids.preNotifica() + "/json", 200).dati());
        assertThat(dopo.get("incidente")).isEqualTo(prima.get("incidente"));
        assertThat(dopo.at("/incidente/stato").asText()).isEqualTo("SIGNIFICATIVO");
        assertThat(dopo.get("soggetto")).isEqualTo(prima.get("soggetto"));
        assertThat(api.get("operatore", "/api/incidenti/" + ids.incidente() + "/catena", 200).at("/anelli/2/schema").asText())
                .isEqualTo("pw29-catena-v2");
    }

    @Test
    @DisplayName("dopo il declassamento le categorie IS della notifica sono vuote, non quelle precedenti")
    void declassamentoSenzaCategorieResidue() throws Exception {
        long id = percorso.registra("Declassamento", orologio.instant().minus(Duration.ofMinutes(50)));
        percorso.qualifica(id, Incidenti.neutro().indisponibilita(45).build(), orologio.instant().minus(Duration.ofMinutes(10)), "m");
        long pre = percorso.bozza(percorso.adempimento(id, "PRE_NOTIFICA")).get("id").asLong();
        percorso.compila(pre, Map.of("impattoTransfrontaliero", "NO"), Percorso.VOCI_PRE_NOTIFICA);
        percorso.approvaETrasmetti(pre, "REV6-01");
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione", new RiclassificazioneRequest(
                Incidenti.neutro().indisponibilita(20).build(), List.of("BC:IM_NO", "BC:RO_SY", "TT:AV_OU", "BC:SE_LO"),
                "Sotto il livello di servizio atteso"), 201);
        long not = percorso.bozza(percorso.adempimento(id, "NOTIFICA")).get("id").asLong();
        assertThat(percorso.documento(not).at("/contenuto/categorieIs")).isEmpty();
        assertThat(percorso.documento(not).at("/contenuto/criteriRegolamento")).isEmpty();
    }

    @Test
    @DisplayName("le categorie IS le compila il sistema: niente IS-4 per un importante, niente cambi senza riclassificazione")
    void categorieDalSistema() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Categorie", "REV9");
        long fin = percorso.bozza(percorso.adempimento(ids.incidente(), "RELAZIONE_FINALE")).get("id").asLong();
        percorso.compila(fin, Map.of("descrizioneDettagliata", "d", "categorieIs", List.of("IS_1", "IS_4"),
                "criteriRegolamento", List.of("R5")), Percorso.VOCI_NOTIFICA);
        JsonNode doc = percorso.documento(fin);
        assertThat(doc.at("/contenuto/categorieIs").toString()).isEqualTo("[\"IS_3\"]");
        assertThat(doc.at("/contenuto/criteriRegolamento").toString()).isEqualTo("[\"R1\"]");
    }

    @Test
    @DisplayName("il portale clienti non è un servizio gestito, i criteri dell'art. 10 non si applicano")
    void portaleNonGestito() throws Exception {
        JsonNode e = api.post("operatore", "/api/significativita/valutazione",
                Incidenti.neutro().servizio("PORTALE_TICKETING").indisponibilita(31).build(), 200);
        assertThat(e.get("significativo").asBoolean()).isFalse();
        JsonNode oltreSl = api.post("operatore", "/api/significativita/valutazione",
                Incidenti.neutro().servizio("PORTALE_TICKETING").indisponibilita(121).build(), 200);
        assertThat(oltreSl.get("categorieIs").toString()).isEqualTo("[\"IS_3\"]");
        assertThat(oltreSl.get("regoleAttivate")).isEmpty();
    }

    @Test
    @DisplayName("la relazione finale si rettifica anche a incidente chiuso, con un nuovo anello della catena")
    void rettificaFinale() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Rettifica finale", "REV10");
        percorso.conclusione(ids.incidente());
        percorso.approvaETrasmetti(finaleCompilata(ids.incidente()), "REV10-FIN");
        long a = percorso.adempimento(ids.incidente(), "RELAZIONE_FINALE");
        long v2 = api.post("operatore", "/api/adempimenti/" + a + "/rettifica",
                Map.of("motivo", "Refuso nella causa originale"), 201).get("id").asLong();
        percorso.compila(v2, Map.of("minacciaOCausaOriginale", "Guasto hardware del controller di storage"), Percorso.VOCI_NOTIFICA);
        JsonNode rett = percorso.approvaETrasmetti(v2, "REV10-FIN-R");
        assertThat(rett.get("stato").asText()).isEqualTo("TRASMESSO");
        assertThat(percorso.incidente(ids.incidente()).get("stato").asText()).isEqualTo("CHIUSO");
        JsonNode catena = api.get("operatore", "/api/incidenti/" + ids.incidente() + "/catena", 200);
        assertThat(catena.get("integra").asBoolean()).isTrue();
        assertThat(catena.get("anelli")).hasSize(4);
        // Le altre bozze restano bloccate: per un incidente chiuso non si crea nulla di nuovo
        api.post("operatore", "/api/incidenti/" + ids.incidente() + "/richieste-csirt",
                new RichiestaCsirtRequest("RIC-TARDA", null, null), 409);
    }

    @Test
    @DisplayName("prima di ogni invio un falso positivo torna non significativo, senza termini aperti; "
            + "se torna significativo i termini decorrono dalla prima conoscenza")
    void ritornoANonSignificativoPrimaDelPrimoInvio() throws Exception {
        long id = percorso.registra("Falso positivo", orologio.instant().minus(Duration.ofMinutes(50)));
        Instant conoscenza = orologio.instant().minus(Duration.ofMinutes(10));
        percorso.qualifica(id, Incidenti.neutro().indisponibilita(45).build(), conoscenza, "Allarme di indisponibilità");
        long pre = percorso.bozza(percorso.adempimento(id, "PRE_NOTIFICA")).get("id").asLong();

        // Senza valutazione, o con una valutazione ancora significativa: rifiutata
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione",
                new RiclassificazioneRequest(null, null, "Falso positivo"), 409);
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione",
                new RiclassificazioneRequest(Incidenti.neutro().indisponibilita(40).build(), null, "x"), 409);

        JsonNode ricl = api.post("operatore", "/api/incidenti/" + id + "/riclassificazione", new RiclassificazioneRequest(
                Incidenti.neutro().indisponibilita(4).build(), null,
                "La sonda di monitoraggio ha perso la connessione: la console è sempre rimasta raggiungibile"), 201);
        assertThat(ricl.get("statoDopo").asText()).isEqualTo("NON_SIGNIFICATIVO");
        assertThat(ricl.get("documentoPrimaId").isNull()).isTrue();
        JsonNode inc = percorso.incidente(id);
        assertThat(inc.get("stato").asText()).isEqualTo("NON_SIGNIFICATIVO");
        assertThat(inc.get("scadenze")).isEmpty();
        api.post("operatore", "/api/documenti/" + pre + "/invio-approvazione", null, 409);

        // Nuovi elementi: di nuovo significativo, stessa conoscenza, stessi adempimenti
        orologio.avanza(Duration.ofHours(2));
        percorso.qualifica(id, Incidenti.neutro().indisponibilita(50).build(), null, "Nuova indisponibilità confermata");
        inc = percorso.incidente(id);
        assertThat(inc.get("stato").asText()).isEqualTo("SIGNIFICATIVO");
        assertThat(Instant.parse(inc.get("conoscenzaIl").asText())).isEqualTo(conoscenza);
        assertThat(inc.get("adempimenti")).hasSize(2);
        assertThat(inc.at("/scadenze/0/dovutaEntro").asText()).isEqualTo(conoscenza.plus(Duration.ofHours(24)).toString());
    }

    @Test
    @DisplayName("notifica e relazione finale incomplete rifiutate via API con HTTP 422")
    void incompletiRifiutati() throws Exception {
        long id = percorso.registra("Incompleti", orologio.instant().minus(Duration.ofMinutes(50)));
        percorso.qualifica(id, Incidenti.neutro().indisponibilita(45).build(), orologio.instant().minus(Duration.ofMinutes(10)), "m");
        long pre = percorso.bozza(percorso.adempimento(id, "PRE_NOTIFICA")).get("id").asLong();
        percorso.compila(pre, Map.of("impattoTransfrontaliero", "NO"), Percorso.VOCI_PRE_NOTIFICA);
        percorso.approvaETrasmetti(pre, "REV11-01");

        long not = percorso.bozza(percorso.adempimento(id, "NOTIFICA")).get("id").asLong();
        JsonNode rifiutoNotifica = api.post("operatore", "/api/documenti/" + not + "/invio-approvazione", null, 422);
        assertThat(rifiutoNotifica.get("violazioni").findValuesAsText("campo")).contains("valutazioneIniziale", "classificazione");
        percorso.compila(not, Map.of("valutazioneIniziale", Percorso.VALUTAZIONE_NOTIFICA), Percorso.VOCI_NOTIFICA);
        percorso.approvaETrasmetti(not, "REV11-02");

        long fin = percorso.bozza(percorso.adempimento(id, "RELAZIONE_FINALE")).get("id").asLong();
        JsonNode rifiutoFinale = api.post("operatore", "/api/documenti/" + fin + "/invio-approvazione", null, 422);
        assertThat(rifiutoFinale.get("violazioni").findValuesAsText("campo")).contains("descrizioneDettagliata",
                "minacciaOCausaOriginale", "misureAttenuazione", "conclusioneGestioneIl");
    }

    @Test
    @DisplayName("Conclusione rifiutata con un documento in approvazione, per non trasmettere un contenuto superato")
    void conclusioneConDocumentoInApprovazione() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("In approvazione", "CIA");
        long fin = finaleCompilata(ids.incidente());
        api.post("operatore", "/api/documenti/" + fin + "/invio-approvazione", null, 422);
        api.post("operatore", "/api/incidenti/" + ids.incidente() + "/riclassificazione", new RiclassificazioneRequest(
                null, List.of("BC:IM_AV", "BC:RO_SY", "TT:AV_OU", "BC:SE_HI"), "Gravità rivalutata"), 201);
        api.post("operatore", "/api/incidenti/" + ids.incidente() + "/richieste-csirt", new RichiestaCsirtRequest("RIC-2", null, null), 201);
        long inter = percorso.bozza(percorso.adempimento(ids.incidente(), "RELAZIONE_INTERMEDIA")).get("id").asLong();
        percorso.compila(inter, Map.of("aggiornamentiSituazione", "Ripristino completato"),
                List.of("BC:IM_AV", "BC:RO_SY", "TT:AV_OU", "BC:SE_HI"));
        api.post("operatore", "/api/documenti/" + inter + "/invio-approvazione", null, 200);
        JsonNode errore = api.post("operatore", "/api/incidenti/" + ids.incidente() + "/conclusione",
                new ConclusioneRequest(orologio.instant()), 409);
        assertThat(errore.get("messaggio").asText()).contains("in approvazione");
    }

    @Test
    @DisplayName("Qualificazione: le soglie sono quelle vigenti alla data della conoscenza")
    void soglieAllaDataDellaConoscenza() throws Exception {
        long id = percorso.registra("Soglie", orologio.instant().minus(Duration.ofMinutes(50)));
        JsonNode d = api.post("operatore", "/api/incidenti/" + id + "/qualificazione", new QualificazioneRequest(
                Incidenti.neutro().indisponibilita(45).build(), orologio.instant().minus(Duration.ofMinutes(10)), "m"), 200);
        assertThat(d.at("/ultimaValutazione/versioneSoglie").asText()).isEqualTo("MSSP-2024-2690-v1");
    }
}
