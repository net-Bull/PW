package it.pw29.notifiche.domain.contenuto;

import it.pw29.notifiche.domain.CategoriaIs;
import it.pw29.notifiche.domain.Esito3;
import it.pw29.notifiche.domain.Regola;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static it.pw29.notifiche.domain.TipoAdempimento.NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.PRE_NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_FINALE;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_INTERMEDIA;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_MENSILE;

/**
 * Contenuto informativo comune ai cinque adempimenti. Un solo modello per tutti gli invii rende
 * naturale il riuso dei dati: la bozza di un adempimento parte dal contenuto dell'ultimo
 * documento trasmesso, e l'annotazione {@link Campo} dice quali campi servono a ciascun invio.
 * <p>
 * Alcuni campi sono <b>compilati dal sistema</b> e il valore inviato dal client è ignorato: categorie IS e
 * criteri del regolamento (dall'ultima valutazione), riclassificazioni (dallo storico), conclusione della
 * gestione (dall'incidente), riferimento della richiesta del CSIRT (dall'adempimento). Così non possono
 * divergere dai fatti registrati (ADR-0005, ADR-0007).
 * Riferimenti normativi: art. 25, c. 5, D.Lgs. 138/2024 (abbreviato "art. 25.5").
 */
public class ContenutoDocumento {

    // --- Identificazione e contatti --------------------------------------------------------

    @Campo(ordine = 10, etichetta = "Descrizione sintetica dell'incidente",
            obbligatorioIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Scelta progettuale: identifica l'evento in modo coerente in tutti gli invii")
    private String descrizioneSintetica;

    @Campo(ordine = 20, etichetta = "Referente CSIRT - nome",
            obbligatorioIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Det. ACN 379907/2025, misura GV.RR-02 (referente CSIRT, interlocutore del CSIRT Italia)")
    private String referenteNome;

    @Campo(ordine = 21, etichetta = "Referente CSIRT - e-mail",
            obbligatorioIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Det. ACN 379907/2025, misura GV.RR-02")
    private String referenteEmail;

    @Campo(ordine = 22, etichetta = "Referente CSIRT - telefono",
            obbligatorioIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Det. ACN 379907/2025, misura GV.RR-02")
    private String referenteTelefono;

    // --- Qualificazione ----------------------------------------------------------------------

    @Campo(ordine = 30, compilatoDalSistema = true, etichetta = "Categorie di incidente significativo (IS-1..IS-4)",
            facoltativoIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Det. ACN 379907/2025, allegati 3 e 4. Compilato dal sistema dall'ultima valutazione di "
                    + "significatività: vuoto se l'incidente è significativo solo per il Reg. 2024/2690 o se è declassato")
    private List<CategoriaIs> categorieIs = new ArrayList<>();

    @Campo(ordine = 35, compilatoDalSistema = true, etichetta = "Criteri del Reg. (UE) 2024/2690 soddisfatti (R1-R8)",
            facoltativoIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Reg. di esecuzione (UE) 2024/2690, artt. 3, 4 e 10. Compilato dal sistema dall'ultima "
                    + "valutazione: con le categorie IS motiva la significatività (ADR-0004)")
    private List<Regola> criteriRegolamento = new ArrayList<>();

    @Campo(ordine = 40, etichetta = "Servizi impattati",
            obbligatorioIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_FINALE},
            facoltativoIn = {RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE},
            fonte = "art. 25, c. 1 (impatto significativo sulla fornitura dei servizi)")
    private List<String> serviziImpattati = new ArrayList<>();

    @Campo(ordine = 50, etichetta = "Possibile risultato di atti illegittimi o malevoli",
            obbligatorioIn = {PRE_NOTIFICA, NOTIFICA},
            facoltativoIn = {RELAZIONE_FINALE},
            fonte = "art. 25.5 lett. a) \"ove possibile\", aggiornato con lett. b): ammesso NON_NOTO")
    private Esito3 attoIllegittimoOMalevolo;

    @Campo(ordine = 60, etichetta = "Impatto transfrontaliero",
            obbligatorioIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_FINALE},
            fonte = "art. 25.5 lett. a), b) e d) n. 4 \"ove noto\": ammesso NON_NOTO")
    private Esito3 impattoTransfrontaliero;

    // --- Valutazione e impatto ---------------------------------------------------------------

    @Campo(ordine = 70, etichetta = "Valutazione iniziale (gravità e impatto)",
            obbligatorioIn = {NOTIFICA},
            fonte = "art. 25.5 lett. b): valutazione iniziale comprensiva di gravità e impatto")
    private String valutazioneIniziale;

    @Campo(ordine = 80, etichetta = "Utenti impattati",
            facoltativoIn = {NOTIFICA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Reg. (UE) 2024/2690, art. 10 e art. 3, par. 3 (conteggio degli utenti); se non è noto, stima del "
                    + "massimo possibile (considerando 32)")
    private Integer utentiImpattati;

    @Campo(ordine = 90, etichetta = "Indicatori di compromissione",
            facoltativoIn = {NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "art. 25.5 lett. b) \"ove disponibili\"")
    private List<IndicatoreCompromissione> indicatoriCompromissione = new ArrayList<>();

    // --- Aggiornamenti --------------------------------------------------------------------

    @Campo(ordine = 100, compilatoDalSistema = true, etichetta = "Riferimento della richiesta del CSIRT Italia",
            obbligatorioIn = {RELAZIONE_INTERMEDIA},
            fonte = "art. 25.5 lett. c): relazione intermedia su richiesta del CSIRT Italia. Compilato dal sistema "
                    + "dalla richiesta registrata")
    private String riferimentoRichiestaCsirt;

    @Campo(ordine = 110, etichetta = "Aggiornamenti sulla situazione / progressi",
            obbligatorioIn = {RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE},
            fonte = "art. 25.5 lett. c) (pertinenti aggiornamenti) e lett. e) (relazione mensile sui progressi)")
    private String aggiornamentiSituazione;

    // --- Relazione finale -------------------------------------------------------------------

    @Campo(ordine = 120, etichetta = "Descrizione dettagliata, con gravità e impatto",
            obbligatorioIn = {RELAZIONE_FINALE},
            fonte = "art. 25.5 lett. d) n. 1")
    private String descrizioneDettagliata;

    @Campo(ordine = 130, etichetta = "Tipo di minaccia o causa originale (root cause)",
            obbligatorioIn = {RELAZIONE_FINALE},
            fonte = "art. 25.5 lett. d) n. 2")
    private String minacciaOCausaOriginale;

    @Campo(ordine = 140, etichetta = "Misure di attenuazione adottate e in corso",
            obbligatorioIn = {RELAZIONE_MENSILE, RELAZIONE_FINALE},
            facoltativoIn = {NOTIFICA, RELAZIONE_INTERMEDIA},
            fonte = "art. 25.5 lett. d) n. 3; per la mensile, lett. e) (progressi)")
    private String misureAttenuazione;

    @Campo(ordine = 150, compilatoDalSistema = true, etichetta = "Conclusione della gestione dell'incidente",
            obbligatorioIn = {RELAZIONE_FINALE},
            fonte = "art. 25.5 lett. d)-e): con l'incidente in corso la finale è sostituita dalla mensile. Compilato "
                    + "dal sistema dalla conclusione registrata sull'incidente (POST /api/incidenti/{id}/conclusione)")
    private Instant conclusioneGestioneIl;

    // --- Riclassificazione tardiva --------------------------------------------------------

    @Campo(ordine = 160, compilatoDalSistema = true, etichetta = "Riclassificazioni (classificazione precedente e nuova, momento, motivazione)",
            facoltativoIn = {PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE, RELAZIONE_FINALE},
            fonte = "Traccia PW 29, punto 3. Compilato dal sistema dallo storico: presente se l'incidente "
                    + "è stato riclassificato dopo un invio (ADR-0007)")
    private List<Riclassificazione> riclassificazioni = new ArrayList<>();

    public ContenutoDocumento() {
    }

    public String getDescrizioneSintetica() { return descrizioneSintetica; }
    public void setDescrizioneSintetica(String v) { this.descrizioneSintetica = v; }
    public String getReferenteNome() { return referenteNome; }
    public void setReferenteNome(String v) { this.referenteNome = v; }
    public String getReferenteEmail() { return referenteEmail; }
    public void setReferenteEmail(String v) { this.referenteEmail = v; }
    public String getReferenteTelefono() { return referenteTelefono; }
    public void setReferenteTelefono(String v) { this.referenteTelefono = v; }
    public List<CategoriaIs> getCategorieIs() { return categorieIs; }
    public void setCategorieIs(List<CategoriaIs> v) { this.categorieIs = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<String> getServiziImpattati() { return serviziImpattati; }
    public void setServiziImpattati(List<String> v) { this.serviziImpattati = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<Regola> getCriteriRegolamento() { return criteriRegolamento; }
    public void setCriteriRegolamento(List<Regola> v) {
        this.criteriRegolamento = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
    public Esito3 getAttoIllegittimoOMalevolo() { return attoIllegittimoOMalevolo; }
    public void setAttoIllegittimoOMalevolo(Esito3 v) { this.attoIllegittimoOMalevolo = v; }
    public Esito3 getImpattoTransfrontaliero() { return impattoTransfrontaliero; }
    public void setImpattoTransfrontaliero(Esito3 v) { this.impattoTransfrontaliero = v; }
    public String getValutazioneIniziale() { return valutazioneIniziale; }
    public void setValutazioneIniziale(String v) { this.valutazioneIniziale = v; }
    public Integer getUtentiImpattati() { return utentiImpattati; }
    public void setUtentiImpattati(Integer v) { this.utentiImpattati = v; }
    public List<IndicatoreCompromissione> getIndicatoriCompromissione() { return indicatoriCompromissione; }
    public void setIndicatoriCompromissione(List<IndicatoreCompromissione> v) {
        this.indicatoriCompromissione = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
    public String getRiferimentoRichiestaCsirt() { return riferimentoRichiestaCsirt; }
    public void setRiferimentoRichiestaCsirt(String v) { this.riferimentoRichiestaCsirt = v; }
    public String getAggiornamentiSituazione() { return aggiornamentiSituazione; }
    public void setAggiornamentiSituazione(String v) { this.aggiornamentiSituazione = v; }
    public String getDescrizioneDettagliata() { return descrizioneDettagliata; }
    public void setDescrizioneDettagliata(String v) { this.descrizioneDettagliata = v; }
    public String getMinacciaOCausaOriginale() { return minacciaOCausaOriginale; }
    public void setMinacciaOCausaOriginale(String v) { this.minacciaOCausaOriginale = v; }
    public String getMisureAttenuazione() { return misureAttenuazione; }
    public void setMisureAttenuazione(String v) { this.misureAttenuazione = v; }
    public Instant getConclusioneGestioneIl() { return conclusioneGestioneIl; }
    public void setConclusioneGestioneIl(Instant v) { this.conclusioneGestioneIl = v; }
    public List<Riclassificazione> getRiclassificazioni() { return riclassificazioni; }
    public void setRiclassificazioni(List<Riclassificazione> v) {
        this.riclassificazioni = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }
}
