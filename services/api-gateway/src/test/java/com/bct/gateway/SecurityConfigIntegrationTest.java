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
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que l'authentification JWT du Gateway est bien appliquée (cf.
 * SecurityConfig / AuthController) sans dépendre d'un vrai Eureka Server.
 *
 * Le ReactiveDiscoveryClient est remplacé par un stub (au lieu d'être
 * désactivé) pour que la chaîne de filtres Gateway/CORS se comporte
 * exactement comme en production — la désactiver casse la résolution
 * des routes lb:// et fausse le comportement CORS observé dans ce test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "security.admin.username=admin",
        "security.admin.password=test-password-123",
        "security.jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "security.jwt.expiration-ms=3600000"
})
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
class SecurityConfigIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @TestConfiguration
    static class StubDiscoveryClientConfig {
        @Bean
        ReactiveDiscoveryClient reactiveDiscoveryClient() {
            ServiceInstance instance = new DefaultServiceInstance(
                    "discovery-service-1", "discovery-service", "localhost", 8081, false);
            return new ReactiveDiscoveryClient() {
                @Override
                public String description() {
                    return "stub";
                }

                @Override
                public Flux<ServiceInstance> getInstances(String serviceId) {
                    return "discovery-service".equals(serviceId) ? Flux.just(instance) : Flux.empty();
                }

                @Override
                public Flux<String> getServices() {
                    return Flux.just("discovery-service");
                }
            };
        }
    }

    @Test
    void login_shouldRejectWrongPassword() {
        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void login_shouldIssueTokenWithValidCredentials() {
        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "test-password-123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty()
                .jsonPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    void protectedRoute_shouldReject401WithoutToken() {
        webTestClient.get().uri("/api/discovery/resources")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_shouldReject401WithInvalidToken() {
        webTestClient.get().uri("/api/discovery/resources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_shouldNotReturn401WithValidToken() {
        String token = login("admin", "test-password-123");

        // Pas de discovery-service réel en test : on vérifie seulement que
        // l'authentification passe (la requête peut ensuite échouer plus loin
        // dans le routage, mais plus jamais sur un 401).
        webTestClient.get().uri("/api/discovery/resources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void actuatorHealth_shouldBeAccessibleWithoutToken() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        Map<String, Object> body = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return (String) body.get("token");
    }
}
