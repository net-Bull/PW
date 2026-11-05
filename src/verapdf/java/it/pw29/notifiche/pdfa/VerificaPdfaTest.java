package it.pw29.notifiche.pdfa;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformità PDF/A-2b dei documenti prodotti dall'applicazione, verificata con veraPDF (motore greenfield,
 * validatore di riferimento della PDF Association). Gira solo nel profilo Maven {@code verapdf}:
 *
 * <pre>mvn -Pverapdf -Dtest=VerificaPdfaTest test [-Dverapdf.cartella=...] [-Dverapdf.rapporto=...]</pre>
 *
 * Verifica i PDF dello scenario M3 in {@code docs/scenari/documenti} e, se indicata, una cartella di PDF
 * scaricati dall'interfaccia. Scrive un rapporto Markdown in {@code target/verapdf/VERIFICA-PDFA.md}
 * (e nel percorso di {@code verapdf.rapporto}, se presente) e fallisce se un file non è conforme.
 */
class VerificaPdfaTest {

    private static final PDFAFlavour PROFILO = PDFAFlavour.PDFA_2_B;

    @BeforeAll
    static void inizializza() {
        VeraGreenfieldFoundryProvider.initialise();
    }

    @Test
    @DisplayName("I PDF prodotti sono conformi a PDF/A-2b secondo veraPDF")
    void pdfConformi() throws Exception {
        List<Path> file = new ArrayList<>(pdfIn(Path.of("docs", "scenari", "documenti")));
        String cartella = System.getProperty("verapdf.cartella");
        if (cartella != null && !cartella.isBlank()) {
            file.addAll(pdfIn(Path.of(cartella)));
        }
        assertThat(file).as("PDF da verificare").isNotEmpty();

        List<Esito> esiti = new ArrayList<>();
        for (Path pdf : file) {
            esiti.add(verifica(pdf));
        }
        String rapporto = rapporto(esiti);
        scrivi(Path.of("target", "verapdf", "VERIFICA-PDFA.md"), rapporto);
        String destinazione = System.getProperty("verapdf.rapporto");
        if (destinazione != null && !destinazione.isBlank()) {
            scrivi(Path.of(destinazione), rapporto);
        }
        System.out.println(rapporto);

        assertThat(esiti).as("file non conformi").allSatisfy(e -> assertThat(e.conforme())
                .as("%s: %s", e.file(), e.regoleFallite()).isTrue());
    }

    @Test
    @DisplayName("Controllo negativo: un PDF senza i requisiti PDF/A viene rifiutato")
    void pdfNonConformeRifiutato() throws Exception {
        Path semplice = Path.of("target", "verapdf", "controllo-negativo.pdf");
        Files.createDirectories(semplice.getParent());
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage());
            documento.save(semplice.toFile());
        }
        Esito esito = verifica(semplice);
        assertThat(esito.conforme()).as("PDF semplice senza metadati XMP né OutputIntent").isFalse();
        assertThat(esito.regoleFallite()).isNotEmpty();
        System.out.println("Controllo negativo, regole fallite: " + esito.regoleFallite());
    }

    private static List<Path> pdfIn(Path cartella) throws Exception {
        if (!Files.isDirectory(cartella)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(cartella)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf")).sorted().toList();
        }
    }

    private static Esito verifica(Path pdf) throws Exception {
        String dichiarato = dichiarato(pdf);
        try (InputStream in = Files.newInputStream(pdf);
             PDFAParser parser = Foundries.defaultInstance().createParser(in, PROFILO)) {
            PDFAValidator validatore = Foundries.defaultInstance().createValidator(PROFILO, false);
            ValidationResult risultato = validatore.validate(parser);
            Map<String, Integer> fallite = new TreeMap<>();
            for (TestAssertion a : risultato.getTestAssertions()) {
                if (a.getStatus() == TestAssertion.Status.FAILED) {
                    String regola = a.getRuleId().getClause() + "-" + a.getRuleId().getTestNumber()
                            + " " + a.getMessage();
                    fallite.merge(regola, 1, Integer::sum);
                }
            }
            return new Esito(pdf.toString().replace('\\', '/'), Files.size(pdf), dichiarato,
                    risultato.isCompliant(), fallite);
        }
    }

    /** Profilo PDF/A dichiarato nei metadati XMP del file; "nessuno" se il file non si identifica come PDF/A. */
    private static String dichiarato(Path pdf) {
        try (InputStream in = Files.newInputStream(pdf);
             PDFAParser parser = Foundries.defaultInstance().createParser(in)) {
            return parser.getFlavour().getId();
        } catch (Exception e) {
            return "nessuno";
        }
    }

    private static String rapporto(List<Esito> esiti) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Verifica PDF/A con veraPDF\n\n");
        sb.append("- Data: ").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n');
        sb.append("- Validatore: veraPDF ").append(System.getProperty("verapdf.versione", "?"))
                .append(" (motore greenfield, `org.verapdf:validation-model-jakarta`)\n");
        sb.append("- Profilo di validazione: PDF/A-").append(PROFILO.getId()).append('\n');
        sb.append("- Java: ").append(System.getProperty("java.version")).append(", ")
                .append(System.getProperty("os.name")).append('\n');
        long conformi = esiti.stream().filter(Esito::conforme).count();
        sb.append("- Esito: **").append(conformi).append(" conformi su ").append(esiti.size()).append("**\n\n");
        sb.append("| File | Byte | Dichiarato | Conforme | Regole fallite |\n| --- | ---: | --- | --- | --- |\n");
        for (Esito e : esiti) {
            sb.append("| `").append(e.file()).append("` | ").append(e.byte_()).append(" | ").append(e.dichiarato())
                    .append(" | ").append(e.conforme() ? "sì" : "**no**").append(" | ")
                    .append(e.regoleFallite().isEmpty() ? "nessuna" : e.regoleFallite().toString().replace("|", "/"))
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static void scrivi(Path destinazione, String testo) throws Exception {
        Path cartella = destinazione.toAbsolutePath().getParent();
        if (cartella != null) {
            Files.createDirectories(cartella);
        }
        Files.writeString(destinazione, testo, StandardCharsets.UTF_8);
    }

    private record Esito(String file, long byte_, String dichiarato, boolean conforme,
                         Map<String, Integer> regoleFallite) {
    }
}
