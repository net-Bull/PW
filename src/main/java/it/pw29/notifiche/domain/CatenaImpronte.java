package it.pw29.notifiche.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Catena di hash SHA-256 dei documenti trasmessi di un incidente (ADR-0006).
 * <p>
 * L'impronta di un documento è lo SHA-256 di un JSON canonico (chiavi in ordine alfabetico, UTF-8).
 * Schema corrente {@value #SCHEMA}: contenuto JSON canonico, voci di classificazione ordinate,
 * identificazione dell'adempimento e della versione (senza chiavi surrogate del database),
 * <b>intestazione sigillata</b> (soggetto e incidente al momento dell'invio), <b>autori</b> della versione,
 * metadati di trasmissione e <b>impronta dell'anello precedente</b>. Le versioni sigillate con lo schema
 * precedente ({@value #SCHEMA_V1}) si verificano con il loro algoritmo: lo schema è salvato per riga.
 * <p>
 * Il primo anello di ogni incidente si aggancia a un'impronta di genesi derivata dal codice
 * dell'incidente, così ogni anello ha un precedente non nullo e un indice univoco sul precedente impedisce
 * biforcazioni. La genesi non dipende dallo schema: catene miste v1/v2 restano collegate.
 * <p>
 * Modificare un documento trasmesso (anche aggirando l'applicazione, con SQL diretto) cambia la
 * sua impronta ricalcolata; ricalcolarla e salvarla rompe il collegamento con l'anello successivo.
 */
public final class CatenaImpronte {

    /** Schema corrente dell'input, salvato in ogni versione sigillata. */
    public static final String SCHEMA = "pw29-catena-v2";

    /** Schema delle versioni sigillate prima della migrazione V6 (include l'id della versione). */
    public static final String SCHEMA_V1 = "pw29-catena-v1";

    private static final ObjectMapper CANONICO = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    private CatenaImpronte() {
    }

    /** Impronta di genesi dell'incidente: primo "anello precedente" della sua catena (invariata dallo schema v1). */
    public static String genesi(String codiceIncidente) {
        return sha256(SCHEMA_V1 + "|GENESI|" + codiceIncidente);
    }

    /** Schema con cui è (o sarà) sigillata la versione. */
    public static String schemaDi(VersioneDocumento v) {
        return v.getSchemaImpronta() == null ? SCHEMA : v.getSchemaImpronta();
    }

    /**
     * Input canonico dell'impronta: è anche ciò che viene esposto per la verifica indipendente
     * (chiunque abbia il JSON può ricalcolare lo SHA-256 con uno strumento esterno).
     */
    public static String inputCanonico(VersioneDocumento v, String improntaPrecedente) {
        String schema = schemaDi(v);
        Adempimento a = v.getAdempimento();
        Map<String, Object> m = new TreeMap<>();
        m.put("schema", schema);
        m.put("incidente", a.getIncidente().getCodice());
        m.put("adempimento", a.getTipo().name());
        m.put("progressivo", a.getProgressivo());
        m.put("numero", v.getNumero());
        if (SCHEMA_V1.equals(schema)) {
            m.put("versioneId", v.getId());
        } else {
            m.put("intestazione", v.getIntestazioneJson());
            m.put("autori", List.copyOf(v.getAutori()));
        }
        if (v.getMotivoRettifica() != null) {
            m.put("motivoRettifica", v.getMotivoRettifica());
        }
        m.put("tassonomia", v.getTassonomiaVersione().getCodice());
        List<String> voci = v.getVoci().stream().map(TassonomiaVoce::getCodice).sorted().toList();
        m.put("voci", voci);
        m.put("contenuto", v.getContenutoJson());
        m.put("inviataApprovazioneDa", v.getInviataApprovazioneDa());
        m.put("approvataDa", v.getApprovataDa());
        m.put("trasmessaIl", String.valueOf(v.getTrasmessaIl()));
        m.put("trasmissioneRegistrataIl", String.valueOf(v.getTrasmissioneRegistrataIl()));
        m.put("riferimentoTrasmissione", v.getRiferimentoTrasmissione());
        m.put("improntaPrecedente", improntaPrecedente);
        try {
            return CANONICO.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Input dell'impronta non serializzabile", e);
        }
    }

    /** Impronta della versione agganciata all'anello indicato, con lo schema della versione. */
    public static String impronta(VersioneDocumento v, String improntaPrecedente) {
        return sha256(inputCanonico(v, improntaPrecedente));
    }

    public static String sha256(String testo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(testo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
