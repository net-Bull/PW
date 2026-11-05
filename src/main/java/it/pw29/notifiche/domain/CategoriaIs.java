package it.pw29.notifiche.domain;

/**
 * Incidenti significativi di base (Determinazione ACN n. 379907/2025, allegato 3 per i soggetti
 * importanti, allegato 4 per gli essenziali). Testo da: ACN, Linee guida NIS – Definizione del
 * processo di gestione degli incidenti di sicurezza informatica, v1.1 (aprile 2026), par. 2.3.1.
 * Per tutte le categorie il presupposto è che il soggetto "abbia evidenza" dell'evento.
 */
public enum CategoriaIs {
    IS_1("IS-1", "Perdita di riservatezza, verso l'esterno, di dati digitali di proprietà del soggetto "
            + "o sui quali esercita il controllo, anche parziale", false),
    IS_2("IS-2", "Perdita di integrità, con impatto verso l'esterno, di dati digitali di proprietà del "
            + "soggetto o sui quali esercita il controllo, anche parziale", false),
    IS_3("IS-3", "Violazione dei livelli di servizio attesi dei servizi e/o delle attività del soggetto, "
            + "sulla base dei livelli di servizio attesi (SL) definiti ai sensi della misura DE.CM-01", false),
    IS_4("IS-4", "Accesso, non autorizzato o con abuso dei privilegi concessi, a dati digitali di proprietà "
            + "del soggetto o sui quali esercita il controllo, anche parziale (solo soggetti essenziali)", true);

    private final String codice;
    private final String descrizione;
    private final boolean soloEssenziali;

    CategoriaIs(String codice, String descrizione, boolean soloEssenziali) {
        this.codice = codice;
        this.descrizione = descrizione;
        this.soloEssenziali = soloEssenziali;
    }

    public String getCodice() { return codice; }
    public String getDescrizione() { return descrizione; }
    public boolean isSoloEssenziali() { return soloEssenziali; }

    public boolean applicabileA(TipoSoggetto tipo) {
        return !soloEssenziali || tipo == TipoSoggetto.ESSENZIALE;
    }
}
