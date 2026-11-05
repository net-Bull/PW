package it.pw29.notifiche.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La matrice pubblicata in docs/MATRICE-CAMPI.md deve coincidere con quella generata dal codice:
 * se un campo cambia e il documento no, la build fallisce. Rigenerazione:
 * {@code mvn test -Dtest=MatriceCampiDocumentazioneTest -Dmatrice.aggiorna=true}.
 */
class MatriceCampiDocumentazioneTest {

    private static final Path FILE = Path.of("docs", "MATRICE-CAMPI.md");

    @Test
    @DisplayName("docs/MATRICE-CAMPI.md è allineato al codice")
    void documentoAllineato() throws IOException {
        String generato = new MatriceCampi().markdown();
        if (Boolean.getBoolean("matrice.aggiorna")) {
            Files.writeString(FILE, generato, StandardCharsets.UTF_8);
        }
        assertThat(Files.exists(FILE)).as("manca " + FILE + ": rigenerarlo con -Dmatrice.aggiorna=true").isTrue();
        String pubblicato = Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(pubblicato).isEqualTo(generato);
    }
}
