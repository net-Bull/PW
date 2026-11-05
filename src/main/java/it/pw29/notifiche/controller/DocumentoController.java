package it.pw29.notifiche.controller;

import it.pw29.notifiche.dto.AggiornaDocumentoRequest;
import it.pw29.notifiche.dto.DocumentoDettaglio;
import it.pw29.notifiche.dto.EsitoVerifica;
import it.pw29.notifiche.dto.RettificaRequest;
import it.pw29.notifiche.dto.RespingimentoRequest;
import it.pw29.notifiche.dto.TrasmissioneRequest;
import it.pw29.notifiche.service.DocumentoService;
import it.pw29.notifiche.service.EsportazioneService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DocumentoController {

    private final DocumentoService service;
    private final EsportazioneService esportazione;

    public DocumentoController(DocumentoService service, EsportazioneService esportazione) {
        this.service = service;
        this.esportazione = esportazione;
    }

    /** Nuova bozza per l'adempimento, con i dati ripresi dall'ultimo documento trasmesso. */
    @PostMapping("/adempimenti/{adempimentoId}/bozza")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoDettaglio creaBozza(@PathVariable Long adempimentoId) {
        return service.creaBozza(adempimentoId);
    }

    /**
     * Rettifica di un adempimento già trasmesso: nuova versione, la precedente resta immutata.
     * Ammessa solo sull'ultimo documento trasmesso dell'incidente.
     */
    @PostMapping("/adempimenti/{adempimentoId}/rettifica")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoDettaglio rettifica(@PathVariable Long adempimentoId, @Valid @RequestBody RettificaRequest r) {
        return service.rettifica(adempimentoId, r.motivo());
    }

    @GetMapping("/documenti/{id}")
    public DocumentoDettaglio dettaglio(@PathVariable Long id) {
        return service.dettaglio(id);
    }

    /** Documento strutturato (JSON) con i soli campi previsti per l'adempimento. */
    @GetMapping("/documenti/{id}/json")
    public ResponseEntity<byte[]> json(@PathVariable Long id) {
        return file(esportazione.json(id));
    }

    /** Documento stampabile (PDF/A-2b), generato dalla stessa rappresentazione del JSON. */
    @GetMapping("/documenti/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return file(esportazione.pdf(id));
    }

    private static ResponseEntity<byte[]> file(EsportazioneService.FileEsportato f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.nome()).build().toString())
                .body(f.dati());
    }

    @PutMapping("/documenti/{id}")
    public DocumentoDettaglio aggiorna(@PathVariable Long id, @Valid @RequestBody AggiornaDocumentoRequest r) {
        return service.aggiorna(id, r);
    }

    @GetMapping("/documenti/{id}/verifica")
    public EsitoVerifica verifica(@PathVariable Long id) {
        return service.verifica(id);
    }

    @PostMapping("/documenti/{id}/invio-approvazione")
    public DocumentoDettaglio inviaInApprovazione(@PathVariable Long id) {
        return service.inviaInApprovazione(id);
    }

    @PostMapping("/documenti/{id}/respingimento")
    public DocumentoDettaglio respingi(@PathVariable Long id, @Valid @RequestBody RespingimentoRequest r) {
        return service.respingi(id, r.nota());
    }

    @PostMapping("/documenti/{id}/trasmissione")
    public DocumentoDettaglio trasmetti(@PathVariable Long id, @Valid @RequestBody TrasmissioneRequest r) {
        return service.trasmetti(id, r);
    }
}
