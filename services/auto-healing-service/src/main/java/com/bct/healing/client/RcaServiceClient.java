package com.bct.healing.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client vers le RCA Service : referme automatiquement un incident quand
 * l'action corrective correspondante a été exécutée avec succès.
 */
@Component
@Slf4j
public class RcaServiceClient {

    private final WebClient webClient;

    public RcaServiceClient(@Value("${rca.service.url:http://localhost:8083}") String rcaServiceUrl) {
        this.webClient = WebClient.create(rcaServiceUrl);
    }

    public void resolveIncident(Long incidentId) {
        try {
            webClient.patch()
                    .uri("/api/rca/{id}/resolve", incidentId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Incident RCA #{} marqué résolu automatiquement (auto-healing réussi)", incidentId);
        } catch (Exception e) {
            log.warn("Impossible de résoudre automatiquement l'incident #{}: {}", incidentId, e.getMessage());
        }
    }
}
