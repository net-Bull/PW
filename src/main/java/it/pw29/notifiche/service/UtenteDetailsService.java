package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.Utente;
import it.pw29.notifiche.repository.UtenteRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Utenti applicativi letti dalla tabella {@code utente}; il ruolo diventa l'autorità ROLE_*. */
@Service
public class UtenteDetailsService implements UserDetailsService {

    private final UtenteRepository utenti;

    public UtenteDetailsService(UtenteRepository utenti) {
        this.utenti = utenti;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        Utente u = utenti.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utente sconosciuto"));
        return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .authorities(u.getRuolo().authority())
                .disabled(!u.isAttivo())
                .build();
    }
}
