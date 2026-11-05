package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.Esito3;
import it.pw29.notifiche.domain.LivelloTassonomia;
import it.pw29.notifiche.domain.TassonomiaVoce;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import it.pw29.notifiche.dto.EsitoVerifica;
import it.pw29.notifiche.dto.RigaMatrice;
import it.pw29.notifiche.dto.Violazione;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static it.pw29.notifiche.domain.TipoAdempimento.NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.PRE_NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_FINALE;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_INTERMEDIA;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_MENSILE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validazione differenziata per adempimento: per ciascuno dei cinque adempimenti un documento
 * vuoto è rifiutato con l'elenco esatto dei campi obbligatori, e un documento completo è accettato.
 */
class MatriceCampiTest {

    private final MatriceCampi matrice = new MatriceCampi();

    private static final List<String> COMUNI = List.of("descrizioneSintetica", "referenteNome", "referenteEmail",
            "referenteTelefono");

    private static final Map<TipoAdempimento, List<String>> OBBLIGATORI = Map.of(
            PRE_NOTIFICA, concat(COMUNI, "serviziImpattati", "attoIllegittimoOMalevolo",
                    "impattoTransfrontaliero", "classificazione"),
            NOTIFICA, concat(COMUNI, "serviziImpattati", "attoIllegittimoOMalevolo",
                    "impattoTransfrontaliero", "valutazioneIniziale", "classificazione", "classificazione"),
            RELAZIONE_INTERMEDIA, concat(COMUNI, "riferimentoRichiestaCsirt", "aggiornamentiSituazione"),
            RELAZIONE_MENSILE, concat(COMUNI, "aggiornamentiSituazione", "misureAttenuazione"),
            RELAZIONE_FINALE, concat(COMUNI, "serviziImpattati", "impattoTransfrontaliero",
                    "descrizioneDettagliata", "minacciaOCausaOriginale", "misureAttenuazione",
                    "conclusioneGestioneIl", "classificazione", "classificazione", "classificazione"));

    private static List<String> concat(List<String> base, String... altri) {
        return java.util.stream.Stream.concat(base.stream(), java.util.Arrays.stream(altri)).toList();
    }

    static TassonomiaVoce voce(String codice) {
        TassonomiaVoce v = BeanUtils.instantiateClass(TassonomiaVoce.class);
        ReflectionTestUtils.setField(v, "codice", codice);
        ReflectionTestUtils.setField(v, "livello", LivelloTassonomia.VALORE);
        return v;
    }

    static ContenutoDocumento completo() {
        ContenutoDocumento c = new ContenutoDocumento();
        c.setDescrizioneSintetica("Indisponibilità della console SOC/MDR");
        c.setReferenteNome("Referente CSIRT");
        c.setReferenteEmail("csirt@cyberwall.example");
        c.setReferenteTelefono("+39 089 84 80 90");
        c.setCategorieIs(List.of(CategoriaIs.IS_3));
        c.setServiziImpattati(List.of("SOC_MDR"));
        c.setAttoIllegittimoOMalevolo(Esito3.NON_NOTO);
        c.setImpattoTransfrontaliero(Esito3.NON_NOTO);
        c.setValutazioneIniziale("Gravità media, 140 clienti senza monitoraggio per 45 minuti");
        c.setRiferimentoRichiestaCsirt("CSIRT-REQ-1");
        c.setAggiornamentiSituazione("Contenimento completato");
        c.setDescrizioneDettagliata("Ricostruzione completa");
        c.setMinacciaOCausaOriginale("Ransomware su hypervisor");
        c.setMisureAttenuazione("Ripristino da backup immutabili");
        c.setConclusioneGestioneIl(Instant.parse("2026-11-20T12:00:00Z"));
        return c;
    }

    @ParameterizedTest(name = "{0}: documento vuoto rifiutato con i campi obbligatori dell'adempimento")
    @EnumSource(TipoAdempimento.class)
    void documentoVuotoRifiutato(TipoAdempimento tipo) {
        EsitoVerifica esito = matrice.verifica(tipo, new ContenutoDocumento(), List.of());
        assertThat(esito.completo()).isFalse();
        assertThat(esito.violazioni()).extracting(Violazione::campo).containsExactlyElementsOf(OBBLIGATORI.get(tipo));
        assertThat(esito.violazioni()).allSatisfy(v -> assertThat(v.fonte()).isNotBlank());
    }

    @ParameterizedTest(name = "{0}: documento completo e classificato accettato")
    @EnumSource(TipoAdempimento.class)
    void documentoCompletoAccettato(TipoAdempimento tipo) {
        EsitoVerifica esito = matrice.verifica(tipo, completo(),
                List.of(voce("BC:IM_AV"), voce("BC:SE_ME"), voce("TT:MA_RA")));
        assertThat(esito.violazioni()).isEmpty();
        assertThat(esito.completo()).isTrue();
    }

    @Test
    @DisplayName("NON_NOTO è una risposta valida per le informazioni richieste \"ove possibile\"")
    void nonNotoAmmesso() {
        assertThat(matrice.verifica(PRE_NOTIFICA, completo(), List.of(voce("BC:IM_AV"))).completo()).isTrue();
    }

    @Test
    @DisplayName("Notifica: due valori di Severity sono rifiutati; finale senza minaccia né causa rifiutata")
    void vincoliClassificazione() {
        assertThat(matrice.verifica(NOTIFICA, completo(), List.of(voce("BC:SE_ME"), voce("BC:SE_HI"))).violazioni())
                .extracting(Violazione::etichetta).containsExactly("Esattamente un valore del predicato Severity (BC:SE)");
        assertThat(matrice.verifica(RELAZIONE_FINALE, completo(), List.of(voce("BC:SE_ME"), voce("BC:IM_AV"))).violazioni())
                .extracting(Violazione::etichetta)
                .containsExactly("Almeno un valore di Threat Type (TT) o di Root Cause (BC:RO)");
        assertThat(matrice.verifica(RELAZIONE_FINALE, completo(), List.of(voce("BC:SE_ME"), voce("BC:RO_SY"))).completo())
                .isTrue();
    }

    @Test
    @DisplayName("Formato e-mail del referente verificato")
    void email() {
        ContenutoDocumento c = completo();
        c.setReferenteEmail("non-una-email");
        assertThat(matrice.verifica(RELAZIONE_MENSILE, c, List.of()).violazioni())
                .extracting(Violazione::campo).containsExactly("referenteEmail");
    }

    @Test
    @DisplayName("La matrice ha una riga per ogni campo annotato, nell'ordine dichiarato")
    void righe() {
        List<RigaMatrice> righe = matrice.righe();
        assertThat(righe).hasSize(19);
        assertThat(righe.get(0).campo()).isEqualTo("descrizioneSintetica");
        assertThat(righe).allSatisfy(r -> assertThat(r.requisiti()).hasSize(TipoAdempimento.values().length));
        assertThat(matrice.campiPrevisti(RELAZIONE_INTERMEDIA)).doesNotContain("descrizioneDettagliata");
    }
}
