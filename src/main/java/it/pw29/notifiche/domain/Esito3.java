package it.pw29.notifiche.domain;

/**
 * Risposta a tre valori per le informazioni che la norma chiede "ove possibile" o "ove noto"
 * (art. 25, c. 5, lett. a, b, d, D.Lgs. 138/2024): il campo va sempre compilato, ma
 * "non noto" è una risposta ammessa e diversa dal campo lasciato vuoto.
 */
public enum Esito3 {
    SI,
    NO,
    NON_NOTO
}
