package it.pw29.notifiche.controller.web;

import it.pw29.notifiche.domain.Esito3;
import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import it.pw29.notifiche.domain.contenuto.IndicatoreCompromissione;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modulo dell'editor di un documento: i soli campi che l'operatore compila. I campi compilati dal sistema
 * (categorie IS, criteri, riclassificazioni, conclusione, riferimento della richiesta) non sono qui.
 * <p>
 * Il modulo si applica sul contenuto corrente della versione e cambia <b>solo</b> i campi previsti per
 * l'adempimento: gli altri restano com'erano, così il riuso dei dati fra un invio e il successivo non perde
 * informazioni che il documento corrente non mostra (ad esempio la valutazione iniziale in una relazione mensile).
 */
public class ModuloDocumento {

    private String descrizioneSintetica;
    private String referenteNome;
    private String referenteEmail;
    private String referenteTelefono;
    private List<String> serviziImpattati = new ArrayList<>();
    private String altriServizi;
    private String attoIllegittimoOMalevolo;
    private String impattoTransfrontaliero;
    private String valutazioneIniziale;
    private String utentiImpattati;
    private String indicatori;
    private String aggiornamentiSituazione;
    private String descrizioneDettagliata;
    private String minacciaOCausaOriginale;
    private String misureAttenuazione;
    private List<String> voci = new ArrayList<>();

    /** Modulo precompilato dal contenuto salvato; i servizi non presenti fra quelli del soggetto vanno in "altri". */
    public static ModuloDocumento da(ContenutoDocumento c, Collection<String> serviziNoti, Collection<String> voci) {
        ModuloDocumento m = new ModuloDocumento();
        m.descrizioneSintetica = c.getDescrizioneSintetica();
        m.referenteNome = c.getReferenteNome();
        m.referenteEmail = c.getReferenteEmail();
        m.referenteTelefono = c.getReferenteTelefono();
        List<String> altri = new ArrayList<>();
        for (String s : c.getServiziImpattati()) {
            (serviziNoti.contains(s) ? m.serviziImpattati : altri).add(s);
        }
        m.altriServizi = String.join("\n", altri);
        m.attoIllegittimoOMalevolo = c.getAttoIllegittimoOMalevolo() == null ? null : c.getAttoIllegittimoOMalevolo().name();
        m.impattoTransfrontaliero = c.getImpattoTransfrontaliero() == null ? null : c.getImpattoTransfrontaliero().name();
        m.valutazioneIniziale = c.getValutazioneIniziale();
        m.utentiImpattati = c.getUtentiImpattati() == null ? null : String.valueOf(c.getUtentiImpattati());
        m.indicatori = String.join("\n", c.getIndicatoriCompromissione().stream()
                .map(i -> i.tipo() + " | " + i.valore() + (i.nota() == null || i.nota().isBlank() ? "" : " | " + i.nota()))
                .toList());
        m.aggiornamentiSituazione = c.getAggiornamentiSituazione();
        m.descrizioneDettagliata = c.getDescrizioneDettagliata();
        m.minacciaOCausaOriginale = c.getMinacciaOCausaOriginale();
        m.misureAttenuazione = c.getMisureAttenuazione();
        m.voci = new ArrayList<>(voci);
        return m;
    }

    /**
     * Scrive nel contenuto i campi del modulo che compaiono in {@code previsti}.
     *
     * @throws ModuloNonValidoException per un numero o un indicatore scritti male
     */
    public ContenutoDocumento applica(ContenutoDocumento c, Set<String> previsti) {
        if (previsti.contains("descrizioneSintetica")) c.setDescrizioneSintetica(testo(descrizioneSintetica));
        if (previsti.contains("referenteNome")) c.setReferenteNome(testo(referenteNome));
        if (previsti.contains("referenteEmail")) c.setReferenteEmail(testo(referenteEmail));
        if (previsti.contains("referenteTelefono")) c.setReferenteTelefono(testo(referenteTelefono));
        if (previsti.contains("serviziImpattati")) {
            Set<String> servizi = new LinkedHashSet<>(serviziImpattati == null ? List.of() : serviziImpattati);
            servizi.addAll(righe(altriServizi));
            c.setServiziImpattati(new ArrayList<>(servizi));
        }
        if (previsti.contains("attoIllegittimoOMalevolo")) c.setAttoIllegittimoOMalevolo(esito(attoIllegittimoOMalevolo));
        if (previsti.contains("impattoTransfrontaliero")) c.setImpattoTransfrontaliero(esito(impattoTransfrontaliero));
        if (previsti.contains("valutazioneIniziale")) c.setValutazioneIniziale(testo(valutazioneIniziale));
        if (previsti.contains("utentiImpattati")) c.setUtentiImpattati(intero(utentiImpattati));
        if (previsti.contains("indicatoriCompromissione")) c.setIndicatoriCompromissione(indicatori(indicatori));
        if (previsti.contains("aggiornamentiSituazione")) c.setAggiornamentiSituazione(testo(aggiornamentiSituazione));
        if (previsti.contains("descrizioneDettagliata")) c.setDescrizioneDettagliata(testo(descrizioneDettagliata));
        if (previsti.contains("minacciaOCausaOriginale")) c.setMinacciaOCausaOriginale(testo(minacciaOCausaOriginale));
        if (previsti.contains("misureAttenuazione")) c.setMisureAttenuazione(testo(misureAttenuazione));
        return c;
    }

    private static String testo(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    private static List<String> righe(String v) {
        if (v == null) {
            return List.of();
        }
        return v.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static Esito3 esito(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Esito3.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new ModuloNonValidoException("Risposta non ammessa: " + v);
        }
    }

    private static Integer intero(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            int n = Integer.parseInt(v.strip().replace(".", ""));
            if (n < 0) {
                throw new ModuloNonValidoException("Gli utenti impattati non possono essere negativi");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ModuloNonValidoException("Utenti impattati: numero non valido (" + v + ")");
        }
    }

    /** Una riga per indicatore: "tipo | valore" oppure "tipo | valore | nota". */
    private static List<IndicatoreCompromissione> indicatori(String v) {
        List<IndicatoreCompromissione> l = new ArrayList<>();
        int n = 0;
        for (String riga : righe(v)) {
            n++;
            String[] parti = riga.split("\\|", 3);
            if (parti.length < 2 || parti[0].isBlank() || parti[1].isBlank()) {
                throw new ModuloNonValidoException("Indicatore alla riga " + n + ": scrivere \"tipo | valore\" "
                        + "oppure \"tipo | valore | nota\"");
            }
            l.add(new IndicatoreCompromissione(parti[0].strip(), parti[1].strip(), parti.length > 2 ? testo(parti[2]) : null));
        }
        return l;
    }

    public String getDescrizioneSintetica() { return descrizioneSintetica; }
    public void setDescrizioneSintetica(String v) { this.descrizioneSintetica = v; }
    public String getReferenteNome() { return referenteNome; }
    public void setReferenteNome(String v) { this.referenteNome = v; }
    public String getReferenteEmail() { return referenteEmail; }
    public void setReferenteEmail(String v) { this.referenteEmail = v; }
    public String getReferenteTelefono() { return referenteTelefono; }
    public void setReferenteTelefono(String v) { this.referenteTelefono = v; }
    public List<String> getServiziImpattati() { return serviziImpattati; }
    public void setServiziImpattati(List<String> v) { this.serviziImpattati = v == null ? new ArrayList<>() : v; }
    public String getAltriServizi() { return altriServizi; }
    public void setAltriServizi(String v) { this.altriServizi = v; }
    public String getAttoIllegittimoOMalevolo() { return attoIllegittimoOMalevolo; }
    public void setAttoIllegittimoOMalevolo(String v) { this.attoIllegittimoOMalevolo = v; }
    public String getImpattoTransfrontaliero() { return impattoTransfrontaliero; }
    public void setImpattoTransfrontaliero(String v) { this.impattoTransfrontaliero = v; }
    public String getValutazioneIniziale() { return valutazioneIniziale; }
    public void setValutazioneIniziale(String v) { this.valutazioneIniziale = v; }
    public String getUtentiImpattati() { return utentiImpattati; }
    public void setUtentiImpattati(String v) { this.utentiImpattati = v; }
    public String getIndicatori() { return indicatori; }
    public void setIndicatori(String v) { this.indicatori = v; }
    public String getAggiornamentiSituazione() { return aggiornamentiSituazione; }
    public void setAggiornamentiSituazione(String v) { this.aggiornamentiSituazione = v; }
    public String getDescrizioneDettagliata() { return descrizioneDettagliata; }
    public void setDescrizioneDettagliata(String v) { this.descrizioneDettagliata = v; }
    public String getMinacciaOCausaOriginale() { return minacciaOCausaOriginale; }
    public void setMinacciaOCausaOriginale(String v) { this.minacciaOCausaOriginale = v; }
    public String getMisureAttenuazione() { return misureAttenuazione; }
    public void setMisureAttenuazione(String v) { this.misureAttenuazione = v; }
    public List<String> getVoci() { return voci; }
    public void setVoci(List<String> v) { this.voci = v == null ? new ArrayList<>() : v; }
}
