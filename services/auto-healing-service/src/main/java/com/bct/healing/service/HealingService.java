package com.bct.healing.service;

import com.bct.healing.client.RcaServiceClient;
import com.bct.healing.model.ActionStatus;
import com.bct.healing.model.ActionType;
import com.bct.healing.model.HealingAction;
import com.bct.healing.repository.HealingActionRepository;
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
public class HealingService {

    private final HealingActionRepository repository;
    private final RcaServiceClient rcaServiceClient;
    private final RealActionExecutor realActionExecutor;

    /**
     * Détermine et déclenche l'action de remédiation selon la catégorie RCA.
     */
    @Transactional
    public HealingAction triggerHealing(Map<String, Object> rcaEvent) {
        String resourceId = (String) rcaEvent.getOrDefault("resourceId", "unknown");
        String resourceName = (String) rcaEvent.getOrDefault("resourceName", "unknown");
        String causeCategory = (String) rcaEvent.getOrDefault("causeCategory", "UNKNOWN");
        String severity = (String) rcaEvent.getOrDefault("severity", "WARNING");
        Long incidentId = rcaEvent.get("incidentId") instanceof Number n ? n.longValue() : null;

        // Idempotence : une redélivrance Kafka du même résultat RCA ne doit
        // pas déclencher une deuxième action de remédiation pour le même incident.
        if (incidentId != null && repository.existsByIncidentId(incidentId)) {
            log.info("Incident #{} déjà traité par auto-healing — pas de doublon", incidentId);
            return null;
        }

        HealingRule rule = selectRule(causeCategory, severity);

        HealingAction action = HealingAction.builder()
                .resourceId(resourceId)
                .resourceName(resourceName)
                .incidentId(incidentId)
                .actionType(rule.actionType())
                .causeCategory(causeCategory)
                .description(rule.description())
                .status(ActionStatus.IN_PROGRESS)
                .isAutomatic(rule.isAutomatic())
                .triggeredAt(LocalDateTime.now())
                .build();

        HealingAction saved = repository.save(action);

        // Exécution de l'action
        RealActionExecutor.ActionResult result = executeAction(saved, rule);
        saved.setResultMessage(result.message());
        saved.setStatus(result.success() ? ActionStatus.SUCCESS : ActionStatus.FAILED);
        saved.setCompletedAt(LocalDateTime.now());
        repository.save(saved);

        log.info("Action {} exécutée pour {} — statut: {} — résultat: {}",
                rule.actionType(), resourceId, saved.getStatus(), result.message());

        // Une action automatique referme l'incident RCA d'origine uniquement
        // si elle a réellement réussi. Les actions non automatiques
        // (NOTIFY_TEAM) ou échouées laissent l'incident ouvert pour
        // intervention humaine.
        if (rule.isAutomatic() && result.success() && incidentId != null) {
            rcaServiceClient.resolveIncident(incidentId);
        }

        return saved;
    }

    private HealingRule selectRule(String causeCategory, String severity) {
        return switch (causeCategory) {
            case "MEMORY_EXHAUSTION" -> new HealingRule(
                    ActionType.RESTART_SERVICE,
                    "Redémarrage du service pour libérer la mémoire",
                    true
            );
            case "CPU_SATURATION" -> new HealingRule(
                    ActionType.KILL_PROCESS,
                    "Terminaison des processus consommateurs de CPU",
                    true
            );
            case "DISK_FULL" -> new HealingRule(
                    ActionType.FREE_DISK_SPACE,
                    "Nettoyage des logs anciens et fichiers temporaires",
                    true
            );
            case "HIGH_LATENCY" -> new HealingRule(
                    ActionType.CLEAR_CACHE,
                    "Vidage du cache applicatif pour améliorer les performances",
                    true
            );
            case "HIGH_ERROR_RATE" -> new HealingRule(
                    ActionType.RESTART_SERVICE,
                    "Redémarrage du service suite à un taux d'erreur élevé",
                    true
            );
            default -> new HealingRule(
                    ActionType.NOTIFY_TEAM,
                    "Cause inconnue — notification de l'équipe technique recommandée",
                    false
            );
        };
    }

    private RealActionExecutor.ActionResult executeAction(HealingAction action, HealingRule rule) {
        // Cible réelle (srv-002 / PlatformeBack) : on exécute vraiment
        // l'action au lieu de la simuler, pour RESTART_SERVICE et KILL_PROCESS
        // (les deux reviennent à arrêter puis relancer le seul processus réel).
        if (realActionExecutor.isRealTarget(action.getResourceId())
                && (rule.actionType() == ActionType.RESTART_SERVICE || rule.actionType() == ActionType.KILL_PROCESS)) {
            return realActionExecutor.restartRealService();
        }

        // Dans l'environnement de démonstration, on simule l'exécution
        // En production, ici on appellerait les APIs de gestion d'infrastructure
        String message = switch (rule.actionType()) {
            case RESTART_SERVICE -> "[SIMULÉ] Service " + action.getResourceName() + " redémarré avec succès.";
            case KILL_PROCESS -> "[SIMULÉ] Processus CPU-intensifs terminés sur " + action.getResourceName();
            case FREE_DISK_SPACE -> "[SIMULÉ] 2.3 GB libérés sur " + action.getResourceName();
            case CLEAR_CACHE -> "[SIMULÉ] Cache vidé sur " + action.getResourceName();
            case SCALE_UP -> "[SIMULÉ] Instance supplémentaire démarrée pour " + action.getResourceName();
            case NOTIFY_TEAM -> "Notification envoyée à l'équipe technique pour " + action.getResourceName();
            default -> "Action enregistrée. Intervention manuelle requise.";
        };
        return new RealActionExecutor.ActionResult(true, message);
    }

    public Page<HealingAction> getAll(Pageable pageable) {
        return repository.findAllByOrderByTriggeredAtDesc(pageable);
    }

    public List<HealingAction> getByResource(String resourceId) {
        return repository.findByResourceIdOrderByTriggeredAtDesc(resourceId);
    }

    public Map<String, Long> getStats() {
        return Map.of(
                "total", repository.count(),
                "success", repository.countByStatus(ActionStatus.SUCCESS),
                "failed", repository.countByStatus(ActionStatus.FAILED),
                "pending", repository.countByStatus(ActionStatus.PENDING)
        );
    }

    @Transactional
    public HealingAction triggerManual(String resourceId, String resourceName, ActionType actionType) {
        HealingRule rule = new HealingRule(actionType, "Action manuelle déclenchée par opérateur", false);
        HealingAction action = HealingAction.builder()
                .resourceId(resourceId)
                .resourceName(resourceName)
                .actionType(actionType)
                .description(rule.description())
                .status(ActionStatus.IN_PROGRESS)
                .isAutomatic(false)
                .triggeredAt(LocalDateTime.now())
                .build();
        HealingAction saved = repository.save(action);
        RealActionExecutor.ActionResult result = executeAction(saved, rule);
        saved.setResultMessage(result.message());
        saved.setStatus(result.success() ? ActionStatus.SUCCESS : ActionStatus.FAILED);
        saved.setCompletedAt(LocalDateTime.now());
        return repository.save(saved);
    }

    record HealingRule(ActionType actionType, String description, boolean isAutomatic) {}
}
