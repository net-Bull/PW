package it.pw29.notifiche.scenari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pw29.notifiche.dto.ConclusioneRequest;
import it.pw29.notifiche.dto.RiclassificazioneRequest;
import it.pw29.notifiche.supporto.ClientApi;
import it.pw29.notifiche.supporto.Incidenti;
import it.pw29.notifiche.supporto.OrologioRegolabile;
import it.pw29.notifiche.supporto.OrologioTestConfig;
import it.pw29.notifiche.supporto.Percorso;
import it.pw29.notifiche.supporto.TracciaScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Riclassificazione tardiva (traccia PW 29, punto 3; milestone M3). Lo scenario principale è percorribile
 * per intero senza sistemi reali e produce la traccia di esecuzione con stato e identificativi di versione
 * dei documenti prima e dopo la riclassificazione ({@code docs/scenari/TRACCIA-RICLASSIFICAZIONE.md}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OrologioTestConfig.class)
class RiclassificazioneTest {

    /** Nuova classificazione dopo l'analisi forense: ransomware sull'hypervisor ed esfiltrazione di log. */
    static final List<String> VOCI_RANSOMWARE = List.of("BC:IM_AV", "BC:IM_DX", "BC:RO_MA", "BC:SE_HI", "TT:AV_DA", "TT:MA_RA");

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

    private static List<String> testi(JsonNode array) {
        List<String> l = new ArrayList<>();
        array.forEach(n -> l.add(n.isObject() ? n.get("codice").asText() : n.asText()));
        return l;
    }

    @Test
    @DisplayName("M3 - Dopo la notifica l'incidente si rivela un ransomware: riclassificazione, documenti trasmessi "
            + "immutati, relazione finale con classificazione precedente e nuova")
    void scenarioRiclassificazioneDopoNotifica() throws Exception {
        TracciaScenario traccia = new TracciaScenario(api, "Riclassificazione tardiva dopo la notifica (milestone M3)");

        // 1-2. Incidente del contesto: console SOC/MDR irraggiungibile, ipotesi iniziale di guasto (R1, IS-3)
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC/MDR irraggiungibile per 45 minuti", "M3");
        long id = ids.incidente();
        traccia.passo(orologio.instant(), "operatore + approvatore", "Pre-notifica e notifica trasmesse, ipotesi di guasto "
                + "del sistema: voci " + Percorso.VOCI_NOTIFICA);
        traccia.fotografia("Prima della riclassificazione", id);

        JsonNode notificaPrima = percorso.documento(ids.notifica());
        String improntaNotifica = notificaPrima.get("improntaSha256").asText();

        // 3. La relazione finale è in bozza con la classificazione della notifica
        orologio.avanza(Duration.ofDays(3));
        long fin = percorso.bozza(percorso.adempimento(id, "RELAZIONE_FINALE")).get("id").asLong();
        assertThat(testi(percorso.documento(fin).get("voci"))).containsExactlyInAnyOrderElementsOf(Percorso.VOCI_NOTIFICA);
        traccia.passo(orologio.instant(), "operatore", "Bozza della relazione finale (versione " + fin
                + ") creata con la classificazione della notifica");

        // 4. Riclassificazione: l'analisi forense trova ransomware e furto di archivi di log di una parte dei clienti
        orologio.avanza(Duration.ofDays(2));
        String motivazione = "L'analisi forense dell'hypervisor ha accertato la cifratura dei datastore da parte di un "
                + "ransomware e l'esfiltrazione preventiva degli archivi di log di 38 dei 140 clienti (circa 2.400 utenti "
                + "del servizio): non si tratta di un guasto";
        JsonNode ricl = api.post("operatore", "/api/incidenti/" + id + "/riclassificazione", new RiclassificazioneRequest(
                Incidenti.neutro().indisponibilita(45).utenti(2400).riservatezza().esterno().dolo().accesso().build(),
                VOCI_RANSOMWARE, motivazione), 201);
        assertThat(ricl.get("statoPrima").asText()).isEqualTo("SIGNIFICATIVO");
        assertThat(ricl.get("statoDopo").asText()).isEqualTo("SIGNIFICATIVO");
        assertThat(testi(ricl.get("categorieIsPrima"))).containsExactly("IS_3");
        assertThat(testi(ricl.get("categorieIsDopo"))).containsExactly("IS_1", "IS_3");
        assertThat(testi(ricl.get("vociPrima"))).containsExactlyInAnyOrderElementsOf(Percorso.VOCI_NOTIFICA);
        assertThat(testi(ricl.get("vociDopo"))).containsExactlyInAnyOrderElementsOf(VOCI_RANSOMWARE);
        assertThat(ricl.get("documentoPrimaId").asLong()).isEqualTo(ids.notifica());
        assertThat(ricl.get("comunicata").asBoolean()).isFalse();
        assertThat(ricl.get("valutazioneId").isNull()).isFalse();
        traccia.passo(orologio.instant(), "operatore", "Riclassificazione " + ricl.get("id").asLong()
                + ": voci " + testi(ricl.get("vociPrima")) + " -> " + testi(ricl.get("vociDopo"))
                + "; categorie " + testi(ricl.get("categorieIsPrima")) + " -> " + testi(ricl.get("categorieIsDopo"))
                + ". Motivazione: " + motivazione);

        // 5. I documenti trasmessi non cambiano (contenuto, voci, impronta); la catena resta integra
        JsonNode notificaDopo = percorso.documento(ids.notifica());
        assertThat(notificaDopo.get("stato").asText()).isEqualTo("TRASMESSO");
        assertThat(notificaDopo.get("improntaSha256").asText()).isEqualTo(improntaNotifica);
        assertThat(testi(notificaDopo.get("voci"))).containsExactlyInAnyOrderElementsOf(Percorso.VOCI_NOTIFICA);
        assertThat(api.get("operatore", "/api/incidenti/" + id + "/catena", 200).get("integra").asBoolean()).isTrue();

        // 6. La bozza della relazione finale è stata riallineata: nuove voci, nuove categorie, storico
        JsonNode bozzaFinale = percorso.documento(fin);
        assertThat(testi(bozzaFinale.get("voci"))).containsExactlyInAnyOrderElementsOf(VOCI_RANSOMWARE);
        assertThat(testi(bozzaFinale.at("/contenuto/categorieIs"))).containsExactly("IS_1", "IS_3");
        assertThat(bozzaFinale.at("/contenuto/riclassificazioni")).hasSize(1);
        traccia.fotografia("Dopo la riclassificazione, prima della relazione finale", id);

        // 7. Coerenza: riportare a mano la vecchia classificazione nella finale è rifiutato
        Map<String, Object> campiFinale = Map.of(
                "descrizioneDettagliata", "Cifratura dei datastore dell'hypervisor del cluster SOC da parte di un ransomware: "
                        + "console SOC/MDR non disponibile per 45 minuti per tutti i 140 clienti (9.000 utenti), servizio "
                        + "ripristinato dal sito secondario. Esfiltrazione preventiva degli archivi di log di 38 clienti "
                        + "(circa 2.400 utenti), con indirizzi IP, nomi host e account dei loro sistemi monitorati. "
                        + "Gravità alta",
                "minacciaOCausaOriginale", "Ransomware distribuito tramite credenziali VPN compromesse di un fornitore di "
                        + "manutenzione, prive di autenticazione multifattore",
                "misureAttenuazione", "Isolamento del cluster, ripristino da backup immutabili, revoca e rotazione delle "
                        + "credenziali, MFA su tutti gli accessi VPN di terze parti, comunicazione ai 38 clienti interessati "
                        + "(art. 25, c. 9, D.Lgs. 138/2024)",
                "attoIllegittimoOMalevolo", "SI",
                "impattoTransfrontaliero", "NO",
                "utentiImpattati", 9000);
        percorso.compila(fin, campiFinale, Percorso.VOCI_NOTIFICA);
        JsonNode incoerente = api.post("operatore", "/api/documenti/" + fin + "/invio-approvazione", null, 422);
        assertThat(incoerente.get("violazioni").findValuesAsText("etichetta"))
                .contains("Coerenza con la classificazione comunicata");
        traccia.passo(orologio.instant(), "operatore", "Tentativo di inviare la finale con le voci precedenti: rifiutato "
                + "(HTTP 422, coerenza con la classificazione comunicata)");

        // 8. Bonifica e ripristino richiedono due settimane: la gestione si conclude il 22/11, la relazione finale
        //    parte il giorno stesso, entro il termine di un mese dalla notifica (02/12)
        orologio.avanza(Duration.ofDays(15));
        percorso.compila(fin, campiFinale, VOCI_RANSOMWARE);
        api.post("operatore", "/api/incidenti/" + id + "/conclusione", new ConclusioneRequest(orologio.instant()), 200);
        traccia.passo(orologio.instant(), "operatore", "Conclusione della gestione registrata dopo bonifica e ripristino");
        JsonNode finale = percorso.approvaETrasmetti(fin, "ACN-M3-03");
        assertThat(finale.at("/contenuto/conclusioneGestioneIl").asText()).isEqualTo(
                percorso.incidente(id).get("conclusioneGestioneIl").asText());
        assertThat(finale.at("/contenuto/attoIllegittimoOMalevolo").asText()).isEqualTo("SI");
        JsonNode esposta = finale.at("/contenuto/riclassificazioni/0");
        assertThat(esposta.get("motivazione").asText()).isEqualTo(motivazione);
        assertThat(esposta.get("registrataIl").asText()).isEqualTo(ricl.get("registrataIl").asText());
        assertThat(testi(esposta.get("vociPrima"))).containsExactlyInAnyOrderElementsOf(Percorso.VOCI_NOTIFICA);
        assertThat(testi(esposta.get("vociDopo"))).containsExactlyInAnyOrderElementsOf(VOCI_RANSOMWARE);
        assertThat(esposta.get("riferimentoDocumentoPrima").asText()).isEqualTo("ACN-M3-02");
        traccia.passo(orologio.instant(), "approvatore", "Relazione finale (versione " + fin + ") trasmessa con la "
                + "classificazione nuova e la sezione riclassificazioni");

        // 9. Lo storico collega il documento che ha comunicato la riclassificazione
        JsonNode storico = api.get("operatore", "/api/incidenti/" + id + "/riclassificazioni", 200).get(0);
        assertThat(storico.get("comunicata").asBoolean()).isTrue();
        assertThat(storico.get("documentoDopoId").asLong()).isEqualTo(fin);
        JsonNode chiuso = percorso.incidente(id);
        assertThat(chiuso.get("stato").asText()).isEqualTo("CHIUSO");
        for (JsonNode sc : chiuso.get("scadenze")) {
            assertThat(sc.get("stato").asText()).as(sc.get("tipo").asText()).isEqualTo("RISPETTATA");
        }

        // 10. La copia conforme della notifica non cambia con la chiusura: intestazione sigillata all'invio
        JsonNode notificaEsportata = json.readTree(api.scarica("operatore", "/api/documenti/" + ids.notifica() + "/json", 200).dati());
        assertThat(notificaEsportata.at("/incidente/stato").asText()).isEqualTo("SIGNIFICATIVO");
        assertThat(notificaEsportata.at("/incidente").has("conclusioneGestioneIl")).isFalse();

        JsonNode catena = api.get("operatore", "/api/incidenti/" + id + "/catena", 200);
        assertThat(catena.get("integra").asBoolean()).isTrue();
        assertThat(catena.get("anelli")).hasSize(3);
        traccia.fotografia("Dopo la relazione finale", id);
        traccia.storico(id);
        traccia.catena(id);
        for (long versione : List.of(ids.preNotifica(), ids.notifica(), fin)) {
            traccia.allega("documenti", api.scarica("operatore", "/api/documenti/" + versione + "/json", 200));
            traccia.allega("documenti", api.scarica("operatore", "/api/documenti/" + versione + "/pdf", 200));
        }
        traccia.scrivi("TRACCIA-RICLASSIFICAZIONE.md");
    }

    @Test
    @DisplayName("Declassamento dopo la pre-notifica: la notifica lo comunica e la relazione finale chiude l'incidente")
    void declassamento() throws Exception {
        long id = percorso.registra("Console SOC/MDR irraggiungibile", orologio.instant().minus(Duration.ofMinutes(50)));
        percorso.qualifica(id, Incidenti.neutro().indisponibilita(45).build(), orologio.instant().minus(Duration.ofMinutes(10)),
                "Allarme di indisponibilità della console da 45 minuti");
        long pre = percorso.bozza(percorso.adempimento(id, "PRE_NOTIFICA")).get("id").asLong();
        percorso.compila(pre, Map.of("impattoTransfrontaliero", "NO"), Percorso.VOCI_PRE_NOTIFICA);
        percorso.approvaETrasmetti(pre, "ACN-DEC-01");

        // I log mostrano che il servizio è stato erogato dal sito secondario: indisponibilità effettiva 20 minuti
        JsonNode ricl = api.post("operatore", "/api/incidenti/" + id + "/riclassificazione", new RiclassificazioneRequest(
                Incidenti.neutro().indisponibilita(20).build(), List.of("BC:IM_NO", "BC:RO_SY", "TT:AV_OU", "BC:SE_LO"),
                "Dai log del bilanciatore il servizio è stato erogato dal sito secondario dopo 20 minuti: "
                        + "sotto il livello di servizio atteso (30 minuti)"), 201);
        assertThat(ricl.get("statoDopo").asText()).isEqualTo("DECLASSATO");
        assertThat(ricl.get("significativoDopo").asBoolean()).isFalse();
        assertThat(ricl.get("categorieIsDopo")).isEmpty();
        assertThat(percorso.incidente(id).get("stato").asText()).isEqualTo("DECLASSATO");

        // Gli obblighi già sorti restano: la notifica comunica il declassamento
        long not = percorso.bozza(percorso.adempimento(id, "NOTIFICA")).get("id").asLong();
        JsonNode bozza = percorso.documento(not);
        assertThat(bozza.at("/contenuto/riclassificazioni/0/significativoDopo").asBoolean()).isFalse();
        assertThat(testi(bozza.get("voci"))).contains("BC:IM_NO", "BC:SE_LO");
        percorso.compila(not, Map.of("valutazioneIniziale", "Nessun impatto effettivo: incidente declassato"),
                List.of("BC:IM_NO", "BC:RO_SY", "TT:AV_OU", "BC:SE_LO"));
        percorso.approvaETrasmetti(not, "ACN-DEC-02");

        // La relazione finale chiude un incidente declassato
        percorso.conclusione(id);
        long fin = percorso.bozza(percorso.adempimento(id, "RELAZIONE_FINALE")).get("id").asLong();
        assertThat(percorso.documento(fin).at("/contenuto/categorieIs")).isEmpty();
        percorso.compila(fin, Map.of("descrizioneDettagliata", "Falso positivo del monitoraggio di disponibilità",
                "minacciaOCausaOriginale", "Guasto della sonda di monitoraggio", "misureAttenuazione", "Sonda ridondata"),
                List.of("BC:IM_NO", "BC:RO_SY", "TT:AV_OU", "BC:SE_LO"));
        percorso.approvaETrasmetti(fin, "ACN-DEC-03");
        assertThat(percorso.incidente(id).get("stato").asText()).isEqualTo("CHIUSO");
        assertThat(api.get("operatore", "/api/incidenti/" + id + "/riclassificazioni", 200).at("/0/documentoDopoId").asLong())
                .isEqualTo(not);
    }

    @Test
    @DisplayName("Riclassificazione rifiutata: prima di ogni invio, senza cambiamenti, con un documento in approvazione, "
            + "con voci non di livello VALORE")
    void rifiuti() throws Exception {
        long id = percorso.registra("Console SOC/MDR irraggiungibile", orologio.instant().minus(Duration.ofMinutes(50)));
        percorso.qualifica(id, Incidenti.neutro().indisponibilita(45).build(), null, "Allarme di indisponibilità");
        RiclassificazioneRequest valida = new RiclassificazioneRequest(null, VOCI_RANSOMWARE, "Nuovi elementi");
        // Nessun documento trasmesso: si corregge la bozza
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione", valida, 409);

        long pre = percorso.bozza(percorso.adempimento(id, "PRE_NOTIFICA")).get("id").asLong();
        percorso.compila(pre, Map.of("impattoTransfrontaliero", "NO"), Percorso.VOCI_PRE_NOTIFICA);
        percorso.approvaETrasmetti(pre, "ACN-RIF-01");

        // Una riclassificazione che aggiunge la gravità è accettata; ripeterla identica non cambia nulla
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione",
                new RiclassificazioneRequest(null, List.of("BC:IM_AV", "BC:RO_SY", "TT:AV_OU", "BC:SE_ME"), "Gravità valutata"), 201);
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione",
                new RiclassificazioneRequest(null, List.of("BC:IM_AV", "BC:RO_SY", "TT:AV_OU", "BC:SE_ME"), "Di nuovo"), 422);
        // Voce di livello PREDICATO o due gravità
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione",
                new RiclassificazioneRequest(null, List.of("BC:IM", "BC:SE_HI"), "z"), 422);
        api.post("operatore", "/api/incidenti/" + id + "/riclassificazione",
                new RiclassificazioneRequest(null, List.of("BC:SE_HI", "BC:SE_LO"), "z"), 422);
        // Documento in approvazione: va respinto prima
        long not = percorso.bozza(percorso.adempimento(id, "NOTIFICA")).get("id").asLong();
        percorso.compila(not, Map.of("valutazioneIniziale", "Gravità media"), List.of("BC:IM_AV", "BC:RO_SY", "TT:AV_OU", "BC:SE_ME"));
        api.post("operatore", "/api/documenti/" + not + "/invio-approvazione", null, 200);
        JsonNode errore = api.post("operatore", "/api/incidenti/" + id + "/riclassificazione", valida, 409);
        assertThat(errore.get("messaggio").asText()).contains("in approvazione");
    }
}
