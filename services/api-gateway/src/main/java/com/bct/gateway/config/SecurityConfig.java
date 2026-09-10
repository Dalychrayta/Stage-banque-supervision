package com.bct.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * L'API Gateway est un "resource server" OAuth2 : il ne connaît aucun mot de
 * passe et ne fabrique aucun jeton. Keycloak (realm "bct") gère les comptes,
 * les rôles et la connexion, et signe les jetons. Ici on se contente de :
 *   - vérifier la signature et l'émetteur du jeton (issuer-uri en config)
 *   - lire les rôles depuis realm_access.roles
 *   - appliquer les règles d'accès par rôle ci-dessous
 *
 * Rôles : VIEWER (lecture seule), OPERATOR (peut agir sur les incidents),
 * ADMIN (gestion faite dans la console Keycloak, pas d'API dédiée ici).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/**").permitAll()

                        // Actions qui modifient l'état des systèmes surveillés
                        // ou des incidents : réservées à OPERATOR et ADMIN.
                        .pathMatchers(HttpMethod.POST,  "/api/healing/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/healing/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST,  "/api/rca/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/rca/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST,  "/api/discovery/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/discovery/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.PUT,   "/api/discovery/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("OPERATOR", "ADMIN")

                        // Tout le reste (consultation) : n'importe quel rôle authentifié.
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakAuthenticationConverter())))
                .build();
    }

    /**
     * Keycloak place les rôles du realm dans le claim realm_access.roles.
     * Spring, par défaut, ne regarde que "scope"/"scp" — d'où ce convertisseur
     * qui transforme chaque rôle en autorité ROLE_&lt;NOM&gt;.
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                for (Object role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return authorities;
        });

        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}
