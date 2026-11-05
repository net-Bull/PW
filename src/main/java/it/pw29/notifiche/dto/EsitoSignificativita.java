package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.Regola;

import java.math.BigDecimal;
import java.util.List;

/**
 * Esito della valutazione: oltre al sì/no, registra quali regole R1-R7 e quali categorie IS sono
 * scattate e con quale versione delle soglie, così la motivazione della significatività resta tracciabile.
 * L'incidente è significativo se scatta almeno una regola o almeno una categoria (ADR-0004).
 */
public record EsitoSignificativita(
        boolean significativo,
        List<Regola> regoleAttivate,
        List<CategoriaIs> categorieIs,
        String versioneSoglie,
        int sogliaUtenti,
        BigDecimal sogliaEconomica
) {

    /** Esito delle sole regole R1-R7, senza categorie IS. */
    public static EsitoSignificativita soloRegole(List<Regola> regole, String versioneSoglie,
                                                  int sogliaUtenti, BigDecimal sogliaEconomica) {
        return new EsitoSignificativita(!regole.isEmpty(), List.copyOf(regole), List.of(),
                versioneSoglie, sogliaUtenti, sogliaEconomica);
    }

    public EsitoSignificativita conCategorie(List<CategoriaIs> categorie) {
        return new EsitoSignificativita(!regoleAttivate.isEmpty() || !categorie.isEmpty(), regoleAttivate,
                List.copyOf(categorie), versioneSoglie, sogliaUtenti, sogliaEconomica);
    }
}
