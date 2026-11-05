package it.pw29.notifiche;

import it.pw29.notifiche.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autenticazione, ruoli e audit degli accessi falliti. */
@SpringBootTest
@AutoConfigureMockMvc
class SicurezzaTest {

    @Autowired MockMvc mvc;
    @Autowired AuditService audit;

    @Test
    @DisplayName("API senza credenziali: 401, non un redirect alla pagina di login")
    void senzaCredenziali() throws Exception {
        mvc.perform(get("/api/incidenti")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Password errata: 401 e tentativo registrato nell'audit")
    void passwordErrata() throws Exception {
        mvc.perform(get("/api/incidenti").with(httpBasic("operatore", "sbagliata"))).andExpect(status().isUnauthorized());
        assertThat(audit.perAzione("ACCESSO_FALLITO")).anySatisfy(a -> assertThat(a.getUsername()).isEqualTo("operatore"));
    }

    @Test
    @DisplayName("Operatore: legge e registra; non respinge né trasmette (ruolo approvatore)")
    void ruoli() throws Exception {
        mvc.perform(get("/api/incidenti").with(httpBasic("operatore", "Operatore-Demo-2026"))).andExpect(status().isOk());
        mvc.perform(post("/api/documenti/999/respingimento").with(httpBasic("operatore", "Operatore-Demo-2026"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"x\"}"))
                .andExpect(status().isForbidden());
        // L'approvatore supera il controllo di ruolo: la versione inesistente dà 404
        mvc.perform(post("/api/documenti/999/respingimento").with(httpBasic("approvatore", "Approvatore-Demo-2026"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Matrice campo/adempimento disponibile in JSON e Markdown agli utenti autenticati")
    void matrice() throws Exception {
        mvc.perform(get("/api/matrice-campi").with(httpBasic("operatore", "Operatore-Demo-2026"))).andExpect(status().isOk());
        mvc.perform(get("/api/matrice-campi/markdown").with(httpBasic("operatore", "Operatore-Demo-2026")))
                .andExpect(status().isOk());
    }
}
