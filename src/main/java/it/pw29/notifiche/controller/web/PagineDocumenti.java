package it.pw29.notifiche.controller.web;

import it.pw29.notifiche.domain.StatoDocumento;
import it.pw29.notifiche.domain.contenuto.ContenutoDocumento;
import it.pw29.notifiche.dto.AggiornaDocumentoRequest;
import it.pw29.notifiche.dto.DocumentoDettaglio;
import it.pw29.notifiche.dto.EsitoVerifica;
import it.pw29.notifiche.dto.RespingimentoRequest;
import it.pw29.notifiche.dto.ServizioDto;
import it.pw29.notifiche.dto.TrasmissioneRequest;
import it.pw29.notifiche.dto.Violazione;
import it.pw29.notifiche.dto.VoceDto;
import it.pw29.notifiche.service.DatiNonValidiException;
import it.pw29.notifiche.service.DocumentoService;
import it.pw29.notifiche.service.EsportazioneService;
import it.pw29.notifiche.service.MatriceCampi;
import it.pw29.notifiche.service.MatriceCampiService;
import it.pw29.notifiche.service.TassonomiaService;
import it.pw29.notifiche.service.ValutazioneService;
import it.pw29.notifiche.service.UtenteCorrente;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Editor e ciclo di vita di una versione di documento: compilazione guidata dalla matrice dei campi (solo i
 * campi previsti per l'adempimento, con l'indicazione di quelli obbligatori), verifica mentre si scrive,
 * selezione delle voci della tassonomia, invio in approvazione, respingimento, registrazione della
 * trasmissione, download di JSON e PDF e anteprima di stampa.
 */
@Controller
public class PagineDocumenti {

    private final DocumentoService documenti;
    private final EsportazioneService esportazione;
    private final MatriceCampiService matrice;
    private final TassonomiaService tassonomia;
    private final ValutazioneService valutazione;
    private final UtenteCorrente utente;
    private final Azioni azioni;
    private final Formati fmt;

    public PagineDocumenti(DocumentoService documenti, EsportazioneService esportazione, MatriceCampiService matrice,
                           TassonomiaService tassonomia, ValutazioneService valutazione, UtenteCorrente utente,
                           Azioni azioni, Formati fmt) {
        this.documenti = documenti;
        this.esportazione = esportazione;
        this.matrice = matrice;
        this.tassonomia = tassonomia;
        this.valutazione = valutazione;
        this.utente = utente;
        this.azioni = azioni;
        this.fmt = fmt;
    }

    @GetMapping("/documenti/{id}")
    public String documento(@PathVariable Long id, Model m) {
        DocumentoDettaglio d = documenti.dettaglio(id);
        List<MatriceCampi.CampoModulo> campi = matrice.campiModulo(d.tipo());
        List<String> serviziNoti = valutazione.servizi(null).stream().map(ServizioDto::servizio).toList();
        List<String> vociDocumento = d.voci().stream().map(VoceDto::codice).toList();

        m.addAttribute("d", d);
        m.addAttribute("campi", campi);
        m.addAttribute("previsti", campi.stream().map(MatriceCampi.CampoModulo::nome).collect(Collectors.toSet()));
        m.addAttribute("obbligatori", campi.stream().filter(MatriceCampi.CampoModulo::obbligatorio)
                .map(MatriceCampi.CampoModulo::nome).collect(Collectors.toSet()));
        m.addAttribute("servizi", serviziNoti);
        if (!m.containsAttribute("modulo")) {
            m.addAttribute("modulo", ModuloDocumento.da(d.contenuto(), serviziNoti, vociDocumento));
        }
        ModuloDocumento modulo = (ModuloDocumento) m.getAttribute("modulo");
        m.addAttribute("selezionate", modulo.getVoci());
        m.addAttribute("valori", tassonomia.valoriVigenti());
        m.addAttribute("mancanti", d.verifica().violazioni().stream().map(Violazione::campo).collect(Collectors.toSet()));
        if (d.stato() == StatoDocumento.TRASMESSO) {
            // Intestazione sigillata all'invio: stato dell'incidente e dati del soggetto di quel momento (ADR-0008)
            m.addAttribute("esportato", esportazione.documento(id));
        }
        String io = utente.username();
        m.addAttribute("sonoAutore", d.autori().contains(io));
        m.addAttribute("hoInviato", io.equals(d.inviataApprovazioneDa()));
        m.addAttribute("adesso", fmt.campoAdesso());
        return "interfaccia/documento";
    }

    @PostMapping("/documenti/{id}")
    public String salva(@PathVariable Long id, @ModelAttribute("modulo") ModuloDocumento modulo,
                        @RequestParam(defaultValue = "salva") String poi, RedirectAttributes ra) {
        ra.addFlashAttribute("modulo", modulo);
        return azioni.esegui(ra, "/documenti/" + id, () -> {
            documenti.aggiorna(id, richiesta(id, modulo));
            if ("invia".equals(poi)) {
                documenti.inviaInApprovazione(id);
                return new Azioni.Esito("/documenti/" + id, "Bozza salvata e inviata in approvazione.");
            }
            return new Azioni.Esito("/documenti/" + id, "Bozza salvata.");
        });
    }

    /**
     * Verifica di completezza mentre l'operatore compila, senza salvare: l'editor la chiama con il contenuto del
     * modulo (stessa sessione e stesso token CSRF dell'interfaccia) e mostra subito i campi mancanti.
     */
    @PostMapping("/documenti/{id}/verifica")
    @ResponseBody
    public ResponseEntity<Object> verifica(@PathVariable Long id, @ModelAttribute ModuloDocumento modulo) {
        try {
            EsitoVerifica esito = documenti.verificaProvvisoria(id, richiesta(id, modulo));
            return ResponseEntity.ok(esito);
        } catch (ModuloNonValidoException | IllegalArgumentException | DatiNonValidiException e) {
            return ResponseEntity.unprocessableEntity().body(new Messaggio(e.getMessage()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(new Messaggio("Operazione non consentita al tuo profilo."));
        }
    }

    record Messaggio(String errore) {
    }

    private AggiornaDocumentoRequest richiesta(Long id, ModuloDocumento modulo) {
        DocumentoDettaglio d = documenti.dettaglio(id);
        Set<String> previsti = matrice.campiModulo(d.tipo()).stream()
                .filter(c -> !c.compilatoDalSistema()).map(MatriceCampi.CampoModulo::nome).collect(Collectors.toSet());
        ContenutoDocumento contenuto = modulo.applica(d.contenuto(), previsti);
        return new AggiornaDocumentoRequest(contenuto, modulo.getVoci());
    }

    @PostMapping("/documenti/{id}/invio-approvazione")
    public String inviaInApprovazione(@PathVariable Long id, RedirectAttributes ra) {
        return azioni.esegui(ra, "/documenti/" + id, () -> {
            documenti.inviaInApprovazione(id);
            return new Azioni.Esito("/documenti/" + id, "Documento inviato in approvazione.");
        });
    }

    @PostMapping("/documenti/{id}/respingimento")
    public String respingi(@PathVariable Long id, @RequestParam(required = false) String nota, RedirectAttributes ra) {
        return azioni.esegui(ra, "/documenti/" + id, () -> {
            documenti.respingi(id, azioni.valida(new RespingimentoRequest(nota)).nota());
            return new Azioni.Esito("/documenti/" + id, "Documento respinto: torna in bozza con la nota per l'operatore.");
        });
    }

    @PostMapping("/documenti/{id}/trasmissione")
    public String trasmetti(@PathVariable Long id, @RequestParam(required = false) String riferimento,
                            @RequestParam(required = false) String trasmessaIl,
                            @RequestParam(defaultValue = "false") boolean conferma, RedirectAttributes ra) {
        return azioni.esegui(ra, "/documenti/" + id, () -> {
            if (!conferma) {
                throw new ModuloNonValidoException("Confermare che il documento è stato inviato al CSIRT Italia: "
                        + "dopo la registrazione non si modifica più");
            }
            // Il campo è al minuto: se l'invio in approvazione cade nel minuto indicato, la trasmissione vale da lì.
            Instant invio = documenti.dettaglio(id).inviataApprovazioneIl();
            DocumentoDettaglio d = documenti.trasmetti(id, azioni.valida(
                    new TrasmissioneRequest(riferimento, fmt.leggi(trasmessaIl, invio))));
            return new Azioni.Esito("/documenti/" + id, d.tipo().getEtichetta() + " registrata come trasmessa: "
                    + "documento sigillato con impronta " + fmt.impronta(d.improntaSha256()) + ".");
        });
    }

    // --- Esportazioni ---------------------------------------------------------------------------

    @GetMapping("/documenti/{id}/json")
    public ResponseEntity<byte[]> json(@PathVariable Long id) {
        return file(esportazione.json(id));
    }

    @GetMapping("/documenti/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return file(esportazione.pdf(id));
    }

    /** Anteprima di stampa nel browser: lo stesso template del PDF. */
    @GetMapping(value = "/documenti/{id}/anteprima", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String anteprima(@PathVariable Long id) {
        return esportazione.anteprimaHtml(id);
    }

    private static ResponseEntity<byte[]> file(EsportazioneService.FileEsportato f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.nome()).build().toString())
                .body(f.dati());
    }
}
