package it.pw29.notifiche;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Il numero di voci caricate deve coincidere con il documento ufficiale TC-ACN v2.0:
 * 4 macrocategorie, 21 predicati, 19 sotto-predicati (solo Involved Asset), 219 valori.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TassonomiaApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Query di conteggio: 4 / 21 / 19 / 219, come nel documento ACN")
    void conteggioCoincideConDocumentoUfficiale() throws Exception {
        mvc.perform(get("/api/tassonomia/conteggio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versione").value("TC-ACN-2.0"))
                .andExpect(jsonPath("$.versioneDocumento").value("2.0"))
                .andExpect(jsonPath("$.perLivello.MACROCATEGORIA").value(4))
                .andExpect(jsonPath("$.perLivello.PREDICATO").value(21))
                .andExpect(jsonPath("$.perLivello.SOTTOPREDICATO").value(19))
                .andExpect(jsonPath("$.perLivello.VALORE").value(219));
    }

    @Test
    @DisplayName("Ogni valore risale a una macrocategoria; i sotto-predicati esistono solo sotto Involved Asset")
    void strutturaDellAlbero() {
        Integer orfani = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tassonomia_voce v
                LEFT JOIN tassonomia_voce p ON p.id = v.padre_id
                WHERE (v.livello = 'MACROCATEGORIA' AND v.padre_id IS NOT NULL)
                   OR (v.livello = 'PREDICATO' AND p.livello <> 'MACROCATEGORIA')
                   OR (v.livello = 'SOTTOPREDICATO' AND p.codice <> 'AC:IN')
                   OR (v.livello = 'VALORE' AND p.livello NOT IN ('PREDICATO', 'SOTTOPREDICATO'))
                """, Integer.class);
        assertThat(orfani).isZero();

        Integer valoriInvolvedAsset = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tassonomia_voce WHERE livello = 'VALORE' AND codice LIKE 'AC:IN\\_%' ESCAPE '\\'",
                Integer.class);
        assertThat(valoriInvolvedAsset).isEqualTo(98);
    }
}
