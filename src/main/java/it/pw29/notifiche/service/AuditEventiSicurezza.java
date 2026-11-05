package it.pw29.notifiche.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Riporta nel registro di audit gli accessi interattivi riusciti (login da form) e tutti i tentativi
 * falliti. Le autenticazioni HTTP Basic riuscite non sono registrate: avvengono a ogni chiamata API.
 */
@Component
public class AuditEventiSicurezza {

    private final AuditService audit;

    public AuditEventiSicurezza(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void accessoRiuscito(InteractiveAuthenticationSuccessEvent e) {
        audit.registraEvento(e.getAuthentication().getName(), "ACCESSO", "Accesso interattivo riuscito");
    }

    @EventListener
    public void accessoFallito(AbstractAuthenticationFailureEvent e) {
        audit.registraEvento(String.valueOf(e.getAuthentication().getName()), "ACCESSO_FALLITO",
                e.getException().getClass().getSimpleName());
    }
}
