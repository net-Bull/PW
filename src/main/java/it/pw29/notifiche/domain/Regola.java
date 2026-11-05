package it.pw29.notifiche.domain;

/**
 * Regole di significatività del contesto MSSP, con la fonte normativa di ciascuna (Reg. di esecuzione
 * (UE) 2024/2690, testo italiano su EUR-Lex). R1-R4 traducono l'art. 10 e valgono per i soli servizi
 * gestiti o di sicurezza gestiti; R5-R8 traducono i criteri orizzontali degli artt. 3 e 4 e valgono per
 * qualunque servizio. I criteri dell'art. 3, par. 1, lett. c) e d) (decesso, danni alla salute) non sono
 * tradotti in regole: vedi docs/criteri/DISCREPANZE-CRITERI.md.
 */
public enum Regola {
    R1("Servizio gestito completamente indisponibile oltre la soglia in minuti", "Reg. (UE) 2024/2690, art. 10, lett. a)"),
    R2("Disponibilità limitata oltre la soglia in minuti per più del 5% degli utenti del servizio",
            "Reg. (UE) 2024/2690, art. 10, lett. b)"),
    R3("Integrità, riservatezza o autenticità dei dati del servizio compromesse da un'azione che si sospetta malevola",
            "Reg. (UE) 2024/2690, art. 10, lett. c)"),
    R4("Integrità, riservatezza o autenticità dei dati del servizio compromesse per più del 5% degli utenti del servizio",
            "Reg. (UE) 2024/2690, art. 10, lett. d)"),
    R5("Perdita finanziaria diretta, anche potenziale, oltre la soglia economica", "Reg. (UE) 2024/2690, art. 3, par. 1, lett. a)"),
    R6("Accesso non autorizzato, che si sospetta malevolo, in grado di causare gravi perturbazioni operative",
            "Reg. (UE) 2024/2690, art. 3, par. 1, lett. e)"),
    R7("Incidenti ricorrenti con la stessa causa apparente e perdite cumulate oltre la soglia economica",
            "Reg. (UE) 2024/2690, art. 4"),
    R8("Esfiltrazione, anche solo potenziale, di segreti commerciali del soggetto",
            "Reg. (UE) 2024/2690, art. 3, par. 1, lett. b)");

    private final String descrizione;
    private final String fonte;

    Regola(String descrizione, String fonte) {
        this.descrizione = descrizione;
        this.fonte = fonte;
    }

    public String getDescrizione() { return descrizione; }
    public String getFonte() { return fonte; }
}
