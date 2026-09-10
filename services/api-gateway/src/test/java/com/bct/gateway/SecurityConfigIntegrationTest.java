package com.bct.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que l'API Gateway applique bien les règles d'accès par rôle
 * (cf. SecurityConfig) sans dépendre d'un vrai Keycloak : le
 * ReactiveJwtDecoder est remplacé par un stub qui fabrique des jetons
 * synthétiques portant le rôle demandé dans realm_access.roles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/bct"
})
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
class SecurityConfigIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @TestConfiguration
    static class StubConfig {
        @Bean
        ReactiveDiscoveryClient reactiveDiscoveryClient() {
            ServiceInstance instance = new DefaultServiceInstance(
                    "rca-service-1", "rca-service", "localhost", 8083, false);
            return new ReactiveDiscoveryClient() {
                @Override public String description() { return "stub"; }
                @Override public Flux<ServiceInstance> getInstances(String serviceId) {
                    return "rca-service".equals(serviceId) ? Flux.just(instance) : Flux.empty();
                }
                @Override public Flux<String> getServices() { return Flux.just("rca-service"); }
            };
        }

        /** Décodeur bidon : le "token" est juste le nom du rôle voulu. */
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> {
                if (!List.of("VIEWER", "OPERATOR", "ADMIN").contains(token)) {
                    return Mono.error(new org.springframework.security.oauth2.jwt.BadJwtException("token invalide"));
                }
                Jwt jwt = Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("user-" + token)
                        .claim("preferred_username", token.toLowerCase() + ".bct")
                        .claim("realm_access", Map.of("roles", List.of(token)))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
                return Mono.just(jwt);
            };
        }
    }

    @Test
    void noToken_shouldReturn401() {
        webTestClient.get().uri("/api/rca")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidToken_shouldReturn401() {
        webTestClient.get().uri("/api/rca")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-role")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void viewer_canRead_butCannotMutate() {
        // lecture : autorisée (peut échouer plus loin dans le routage, mais jamais 401/403)
        webTestClient.get().uri("/api/rca")
                .header(HttpHeaders.AUTHORIZATION, "Bearer VIEWER")
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));

        // action : interdite pour un VIEWER
        webTestClient.patch().uri("/api/rca/1/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer VIEWER")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void operator_canMutate() {
        webTestClient.patch().uri("/api/rca/1/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer OPERATOR")
                .exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void actuatorHealth_shouldBeOpen() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
