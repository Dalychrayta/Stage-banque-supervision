package com.bct.collector.scheduler;

import com.bct.collector.client.DiscoveryServiceClient;
import com.bct.collector.model.LogEntry;
import com.bct.collector.model.LogLevel;
import com.bct.collector.model.MetricSnapshot;
import com.bct.collector.service.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Simulateur de métriques pour l'environnement de démonstration.
 * Génère des métriques réalistes pour plusieurs ressources simulées.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimulatorScheduler {

    private final CollectorService collectorService;
    private final DiscoveryServiceClient discoveryClient;
    private final Random random = new Random();

    // Toutes les ressources connues du Discovery Service (inclut la cible réelle srv-002).
    private static final List<String[]> ALL_RESOURCES = List.of(
            new String[]{"srv-001", "payment-server-01", "SERVER"},
            new String[]{"srv-002", "auth-server-01", "SERVER"},
            new String[]{"app-001", "banking-api", "APPLICATION"},
            new String[]{"app-002", "mobile-gateway", "APPLICATION"},
            new String[]{"db-001", "oracle-primary", "DATABASE"}
    );

    // srv-002 (auth-server-01) n'est plus simulé : ses métriques viennent d'une
    // vraie instance (PlatformeBack), collectées par RealTargetCollector.
    private static final List<String[]> SIMULATED_RESOURCES = ALL_RESOURCES.stream()
            .filter(r -> !r[0].equals("srv-002"))
            .toList();

    @EventListener(ApplicationReadyEvent.class)
    public void registerSimulatedResources() {
        for (String[] resource : ALL_RESOURCES) {
            boolean simulated = !resource[0].equals("srv-002");
            discoveryClient.register(resource[0], resource[1], resource[2], simulated);
            discoveryClient.updateStatus(resource[0], "UP");
        }
        log.info("{} ressources enregistrées auprès du Discovery Service", ALL_RESOURCES.size());
    }

    @Scheduled(fixedDelayString = "${collector.metrics.interval-ms:30000}")
    public void simulateMetrics() {
        for (String[] resource : SIMULATED_RESOURCES) {
            MetricSnapshot metric = generateMetric(resource[0], resource[1], resource[2]);
            collectorService.saveMetric(metric);
        }
        log.debug("Métriques simulées générées pour {} ressources", SIMULATED_RESOURCES.size());
    }

    @Scheduled(fixedDelayString = "${collector.logs.interval-ms:60000}")
    public void simulateLogs() {
        for (String[] resource : SIMULATED_RESOURCES) {
            LogLevel level = randomLogLevel();
            LogEntry entry = LogEntry.builder()
                    .resourceId(resource[0])
                    .resourceName(resource[1])
                    .level(level)
                    .message(generateLogMessage(resource[1], level))
                    .source("simulator")
                    .logTimestamp(LocalDateTime.now())
                    .build();
            collectorService.saveLog(entry);
        }
    }

    private MetricSnapshot generateMetric(String id, String name, String type) {
        boolean isAnomaly = random.nextDouble() < 0.10;
        double cpu    = isAnomaly ? 85 + random.nextDouble() * 15 : 20 + random.nextDouble() * 50;
        double memory = isAnomaly ? 88 + random.nextDouble() * 12 : 30 + random.nextDouble() * 40;
        double disk   = 40 + random.nextDouble() * 30;
        double netIn  = 50 + random.nextDouble() * 200;
        double netOut = 20 + random.nextDouble() * 100;
        double rt     = isAnomaly ? 2000 + random.nextDouble() * 3000 : 50 + random.nextDouble() * 300;
        double err    = isAnomaly ? 5 + random.nextDouble() * 20 : random.nextDouble() * 2;

        return MetricSnapshot.builder()
                .resourceId(id).resourceName(name).resourceType(type)
                .cpuUsage(Math.min(cpu, 100)).memoryUsage(Math.min(memory, 100))
                .diskUsage(Math.min(disk, 100)).networkInMbps(netIn).networkOutMbps(netOut)
                .responseTimeMs(rt).errorRate(Math.min(err, 100))
                .requestCount(100 + random.nextInt(900))
                .collectedAt(LocalDateTime.now())
                .build();
    }

    private LogLevel randomLogLevel() {
        double r = random.nextDouble();
        if (r < 0.60) return LogLevel.INFO;
        if (r < 0.80) return LogLevel.WARN;
        if (r < 0.95) return LogLevel.ERROR;
        return LogLevel.FATAL;
    }

    private String generateLogMessage(String name, LogLevel level) {
        return switch (level) {
            case INFO  -> name + " - Traitement normal, " + (100 + random.nextInt(900)) + " req/min";
            case WARN  -> name + " - Latence élevée: " + (500 + random.nextInt(1000)) + "ms";
            case ERROR -> name + " - Erreur connexion DB, retry " + random.nextInt(3);
            case FATAL -> name + " - Service indisponible, arrêt inattendu";
            default    -> name + " - Debug message";
        };
    }
}
