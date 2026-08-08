package com.bct.rca.service;

import com.bct.rca.kafka.RcaResultProducer;
import com.bct.rca.model.AnalysisStatus;
import com.bct.rca.model.IncidentAnalysis;
import com.bct.rca.repository.IncidentAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RcaService {

    private final IncidentAnalysisRepository repository;
    private final RcaResultProducer rcaResultProducer;

    @Transactional
    public IncidentAnalysis analyzeAnomaly(Map<String, Object> anomalyEvent) {
        String resourceId = (String) anomalyEvent.getOrDefault("resourceId", "unknown");
        String resourceName = (String) anomalyEvent.getOrDefault("resourceName", "unknown");
        String severity = (String) anomalyEvent.getOrDefault("severity", "WARNING");
        String anomalousMetrics = anomalyEvent.getOrDefault("anomalousMetrics", "[]").toString();
        Double anomalyScore = anomalyEvent.get("anomalyScore") instanceof Number n ? n.doubleValue() : -0.5;

        // Analyse de la cause racine par corrélation des métriques
        RcaResult result = determineRootCause(anomalyEvent);

        IncidentAnalysis analysis = IncidentAnalysis.builder()
                .resourceId(resourceId)
                .resourceName(resourceName)
                .anomalyScore(anomalyScore)
                .severity(severity)
                .anomalousMetrics(anomalousMetrics)
                .rootCause(result.rootCause())
                .causeCategory(result.category())
                .confidenceScore(result.confidence())
                .recommendation(result.recommendation())
                .correlatedLogs("[]")
                .status(AnalysisStatus.OPEN)
                .detectedAt(LocalDateTime.now())
                .build();

        IncidentAnalysis saved = repository.save(analysis);
        rcaResultProducer.sendRcaResult(saved);
        log.info("Analyse RCA terminée pour {} — catégorie: {}", resourceId, result.category());
        return saved;
    }

    private RcaResult determineRootCause(Map<String, Object> event) {
        Double cpu = getDouble(event, "cpuUsage");
        Double memory = getDouble(event, "memoryUsage");
        Double disk = getDouble(event, "diskUsage");
        Double responseTime = getDouble(event, "responseTimeMs");
        Double errorRate = getDouble(event, "errorRate");

        // Règles de corrélation pour identifier la cause
        if (cpu != null && cpu > 90) {
            return new RcaResult(
                    "CPU_SATURATION",
                    "Saturation CPU détectée (" + String.format("%.1f", cpu) + "%) — processus consommateurs excessifs.",
                    0.90,
                    "Identifier et terminer les processus CPU-intensifs. Envisager un scaling horizontal."
            );
        }
        if (memory != null && memory > 90) {
            return new RcaResult(
                    "MEMORY_EXHAUSTION",
                    "Mémoire saturée (" + String.format("%.1f", memory) + "%) — possible fuite mémoire.",
                    0.88,
                    "Redémarrer le service pour libérer la mémoire. Analyser les heap dumps."
            );
        }
        if (disk != null && disk > 90) {
            return new RcaResult(
                    "DISK_FULL",
                    "Espace disque critique (" + String.format("%.1f", disk) + "%) — écriture impossible imminente.",
                    0.95,
                    "Nettoyer les logs anciens et archiver les données. Augmenter l'espace disque."
            );
        }
        if (responseTime != null && responseTime > 3000) {
            return new RcaResult(
                    "HIGH_LATENCY",
                    "Temps de réponse élevé (" + String.format("%.0f", responseTime) + "ms) — goulot d'étranglement probable.",
                    0.80,
                    "Vérifier les requêtes SQL lentes et les dépendances réseau."
            );
        }
        if (errorRate != null && errorRate > 10) {
            return new RcaResult(
                    "HIGH_ERROR_RATE",
                    "Taux d'erreur élevé (" + String.format("%.1f", errorRate) + "%) — dysfonctionnement applicatif.",
                    0.85,
                    "Analyser les logs d'erreurs et vérifier les dépendances du service."
            );
        }

        return new RcaResult(
                "UNKNOWN",
                "Anomalie détectée sans cause clairement identifiée. Analyse manuelle recommandée.",
                0.50,
                "Consulter les logs détaillés et contacter l'équipe technique."
        );
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number n ? n.doubleValue() : null;
    }

    public List<IncidentAnalysis> getAll() {
        return repository.findTop10ByOrderByAnalyzedAtDesc();
    }

    public List<IncidentAnalysis> getByResource(String resourceId) {
        return repository.findByResourceIdOrderByAnalyzedAtDesc(resourceId);
    }

    public List<IncidentAnalysis> getOpenIncidents() {
        return repository.findByStatusOrderByAnalyzedAtDesc(AnalysisStatus.OPEN);
    }

    @Transactional
    public IncidentAnalysis resolve(Long id) {
        IncidentAnalysis analysis = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident non trouvé: " + id));
        analysis.setStatus(AnalysisStatus.RESOLVED);
        analysis.setResolvedAt(LocalDateTime.now());
        return repository.save(analysis);
    }

    public Map<String, Long> getStats() {
        return Map.of(
                "open", repository.countByStatus(AnalysisStatus.OPEN),
                "resolved", repository.countByStatus(AnalysisStatus.RESOLVED),
                "total", repository.count()
        );
    }

    record RcaResult(String category, String rootCause, double confidence, String recommendation) {}
}
