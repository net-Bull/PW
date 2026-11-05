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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documenti in formato strutturato (JSON) e stampabile (PDF/A-2b): stessi campi, solo quelli previsti
 * dalla matrice per l'adempimento, classificazione con percorso nella tassonomia, impronta nella catena.
 * Gli esempi generati restano in {@code target/esempi/} per il controllo visivo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OrologioTestConfig.class)
class EsportazioneTest {

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

    /** Campi previsti e obbligatori per un adempimento, letti dalla matrice esposta dalle API. */
    private Set<String> campi(String tipo, boolean soloObbligatori) throws Exception {
        Set<String> campi = new HashSet<>();
        for (JsonNode r : api.get("operatore", "/api/matrice-campi", 200)) {
            String req = r.at("/requisiti/" + tipo).asText();
            if (req.equals("OBBLIGATORIO") || (!soloObbligatori && req.equals("FACOLTATIVO"))) {
                campi.add(r.get("campo").asText());
            }
        }
        return campi;
    }

    private JsonNode esportaJson(long versione) throws Exception {
        ClientApi.File f = api.scarica("operatore", "/api/documenti/" + versione + "/json", 200);
        assertThat(f.tipo()).startsWith("application/json");
        salva(f);
        return json.readTree(new String(f.dati(), StandardCharsets.UTF_8));
    }

    private void verificaCampi(JsonNode doc) throws Exception {
        String tipo = doc.at("/adempimento/tipo").asText();
        List<String> presenti = new ArrayList<>();
        doc.get("contenuto").fieldNames().forEachRemaining(presenti::add);
        assertThat(campi(tipo, false)).as("campi previsti per " + tipo).containsAll(presenti);
        assertThat(presenti).as("campi obbligatori per " + tipo).containsAll(campi(tipo, true));
    }

    private static void salva(ClientApi.File f) throws Exception {
        String nome = f.disposizione().replaceAll(".*filename=\"([^\"]+)\".*", "$1");
        Path p = Path.of("target", "esempi", nome);
        Files.createDirectories(p.getParent());
        Files.write(p, f.dati());
    }

    /**
     * Testo del PDF senza spazi né a capo e in minuscolo: il confronto non dipende da dove l'impaginazione
     * spezza le righe né dalle etichette in maiuscolo del modulo (ADR-0008).
     */
    private static String testo(PDDocument pdf) throws Exception {
        return compatto(new PDFTextStripper().getText(pdf));
    }

    private static String compatto(String s) {
        return s.replaceAll("\\s", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static String[] compatti(String... attesi) {
        return java.util.Arrays.stream(attesi).map(EsportazioneTest::compatto).toArray(String[]::new);
    }

    @Test
    @DisplayName("JSON: per ciascun adempimento solo i campi previsti dalla matrice, obbligatori presenti, "
            + "voci con percorso, impronta della catena")
    void jsonPerAdempimento() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC/MDR irraggiungibile", "ESP1");

        JsonNode pre = esportaJson(ids.preNotifica());
        assertThat(pre.get("schema").asText()).isEqualTo("pw29-documento-csirt/2");
        assertThat(pre.at("/contenuto/criteriRegolamento").toString()).isEqualTo("[\"R1\"]");
        assertThat(pre.at("/incidente/stato").asText()).isEqualTo("SIGNIFICATIVO");
        assertThat(pre.at("/adempimento/tipo").asText()).isEqualTo("PRE_NOTIFICA");
        verificaCampi(pre);
        assertThat(pre.get("contenuto").has("valutazioneIniziale")).isFalse();

        JsonNode not = esportaJson(ids.notifica());
        verificaCampi(not);
        assertThat(not.at("/contenuto/valutazioneIniziale").asText()).startsWith("Gravità media");
        assertThat(not.at("/contenuto/utentiImpattati").asInt()).isEqualTo(9000);
        assertThat(not.get("contenuto").has("descrizioneDettagliata")).isFalse();
        assertThat(not.at("/soggetto/denominazione").asText()).isEqualTo("CyberWall S.r.l.");
        assertThat(not.at("/destinatario/canale").asText()).contains("portale.acn.gov.it");
        assertThat(not.at("/classificazione/voci")).hasSize(4);
        assertThat(not.at("/classificazione/voci").findValuesAsText("percorso"))
                .allSatisfy(p -> assertThat(p).contains(" > "));
        assertThat(not.at("/trasmissione/riferimentoPortale").asText()).isEqualTo("ACN-ESP1-02");
        JsonNode dettaglio = percorso.documento(ids.notifica());
        assertThat(not.at("/catena/impronta").asText()).isEqualTo(dettaglio.get("improntaSha256").asText());
        assertThat(not.at("/catena/improntaPrecedente").asText()).isEqualTo(dettaglio.get("improntaPrecedente").asText());

        // Bozza della relazione finale: nessuna trasmissione; l'ultimo anello noto fa da ancora esterna
        long fin = percorso.bozza(percorso.adempimento(ids.incidente(), "RELAZIONE_FINALE")).get("id").asLong();
        ClientApi.File f = api.scarica("operatore", "/api/documenti/" + fin + "/json", 200);
        assertThat(f.disposizione()).contains("_RELAZIONE_FINALE_1_v1_BOZZA.json");
        JsonNode bozza = json.readTree(f.dati());
        assertThat(bozza.at("/versione/stato").asText()).isEqualTo("BOZZA");
        assertThat(bozza.has("trasmissione")).isFalse();
        assertThat(bozza.at("/catena/improntaPrecedente").asText()).isEqualTo(dettaglio.get("improntaSha256").asText());
        assertThat(bozza.at("/catena").has("impronta")).isFalse();

        // Anche la bozza si stampa, con la fascia di stato che ne impedisce l'uso come documento trasmesso
        ClientApi.File pdfBozza = api.scarica("operatore", "/api/documenti/" + fin + "/pdf", 200);
        salva(pdfBozza);
        try (PDDocument pdf = Loader.loadPDF(pdfBozza.dati())) {
            assertThat(testo(pdf)).contains(compatti("BOZZA - non approvata, non trasmettere", "Ultimo documento trasmesso (impronta)"));
        }
    }

    @Test
    @DisplayName("PDF/A-2b: testo del documento, caratteri incorporati, profilo colore, impronta; "
            + "la relazione finale espone la riclassificazione")
    void pdf() throws Exception {
        Percorso.Ids ids = percorso.incidenteConNotifica("Console SOC/MDR irraggiungibile", "ESP2");
        ClientApi.File f = api.scarica("operatore", "/api/documenti/" + ids.notifica() + "/pdf", 200);
        salva(f);
        assertThat(f.tipo()).isEqualTo("application/pdf");
        assertThat(f.disposizione()).contains("_NOTIFICA_1_v1.pdf");
        assertThat(new String(f.dati(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String impronta = percorso.documento(ids.notifica()).get("improntaSha256").asText();

        try (PDDocument pdf = Loader.loadPDF(f.dati())) {
            String t = testo(pdf);
            assertThat(t).contains(compatti("Notifica", "CSIRT Italia", "CyberWall S.r.l.",
                    "Valutazione iniziale (gravità e impatto)", "Gravità media", "BC:SE_ME", "Medium",
                    "Copia del documento trasmesso", "ACN-ESP2-02", "Foglio 1 di", impronta));
            assertThat(t).doesNotContain(compatto("Descrizione dettagliata"));

            // PDF/A-2b: identificazione nei metadati XMP e profilo colore di output
            String xmp = new String(pdf.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertThat(xmp).containsPattern("pdfaid:part(=\"|>)2").containsPattern("pdfaid:conformance(=\"|>)B");
            assertThat(pdf.getDocumentCatalog().getOutputIntents()).isNotEmpty();
            assertThat(pdf.getDocumentInformation().getTitle()).contains("Notifica");
            for (PDPage pagina : pdf.getPages()) {
                PDResources r = pagina.getResources();
                for (COSName nome : r.getFontNames()) {
                    PDFont font = r.getFont(nome);
                    assertThat(font.isEmbedded()).as("font incorporato: " + font.getName()).isTrue();
                }
            }
        }

        // Relazione finale dopo una riclassificazione: sezione con prima/dopo, momento e motivazione
        api.post("operatore", "/api/incidenti/" + ids.incidente() + "/riclassificazione", new RiclassificazioneRequest(
                Incidenti.neutro().indisponibilita(45).utenti(2400).riservatezza().esterno().dolo().accesso().build(),
                RiclassificazioneTest.VOCI_RANSOMWARE, "Ransomware sull'hypervisor accertato dall'analisi forense"), 201);
        orologio.avanza(Duration.ofDays(5));
        long fin = percorso.bozza(percorso.adempimento(ids.incidente(), "RELAZIONE_FINALE")).get("id").asLong();
        percorso.compila(fin, Map.of("descrizioneDettagliata", "Cifratura dei datastore dell'hypervisor",
                "minacciaOCausaOriginale", "Ransomware tramite credenziali VPN compromesse",
                "misureAttenuazione", "Ripristino da backup immutabili; MFA sulla VPN",
                "attoIllegittimoOMalevolo", "SI"), RiclassificazioneTest.VOCI_RANSOMWARE);
        api.post("operatore", "/api/incidenti/" + ids.incidente() + "/conclusione", new ConclusioneRequest(orologio.instant()), 200);
        percorso.approvaETrasmetti(fin, "ACN-ESP2-03");

        ClientApi.File finale = api.scarica("approvatore", "/api/documenti/" + fin + "/pdf", 200);
        salva(finale);
        try (PDDocument pdf = Loader.loadPDF(finale.dati())) {
            String t = testo(pdf);
            assertThat(t).contains(compatti("Relazione finale", "Riclassificazioni successive a invii precedenti",
                    "TT:MA_RA", "Ransomware", "ACN-ESP2-02", "IS-3 -> IS-1, IS-3",
                    "Ransomware sull'hypervisor accertato dall'analisi forense"));
        }
        JsonNode finaleJson = esportaJson(fin);
        verificaCampi(finaleJson);
        assertThat(finaleJson.at("/contenuto/riclassificazioni/0/motivazione").asText()).contains("Ransomware");

    }

    @Test
    @DisplayName("Esportazione di una versione inesistente: 404")
    void inesistente() throws Exception {
        api.get("operatore", "/api/documenti/999999/json", 404);
        api.get("operatore", "/api/documenti/999999/pdf", 404);
    }
}
