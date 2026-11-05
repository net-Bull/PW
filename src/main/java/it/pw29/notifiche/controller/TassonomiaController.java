package it.pw29.notifiche.controller;

import it.pw29.notifiche.service.TassonomiaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tassonomia")
public class TassonomiaController {

    private final TassonomiaService service;

    public TassonomiaController(TassonomiaService service) {
        this.service = service;
    }

    /** Conteggio di categorie e sottocategorie della versione vigente, da confrontare col documento ACN. */
    @GetMapping("/conteggio")
    public ResponseEntity<TassonomiaService.Conteggio> conteggio() {
        return ResponseEntity.of(service.conteggioVigente());
    }
}
