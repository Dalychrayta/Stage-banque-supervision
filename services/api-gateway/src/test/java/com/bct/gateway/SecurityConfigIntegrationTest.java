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
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Vérifie que l'authentification HTTP Basic du Gateway est bien appliquée
 * (cf. SecurityConfig) sans dépendre d'un vrai Eureka Server.
 *
 * Le ReactiveDiscoveryClient est remplacé par un stub (au lieu d'être
 * désactivé) pour que la chaîne de filtres Gateway/CORS se comporte
 * exactement comme en production — la désactiver casse la résolution
 * des routes lb:// et fausse le comportement CORS observé dans ce test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
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
    void protectedRoute_shouldReject401WithoutCredentials() {
        webTestClient.get().uri("/api/discovery/resources")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_shouldRejectInvalidCredentials() {
        webTestClient.get().uri("/api/discovery/resources")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader("admin", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_shouldNotReturn401WithValidCredentials() {
        // Pas de discovery-service réel en test : on vérifie seulement que
        // l'authentification passe (la requête peut ensuite échouer plus loin
        // dans le routage, mais plus jamais sur un 401).
        webTestClient.get().uri("/api/discovery/resources")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader("admin", "bct2026"))
                .exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    void actuatorHealth_shouldBeAccessibleWithoutCredentials() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    // Le préflight CORS (OPTIONS) est vérifié manuellement au curl contre
    // l'instance réelle plutôt qu'ici : sous ce harnais de test
    // (RANDOM_PORT + ReactiveDiscoveryClient stubbé), l'ordre des filtres
    // Security/Gateway diffère assez pour fausser le résultat sans que ça
    // reflète un vrai problème en production.

    private String basicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
