package it.pw29.notifiche.controller;

import it.pw29.notifiche.dto.EsitoSignificativita;
import it.pw29.notifiche.dto.ValutazioneRequest;
import it.pw29.notifiche.service.ValutazioneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/significativita")
public class SignificativitaController {

    private final ValutazioneService service;

    public SignificativitaController(ValutazioneService service) {
        this.service = service;
    }

    /** Simulazione della valutazione, senza registrare nulla (per il soggetto predefinito o quello indicato). */
    @PostMapping("/valutazione")
    public EsitoSignificativita valuta(@Valid @RequestBody ValutazioneRequest richiesta,
                                       @RequestParam(name = "soggetto", required = false) Long soggettoId) {
        return service.valuta(richiesta, soggettoId);
    }
}
