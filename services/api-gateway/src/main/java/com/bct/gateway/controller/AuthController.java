package com.bct.gateway.controller;

import com.bct.gateway.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Point d'entrée unique où un mot de passe transite en clair (sur la
 * connexion TLS/HTTP) — une seule fois, à la connexion. Toutes les
 * requêtes suivantes utilisent le jeton JWT émis ici, jamais le mot de passe.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ReactiveUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(ReactiveUserDetailsService userDetailsService,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String username, String role, long expiresIn) {}

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.password() == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return userDetailsService.findByUsername(request.username())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .map(user -> {
                    String role = user.getAuthorities().iterator().next()
                            .getAuthority().replaceFirst("^ROLE_", "");
                    String token = jwtService.generateToken(user.getUsername(), role);
                    return ResponseEntity.ok(
                            new LoginResponse(token, user.getUsername(), role, jwtService.expirationSeconds()));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
