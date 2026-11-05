package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.TassonomiaVoce;
import it.pw29.notifiche.domain.TipoAdempimento;
import it.pw29.notifiche.domain.contenuto.Campo;
import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import it.pw29.notifiche.domain.contenuto.VincoloClassificazione;
import it.pw29.notifiche.dto.EsitoVerifica;
import it.pw29.notifiche.dto.RigaMatrice;
import it.pw29.notifiche.dto.RigaMatrice.Requisito;
import it.pw29.notifiche.dto.Violazione;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registro dei campi dei documenti, costruito per riflessione dalle annotazioni {@link Campo}
 * di {@link ContenutoDocumento} e dai {@link VincoloClassificazione}. Da questo unico registro
 * derivano sia la validazione lato server differenziata per adempimento sia la matrice
 * campo/adempimento pubblicata: le due non possono divergere (ADR-0005).
 */
@Component
public class MatriceCampi {

    private record Voce(Field campo, Campo meta) {
    }

    private final List<Voce> voci;

    public MatriceCampi() {
        List<Voce> elenco = new ArrayList<>();
        for (Field f : ContenutoDocumento.class.getDeclaredFields()) {
            Campo c = f.getAnnotation(Campo.class);
            if (c != null) {
                f.setAccessible(true);
                elenco.add(new Voce(f, c));
            }
        }
        elenco.sort(Comparator.comparingInt(v -> v.meta().ordine()));
        this.voci = List.copyOf(elenco);
    }

    /** Requisito di un campo per un adempimento. */
    static Requisito requisito(Campo c, TipoAdempimento tipo) {
        if (Arrays.asList(c.obbligatorioIn()).contains(tipo)) {
            return Requisito.OBBLIGATORIO;
        }
        if (Arrays.asList(c.facoltativoIn()).contains(tipo)) {
            return Requisito.FACOLTATIVO;
        }
        return Requisito.NON_PREVISTO;
    }

    public List<RigaMatrice> righe() {
        List<RigaMatrice> righe = new ArrayList<>();
        for (Voce v : voci) {
            Map<TipoAdempimento, Requisito> req = new EnumMap<>(TipoAdempimento.class);
            for (TipoAdempimento t : TipoAdempimento.values()) {
                req.put(t, requisito(v.meta(), t));
            }
            righe.add(new RigaMatrice(v.campo().getName(), v.meta().etichetta(), req, v.meta().fonte()));
        }
        return righe;
    }

    /** Campi previsti (obbligatori o facoltativi) per un adempimento, nell'ordine di presentazione. */
    public List<String> campiPrevisti(TipoAdempimento tipo) {
        return voci.stream()
                .filter(v -> requisito(v.meta(), tipo) != Requisito.NON_PREVISTO)
                .map(v -> v.campo().getName())
                .toList();
    }

    /** Campo di un modulo di compilazione: metadati della matrice per l'adempimento indicato. */
    public record CampoModulo(String nome, String etichetta, Requisito requisito, String fonte, boolean compilatoDalSistema) {
        public boolean obbligatorio() {
            return requisito == Requisito.OBBLIGATORIO;
        }
    }

    /** Campi previsti per l'adempimento, nell'ordine della matrice: guidano l'editor dell'interfaccia. */
    public List<CampoModulo> campiModulo(TipoAdempimento tipo) {
        return voci.stream()
                .filter(v -> requisito(v.meta(), tipo) != Requisito.NON_PREVISTO)
                .map(v -> new CampoModulo(v.campo().getName(), v.meta().etichetta(), requisito(v.meta(), tipo),
                        v.meta().fonte(), v.meta().compilatoDalSistema()))
                .toList();
    }

    /** Nomi dei campi compilati dal sistema. */
    public List<String> campiDiSistema() {
        return voci.stream().filter(v -> v.meta().compilatoDalSistema()).map(v -> v.campo().getName()).toList();
    }

    /** Valore di un campo previsto per un adempimento, con i metadati della matrice. */
    public record ValoreCampo(String nome, String etichetta, Requisito requisito, Object valore) {
        public boolean vuoto() {
            return MatriceCampi.vuoto(valore);
        }
    }

    /**
     * Campi previsti (obbligatori o facoltativi) per l'adempimento con i loro valori, nell'ordine della
     * matrice. È la selezione usata dai documenti esportati: JSON e PDF contengono solo questi campi.
     */
    public List<ValoreCampo> valoriPrevisti(TipoAdempimento tipo, ContenutoDocumento contenuto) {
        List<ValoreCampo> valori = new ArrayList<>();
        for (Voce v : voci) {
            Requisito r = requisito(v.meta(), tipo);
            if (r != Requisito.NON_PREVISTO) {
                valori.add(new ValoreCampo(v.campo().getName(), v.meta().etichetta(), r, leggi(v.campo(), contenuto)));
            }
        }
        return valori;
    }

    /** Verifica di completezza di contenuto e classificazione per l'adempimento indicato. */
    public EsitoVerifica verifica(TipoAdempimento tipo, ContenutoDocumento contenuto, Collection<TassonomiaVoce> classificazione) {
        List<Violazione> violazioni = new ArrayList<>();
        for (Voce v : voci) {
            if (requisito(v.meta(), tipo) == Requisito.OBBLIGATORIO && vuoto(leggi(v.campo(), contenuto))) {
                violazioni.add(new Violazione(v.campo().getName(), v.meta().etichetta(),
                        "Campo obbligatorio per " + tipo.getEtichetta(), v.meta().fonte()));
            }
        }
        for (VincoloClassificazione vc : VincoloClassificazione.values()) {
            if (vc.siApplicaA(tipo) && !vc.soddisfatto(classificazione)) {
                violazioni.add(new Violazione("classificazione", vc.getDescrizione(),
                        "Vincolo di classificazione non rispettato per " + tipo.getEtichetta(), vc.getFonte()));
            }
        }
        String email = contenuto.getReferenteEmail();
        if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            violazioni.add(new Violazione("referenteEmail", "Referente CSIRT - e-mail",
                    "Indirizzo e-mail non valido", "Controllo di formato"));
        }
        return EsitoVerifica.di(tipo, violazioni);
    }

    private static Object leggi(Field f, ContenutoDocumento c) {
        try {
            return f.get(c);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean vuoto(Object valore) {
        return valore == null
                || (valore instanceof String s && s.isBlank())
                || (valore instanceof Collection<?> col && col.isEmpty());
    }

    /** Matrice in Markdown, pubblicata in docs/MATRICE-CAMPI.md e verificata da un test. */
    public String markdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Matrice campo/adempimento\n\n")
                .append("> File GENERATO dal codice (`MatriceCampi.markdown()`), non modificare a mano.\n")
                .append("> Fonte unica: annotazioni `@Campo` di `ContenutoDocumento` ed enum `VincoloClassificazione`.\n")
                .append("> Il test `MatriceCampiDocumentazioneTest` fallisce se questo file non coincide con il codice;\n")
                .append("> per rigenerarlo: `mvn test -Dtest=MatriceCampiDocumentazioneTest -Dmatrice.aggiorna=true`.\n\n")
                .append("Legenda: **O** obbligatorio, F facoltativo, – non previsto. ")
                .append("Riferimenti: art. 25, c. 5, D.Lgs. 138/2024 (\"art. 25.5\").\n\n")
                .append("## Campi del documento\n\n")
                .append("| Campo | ");
        for (TipoAdempimento t : TipoAdempimento.values()) {
            sb.append(t.getEtichetta()).append(" | ");
        }
        sb.append("Fonte |\n| --- |");
        sb.append(" :---: |".repeat(TipoAdempimento.values().length)).append(" --- |\n");
        for (RigaMatrice r : righe()) {
            sb.append("| ").append(r.etichetta()).append(" (`").append(r.campo()).append("`) | ");
            for (TipoAdempimento t : TipoAdempimento.values()) {
                sb.append(switch (r.requisiti().get(t)) {
                    case OBBLIGATORIO -> "**O**";
                    case FACOLTATIVO -> "F";
                    case NON_PREVISTO -> "–";
                }).append(" | ");
            }
            sb.append(r.fonte().replace("|", "\\|")).append(" |\n");
        }
        sb.append("\n## Vincoli sulla classificazione (Tassonomia Cyber ACN)\n\n")
                .append("Valgono sempre: solo voci di livello VALORE; tutte della versione della tassonomia del documento.\n\n")
                .append("| Vincolo | ");
        for (TipoAdempimento t : TipoAdempimento.values()) {
            sb.append(t.getEtichetta()).append(" | ");
        }
        sb.append("Fonte |\n| --- |");
        sb.append(" :---: |".repeat(TipoAdempimento.values().length)).append(" --- |\n");
        for (VincoloClassificazione vc : VincoloClassificazione.values()) {
            sb.append("| ").append(vc.getDescrizione()).append(" | ");
            for (TipoAdempimento t : TipoAdempimento.values()) {
                sb.append(vc.siApplicaA(t) ? "**O**" : "–").append(" | ");
            }
            sb.append(vc.getFonte()).append(" |\n");
        }
        return sb.toString();
    }
}
