package it.pw29.notifiche;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenari di soglia end-to-end (API + soglie caricate da Flyway): un incidente appena sopra
 * i criteri attiva il flusso di notifica, uno appena sotto no; un input incompleto è rifiutato.
 * Con 31 minuti scattano sia R1 (Reg. 2024/2690) sia IS-3 (SL del SOC/MDR, 30 minuti).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "OPERATORE")
class SignificativitaApiTest {

    @Autowired
    private MockMvc mvc;

    private static String incidente(int minutiIndisponibilita) {
        return """
                {"servizioCoinvolto": "SOC_MDR", "minutiIndisponibilitaTotale": %d, "minutiDegrado": 0,
                 "utentiImpattati": 0, "perditaRiservatezza": false, "perditaIntegrita": false,
                 "perditaAutenticita": false, "impattoEsterno": false, "sospettoDoloso": false,
                 "accessoNonAutorizzato": false, "gravePerturbazione": false, "perditaDiretta": 0,
                 "incidentiStessaCausaInFinestra": 0, "perditeCumulateInFinestra": 0}
                """.formatted(minutiIndisponibilita);
    }

    @Test
    @DisplayName("Scenario sopra soglia: SOC/MDR indisponibile 31 minuti -> flusso di notifica attivato")
    void sopraSoglia() throws Exception {
        mvc.perform(post("/api/significativita/valutazione")
                        .contentType(MediaType.APPLICATION_JSON).content(incidente(31)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.significativo").value(true))
                .andExpect(jsonPath("$.regoleAttivate[0]").value("R1"))
                .andExpect(jsonPath("$.categorieIs[0]").value("IS_3"))
                .andExpect(jsonPath("$.versioneSoglie").value("MSSP-2024-2690-v1"))
                .andExpect(jsonPath("$.sogliaUtenti").value(450));
    }

    @Test
    @DisplayName("Scenario sotto soglia: SOC/MDR indisponibile 30 minuti -> flusso non attivato")
    void sottoSoglia() throws Exception {
        mvc.perform(post("/api/significativita/valutazione")
                        .contentType(MediaType.APPLICATION_JSON).content(incidente(30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.significativo").value(false))
                .andExpect(jsonPath("$.regoleAttivate").isEmpty())
                .andExpect(jsonPath("$.categorieIs").isEmpty());
    }

    @Test
    @DisplayName("Input incompleto: rifiutato dalla validazione lato server")
    void inputIncompleto() throws Exception {
        mvc.perform(post("/api/significativita/valutazione")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutiDegrado\": 10}"))
                .andExpect(status().isBadRequest());
    }
}
