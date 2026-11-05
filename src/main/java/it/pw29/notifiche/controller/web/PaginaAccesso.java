package it.pw29.notifiche.controller.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pagina di accesso dell'interfaccia (login a form, ADR-0009). Le credenziali dimostrative si mostrano solo nei
 * profili che caricano gli utenti demo ({@code notifiche.interfaccia.credenziali-demo}).
 */
@Controller
public class PaginaAccesso {

    private final boolean credenzialiDemo;

    public PaginaAccesso(@Value("${notifiche.interfaccia.credenziali-demo:false}") boolean credenzialiDemo) {
        this.credenzialiDemo = credenzialiDemo;
    }

    @GetMapping("/login")
    public String accesso(Model m) {
        m.addAttribute("credenzialiDemo", credenzialiDemo);
        return "interfaccia/accesso";
    }
}
