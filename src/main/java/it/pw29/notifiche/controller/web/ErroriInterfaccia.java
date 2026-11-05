package it.pw29.notifiche.controller.web;

import it.pw29.notifiche.service.RisorsaNonTrovataException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Errori delle pagine aperte con un indirizzo (GET): una risorsa inesistente mostra una pagina 404 leggibile.
 * Le azioni (POST) gestiscono i propri errori con {@link Azioni}; le API REST hanno {@code GestioneErrori}.
 */
@ControllerAdvice(basePackageClasses = ErroriInterfaccia.class)
public class ErroriInterfaccia {

    @ExceptionHandler(RisorsaNonTrovataException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String nonTrovata(RisorsaNonTrovataException e, Model m) {
        m.addAttribute("titolo", "Non trovato");
        m.addAttribute("messaggio", e.getMessage());
        return "interfaccia/errore";
    }
}
