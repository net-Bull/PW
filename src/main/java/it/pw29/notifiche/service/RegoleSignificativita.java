package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.LivelloServizio;
import it.pw29.notifiche.domain.Regola;
import it.pw29.notifiche.domain.SogliaSet;
import it.pw29.notifiche.dto.EsitoSignificativita;
import it.pw29.notifiche.dto.ValutazioneRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Motore delle regole R1-R8 (Reg. di esecuzione (UE) 2024/2690). Logica pura: riceve le grandezze
 * dell'incidente, le soglie e il servizio coinvolto, restituisce l'esito. Tutti i confronti sono
 * strettamente maggiori, come nel testo del regolamento ("per più di", "oltre", "superiore a").
 * <ul>
 *   <li>R1-R4 (art. 10) si applicano solo se il servizio è un servizio gestito o di sicurezza gestito;
 *       la soglia di utenti è il 5% degli utenti di quel servizio, con il tetto di un milione;</li>
 *   <li>R1 e R2 non scattano se l'indisponibilità deriva da un'interruzione programmata (art. 3, par. 2);</li>
 *   <li>R5-R8 (artt. 3 e 4) valgono per qualunque servizio.</li>
 * </ul>
 */
@Component
public class RegoleSignificativita {

    public EsitoSignificativita valuta(ValutazioneRequest in, SogliaSet s, LivelloServizio servizio) {
        int sogliaUtenti = s.sogliaUtenti(servizio.getUtentiUe());
        BigDecimal sogliaEconomica = s.sogliaEconomica();
        boolean art10 = servizio.isServizioGestito();
        boolean disponibilitaRilevante = art10 && !in.programmata();
        List<Regola> attivate = new ArrayList<>();

        if (disponibilitaRilevante && in.minutiIndisponibilitaTotale() > s.getMinutiIndisponibilita()) {
            attivate.add(Regola.R1);
        }
        if (disponibilitaRilevante && in.minutiDegrado() > s.getMinutiDegrado() && in.utentiImpattati() > sogliaUtenti) {
            attivate.add(Regola.R2);
        }
        if (art10 && in.datiCompromessi() && in.sospettoDoloso()) {
            attivate.add(Regola.R3);
        }
        if (art10 && in.datiCompromessi() && in.utentiImpattati() > sogliaUtenti) {
            attivate.add(Regola.R4);
        }
        if (in.perditaDiretta().compareTo(sogliaEconomica) > 0) {
            attivate.add(Regola.R5);
        }
        if (in.accessoNonAutorizzato() && in.sospettoDoloso() && in.gravePerturbazione()) {
            attivate.add(Regola.R6);
        }
        if (in.incidentiStessaCausaInFinestra() >= s.getMinIncidentiRicorrenza()
                && in.perditeCumulateInFinestra().compareTo(sogliaEconomica) > 0) {
            attivate.add(Regola.R7);
        }
        if (in.segretiCommerciali()) {
            attivate.add(Regola.R8);
        }

        return EsitoSignificativita.soloRegole(attivate, s.getVersione(), sogliaUtenti, sogliaEconomica);
    }
}
