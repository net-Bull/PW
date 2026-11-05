package it.pw29.notifiche.scenari;

import it.pw29.notifiche.domain.StatoIncidente;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.dto.AdempimentoDto;
import it.pw29.notifiche.dto.IncidenteDettaglio;
import it.pw29.notifiche.dto.Violazione;
import it.pw29.notifiche.service.IncidenteService;
import it.pw29.notifiche.supporto.OrologioRegolabile;
import it.pw29.notifiche.supporto.OrologioTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone M4: lo scenario dell'incidente percorso per intero dall'interfaccia web, come farebbero l'operatore e
 * l'approvatore dal browser. Accesso con il login a form (utenti demo), una sessione per utente, token CSRF in
 * ogni modulo, nessuna chiamata alle API REST: registrazione, qualificazione, pre-notifica, notifica (prima
 * rifiutata perché incompleta), riclassificazione tardiva, conclusione, relazione finale che chiude l'incidente.
 * <p>
 * Il test produce la traccia {@code TRACCIA-INTERFACCIA.md} (in {@code target/scenari/}; con
 * {@code -Dtraccia.aggiorna=true} anche in {@code docs/scenari/}) con le pagine chiamate e gli esiti mostrati.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OrologioTestConfig.class)
class ScenarioInterfacciaTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter CAMPO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ROMA);
    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ROMA);

    @Autowired MockMvc mvc;
    @Autowired OrologioRegolabile orologio;
    @Autowired IncidenteService incidenti;

    private final List<String> traccia = new ArrayList<>();
    private final Map<String, MockHttpSession> sessioni = new LinkedHashMap<>();

    @BeforeEach
    void prepara() {
        orologio.imposta(OrologioTestConfig.INIZIO);
    }

    // --- Supporto ----------------------------------------------------------------------------------

    private MockHttpSession accedi(String utente, String password) throws Exception {
        MvcResult r = mvc.perform(formLogin("/login").user(utente).password(password))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/incidenti"))
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private MockHttpSession sessione(String utente) throws Exception {
        if (!sessioni.containsKey(utente)) {
            String password = Map.of("operatore", "Operatore-Demo-2026", "approvatore", "Approvatore-Demo-2026").get(utente);
            sessioni.put(utente, accedi(utente, password));
        }
        return sessioni.get(utente);
    }

    /** Invio di un modulo: restituisce la pagina di destinazione; il messaggio flash finisce nella traccia. */
    private Esito invia(String utente, String percorso, String descrizione, String... campi) throws Exception {
        MockHttpServletRequestBuilder r = post(percorso).session(sessione(utente)).with(csrf());
        for (int i = 0; i < campi.length; i += 2) {
            r.param(campi[i], campi[i + 1]);
        }
        MvcResult res = mvc.perform(r).andExpect(status().is3xxRedirection()).andReturn();
        String dove = res.getResponse().getRedirectedUrl();
        Object conferma = res.getFlashMap().get("conferma");
        Object errore = res.getFlashMap().get("errore");
        @SuppressWarnings("unchecked")
        List<Violazione> violazioni = (List<Violazione>) res.getFlashMap().get("violazioni");
        String esito = conferma != null ? conferma.toString() : "**Rifiutato:** " + errore
                + (violazioni == null ? "" : " " + violazioni.stream().map(Violazione::etichetta).toList());
        traccia.add("| " + (traccia.size() + 1) + " | " + ORA.format(orologio.instant()) + " | " + utente + " | "
                + descrizione + " | `POST " + percorso + "` | `" + dove + "` | " + esito.replace("|", "/") + " |");
        return new Esito(dove, conferma == null ? null : conferma.toString(), errore == null ? null : errore.toString(), violazioni);
    }

    record Esito(String pagina, String conferma, String errore, List<Violazione> violazioni) {
        long id() {
            return Long.parseLong(pagina.replaceAll("^/[a-z]+/(\\d+).*$", "$1"));
        }
    }

    private String pagina(String utente, String percorso) throws Exception {
        return mvc.perform(get(percorso).session(sessione(utente))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long adempimento(long incidente, TipoAdempimento tipo) {
        return incidenti.dettaglio(incidente).adempimenti().stream().filter(a -> a.tipo() == tipo)
                .map(AdempimentoDto::id).findFirst().orElseThrow();
    }

    private static String campo(Instant i) {
        return CAMPO.format(i);
    }

    // --- Scenario ----------------------------------------------------------------------------------

    @Test
    @DisplayName("M4: scenario completo dall'interfaccia, dalla registrazione alla relazione finale")
    void scenarioCompleto() throws Exception {
        // Registrazione e qualificazione
        Esito reg = invia("operatore", "/incidenti", "Registra l'incidente",
                "titolo", "Console SOC/MDR irraggiungibile",
                "descrizione", "Scenario simulato: la console del SOC/MDR non risponde, nessun dato reale",
                "rilevatoIl", campo(orologio.instant().minus(Duration.ofMinutes(50))));
        assertThat(reg.pagina()).matches("/incidenti/\\d+/qualificazione");
        long id = Long.parseLong(reg.pagina().replaceAll("\\D", ""));
        assertThat(pagina("operatore", reg.pagina())).contains("Valutazione di significatività", "SOC_MDR");

        Esito qual = invia("operatore", "/incidenti/" + id + "/qualificazione", "Valuta la significatività",
                "servizioCoinvolto", "SOC_MDR", "minutiIndisponibilitaTotale", "45", "minutiDegrado", "0",
                "utentiImpattati", "9000", "perditaDiretta", "0", "incidentiStessaCausaInFinestra", "0",
                "perditeCumulateInFinestra", "0",
                "conoscenzaIl", campo(orologio.instant().minus(Duration.ofMinutes(10))),
                "motivazioneConoscenza", "Console irraggiungibile da 45 minuti, confermato dal monitoraggio di disponibilità");
        assertThat(qual.conferma()).contains("significativo", "R1", "IS-3");
        String dettaglio = pagina("operatore", "/incidenti/" + id);
        assertThat(dettaglio).contains("Termini verso il CSIRT Italia", "Pre-notifica", "mancano", "Crea bozza");

        // Pre-notifica
        long pre = invia("operatore", "/adempimenti/" + adempimento(id, TipoAdempimento.PRE_NOTIFICA) + "/bozza",
                "Crea la bozza della pre-notifica", "incidente", String.valueOf(id)).id();
        String editor = pagina("operatore", "/documenti/" + pre);
        assertThat(editor).contains("Impatto transfrontaliero", "Compilato dal sistema", "data-verifica")
                .doesNotContain("Valutazione iniziale (gravità e impatto)");   // non previsto per la pre-notifica
        Esito invioPre = invia("operatore", "/documenti/" + pre, "Compila e invia in approvazione",
                "descrizioneSintetica", "Console SOC/MDR irraggiungibile", "referenteNome", "Referente CSIRT (fittizio)",
                "referenteEmail", "csirt@cyberwall.example", "referenteTelefono", "+39 089 84 80 90",
                "serviziImpattati", "SOC_MDR", "attoIllegittimoOMalevolo", "NON_NOTO", "impattoTransfrontaliero", "NON_NOTO",
                "voci", "BC:IM_AV", "voci", "BC:RO_SY", "voci", "TT:AV_OU", "poi", "invia");
        assertThat(invioPre.conferma()).contains("inviata in approvazione");
        orologio.avanza(Duration.ofMinutes(20));
        Esito vietato = invia("operatore", "/documenti/" + pre + "/trasmissione", "Tenta di registrare la trasmissione",
                "riferimento", "ACN-UI-01", "conferma", "true");
        assertThat(vietato.errore()).contains("non consentita al tuo profilo");
        assertThat(invia("approvatore", "/documenti/" + pre + "/trasmissione", "Registra la trasmissione",
                "riferimento", "ACN-UI-01", "conferma", "true").conferma()).contains("sigillato con impronta");

        // Notifica: prima rifiutata perché manca la valutazione iniziale, poi completata
        long not = invia("operatore", "/adempimenti/" + adempimento(id, TipoAdempimento.NOTIFICA) + "/bozza",
                "Crea la bozza della notifica (dati ripresi dalla pre-notifica)", "incidente", String.valueOf(id)).id();
        assertThat(pagina("operatore", "/documenti/" + not)).contains("Console SOC/MDR irraggiungibile", "BC:RO_SY");
        Esito incompleta = invia("operatore", "/documenti/" + not, "Invia la notifica senza valutazione iniziale",
                "descrizioneSintetica", "Console SOC/MDR irraggiungibile", "referenteNome", "Referente CSIRT (fittizio)",
                "referenteEmail", "csirt@cyberwall.example", "referenteTelefono", "+39 089 84 80 90",
                "serviziImpattati", "SOC_MDR", "attoIllegittimoOMalevolo", "NON_NOTO", "impattoTransfrontaliero", "NON_NOTO",
                "utentiImpattati", "9000",
                "voci", "BC:IM_AV", "voci", "BC:RO_SY", "voci", "TT:AV_OU", "voci", "BC:SE_ME", "poi", "invia");
        assertThat(incompleta.errore()).contains("non è completo per Notifica");
        assertThat(incompleta.violazioni()).extracting(Violazione::campo).contains("valutazioneIniziale");
        assertThat(invia("operatore", "/documenti/" + not, "Aggiunge la valutazione iniziale e invia",
                "descrizioneSintetica", "Console SOC/MDR irraggiungibile", "referenteNome", "Referente CSIRT (fittizio)",
                "referenteEmail", "csirt@cyberwall.example", "referenteTelefono", "+39 089 84 80 90",
                "serviziImpattati", "SOC_MDR", "attoIllegittimoOMalevolo", "NON_NOTO", "impattoTransfrontaliero", "NON_NOTO",
                "utentiImpattati", "9000",
                "valutazioneIniziale", "Gravità media: console SOC/MDR non disponibile per 45 minuti, 140 clienti (9.000 utenti)",
                "voci", "BC:IM_AV", "voci", "BC:RO_SY", "voci", "TT:AV_OU", "voci", "BC:SE_ME", "poi", "invia").conferma())
                .contains("inviata in approvazione");
        orologio.avanza(Duration.ofHours(1));
        invia("approvatore", "/documenti/" + not + "/trasmissione", "Registra la trasmissione della notifica",
                "riferimento", "ACN-UI-02", "conferma", "true");

        // Riclassificazione tardiva dopo la notifica
        orologio.avanza(Duration.ofDays(3));
        assertThat(pagina("operatore", "/incidenti/" + id + "/riclassificazione")).contains("resta", "immutato");
        Esito ricl = invia("operatore", "/incidenti/" + id + "/riclassificazione", "Riclassifica dopo l'analisi forense",
                "voci", "BC:IM_AV", "voci", "BC:IM_DX", "voci", "BC:RO_MA", "voci", "BC:SE_HI", "voci", "TT:AV_DA",
                "voci", "TT:MA_RA",
                "motivazione", "L'analisi forense ha accertato la cifratura ransomware dell'hypervisor e l'esfiltrazione dei log di 38 clienti");
        assertThat(ricl.conferma()).contains("Riclassificazione registrata");
        assertThat(pagina("operatore", "/incidenti/" + id)).contains("Storico delle riclassificazioni", "TT:MA_RA",
                "da comunicare con il prossimo invio");

        // Conclusione della gestione e relazione finale
        orologio.avanza(Duration.ofDays(10));
        Esito senzaConferma = invia("operatore", "/incidenti/" + id + "/conclusione", "Registra la conclusione senza conferma",
                "conclusaIl", campo(orologio.instant()));
        assertThat(senzaConferma.errore()).contains("una sola volta");
        invia("operatore", "/incidenti/" + id + "/conclusione", "Registra la conclusione della gestione",
                "conclusaIl", campo(orologio.instant()), "conferma", "true");
        long fin = invia("operatore", "/adempimenti/" + adempimento(id, TipoAdempimento.RELAZIONE_FINALE) + "/bozza",
                "Crea la bozza della relazione finale", "incidente", String.valueOf(id)).id();
        assertThat(pagina("operatore", "/documenti/" + fin)).contains("Riclassificazioni", "BC:RO_SY", "TT:MA_RA");
        invia("operatore", "/documenti/" + fin, "Compila e invia la relazione finale",
                "descrizioneSintetica", "Console SOC/MDR irraggiungibile", "referenteNome", "Referente CSIRT (fittizio)",
                "referenteEmail", "csirt@cyberwall.example", "referenteTelefono", "+39 089 84 80 90",
                "serviziImpattati", "SOC_MDR", "attoIllegittimoOMalevolo", "SI", "impattoTransfrontaliero", "NO",
                "utentiImpattati", "9000",
                "descrizioneDettagliata", "Ransomware sull'hypervisor della piattaforma SOC/MDR con esfiltrazione dei log di 38 clienti",
                "minacciaOCausaOriginale", "Credenziali amministrative sottratte con phishing mirato",
                "misureAttenuazione", "Ripristino da backup immutabili, rotazione delle credenziali, MFA sugli accessi amministrativi",
                "voci", "BC:IM_AV", "voci", "BC:IM_DX", "voci", "BC:RO_MA", "voci", "BC:SE_HI", "voci", "TT:AV_DA",
                "voci", "TT:MA_RA", "poi", "invia");
        orologio.avanza(Duration.ofHours(2));
        Esito chiusura = invia("approvatore", "/documenti/" + fin + "/trasmissione", "Registra la trasmissione della finale",
                "riferimento", "ACN-UI-03", "conferma", "true");
        assertThat(chiusura.conferma()).contains("Relazione finale registrata come trasmessa");

        IncidenteDettaglio dopo = incidenti.dettaglio(id);
        assertThat(dopo.stato()).isEqualTo(StatoIncidente.CHIUSO);
        String finale = pagina("approvatore", "/documenti/" + fin);
        assertThat(finale).contains("Sigillo della trasmissione", "Anello precedente", "ACN-UI-03");
        assertThat(pagina("approvatore", "/incidenti/" + id)).contains("Catena integra", "3 documenti trasmessi");

        // Documenti stampabile e strutturato dall'interfaccia
        assertThat(mvc.perform(get("/documenti/" + fin + "/pdf").session(sessione("approvatore")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(mvc.perform(get("/documenti/" + fin + "/json").session(sessione("approvatore")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"riclassificazioni\"", "TT:MA_RA");
        assertThat(pagina("approvatore", "/documenti/" + fin + "/anteprima")).contains("Relazione finale");

        scriviTraccia(dopo);
    }

    @Test
    @DisplayName("Verifica mentre si compila: campi mancanti senza salvare la bozza")
    void verificaProvvisoria() throws Exception {
        Esito reg = invia("operatore", "/incidenti", "Registra", "titolo", "Portale clienti lento",
                "descrizione", "Scenario simulato", "rilevatoIl", campo(orologio.instant().minus(Duration.ofMinutes(5))));
        long id = Long.parseLong(reg.pagina().replaceAll("\\D", ""));
        Esito q = invia("operatore", "/incidenti/" + id + "/qualificazione", "Valuta", "servizioCoinvolto", "WAF_GESTITO",
                "minutiIndisponibilitaTotale", "31", "conoscenzaIl", campo(orologio.instant()),
                "motivazioneConoscenza", "WAF indisponibile da 31 minuti");
        assertThat(q.conferma()).as(String.valueOf(q.errore())).contains("significativo");
        long pre = invia("operatore", "/adempimenti/" + adempimento(id, TipoAdempimento.PRE_NOTIFICA) + "/bozza", "Bozza",
                "incidente", String.valueOf(id)).id();
        String esito = mvc.perform(post("/documenti/" + pre + "/verifica").session(sessione("operatore")).with(csrf())
                        .param("descrizioneSintetica", "").param("voci", "BC:IM_AV"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(esito).contains("\"completo\":false", "descrizioneSintetica", "impattoTransfrontaliero");
        // Nulla è stato salvato: la bozza non ha voci
        assertThat(pagina("operatore", "/documenti/" + pre)).contains("0</span> voci scelte");
    }

    @Test
    @DisplayName("Interfaccia: pagine riservate, CSRF obbligatorio, 404 leggibile")
    void sicurezzaInterfaccia() throws Exception {
        mvc.perform(get("/incidenti")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
        mvc.perform(post("/incidenti").session(sessione("operatore")).param("titolo", "x"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/incidenti/999999").session(sessione("operatore"))).andExpect(status().isNotFound());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(formLogin("/login").user("operatore").password("sbagliata"))
                .andExpect(header().string("Location", "/login?error"));
    }

    private void scriviTraccia(IncidenteDettaglio inc) throws Exception {
        StringBuilder md = new StringBuilder("# Traccia di esecuzione – Scenario completo dall'interfaccia web (milestone M4)\n\n")
                .append("> File GENERATO dal test `ScenarioInterfacciaTest`: ogni riga è un modulo inviato dalle pagine ")
                .append("Thymeleaf con il login a form e il token CSRF, come dal browser; nessuna chiamata alle API REST. ")
                .append("Orologio simulato, database H2 in memoria, orari in Europe/Rome. Per rigenerarlo: ")
                .append("`mvn test -Dtest=ScenarioInterfacciaTest -Dtraccia.aggiorna=true`.\n\n")
                .append("Incidente **").append(inc.codice()).append("**, stato finale **").append(inc.stato()).append("**.\n\n")
                .append("| # | Ora | Utente | Azione | Richiesta | Pagina successiva | Esito mostrato |\n")
                .append("| ---: | --- | --- | --- | --- | --- | --- |\n");
        traccia.forEach(r -> md.append(r).append("\n"));
        md.append("\n## Termini\n\n| Adempimento | Dovuto entro | Trasmesso | Stato |\n| --- | --- | --- | --- |\n");
        inc.scadenze().forEach(s -> md.append("| ").append(s.tipo().getEtichetta()).append(" | ")
                .append(s.dovutaEntro() == null ? "–" : ORA.format(s.dovutaEntro())).append(" | ")
                .append(s.trasmessaIl() == null ? "–" : ORA.format(s.trasmessaIl())).append(" | ")
                .append(s.stato()).append(" |\n"));
        Path target = Path.of("target/scenari/TRACCIA-INTERFACCIA.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, md);
        if (Boolean.getBoolean("traccia.aggiorna")) {
            Files.writeString(Path.of("docs/scenari/TRACCIA-INTERFACCIA.md"), md);
        }
    }
}
