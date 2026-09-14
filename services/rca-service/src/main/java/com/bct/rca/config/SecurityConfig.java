package com.bct.rca.config;

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
 * Pourquoi : ce service est directement joignable (port 8083) sans passer par
 * la passerelle. Sans cette vérification, n'importe qui l'atteignant pouvait :
 *  - écrire n'importe quel nom dans "correctedBy" (audit falsifiable, même
 *    défaut que celui corrigé sur auto-healing-service) ;
 *  - surtout, POST /api/rca/analyze avec un diagnostic fabriqué, publié tel
 *    quel sur Kafka — que auto-healing-service consomme pour déclencher de
 *    VRAIES actions sur srv-002. Un accès direct à ce port suffisait donc à
 *    contourner toute la chaîne de détection (métriques, modèle, seuils) et
 *    provoquer un redémarrage réel.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Sondes de santé et métriques : lues par Docker et Prometheus,
                // qui n'ont pas de compte Keycloak.
                .requestMatchers("/actuator/**").permitAll()
                // Écrire un diagnostic ou modifier un incident exige le pouvoir d'agir.
                .requestMatchers(HttpMethod.POST, "/api/rca/**").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/rca/**").hasAnyRole("OPERATOR", "ADMIN")
                // La consultation reste ouverte à tout compte authentifié,
                // y compris en lecture seule (VIEWER).
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
