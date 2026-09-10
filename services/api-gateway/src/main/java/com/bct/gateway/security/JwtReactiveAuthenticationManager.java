package com.bct.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Vérifie la signature et l'expiration du jeton porté par l'Authentication
 * (voir {@link JwtServerAuthenticationConverter}). Si valide, reconstruit
 * une Authentication pleinement authentifiée avec le rôle porté par le jeton.
 */
@Component
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;

    public JwtReactiveAuthenticationManager(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.justOrEmpty(jwtService.validate(token))
                .map(this::toAuthenticatedToken)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Jeton invalide ou expiré")));
    }

    private Authentication toAuthenticatedToken(Claims claims) {
        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }
}
