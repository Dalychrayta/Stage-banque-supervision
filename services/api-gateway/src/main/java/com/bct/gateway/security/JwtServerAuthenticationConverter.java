package com.bct.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extrait le jeton du header "Authorization: Bearer &lt;token&gt;". Ne le
 * valide pas ici — c'est le rôle de {@link JwtReactiveAuthenticationManager}.
 * Aucun header ou mauvais préfixe => Mono vide, la requête suit son cours
 * sans être authentifiée (elle sera rejetée plus loin si la route l'exige).
 */
@Component
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Mono.empty();
        }
        String token = header.substring(BEARER_PREFIX.length());
        // Authentification "non authentifiée" à ce stade : le token brut est
        // porté comme principal, le ReactiveAuthenticationManager le vérifie.
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }
}
