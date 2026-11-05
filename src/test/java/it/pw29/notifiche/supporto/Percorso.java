package it.pw29.notifiche.supporto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.pw29.notifiche.dto.NuovoIncidenteRequest;
import it.pw29.notifiche.dto.QualificazioneRequest;
import it.pw29.notifiche.dto.TrasmissioneRequest;
import it.pw29.notifiche.dto.ValutazioneRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Passi ricorrenti degli scenari, eseguiti attraverso le API come farebbe l'interfaccia:
 * registrazione e qualificazione, bozza, compilazione, approvazione e trasmissione con quattro occhi.
 */
public class Percorso {

    /** Voci della prima ipotesi sull'incidente "console SOC irraggiungibile": guasto, non attacco. */
    public static final List<String> VOCI_PRE_NOTIFICA = List.of("BC:IM_AV", "BC:RO_SY", "TT:AV_OU");
    public static final List<String> VOCI_NOTIFICA = List.of("BC:IM_AV", "BC:RO_SY", "TT:AV_OU", "BC:SE_ME");

    /** Valutazione iniziale della notifica, coerente con il contesto: 140 clienti, 9.000 utenti del SOC/MDR. */
    public static final String VALUTAZIONE_NOTIFICA = "Gravità media: console SOC/MDR non disponibile per 45 minuti, monitoraggio sospeso per tutti i 140 clienti (9.000 utenti del servizio)";

    private final ClientApi api;
    private final ObjectMapper json;
    private final OrologioRegolabile orologio;

    public Percorso(ClientApi api, OrologioRegolabile orologio) {
        this.api = api;
        this.json = api.json();
        this.orologio = orologio;
    }

    public long registra(String titolo, Instant rilevato) throws Exception {
        return api.post("operatore", "/api/incidenti",
                new NuovoIncidenteRequest(titolo, "Scenario simulato, nessun dato reale", rilevato, null), 201)
                .get("id").asLong();
    }

    public JsonNode qualifica(long id, ValutazioneRequest v, Instant conoscenza, String motivazione) throws Exception {
        return api.post("operatore", "/api/incidenti/" + id + "/qualificazione",
                new QualificazioneRequest(v, conoscenza, motivazione), 200);
    }

    public JsonNode incidente(long id) throws Exception {
        return api.get("operatore", "/api/incidenti/" + id, 200);
    }

    public long adempimento(long incidenteId, String tipo) throws Exception {
        for (JsonNode a : incidente(incidenteId).get("adempimenti")) {
            if (a.get("tipo").asText().equals(tipo)) {
                return a.get("id").asLong();
            }
        }
        throw new AssertionError("Adempimento " + tipo + " assente");
    }

    public JsonNode bozza(long adempimentoId) throws Exception {
        return api.post("operatore", "/api/adempimenti/" + adempimentoId + "/bozza", null, 201);
    }

    public JsonNode documento(long versione) throws Exception {
        return api.get("operatore", "/api/documenti/" + versione, 200);
    }

    /** Sostituisce i campi indicati nel contenuto corrente e aggiorna la bozza con le voci date. */
    public JsonNode compila(long versione, Map<String, Object> campi, List<String> voci) throws Exception {
        ObjectNode contenuto = (ObjectNode) documento(versione).get("contenuto");
        campi.forEach((k, v) -> contenuto.set(k, json.valueToTree(v)));
        return api.put("operatore", "/api/documenti/" + versione, Map.of("contenuto", contenuto, "voci", voci), 200);
    }

    /** Invio in approvazione (operatore) e trasmissione (approvatore), un'ora dopo. */
    public JsonNode approvaETrasmetti(long versione, String riferimento) throws Exception {
        api.post("operatore", "/api/documenti/" + versione + "/invio-approvazione", null, 200);
        orologio.avanza(Duration.ofHours(1));
        return api.post("approvatore", "/api/documenti/" + versione + "/trasmissione",
                new TrasmissioneRequest(riferimento, null), 200);
    }

    /**
     * Incidente del contesto: console del SOC/MDR irraggiungibile per 45 minuti (R1, IS-3), qualificato
     * significativo, con pre-notifica e notifica trasmesse. Restituisce gli identificativi utili.
     */
    public Ids incidenteConNotifica(String titolo, String suffissoRif) throws Exception {
        Instant rilevato = orologio.instant().minus(Duration.ofMinutes(50));
        long id = registra(titolo, rilevato);
        qualifica(id, Incidenti.neutro().indisponibilita(45).build(), orologio.instant().minus(Duration.ofMinutes(10)),
                "Console SOC irraggiungibile da 45 minuti, confermato dal monitoraggio di disponibilità");
        long pre = bozza(adempimento(id, "PRE_NOTIFICA")).get("id").asLong();
        compila(pre, Map.of("impattoTransfrontaliero", "NON_NOTO"), VOCI_PRE_NOTIFICA);
        approvaETrasmetti(pre, "ACN-" + suffissoRif + "-01");
        long not = bozza(adempimento(id, "NOTIFICA")).get("id").asLong();
        compila(not, Map.of("valutazioneIniziale", VALUTAZIONE_NOTIFICA, "utentiImpattati", 9000), VOCI_NOTIFICA);
        approvaETrasmetti(not, "ACN-" + suffissoRif + "-02");
        return new Ids(id, pre, not);
    }

    /** Registra la conclusione della gestione all'istante corrente dell'orologio. */
    public void conclusione(long incidenteId) throws Exception {
        api.post("operatore", "/api/incidenti/" + incidenteId + "/conclusione",
                new it.pw29.notifiche.dto.ConclusioneRequest(orologio.instant()), 200);
    }

    public record Ids(long incidente, long preNotifica, long notifica) {
    }
}
