package com.bct.collector.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Client vers le Discovery Service pour enregistrer les ressources
 * simulées et refléter leur statut à partir des anomalies détectées.
 */
@Component
@Slf4j
public class DiscoveryServiceClient {

    private final WebClient webClient;

    public DiscoveryServiceClient(@Value("${discovery.service.url:http://localhost:8081}") String discoveryServiceUrl) {
        this.webClient = WebClient.create(discoveryServiceUrl);
    }

    public void register(String resourceId, String name, String type) {
        register(resourceId, name, type, true);
    }

    public void register(String resourceId, String name, String type, boolean simulated) {
        try {
            webClient.post()
                    .uri("/api/resources/register")
                    .bodyValue(Map.of("resourceId", resourceId, "name", name, "type", type, "simulated", simulated))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Ressource enregistrée auprès du Discovery Service: {} (simulated={})", resourceId, simulated);
        } catch (Exception e) {
            log.warn("Impossible d'enregistrer {} auprès du Discovery Service: {}", resourceId, e.getMessage());
        }
    }

    public void updateStatus(String resourceId, String status) {
        try {
            webClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/resources/{resourceId}/status")
                            .queryParam("status", status)
                            .build(resourceId))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.debug("Impossible de mettre à jour le statut de {}: {}", resourceId, e.getMessage());
        }
    }
}
