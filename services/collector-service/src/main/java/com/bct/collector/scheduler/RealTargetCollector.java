package com.bct.collector.scheduler;

import com.bct.collector.client.DiscoveryServiceClient;
import com.bct.collector.model.MetricSnapshot;
import com.bct.collector.service.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Collecte de VRAIES métriques depuis une instance réelle (PlatformeBack,
 * un ancien projet perso relancé pour servir de cible de test), via son
 * endpoint actuator — au lieu d'inventer des chiffres comme le fait
 * SimulatorScheduler pour les 4 autres ressources.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealTargetCollector {

    static final String RESOURCE_ID = "srv-002";
    static final String RESOURCE_NAME = "auth-server-01";

    private final CollectorService collectorService;
    private final DiscoveryServiceClient discoveryClient;

    @Value("${real-target.actuator-url:http://localhost:8085/actuator}")
    private String actuatorBaseUrl;

    private WebClient client() {
        return WebClient.create(actuatorBaseUrl);
    }

    @Scheduled(fixedDelayString = "${collector.metrics.interval-ms:30000}")
    public void collectRealMetric() {
        long startedAt = System.currentTimeMillis();
        try {
            double memUsed = fetchMetricValue("/metrics/jvm.memory.used?tag=area:heap");
            double memMax = fetchMetricValue("/metrics/jvm.memory.max?tag=area:heap");
            double cpuUsage = fetchMetricValue("/metrics/system.cpu.usage"); // fraction 0.0 - 1.0
            double diskPercent = fetchDiskUsagePercent();

            // Le temps de réponse est mesuré pour de vrai : le temps que la cible a
            // effectivement mis à répondre à nos propres appels actuator ci-dessus.
            double responseTimeMs = System.currentTimeMillis() - startedAt;
            double errorRatePercent = fetchRealErrorRatePercent();

            double memoryPercent = percentOf(memUsed, memMax);
            double cpuPercent = clampPercent(cpuUsage * 100);

            MetricSnapshot metric = MetricSnapshot.builder()
                    .resourceId(RESOURCE_ID)
                    .resourceName(RESOURCE_NAME)
                    .resourceType("SERVER")
                    .cpuUsage(cpuPercent)
                    .memoryUsage(memoryPercent)
                    .diskUsage(diskPercent)
                    .networkInMbps(0.0)
                    .networkOutMbps(0.0)
                    .responseTimeMs(responseTimeMs)
                    .errorRate(errorRatePercent)
                    .requestCount(0)
                    .collectedAt(LocalDateTime.now())
                    .build();

            collectorService.saveMetric(metric);
            discoveryClient.updateStatus(RESOURCE_ID, "UP");
            log.debug("Métrique RÉELLE collectée pour {} — CPU={}%, mémoire={}%, disque={}%, latence={}ms",
                    RESOURCE_NAME, String.format("%.1f", cpuPercent), String.format("%.1f", memoryPercent),
                    String.format("%.1f", diskPercent), String.format("%.0f", responseTimeMs));
        } catch (Exception e) {
            // Comportement propre : si la cible réelle est injoignable, on ne
            // laisse pas son statut mentir sur le dashboard — on le marque DOWN
            // au lieu de rester silencieusement sur son dernier statut connu.
            discoveryClient.updateStatus(RESOURCE_ID, "DOWN");
            log.warn("Cible réelle {} injoignable — marquée DOWN. Raison: {}", RESOURCE_NAME, e.getMessage());
        }
    }

    static double percentOf(double used, double max) {
        return max > 0 ? clampPercent((used / max) * 100) : 0;
    }

    static double clampPercent(double value) {
        return Math.max(0, Math.min(value, 100));
    }

    @SuppressWarnings("unchecked")
    private double fetchMetricValue(String path) {
        Map<String, Object> response = client().get().uri(path)
                .retrieve().bodyToMono(Map.class).block();
        List<Map<String, Object>> measurements = (List<Map<String, Object>>) response.get("measurements");
        return ((Number) measurements.get(0).get("value")).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private double fetchDiskUsagePercent() {
        Map<String, Object> health = client().get().uri("/health")
                .retrieve().bodyToMono(Map.class).block();
        Map<String, Object> components = (Map<String, Object>) health.get("components");
        Map<String, Object> diskSpace = (Map<String, Object>) components.get("diskSpace");
        Map<String, Object> details = (Map<String, Object>) diskSpace.get("details");
        double total = ((Number) details.get("total")).doubleValue();
        double free = ((Number) details.get("free")).doubleValue();
        return total > 0 ? clampPercent(((total - free) / total) * 100) : 0;
    }

    /**
     * Taux d'erreur réel basé sur le vrai trafic HTTP observé par PlatformeBack
     * (compteur http.server.requests). Si aucune requête n'a encore été vue,
     * actuator répond 404 sur ce compteur — c'est normal, on renvoie alors 0%
     * (vrai constat : "aucune erreur observée", pas une valeur inventée).
     */
    private double fetchRealErrorRatePercent() {
        try {
            double total = fetchMetricValue("/metrics/http.server.requests");
            double errors = fetchMetricValueOrZero("/metrics/http.server.requests?tag=outcome:SERVER_ERROR");
            return total > 0 ? clampPercent((errors / total) * 100) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private double fetchMetricValueOrZero(String path) {
        try {
            return fetchMetricValue(path);
        } catch (Exception e) {
            return 0;
        }
    }
}
