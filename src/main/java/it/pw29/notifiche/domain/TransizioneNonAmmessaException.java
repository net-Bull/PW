package it.pw29.notifiche.domain;

/** Tentativo di passare a uno stato non raggiungibile da quello corrente. */
public class TransizioneNonAmmessaException extends RuntimeException {

    public TransizioneNonAmmessaException(String messaggio) {
        super(messaggio);
    }

    public static TransizioneNonAmmessaException da(Enum<?> da, Enum<?> a, String oggetto) {
        return new TransizioneNonAmmessaException(
                "Transizione non ammessa per " + oggetto + ": " + da + " -> " + a);
    }
}
