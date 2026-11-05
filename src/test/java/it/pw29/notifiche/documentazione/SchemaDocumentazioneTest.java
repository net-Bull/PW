package it.pw29.notifiche.documentazione;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ER diagram e data dictionary <b>generati dal DDL</b> (traccia PW 29, punto 5: le entità dell'ER devono
 * corrispondere a quelle presenti nel DDL). Il test applica le migrazioni Flyway su H2 in modalità PostgreSQL,
 * legge {@code INFORMATION_SCHEMA} (tabelle, colonne, chiavi, vincoli, indici univoci e i commenti
 * {@code COMMENT ON} della migrazione V8) e confronta il risultato con {@code docs/ER.md} e
 * {@code docs/DATA-DICTIONARY.md}: se il DDL cambia e i documenti no, la build fallisce.
 * <p>
 * Rigenerazione: {@code mvn test -Dtest=SchemaDocumentazioneTest -Dschema.aggiorna=true}.
 */
@SpringBootTest
class SchemaDocumentazioneTest {

    private static final Path ER = Path.of("docs", "ER.md");
    private static final Path DIZIONARIO = Path.of("docs", "DATA-DICTIONARY.md");
    private static final String ESCLUSA = "flyway_schema_history";

    @Autowired JdbcTemplate jdbc;

    record Colonna(String nome, String tipo, boolean nullable, String predefinito, String descrizione) {
    }

    record ChiaveEsterna(String nome, String tabella, List<String> colonne, String riferita, List<String> colonneRiferite) {
    }

    record Tabella(String nome, String descrizione, List<Colonna> colonne, List<String> primaria,
                   List<ChiaveEsterna> esterne, List<List<String>> univoche, List<String> controlli) {
        boolean inPrimaria(String c) {
            return primaria.contains(c);
        }

        boolean inEsterna(String c) {
            return esterne.stream().anyMatch(f -> f.colonne().contains(c));
        }

        boolean univoca(List<String> colonne) {
            return univoche.stream().anyMatch(u -> u.equals(colonne)) || primaria.equals(colonne);
        }
    }

    @Test
    @DisplayName("docs/ER.md e docs/DATA-DICTIONARY.md coincidono con il DDL delle migrazioni")
    void documentiAllineatiAlDdl() throws IOException {
        List<Tabella> tabelle = leggiSchema();
        assertThat(tabelle).as("tabelle lette dal DDL").hasSizeGreaterThan(10);
        String er = er(tabelle);
        String dizionario = dizionario(tabelle);
        if (Boolean.getBoolean("schema.aggiorna")) {
            Files.writeString(ER, er, StandardCharsets.UTF_8);
            Files.writeString(DIZIONARIO, dizionario, StandardCharsets.UTF_8);
        }
        assertThat(tabelle).allSatisfy(t -> {
            assertThat(t.descrizione()).as("COMMENT ON TABLE per " + t.nome()).isNotBlank();
            assertThat(t.colonne()).allSatisfy(c ->
                    assertThat(c.descrizione()).as("COMMENT ON COLUMN " + t.nome() + "." + c.nome()).isNotBlank());
        });
        confronta(ER, er);
        confronta(DIZIONARIO, dizionario);
    }

    private static void confronta(Path file, String generato) throws IOException {
        assertThat(Files.exists(file)).as("manca " + file + ": rigenerarlo con -Dschema.aggiorna=true").isTrue();
        assertThat(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as(file + " non coincide con il DDL: rigenerarlo con -Dschema.aggiorna=true").isEqualTo(generato);
    }

    // --- Lettura di INFORMATION_SCHEMA -----------------------------------------------------------

    private List<Tabella> leggiSchema() {
        List<Tabella> tabelle = new ArrayList<>();
        List<Map<String, Object>> righe = jdbc.queryForList("""
                SELECT table_name, remarks FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name""");
        for (Map<String, Object> t : righe) {
            String nome = (String) t.get("table_name");
            if (nome.equals(ESCLUSA)) {
                continue;
            }
            tabelle.add(new Tabella(nome, testo(t.get("remarks")), colonne(nome), primaria(nome), esterne(nome),
                    univoche(nome), controlli(nome)));
        }
        return tabelle;
    }

    private List<Colonna> colonne(String tabella) {
        return jdbc.queryForList("""
                        SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale,
                               is_nullable, column_default, remarks
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position""", tabella)
                .stream().map(c -> new Colonna((String) c.get("column_name"), tipo(c),
                        "YES".equals(c.get("is_nullable")), testo(c.get("column_default")), testo(c.get("remarks"))))
                .toList();
    }

    private static String tipo(Map<String, Object> c) {
        String t = ((String) c.get("data_type")).toUpperCase();
        Object lunghezza = c.get("character_maximum_length");
        return switch (t) {
            case "CHARACTER VARYING" -> "VARCHAR(" + lunghezza + ")";
            case "CHARACTER" -> "CHAR(" + lunghezza + ")";
            case "NUMERIC", "DECIMAL" -> "NUMERIC(" + c.get("numeric_precision") + ", " + c.get("numeric_scale") + ")";
            default -> t;
        };
    }

    private List<String> colonneVincolo(String tabella, String vincolo) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.key_column_usage
                WHERE table_schema = 'public' AND table_name = ? AND constraint_name = ?
                ORDER BY ordinal_position""", String.class, tabella, vincolo);
    }

    private List<String> primaria(String tabella) {
        List<String> nomi = jdbc.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = ? AND constraint_type = 'PRIMARY KEY'""", String.class, tabella);
        return nomi.isEmpty() ? List.of() : colonneVincolo(tabella, nomi.get(0));
    }

    private List<ChiaveEsterna> esterne(String tabella) {
        List<ChiaveEsterna> fk = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT rc.constraint_name, rc.unique_constraint_name, tc.table_name AS riferita
                FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints fc
                  ON fc.constraint_schema = rc.constraint_schema AND fc.constraint_name = rc.constraint_name
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_schema = rc.unique_constraint_schema AND tc.constraint_name = rc.unique_constraint_name
                WHERE fc.table_schema = 'public' AND fc.table_name = ?""", tabella)) {
            String riferita = (String) r.get("riferita");
            fk.add(new ChiaveEsterna((String) r.get("constraint_name"), tabella,
                    colonneVincolo(tabella, (String) r.get("constraint_name")), riferita,
                    colonneVincolo(riferita, (String) r.get("unique_constraint_name"))));
        }
        fk.sort((a, b) -> String.join(",", a.colonne()).compareTo(String.join(",", b.colonne())));
        return fk;
    }

    /** Vincoli UNIQUE e indici univoci (anche parziali su PostgreSQL: qui compaiono come indici). */
    private List<List<String>> univoche(String tabella) {
        Map<String, List<String>> perNome = new TreeMap<>();
        for (String v : jdbc.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = ? AND constraint_type = 'UNIQUE'""", String.class, tabella)) {
            perNome.put(v, colonneVincolo(tabella, v));
        }
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT i.index_name, ic.column_name
                FROM information_schema.indexes i
                JOIN information_schema.index_columns ic
                  ON ic.index_schema = i.index_schema AND ic.index_name = i.index_name
                WHERE i.table_schema = 'public' AND i.table_name = ? AND i.index_type_name = 'UNIQUE INDEX'
                ORDER BY i.index_name, ic.ordinal_position""", tabella)) {
            perNome.computeIfAbsent((String) r.get("index_name"), k -> new ArrayList<>()).add((String) r.get("column_name"));
        }
        return perNome.values().stream().distinct().sorted((a, b) -> String.join(",", a).compareTo(String.join(",", b))).toList();
    }

    private List<String> controlli(String tabella) {
        return jdbc.queryForList("""
                SELECT cc.check_clause FROM information_schema.check_constraints cc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name
                WHERE tc.table_schema = 'public' AND tc.table_name = ? AND tc.constraint_type = 'CHECK'
                ORDER BY cc.check_clause""", String.class, tabella)
                .stream().map(c -> c.replaceAll("\\s+", " ")).toList();
    }

    private static String testo(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    // --- Documenti -------------------------------------------------------------------------------

    private static final String INTESTAZIONE = "> File GENERATO da `SchemaDocumentazioneTest` a partire dal DDL: migrazioni Flyway "
            + "`src/main/resources/db/migration` applicate su H2 in modalità PostgreSQL e lette da `INFORMATION_SCHEMA`, "
            + "commenti compresi (`COMMENT ON`, migrazione V8). La build fallisce se il DDL cambia e questo file no. "
            + "Rigenerazione: `mvn test -Dtest=SchemaDocumentazioneTest -Dschema.aggiorna=true`.\n"
            + "> Escluse la tabella tecnica di Flyway e la migrazione `db/vendor/postgresql` (trigger di immutabilità, "
            + "solo PostgreSQL: non cambia tabelle né colonne).\n\n";

    static String er(List<Tabella> tabelle) {
        StringBuilder md = new StringBuilder("# Diagramma entità-relazioni\n\n").append(INTESTAZIONE)
                .append(tabelle.size()).append(" entità, ")
                .append(tabelle.stream().mapToLong(t -> t.esterne().size()).sum()).append(" relazioni (chiavi esterne).\n\n")
                .append("```mermaid\nerDiagram\n");
        for (Tabella t : tabelle) {
            for (ChiaveEsterna f : t.esterne()) {
                boolean obbligatoria = f.colonne().stream().allMatch(c -> t.colonne().stream()
                        .filter(x -> x.nome().equals(c)).noneMatch(Colonna::nullable));
                String lato = obbligatoria ? "||" : "|o";
                String molti = t.univoca(f.colonne()) ? "o|" : "o{";
                md.append("    ").append(f.riferita()).append(" ").append(lato).append("--").append(molti).append(" ")
                        .append(t.nome()).append(" : \"").append(String.join(", ", f.colonne())).append("\"\n");
            }
        }
        for (Tabella t : tabelle) {
            md.append("    ").append(t.nome()).append(" {\n");
            for (Colonna c : t.colonne()) {
                List<String> chiavi = new ArrayList<>();
                if (t.inPrimaria(c.nome())) chiavi.add("PK");
                if (t.inEsterna(c.nome())) chiavi.add("FK");
                if (t.univoche().stream().anyMatch(u -> u.size() == 1 && u.get(0).equals(c.nome()))) chiavi.add("UK");
                md.append("        ").append(c.tipo().replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_")
                                .replaceAll("_$", ""))
                        .append(" ").append(c.nome())
                        .append(chiavi.isEmpty() ? "" : " " + String.join(", ", chiavi)).append("\n");
            }
            md.append("    }\n");
        }
        md.append("```\n\n## Entità\n\n| Entità | Descrizione |\n| --- | --- |\n");
        tabelle.forEach(t -> md.append("| `").append(t.nome()).append("` | ").append(cella(t.descrizione())).append(" |\n"));
        return md.toString();
    }

    static String dizionario(List<Tabella> tabelle) {
        StringBuilder md = new StringBuilder("# Data dictionary\n\n").append(INTESTAZIONE);
        md.append("Legenda: **PK** chiave primaria, **FK** chiave esterna, **UK** valore univoco; ")
                .append("\"Null\" indica se la colonna ammette valori assenti.\n\n## Indice\n\n");
        tabelle.forEach(t -> md.append("- [`").append(t.nome()).append("`](#").append(t.nome())
                .append(") – ").append(primaFrase(t.descrizione())).append("\n"));
        for (Tabella t : tabelle) {
            md.append("\n## ").append(t.nome()).append("\n\n").append(t.descrizione()).append("\n\n")
                    .append("| Colonna | Tipo | Null | Chiave | Predefinito | Descrizione |\n")
                    .append("| --- | --- | --- | --- | --- | --- |\n");
            for (Colonna c : t.colonne()) {
                List<String> chiavi = new ArrayList<>();
                if (t.inPrimaria(c.nome())) chiavi.add("PK");
                t.esterne().stream().filter(f -> f.colonne().contains(c.nome()))
                        .forEach(f -> chiavi.add("FK → `" + f.riferita() + "." + String.join(", ", f.colonneRiferite()) + "`"));
                if (t.univoche().stream().anyMatch(u -> u.size() == 1 && u.get(0).equals(c.nome()))) chiavi.add("UK");
                md.append("| `").append(c.nome()).append("` | ").append(c.tipo()).append(" | ")
                        .append(c.nullable() ? "sì" : "no").append(" | ").append(String.join(", ", chiavi)).append(" | ")
                        .append(c.predefinito().isEmpty() ? "" : "`" + cella(c.predefinito()) + "`").append(" | ")
                        .append(cella(c.descrizione())).append(" |\n");
            }
            List<List<String>> composte = t.univoche().stream().filter(u -> u.size() > 1).toList();
            if (!composte.isEmpty() || !t.controlli().isEmpty()) {
                md.append("\nVincoli:\n\n");
                composte.forEach(u -> md.append("- univocità di (").append(u.stream().map(x -> "`" + x + "`")
                        .collect(Collectors.joining(", "))).append(")\n"));
                t.controlli().forEach(c -> md.append("- controllo `").append(cella(c)).append("`\n"));
            }
        }
        return md.toString();
    }

    private static String primaFrase(String s) {
        int i = s.indexOf(". ");
        return i < 0 ? s.replaceAll("\\.$", "") : s.substring(0, i);
    }

    private static String cella(String s) {
        return Objects.requireNonNullElse(s, "").replace("|", "\\|").replace("\n", " ");
    }
}
