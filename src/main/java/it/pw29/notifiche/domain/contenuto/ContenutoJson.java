package it.pw29.notifiche.domain.contenuto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serializzazione del contenuto in JSON canonico: proprietà in ordine alfabetico, campi nulli
 * omessi, date ISO-8601. Lo stesso contenuto produce sempre gli stessi byte, requisito per la
 * catena di hash SHA-256 della Fase 3.
 */
public final class ContenutoJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private ContenutoJson() {
    }

    public static String scrivi(ContenutoDocumento contenuto) {
        try {
            return MAPPER.writeValueAsString(contenuto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Contenuto non serializzabile", e);
        }
    }

    /** Serializzazione canonica di un oggetto qualsiasi (es. l'intestazione sigillata). */
    public static String scrivi(Object valore) {
        try {
            return MAPPER.writeValueAsString(valore);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Valore non serializzabile", e);
        }
    }

    public static <T> T leggi(String json, Class<T> tipo) {
        try {
            return MAPPER.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON non valido per " + tipo.getSimpleName(), e);
        }
    }

    public static ContenutoDocumento leggi(String json) {
        try {
            return MAPPER.readValue(json, ContenutoDocumento.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Contenuto JSON non valido", e);
        }
    }
}
