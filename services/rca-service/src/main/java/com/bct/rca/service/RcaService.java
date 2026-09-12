package com.bct.rca.service;

import com.bct.rca.kafka.RcaResultProducer;
import com.bct.rca.model.AnalysisStatus;
import com.bct.rca.model.IncidentAnalysis;
import com.bct.rca.repository.IncidentAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        Long sourceMetricId = anomalyEvent.get("metricId") instanceof Number n ? n.longValue() : null;

        // Idempotence : si ce même événement metricId a déjà été traité
        // (redélivrance Kafka après un redémarrage avant commit d'offset),
        // on renvoie l'incident existant plutôt que d'en créer un doublon.
        if (sourceMetricId != null) {
            var existing = repository.findBySourceMetricId(sourceMetricId);
            if (existing.isPresent()) {
                log.info("Anomalie déjà traitée pour metricId={} — incident #{} réutilisé, pas de doublon",
                        sourceMetricId, existing.get().getId());
                return existing.get();
            }
        }

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
                .sourceMetricId(sourceMetricId)
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

        // Aucun seuil absolu franchi. Cela ne veut pas dire qu'il n'y a rien :
        // le moteur d'IA compare au comportement NORMAL APPRIS de la ressource,
        // pas à des seuils universels. Une mémoire qui double par rapport à sa
        // normale est une vraie alerte, même à 40 % — c'est même le seul moyen
        // de voir venir une panne avant qu'elle n'atteigne 90 %.
        //
        // On exploite donc la métrique que le moteur a désignée comme la plus
        // déviante. Confiance plus basse que pour un seuil absolu : on sait
        // QUOI dévie, on est moins sûr de la cause exacte.
        RcaResult fromDeviation = classifyFromDeviation(event);
        if (fromDeviation != null) {
            return fromDeviation;
        }

        return new RcaResult(
                "UNKNOWN",
                "Anomalie détectée sans cause clairement identifiée. Analyse manuelle recommandée.",
                0.50,
                "Consulter les logs détaillés et contacter l'équipe technique."
        );
    }

    /**
     * Traduit la métrique la plus déviante signalée par le moteur d'IA en cause
     * probable. La liste arrive triée : la plus déviante d'abord.
     */
    private RcaResult classifyFromDeviation(Map<String, Object> event) {
        String flagged = firstFlaggedMetric(event);
        if (flagged == null) {
            return null;
        }
        return switch (flagged) {
            case "cpu_usage" -> new RcaResult("CPU_SATURATION",
                    "CPU nettement au-dessus du comportement habituel de la ressource.", 0.65,
                    "Identifier les processus récemment lancés avant que la saturation ne soit atteinte.");
            case "memory_usage" -> new RcaResult("MEMORY_EXHAUSTION",
                    "Mémoire nettement au-dessus du comportement habituel — fuite mémoire possible.", 0.65,
                    "Surveiller la tendance mémoire et redémarrer le service avant saturation.");
            case "disk_usage" -> new RcaResult("DISK_FULL",
                    "Occupation disque nettement au-dessus de l'habitude — croissance anormale.", 0.65,
                    "Identifier les fichiers en croissance et nettoyer les logs anciens.");
            case "response_time_ms" -> new RcaResult("HIGH_LATENCY",
                    "Temps de réponse nettement au-dessus de l'habitude — dégradation en cours.", 0.65,
                    "Vérifier les requêtes lentes et la charge des dépendances.");
            case "error_rate" -> new RcaResult("HIGH_ERROR_RATE",
                    "Taux d'erreur nettement au-dessus de l'habitude.", 0.65,
                    "Analyser les logs d'erreurs récents du service.");
            default -> null;
        };
    }

    /**
     * Nom de la première métrique signalée par le moteur. Le champ arrive soit
     * comme liste (message Kafka), soit comme texte (relecture d'un incident) —
     * on accepte les deux plutôt que de dépendre d'une forme précise.
     */
    static String firstFlaggedMetric(Map<String, Object> event) {
        Object raw = event.get("anomalousMetrics");
        if (raw == null) return null;
        String text = raw instanceof List<?> list
                ? (list.isEmpty() ? "" : String.valueOf(list.get(0)))
                : String.valueOf(raw).replace("[", "").replace("]", "");
        if (text.isBlank()) return null;
        String name = text.split("=")[0].trim();
        return name.isEmpty() ? null : name;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number n ? n.doubleValue() : null;
    }

    public Page<IncidentAnalysis> getAll(Pageable pageable) {
        return repository.findAllByOrderByAnalyzedAtDesc(pageable);
    }

    public List<IncidentAnalysis> getRecent(int hoursBack) {
        return repository.findByAnalyzedAtAfterOrderByAnalyzedAtDesc(LocalDateTime.now().minusHours(hoursBack));
    }

    public List<IncidentAnalysis> getByResource(String resourceId) {
        return repository.findByResourceIdOrderByAnalyzedAtDesc(resourceId);
    }

    public List<IncidentAnalysis> getOpenIncidents() {
        return repository.findByStatusOrderByAnalyzedAtDesc(AnalysisStatus.OPEN);
    }

    /**
     * Correction manuelle de la catégorie de cause par un opérateur, quand le
     * diagnostic automatique (règles à seuils) est faux. Ces corrections
     * constituent le vrai jeu d'étiquettes vérifiées par un humain — la base
     * nécessaire pour, plus tard, entraîner un classifieur de cause au lieu
     * de s'appuyer uniquement sur des règles.
     */
    @Transactional
    public IncidentAnalysis correctCategory(Long id, String newCategory, String correctedBy) {
        if (newCategory == null || newCategory.isBlank()) {
            throw new IllegalArgumentException("Catégorie de correction vide");
        }
        IncidentAnalysis analysis = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident non trouvé: " + id));
        analysis.setCorrectedCategory(newCategory.trim());
        analysis.setCorrectedBy(correctedBy != null ? correctedBy : "inconnu");
        analysis.setCorrectedAt(LocalDateTime.now());
        log.info("Incident #{} : cause corrigée de '{}' vers '{}' par {}",
                id, analysis.getCauseCategory(), newCategory, correctedBy);
        return repository.save(analysis);
    }

    @Transactional
    public IncidentAnalysis resolve(Long id) {
        IncidentAnalysis analysis = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident non trouvé: " + id));
        // Idempotent : un incident déjà résolu (ex. redélivrance Kafka) ne doit
        // pas voir sa date de résolution écrasée par un second appel.
        if (analysis.getStatus() == AnalysisStatus.RESOLVED) {
            return analysis;
        }
        analysis.setStatus(AnalysisStatus.RESOLVED);
        analysis.setResolvedAt(LocalDateTime.now());
        return repository.save(analysis);
    }

    public Map<String, Long> getStats() {
        return Map.of(
                "open", repository.countByStatus(AnalysisStatus.OPEN),
                "resolved", repository.countByStatus(AnalysisStatus.RESOLVED),
                "total", repository.count(),
                "criticalOpen", repository.countBySeverityAndStatus("CRITICAL", AnalysisStatus.OPEN),
                "warningOpen", repository.countBySeverityAndStatus("WARNING", AnalysisStatus.OPEN)
        );
    }

    record RcaResult(String category, String rootCause, double confidence, String recommendation) {}
}
