package com.bct.healing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Ce service vérifie lui-même les jetons Keycloak, en plus de la passerelle.
 *
 * Pourquoi deux vérifications : la passerelle contrôle qui entre dans la
 * plateforme, mais elle ne protège pas ce service de ce qui l'atteindrait
 * directement. Or ce service exécute de VRAIES actions (redémarrer un
 * conteneur, supprimer des fichiers) et enregistre QUI les a demandées.
 *
 * Si le nom de l'utilisateur arrivait dans un simple en-tête posé par la
 * passerelle, n'importe qui pouvant joindre ce service pourrait écrire le nom
 * de son choix — et le journal d'audit deviendrait falsifiable, donc sans
 * valeur devant un auditeur. En lisant le nom dans un jeton signé par Keycloak
 * et vérifié ici, le nom enregistré est prouvé, pas déclaré.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API sans session ni formulaire : le jeton porte l'identité à chaque appel.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Sondes de santé et métriques : lues par Docker et Prometheus,
                // qui n'ont pas de compte Keycloak.
                .requestMatchers("/actuator/**").permitAll()
                // Déclencher une action réelle exige le pouvoir d'agir.
                .requestMatchers(HttpMethod.POST, "/api/healing/**").hasAnyRole("OPERATOR", "ADMIN")
                // La consultation de l'historique reste ouverte à tout compte
                // authentifié, y compris en lecture seule (VIEWER).
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakAuthenticationConverter())));
        return http.build();
    }

    /**
     * Keycloak place les rôles du realm dans "realm_access.roles". Spring
     * Security attend des autorités préfixées "ROLE_" : on fait la traduction.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> keycloakAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities =
                    new ArrayList<>(new JwtGrantedAuthoritiesConverter().convert(jwt));
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }
}
