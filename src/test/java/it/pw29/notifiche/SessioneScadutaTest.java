package it.pw29.notifiche;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressione della prova d'uso della Fase 5: un modulo inviato dopo la scadenza della sessione (token CSRF della
 * sessione scaduta) mostrava la pagina d'errore predefinita di Spring Boot con un 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessioneScadutaTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Modulo inviato con una sessione scaduta: rimando all'accesso con la spiegazione")
    void sessioneScaduta() throws Exception {
        mvc.perform(post("/documenti/1/trasmissione").param("riferimento", "X").param("conferma", "true")
                        .with(r -> {
                            r.setRequestedSessionId("sessione-scaduta");
                            r.setRequestedSessionIdValid(false);
                            return r;
                        }))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?scaduta"));
        mvc.perform(get("/login").param("scaduta", "").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La sessione è scaduta per inattività")));
    }

    @Test
    @DisplayName("Token CSRF mancante in una sessione attiva: resta un rifiuto 403, senza rimando")
    void tokenMancanteInSessioneAttiva() throws Exception {
        mvc.perform(post("/documenti/1/trasmissione").param("riferimento", "X").with(user("approvatore").roles("APPROVATORE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Gli errori fuori dai controller usano la pagina dell'interfaccia, non quella predefinita")
    void paginaErrore() throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML).requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/documenti/1/trasmissione"))
                .andExpect(content().string(containsString("Operazione non consentita")))
                .andExpect(content().string(not(containsString("Whitelabel"))));
    }
}
