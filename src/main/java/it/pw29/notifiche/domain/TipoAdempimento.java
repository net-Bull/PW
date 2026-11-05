package it.pw29.notifiche.domain;

/** Adempimenti verso il CSIRT Italia previsti dall'art. 25, c. 5, D.Lgs. 138/2024. */
public enum TipoAdempimento {
    PRE_NOTIFICA("Pre-notifica", "art. 25, c. 5, lett. a)"),
    NOTIFICA("Notifica", "art. 25, c. 5, lett. b)"),
    RELAZIONE_INTERMEDIA("Relazione intermedia", "art. 25, c. 5, lett. c)"),
    RELAZIONE_MENSILE("Relazione mensile sui progressi", "art. 25, c. 5, lett. e)"),
    RELAZIONE_FINALE("Relazione finale", "art. 25, c. 5, lett. d) ed e)");

    private final String etichetta;
    private final String riferimento;

    TipoAdempimento(String etichetta, String riferimento) {
        this.etichetta = etichetta;
        this.riferimento = riferimento;
    }

    public String getEtichetta() { return etichetta; }
    public String getRiferimento() { return riferimento; }

    /** Adempimenti di cui può esistere più di un'istanza per lo stesso incidente. */
    public boolean ripetibile() {
        return this == RELAZIONE_INTERMEDIA || this == RELAZIONE_MENSILE;
    }
}
