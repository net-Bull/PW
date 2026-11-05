package it.pw29.notifiche.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Grandezze osservate su un incidente, in ingresso alla valutazione di significatività.
 * Servono sia alle regole R1-R8 (Reg. (UE) 2024/2690) sia alle categorie IS-1..IS-4 (ACN).
 * La validazione lato server rifiuta valori mancanti o negativi. I due indicatori aggiunti dopo la revisione
 * sono facoltativi (assenti = falso), così le valutazioni già registrate restano leggibili.
 *
 * @param servizioCoinvolto codice del servizio critico interessato (tabella livello_servizio)
 * @param utentiImpattati utenti del servizio interessati, contati come prevede l'art. 3, par. 3; se il numero
 *        esatto non è noto va indicata la stima del massimo possibile (considerando 32 del Reg. 2024/2690)
 * @param perditaRiservatezza perdita di riservatezza dei dati (IS-1, R3, R4)
 * @param perditaIntegrita perdita di integrità dei dati (IS-2, R3, R4)
 * @param perditaAutenticita perdita di autenticità dei dati (R3, R4)
 * @param impattoEsterno la perdita di riservatezza o integrità ha effetti verso l'esterno del soggetto
 * @param accessoNonAutorizzato accesso riuscito non autorizzato o con abuso dei privilegi concessi (R6, IS-4)
 * @param perditaDiretta perdita finanziaria diretta causata o che l'incidente è in grado di causare (R5)
 * @param esfiltrazioneSegretiCommerciali esfiltrazione, avvenuta o possibile, di segreti commerciali del
 *        soggetto ai sensi dell'art. 2, punto 1, della direttiva (UE) 2016/943 (R8)
 * @param interruzioneProgrammata l'indisponibilità o il degrado derivano da un'interruzione programmata o
 *        dalle conseguenze previste di una manutenzione programmata: non contano per R1, R2 e IS-3
 *        (art. 3, par. 2, e considerando 33 del Reg. 2024/2690)
 */
public record ValutazioneRequest(
        @NotBlank String servizioCoinvolto,
        @NotNull @Min(0) Integer minutiIndisponibilitaTotale,
        @NotNull @Min(0) Integer minutiDegrado,
        @NotNull @Min(0) Integer utentiImpattati,
        @NotNull Boolean perditaRiservatezza,
        @NotNull Boolean perditaIntegrita,
        @NotNull Boolean perditaAutenticita,
        @NotNull Boolean impattoEsterno,
        @NotNull Boolean sospettoDoloso,
        @NotNull Boolean accessoNonAutorizzato,
        @NotNull Boolean gravePerturbazione,
        @NotNull @PositiveOrZero BigDecimal perditaDiretta,
        @NotNull @Min(0) Integer incidentiStessaCausaInFinestra,
        @NotNull @PositiveOrZero BigDecimal perditeCumulateInFinestra,
        Boolean esfiltrazioneSegretiCommerciali,
        Boolean interruzioneProgrammata
) {

    /** Compromissione di integrità, riservatezza o autenticità dei dati (Reg. 2024/2690, art. 10). */
    public boolean datiCompromessi() {
        return perditaRiservatezza || perditaIntegrita || perditaAutenticita;
    }

    public boolean segretiCommerciali() {
        return Boolean.TRUE.equals(esfiltrazioneSegretiCommerciali);
    }

    public boolean programmata() {
        return Boolean.TRUE.equals(interruzioneProgrammata);
    }
}
