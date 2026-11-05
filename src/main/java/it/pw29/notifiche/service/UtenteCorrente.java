package it.pw29.notifiche.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Utente autenticato della richiesta corrente, usato per autore, approvatore e audit. */
@Component
public class UtenteCorrente {

    public static final String SISTEMA = "sistema";

    public String username() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || !a.isAuthenticated() ? SISTEMA : a.getName();
    }
}
