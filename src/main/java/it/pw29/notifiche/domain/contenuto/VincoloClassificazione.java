package it.pw29.notifiche.domain.contenuto;

import it.pw29.notifiche.domain.TassonomiaVoce;
import it.pw29.notifiche.domain.TipoAdempimento;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import static it.pw29.notifiche.domain.TipoAdempimento.NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.PRE_NOTIFICA;
import static it.pw29.notifiche.domain.TipoAdempimento.RELAZIONE_FINALE;

/**
 * Vincoli sulla classificazione secondo la Tassonomia Cyber ACN, differenziati per adempimento.
 * Come {@link Campo}, alimentano sia la validazione sia la matrice pubblicata.
 * I vincoli strutturali (solo voci VALORE, stessa versione della tassonomia) valgono sempre.
 */
public enum VincoloClassificazione {

    ALMENO_UNA_VOCE("Almeno una voce della Tassonomia Cyber ACN",
            EnumSet.of(PRE_NOTIFICA, NOTIFICA, RELAZIONE_FINALE),
            "Traccia PW 29: classificazione vincolata alla tassonomia ACN") {
        @Override
        public boolean soddisfatto(Collection<TassonomiaVoce> voci) {
            return !voci.isEmpty();
        }
    },
    GRAVITA_UNICA("Esattamente un valore del predicato Severity (BC:SE)",
            EnumSet.of(NOTIFICA, RELAZIONE_FINALE),
            "art. 25.5 lett. b) e d) n. 1: la valutazione comprende la gravità") {
        @Override
        public boolean soddisfatto(Collection<TassonomiaVoce> voci) {
            return voci.stream().filter(v -> v.getCodice().startsWith("BC:SE_")).count() == 1;
        }
    },
    MINACCIA_O_CAUSA("Almeno un valore di Threat Type (TT) o di Root Cause (BC:RO)",
            EnumSet.of(RELAZIONE_FINALE),
            "art. 25.5 lett. d) n. 2: tipo di minaccia o causa originale") {
        @Override
        public boolean soddisfatto(Collection<TassonomiaVoce> voci) {
            return voci.stream().anyMatch(v -> v.getCodice().startsWith("TT:") || v.getCodice().startsWith("BC:RO_"));
        }
    };

    private final String descrizione;
    private final Set<TipoAdempimento> adempimenti;
    private final String fonte;

    VincoloClassificazione(String descrizione, Set<TipoAdempimento> adempimenti, String fonte) {
        this.descrizione = descrizione;
        this.adempimenti = adempimenti;
        this.fonte = fonte;
    }

    public abstract boolean soddisfatto(Collection<TassonomiaVoce> voci);

    public boolean siApplicaA(TipoAdempimento tipo) {
        return adempimenti.contains(tipo);
    }

    public String getDescrizione() { return descrizione; }
    public Set<TipoAdempimento> getAdempimenti() { return EnumSet.copyOf(adempimenti); }
    public String getFonte() { return fonte; }
}
