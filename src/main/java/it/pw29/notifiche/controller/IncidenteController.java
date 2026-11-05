package it.pw29.notifiche.controller;

import it.pw29.notifiche.dto.AdempimentoDto;
import it.pw29.notifiche.dto.AuditVoce;
import it.pw29.notifiche.dto.ConclusioneRequest;
import it.pw29.notifiche.dto.IncidenteDettaglio;
import it.pw29.notifiche.dto.IncidenteSintesi;
import it.pw29.notifiche.dto.NuovoIncidenteRequest;
import it.pw29.notifiche.dto.QualificazioneRequest;
import it.pw29.notifiche.dto.RichiestaCsirtRequest;
import it.pw29.notifiche.dto.RiclassificazioneDto;
import it.pw29.notifiche.dto.RiclassificazioneRequest;
import it.pw29.notifiche.dto.VerificaCatena;
import it.pw29.notifiche.dto.Scadenza;
import it.pw29.notifiche.service.AuditConsultazioneService;
import it.pw29.notifiche.service.CatenaService;
import it.pw29.notifiche.service.IncidenteService;
import it.pw29.notifiche.service.RiclassificazioneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidenti")
public class IncidenteController {

    private final IncidenteService service;
    private final AuditConsultazioneService audit;
    private final RiclassificazioneService riclassificazione;
    private final CatenaService catena;

    public IncidenteController(IncidenteService service, AuditConsultazioneService audit,
                               RiclassificazioneService riclassificazione, CatenaService catena) {
        this.service = service;
        this.audit = audit;
        this.riclassificazione = riclassificazione;
        this.catena = catena;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidenteDettaglio registra(@Valid @RequestBody NuovoIncidenteRequest r) {
        return service.registra(r);
    }

    @GetMapping
    public List<IncidenteSintesi> elenco() {
        return service.elenco();
    }

    @GetMapping("/{id}")
    public IncidenteDettaglio dettaglio(@PathVariable Long id) {
        return service.dettaglio(id);
    }

    /** Valutazione di significatività; se positiva registra il momento della conoscenza (immutabile). */
    @PostMapping("/{id}/qualificazione")
    public IncidenteDettaglio qualifica(@PathVariable Long id, @Valid @RequestBody QualificazioneRequest r) {
        return service.qualifica(id, r);
    }

    @GetMapping("/{id}/scadenze")
    public List<Scadenza> scadenze(@PathVariable Long id) {
        return service.scadenze(id);
    }

    @PostMapping("/{id}/richieste-csirt")
    @ResponseStatus(HttpStatus.CREATED)
    public AdempimentoDto richiestaCsirt(@PathVariable Long id, @Valid @RequestBody RichiestaCsirtRequest r) {
        return service.registraRichiestaCsirt(id, r);
    }

    @PostMapping("/{id}/relazioni-mensili")
    @ResponseStatus(HttpStatus.CREATED)
    public AdempimentoDto relazioneMensile(@PathVariable Long id) {
        return service.apriRelazioneMensile(id);
    }

    @PostMapping("/{id}/conclusione")
    public IncidenteDettaglio conclusione(@PathVariable Long id, @Valid @RequestBody ConclusioneRequest r) {
        return service.registraConclusione(id, r);
    }

    /** Riclassificazione tardiva: nuova significatività e/o nuove voci, con motivazione (ADR-0007). */
    @PostMapping("/{id}/riclassificazione")
    @ResponseStatus(HttpStatus.CREATED)
    public RiclassificazioneDto riclassifica(@PathVariable Long id, @Valid @RequestBody RiclassificazioneRequest r) {
        return riclassificazione.riclassifica(id, r);
    }

    @GetMapping("/{id}/riclassificazioni")
    public List<RiclassificazioneDto> riclassificazioni(@PathVariable Long id) {
        return riclassificazione.storico(id);
    }

    /** Verifica della catena di hash SHA-256 dei documenti trasmessi (ADR-0006). */
    @GetMapping("/{id}/catena")
    public VerificaCatena catena(@PathVariable Long id) {
        return catena.verifica(id);
    }

    @GetMapping("/{id}/audit")
    public List<AuditVoce> audit(@PathVariable Long id) {
        return audit.perIncidente(id);
    }
}
