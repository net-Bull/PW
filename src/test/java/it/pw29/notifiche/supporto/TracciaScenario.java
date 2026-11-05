package it.pw29.notifiche.supporto;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Traccia di esecuzione di uno scenario in Markdown: passi con istante e attore, fotografie dello stato
 * dei documenti (identificativo di versione, stato, voci, impronta), storico delle riclassificazioni e
 * verifica della catena. È prodotta dalle chiamate API reali del test, non scritta a mano.
 * <p>
 * Il file va sempre in {@code target/scenari/}; con {@code -Dtraccia.aggiorna=true} anche in
 * {@code docs/scenari/}, da dove è citato nella relazione.
 */
public class TracciaScenario {

    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Rome"));

    private final ClientApi api;
    private final StringBuilder md = new StringBuilder();
    private final List<String> passi = new ArrayList<>();
    private final List<String> allegati = new ArrayList<>();
    private int passiScritti;

    public TracciaScenario(ClientApi api, String titolo) {
        this.api = api;
        md.append("# Traccia di esecuzione – ").append(titolo).append("\n\n")
                .append("> File GENERATO dal test `RiclassificazioneTest` con le chiamate API reali dello scenario, ")
                .append("orologio simulato e database H2 in memoria. Gli identificativi dipendono dall'ordine di esecuzione ")
                .append("dei test; per rigenerarlo: `mvn test -Dtest=RiclassificazioneTest -Dtraccia.aggiorna=true`.\n")
                .append("> Orari in Europe/Rome. Impronte SHA-256 abbreviate ai primi 16 caratteri.\n\n");
    }

    public void passo(Instant istante, String attore, String descrizione) {
        passi.add("| " + (passiScritti + passi.size() + 1) + " | " + ORA.format(istante) + " | " + attore + " | " + cella(descrizione) + " |");
    }

    /** Stato dell'incidente e di tutte le versioni dei suoi documenti in questo momento. */
    public void fotografia(String titolo, long incidente) throws Exception {
        scriviPassi();
        JsonNode inc = api.get("operatore", "/api/incidenti/" + incidente, 200);
        md.append("## ").append(titolo).append("\n\n")
                .append("Incidente **").append(inc.get("codice").asText()).append("**, stato **")
                .append(inc.get("stato").asText()).append("**, categorie IS dell'ultima valutazione: ")
                .append(inc.at("/ultimaValutazione/categorieIs").toString()).append("\n\n")
                .append("| Versione (id) | Adempimento | N. | Stato | Derivata da | Rettifica | Voci | Impronta |\n")
                .append("| ---: | --- | ---: | --- | ---: | --- | --- | --- |\n");
        for (JsonNode a : inc.get("adempimenti")) {
            for (JsonNode v : a.get("versioni")) {
                JsonNode d = api.get("operatore", "/api/documenti/" + v.get("id").asLong(), 200);
                List<String> voci = new ArrayList<>();
                d.get("voci").forEach(x -> voci.add(x.get("codice").asText()));
                md.append("| ").append(d.get("id").asLong()).append(" | ").append(a.get("tipo").asText())
                        .append(" | ").append(d.get("numero").asInt()).append(" | ").append(d.get("stato").asText())
                        .append(" | ").append(d.get("derivataDaId").isNull() ? "–" : d.get("derivataDaId").asText())
                        .append(" | ").append(d.get("motivoRettifica").isNull() ? "no" : "sì")
                        .append(" | ").append(String.join(", ", voci))
                        .append(" | ").append(breve(d.get("improntaSha256"))).append(" |\n");
            }
        }
        md.append("\n");
    }

    public void storico(long incidente) throws Exception {
        scriviPassi();
        md.append("## Storico delle riclassificazioni\n\n")
                .append("| Id | Registrata | Da | Stato prima → dopo | Categorie IS prima → dopo | Voci prima | Voci dopo | ")
                .append("Ultimo documento prima (id, rif.) | Documento che la comunica (id, rif.) | Motivazione |\n")
                .append("| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (JsonNode r : api.get("operatore", "/api/incidenti/" + incidente + "/riclassificazioni", 200)) {
            md.append("| ").append(r.get("id").asLong())
                    .append(" | ").append(ORA.format(Instant.parse(r.get("registrataIl").asText())))
                    .append(" | ").append(r.get("registrataDa").asText())
                    .append(" | ").append(r.get("statoPrima").asText()).append(" → ").append(r.get("statoDopo").asText())
                    .append(" | ").append(elenco(r.get("categorieIsPrima"))).append(" → ").append(elenco(r.get("categorieIsDopo")))
                    .append(" | ").append(elenco(r.get("vociPrima")))
                    .append(" | ").append(elenco(r.get("vociDopo")))
                    .append(" | ").append(r.get("documentoPrimaId").asText()).append(", ").append(r.get("documentoPrimaRiferimento").asText())
                    .append(" | ").append(r.get("documentoDopoId").isNull() ? "non ancora comunicata"
                            : r.get("documentoDopoId").asText() + ", " + r.get("documentoDopoRiferimento").asText())
                    .append(" | ").append(cella(r.get("motivazione").asText())).append(" |\n");
        }
        md.append("\n");
    }

    public void catena(long incidente) throws Exception {
        JsonNode c = api.get("operatore", "/api/incidenti/" + incidente + "/catena", 200);
        md.append("## Catena di hash\n\n")
                .append("Algoritmo ").append(c.get("algoritmo").asText()).append(", catena **")
                .append(c.get("integra").asBoolean() ? "integra" : "NON integra").append("**, genesi `")
                .append(breve(c.get("genesi"))).append("`.\n\n")
                .append("| Pos. | Versione (id) | Adempimento | N. | Rif. portale | Anello precedente | Impronta | Integro |\n")
                .append("| ---: | ---: | --- | ---: | --- | --- | --- | --- |\n");
        for (JsonNode a : c.get("anelli")) {
            md.append("| ").append(a.get("posizione").asInt()).append(" | ").append(a.get("versioneId").asLong())
                    .append(" | ").append(a.get("tipo").asText()).append(" | ").append(a.get("numero").asInt())
                    .append(" | ").append(a.get("riferimentoTrasmissione").asText())
                    .append(" | ").append(breve(a.get("improntaPrecedente"))).append(" | ").append(breve(a.get("impronta")))
                    .append(" | ").append(a.get("contenutoIntegro").asBoolean() && a.get("collegato").asBoolean() ? "sì" : "NO")
                    .append(" |\n");
        }
        md.append("\n");
    }

    /** Salva un file prodotto dallo scenario (JSON, PDF) accanto alla traccia e lo elenca nella traccia. */
    public void allega(String sottocartella, ClientApi.File file) throws IOException {
        String nome = file.disposizione().replaceAll(".*filename=\"([^\"]+)\".*", "$1");
        Path target = Path.of("target", "scenari", sottocartella, nome);
        Files.createDirectories(target.getParent());
        Files.write(target, file.dati());
        if (Boolean.getBoolean("traccia.aggiorna")) {
            Path docs = Path.of("docs", "scenari", sottocartella, nome);
            Files.createDirectories(docs.getParent());
            Files.write(docs, file.dati());
        }
        allegati.add("- [`" + nome + "`](" + sottocartella + "/" + nome + ")");
    }

    public void scrivi(String nomeFile) throws IOException {
        scriviPassi();
        if (!allegati.isEmpty()) {
            md.append("## Documenti prodotti dallo scenario\n\n");
            allegati.forEach(a -> md.append(a).append("\n"));
            md.append("\n");
        }
        String testo = md.toString();
        Path target = Path.of("target", "scenari", nomeFile);
        Files.createDirectories(target.getParent());
        Files.writeString(target, testo, StandardCharsets.UTF_8);
        if (Boolean.getBoolean("traccia.aggiorna")) {
            Path docs = Path.of("docs", "scenari", nomeFile);
            Files.createDirectories(docs.getParent());
            Files.writeString(docs, testo, StandardCharsets.UTF_8);
        }
    }

    private void scriviPassi() {
        if (passi.isEmpty()) {
            return;
        }
        md.append("## Passi\n\n| # | Istante | Attore | Operazione |\n| ---: | --- | --- | --- |\n");
        passi.forEach(p -> md.append(p).append("\n"));
        md.append("\n");
        passiScritti += passi.size();
        passi.clear();
    }

    private static String breve(JsonNode impronta) {
        return impronta == null || impronta.isNull() ? "–" : impronta.asText().substring(0, 16);
    }

    private static String elenco(JsonNode array) {
        List<String> l = new ArrayList<>();
        array.forEach(n -> l.add(n.asText()));
        return l.isEmpty() ? "nessuna" : String.join(", ", l);
    }

    private static String cella(String testo) {
        return testo.replace("|", "\\|").replace("\n", " ");
    }
}
