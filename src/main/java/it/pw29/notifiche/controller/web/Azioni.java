package it.pw29.notifiche.controller.web;

import it.pw29.notifiche.domain.DocumentoImmutabileException;
import it.pw29.notifiche.domain.QuattroOcchiException;
import it.pw29.notifiche.domain.TransizioneNonAmmessaException;
import it.pw29.notifiche.service.DatiNonValidiException;
import it.pw29.notifiche.service.DocumentoNonValidoException;
import it.pw29.notifiche.service.RisorsaNonTrovataException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Esecuzione delle azioni dell'interfaccia secondo lo schema POST-redirect-GET: l'azione chiama un servizio
 * (stesse regole, stessi permessi e stesso audit delle API), poi la pagina viene ricaricata con un messaggio.
 * Le eccezioni di dominio diventano messaggi per l'operatore, con lo stesso significato dei codici HTTP delle API
 * (422 dati incoerenti o documento incompleto, 409 stato incompatibile, 403 quattro occhi o profilo).
 * <p>
 * I DTO costruiti dai moduli passano dalla stessa Bean Validation dei corpi JSON delle API ({@code @Valid}).
 */
@Component
public class Azioni {

    /** Esito di un'azione riuscita: pagina da aprire e messaggio da mostrare. */
    public record Esito(String pagina, String messaggio) {
    }

    private static final Map<String, String> NOMI = Map.ofEntries(
            Map.entry("titolo", "titolo"), Map.entry("descrizione", "descrizione"),
            Map.entry("rilevatoIl", "istante di rilevazione"), Map.entry("motivazione", "motivazione"),
            Map.entry("motivo", "motivo della rettifica"), Map.entry("nota", "nota di respingimento"),
            Map.entry("riferimento", "riferimento"), Map.entry("conclusaIl", "istante di conclusione"),
            Map.entry("motivazioneConoscenza", "motivazione della conoscenza"));

    private final Validator validator;

    public Azioni(Validator validator) {
        this.validator = validator;
    }

    /** Applica i vincoli dichiarati sul DTO; il primo vincolo violato diventa il messaggio per l'operatore. */
    public <T> T valida(T dto) {
        Set<ConstraintViolation<T>> violazioni = validator.validate(dto);
        if (!violazioni.isEmpty()) {
            String testo = violazioni.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(Azioni::messaggio)
                    .distinct()
                    .collect(Collectors.joining("; "));
            throw new ModuloNonValidoException(testo);
        }
        return dto;
    }

    private static String messaggio(ConstraintViolation<?> v) {
        String percorso = v.getPropertyPath().toString();
        String campo = percorso.contains(".") ? percorso.substring(percorso.lastIndexOf('.') + 1) : percorso;
        String nome = NOMI.getOrDefault(campo, campo);
        String vincolo = v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return switch (vincolo) {
            case "NotBlank", "NotNull", "NotEmpty" -> "Compilare il campo \"" + nome + "\"";
            case "Size" -> "Il campo \"" + nome + "\" è troppo lungo";
            case "Min", "PositiveOrZero" -> "Il campo \"" + nome + "\" non può essere negativo";
            default -> "Campo \"" + nome + "\" non valido";
        };
    }

    /**
     * Esegue l'azione: se riesce reindirizza alla pagina dell'esito con il messaggio, altrimenti alla pagina
     * {@code seErrore} con la spiegazione (e, per un documento incompleto, l'elenco dei campi mancanti).
     */
    public String esegui(RedirectAttributes ra, String seErrore, Supplier<Esito> azione) {
        try {
            Esito e = azione.get();
            if (e.messaggio() != null) {
                ra.addFlashAttribute("conferma", e.messaggio());
            }
            return "redirect:" + e.pagina();
        } catch (DocumentoNonValidoException e) {
            ra.addFlashAttribute("errore", "Il documento non è completo per " + e.getEsito().adempimento().getEtichetta()
                    + ": compilare i campi indicati.");
            ra.addFlashAttribute("violazioni", e.getEsito().violazioni());
        } catch (QuattroOcchiException e) {
            ra.addFlashAttribute("errore", "Principio dei quattro occhi: " + e.getMessage());
        } catch (AccessDeniedException e) {
            ra.addFlashAttribute("errore", "Operazione non consentita al tuo profilo.");
        } catch (ModuloNonValidoException | DatiNonValidiException | TransizioneNonAmmessaException
                 | DocumentoImmutabileException | RisorsaNonTrovataException | IllegalArgumentException e) {
            ra.addFlashAttribute("errore", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            ra.addFlashAttribute("errore", "Un altro utente ha appena modificato gli stessi dati: ricaricare la pagina e riprovare.");
        }
        return "redirect:" + seErrore;
    }
}
