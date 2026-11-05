package it.pw29.notifiche.domain;

/** Livelli della Tassonomia Cyber ACN (TC-ACN v2.0, cap. 3). */
public enum LivelloTassonomia {
    MACROCATEGORIA,
    PREDICATO,
    /** Presente solo sotto il predicato "Involved Asset" (19 sotto-predicati). */
    SOTTOPREDICATO,
    /** Elemento classificabile: l'unico livello che un incidente può referenziare. */
    VALORE
}
