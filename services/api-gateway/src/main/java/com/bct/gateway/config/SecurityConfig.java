package com.bct.gateway.config;

import com.bct.gateway.security.JwtReactiveAuthenticationManager;
import com.bct.gateway.security.JwtServerAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import reactor.core.publisher.Mono;

/**
 * Authentification JWT sur l'API Gateway — point d'entrée unique de la
 * plateforme. Le mot de passe ne transite qu'une fois, à /api/auth/login
 * (voir AuthController) ; toutes les autres requêtes portent un jeton signé
 * (voir JwtService / JwtServerAuthenticationConverter / JwtReactiveAuthenticationManager).
 *
 * security.admin.username / security.admin.password / security.jwt.secret
 * n'ont plus de valeur par défaut dans le code : ils doivent être fournis
 * explicitement (variables d'environnement), pour ne plus committer de
 * mot de passe en dur dans le dépôt (voir infra/.env.example).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public MapReactiveUserDetailsService userDetailsService(
            @Value("${security.admin.username}") String username,
            @Value("${security.admin.password}") String password,
            PasswordEncoder encoder) {
        UserDetails admin = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build();
        return new MapReactiveUserDetailsService(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JwtServerAuthenticationConverter converter,
            JwtReactiveAuthenticationManager authenticationManager) {

        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(converter);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/api/auth/login").permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, ex) -> Mono.fromRunnable(() ->
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED))))
                .build();
    }
}
