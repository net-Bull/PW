package it.pw29.notifiche.controller.web;

import it.pw29.notifiche.dto.ValutazioneRequest;

import java.math.BigDecimal;

/**
 * Modulo delle grandezze di una valutazione di significatività. Le caselle non spuntate arrivano assenti dal
 * browser, quindi i booleani partono da {@code false}; i numeri vuoti valgono zero.
 */
public class ModuloValutazione {

    private String servizioCoinvolto;
    private Integer minutiIndisponibilitaTotale = 0;
    private Integer minutiDegrado = 0;
    private Integer utentiImpattati = 0;
    private boolean perditaRiservatezza;
    private boolean perditaIntegrita;
    private boolean perditaAutenticita;
    private boolean impattoEsterno;
    private boolean sospettoDoloso;
    private boolean accessoNonAutorizzato;
    private boolean gravePerturbazione;
    private BigDecimal perditaDiretta = BigDecimal.ZERO;
    private Integer incidentiStessaCausaInFinestra = 0;
    private BigDecimal perditeCumulateInFinestra = BigDecimal.ZERO;
    private boolean esfiltrazioneSegretiCommerciali;
    private boolean interruzioneProgrammata;

    public ValutazioneRequest richiesta() {
        if (servizioCoinvolto == null || servizioCoinvolto.isBlank()) {
            throw new ModuloNonValidoException("Indicare il servizio coinvolto");
        }
        return new ValutazioneRequest(servizioCoinvolto, zero(minutiIndisponibilitaTotale), zero(minutiDegrado),
                zero(utentiImpattati), perditaRiservatezza, perditaIntegrita, perditaAutenticita, impattoEsterno,
                sospettoDoloso, accessoNonAutorizzato, gravePerturbazione, zero(perditaDiretta),
                zero(incidentiStessaCausaInFinestra), zero(perditeCumulateInFinestra),
                esfiltrazioneSegretiCommerciali, interruzioneProgrammata);
    }

    private static int zero(Integer v) {
        if (v != null && v < 0) {
            throw new ModuloNonValidoException("I valori numerici non possono essere negativi");
        }
        return v == null ? 0 : v;
    }

    private static BigDecimal zero(BigDecimal v) {
        if (v != null && v.signum() < 0) {
            throw new ModuloNonValidoException("Gli importi non possono essere negativi");
        }
        return v == null ? BigDecimal.ZERO : v;
    }

    public String getServizioCoinvolto() { return servizioCoinvolto; }
    public void setServizioCoinvolto(String v) { this.servizioCoinvolto = v; }
    public Integer getMinutiIndisponibilitaTotale() { return minutiIndisponibilitaTotale; }
    public void setMinutiIndisponibilitaTotale(Integer v) { this.minutiIndisponibilitaTotale = v; }
    public Integer getMinutiDegrado() { return minutiDegrado; }
    public void setMinutiDegrado(Integer v) { this.minutiDegrado = v; }
    public Integer getUtentiImpattati() { return utentiImpattati; }
    public void setUtentiImpattati(Integer v) { this.utentiImpattati = v; }
    public boolean isPerditaRiservatezza() { return perditaRiservatezza; }
    public void setPerditaRiservatezza(boolean v) { this.perditaRiservatezza = v; }
    public boolean isPerditaIntegrita() { return perditaIntegrita; }
    public void setPerditaIntegrita(boolean v) { this.perditaIntegrita = v; }
    public boolean isPerditaAutenticita() { return perditaAutenticita; }
    public void setPerditaAutenticita(boolean v) { this.perditaAutenticita = v; }
    public boolean isImpattoEsterno() { return impattoEsterno; }
    public void setImpattoEsterno(boolean v) { this.impattoEsterno = v; }
    public boolean isSospettoDoloso() { return sospettoDoloso; }
    public void setSospettoDoloso(boolean v) { this.sospettoDoloso = v; }
    public boolean isAccessoNonAutorizzato() { return accessoNonAutorizzato; }
    public void setAccessoNonAutorizzato(boolean v) { this.accessoNonAutorizzato = v; }
    public boolean isGravePerturbazione() { return gravePerturbazione; }
    public void setGravePerturbazione(boolean v) { this.gravePerturbazione = v; }
    public BigDecimal getPerditaDiretta() { return perditaDiretta; }
    public void setPerditaDiretta(BigDecimal v) { this.perditaDiretta = v; }
    public Integer getIncidentiStessaCausaInFinestra() { return incidentiStessaCausaInFinestra; }
    public void setIncidentiStessaCausaInFinestra(Integer v) { this.incidentiStessaCausaInFinestra = v; }
    public BigDecimal getPerditeCumulateInFinestra() { return perditeCumulateInFinestra; }
    public void setPerditeCumulateInFinestra(BigDecimal v) { this.perditeCumulateInFinestra = v; }
    public boolean isEsfiltrazioneSegretiCommerciali() { return esfiltrazioneSegretiCommerciali; }
    public void setEsfiltrazioneSegretiCommerciali(boolean v) { this.esfiltrazioneSegretiCommerciali = v; }
    public boolean isInterruzioneProgrammata() { return interruzioneProgrammata; }
    public void setInterruzioneProgrammata(boolean v) { this.interruzioneProgrammata = v; }
}
