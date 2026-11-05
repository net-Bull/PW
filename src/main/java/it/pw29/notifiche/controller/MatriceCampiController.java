package it.pw29.notifiche.controller;

import it.pw29.notifiche.dto.RigaMatrice;
import it.pw29.notifiche.service.MatriceCampiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Matrice campo/adempimento generata dal codice, in JSON e in Markdown. */
@RestController
@RequestMapping("/api/matrice-campi")
public class MatriceCampiController {

    private final MatriceCampiService service;

    public MatriceCampiController(MatriceCampiService service) {
        this.service = service;
    }

    @GetMapping
    public List<RigaMatrice> righe() {
        return service.righe();
    }

    @GetMapping(value = "/markdown", produces = "text/markdown;charset=UTF-8")
    public String markdown() {
        return service.markdown();
    }
}
