package it.pw29.notifiche.supporto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/** Chiamate alle API con gli utenti demo (HTTP Basic), con verifica dello stato HTTP atteso. */
public class ClientApi {

    public static final Map<String, String> PASSWORD = Map.of(
            "operatore", "Operatore-Demo-2026",
            "operatore2", "Operatore2-Demo-2026",
            "approvatore", "Approvatore-Demo-2026",
            "approvatore2", "Approvatore2-Demo-2026");

    private final MockMvc mvc;
    private final ObjectMapper json;

    public ClientApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public JsonNode post(String utente, String url, Object corpo, int statoAtteso) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON)
                .content(corpo == null ? "" : json.writeValueAsString(corpo));
        return esegui(utente, r, statoAtteso);
    }

    public JsonNode put(String utente, String url, Object corpo, int statoAtteso) throws Exception {
        return esegui(utente, MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)),
                statoAtteso);
    }

    public JsonNode get(String utente, String url, int statoAtteso) throws Exception {
        return esegui(utente, MockMvcRequestBuilders.get(url), statoAtteso);
    }

    /** File scaricato: tipo, intestazione Content-Disposition e byte. */
    public record File(String tipo, String disposizione, byte[] dati) {
    }

    public File scarica(String utente, String url, int statoAtteso) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(url).with(httpBasic(utente, PASSWORD.get(utente)))).andReturn();
        if (res.getResponse().getStatus() != statoAtteso) {
            throw new AssertionError("Atteso HTTP " + statoAtteso + " ma ricevuto " + res.getResponse().getStatus()
                    + " per GET " + url + ": " + res.getResponse().getContentAsString());
        }
        return new File(res.getResponse().getContentType(), res.getResponse().getHeader("Content-Disposition"),
                res.getResponse().getContentAsByteArray());
    }

    private JsonNode esegui(String utente, MockHttpServletRequestBuilder r, int statoAtteso) throws Exception {
        MvcResult res = mvc.perform(r.with(httpBasic(utente, PASSWORD.get(utente)))).andReturn();
        String corpo = res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if (res.getResponse().getStatus() != statoAtteso) {
            throw new AssertionError("Atteso HTTP " + statoAtteso + " ma ricevuto " + res.getResponse().getStatus()
                    + " per " + res.getRequest().getMethod() + " " + res.getRequest().getRequestURI() + ": " + corpo);
        }
        return corpo.isBlank() ? json.nullNode() : json.readTree(corpo);
    }

    public ObjectMapper json() {
        return json;
    }
}
