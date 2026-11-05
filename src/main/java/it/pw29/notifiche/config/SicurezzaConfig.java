package it.pw29.notifiche.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Sicurezza applicativa (ADR-0009). Due catene di filtri separate, perché i due punti d'ingresso hanno
 * modelli di autenticazione diversi e il CSRF va deciso sul modello, non sul percorso:
 * <ul>
 *   <li><b>API REST</b> ({@code /api/**}): solo HTTP Basic, senza sessione. Il contesto di sicurezza non è
 *       letto né salvato in sessione, quindi un cookie ottenuto dal login a form non autentica le API: una
 *       richiesta forgiata da un altro sito non ha credenziali. La risposta 401 non contiene
 *       {@code WWW-Authenticate}, così il browser non memorizza credenziali Basic da rimandare in automatico.
 *       Per questo il CSRF è disattivato solo qui.</li>
 *   <li><b>Interfaccia web</b> (tutto il resto): login a form con sessione, CSRF attivo, cookie di sessione
 *       {@code SameSite=Strict} (application.yml).</li>
 * </ul>
 * Autorizzazione: i permessi per operazione sono sui metodi dei servizi ({@code @PreAuthorize}), quindi
 * valgono per entrambi i punti d'ingresso. Gerarchia: APPROVATORE include i permessi di OPERATORE; il
 * principio dei quattro occhi è imposto dal dominio ({@code VersioneDocumento.trasmetti}).
 */
@Configuration
@EnableMethodSecurity
public class SicurezzaConfig {

    @Bean
    @Order(1)
    SecurityFilterChain filtriApi(HttpSecurity http) throws Exception {
        HttpStatusEntryPoint nonAutenticato = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .httpBasic(b -> b.authenticationEntryPoint(nonAutenticato))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(c -> c.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .exceptionHandling(e -> e.authenticationEntryPoint(nonAutenticato))
                .csrf(c -> c.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filtriInterfaccia(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/fonti/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/incidenti", false).permitAll())
                .logout(l -> l.logoutSuccessUrl("/login?uscita").permitAll())
                // Le pagine non si incorniciano in siti terzi (clickjacking): DENY è già il valore predefinito,
                // qui è dichiarato. Il CSRF resta attivo con il token nei moduli e nell'intestazione X-CSRF-TOKEN.
                .headers(h -> h.frameOptions(fo -> fo.deny()))
                .exceptionHandling(e -> e.accessDeniedHandler(sessioneScaduta()));
        return http.build();
    }

    /**
     * Un modulo aperto prima della scadenza della sessione (30 minuti di inattività) porta il token CSRF della
     * sessione scaduta: il rifiuto è corretto, ma l'utente va riportato all'accesso con una spiegazione, non a una
     * pagina d'errore 403. Il rimando vale solo se la richiesta indica una sessione non più valida; ogni altro
     * rifiuto (token mancante o errato in una sessione attiva, richiesta forgiata) resta un 403.
     */
    static AccessDeniedHandler sessioneScaduta() {
        AccessDeniedHandler predefinito = new AccessDeniedHandlerImpl();
        return (richiesta, risposta, eccezione) -> {
            if (eccezione instanceof CsrfException && richiesta.getRequestedSessionId() != null
                    && !richiesta.isRequestedSessionIdValid()) {
                risposta.sendRedirect(richiesta.getContextPath() + "/login?scaduta");
            } else {
                predefinito.handle(richiesta, risposta, eccezione);
            }
        };
    }

    @Bean
    static RoleHierarchy gerarchiaRuoli() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_APPROVATORE > ROLE_OPERATORE");
    }

    @Bean
    static MethodSecurityExpressionHandler gestoreEspressioni(RoleHierarchy gerarchia) {
        DefaultMethodSecurityExpressionHandler h = new DefaultMethodSecurityExpressionHandler();
        h.setRoleHierarchy(gerarchia);
        return h;
    }

    /** Codifica delegante: riconosce il prefisso {bcrypt} degli hash memorizzati. */
    @Bean
    PasswordEncoder codificaPassword() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
