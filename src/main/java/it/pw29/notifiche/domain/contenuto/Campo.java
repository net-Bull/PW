package it.pw29.notifiche.domain.contenuto;

import it.pw29.notifiche.domain.TipoAdempimento;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadati di un campo dei documenti verso il CSIRT Italia. È l'unica fonte di verità per:
 * la validazione lato server differenziata per adempimento, la matrice campo/adempimento
 * pubblicata in {@code docs/MATRICE-CAMPI.md} e (Fase 3) la selezione dei campi da stampare.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Campo {

    /** Etichetta leggibile, usata nei messaggi di errore e nella matrice. */
    String etichetta();

    /** Adempimenti per cui il campo è obbligatorio. */
    TipoAdempimento[] obbligatorioIn() default {};

    /** Adempimenti per cui il campo è previsto ma facoltativo (es. "ove disponibili"). */
    TipoAdempimento[] facoltativoIn() default {};

    /** Riferimento normativo o motivazione progettuale del campo. */
    String fonte();

    /** Ordine di presentazione nella matrice e nei documenti. */
    int ordine();

    /**
     * Campo compilato dal sistema a partire dai fatti registrati (valutazione, storico, incidente): l'interfaccia
     * lo mostra in sola lettura e il valore inviato dal client è ignorato (ADR-0005).
     */
    boolean compilatoDalSistema() default false;
}
