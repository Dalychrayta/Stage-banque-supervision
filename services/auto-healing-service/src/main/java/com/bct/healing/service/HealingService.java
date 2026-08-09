package com.bct.healing.service;

import com.bct.healing.client.RcaServiceClient;
import com.bct.healing.model.ActionStatus;
import com.bct.healing.model.ActionType;
import com.bct.healing.model.HealingAction;
import com.bct.healing.repository.HealingActionRepository;
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
public class HealingService {

    private final HealingActionRepository repository;
    private final RcaServiceClient rcaServiceClient;

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
        String resultMessage = executeAction(saved, rule);
        saved.setResultMessage(resultMessage);
        saved.setStatus(ActionStatus.SUCCESS);
        saved.setCompletedAt(LocalDateTime.now());
        repository.save(saved);

        log.info("Action {} exécutée pour {} — résultat: {}", rule.actionType(), resourceId, resultMessage);

        // Une action automatique réussie referme l'incident RCA d'origine.
        // Les actions non automatiques (NOTIFY_TEAM) laissent l'incident ouvert
        // pour intervention humaine.
        if (rule.isAutomatic() && incidentId != null) {
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

    private String executeAction(HealingAction action, HealingRule rule) {
        // Dans l'environnement de démonstration, on simule l'exécution
        // En production, ici on appellerait les APIs de gestion d'infrastructure
        return switch (rule.actionType()) {
            case RESTART_SERVICE -> "[SIMULÉ] Service " + action.getResourceName() + " redémarré avec succès.";
            case KILL_PROCESS -> "[SIMULÉ] Processus CPU-intensifs terminés sur " + action.getResourceName();
            case FREE_DISK_SPACE -> "[SIMULÉ] 2.3 GB libérés sur " + action.getResourceName();
            case CLEAR_CACHE -> "[SIMULÉ] Cache vidé sur " + action.getResourceName();
            case SCALE_UP -> "[SIMULÉ] Instance supplémentaire démarrée pour " + action.getResourceName();
            case NOTIFY_TEAM -> "Notification envoyée à l'équipe technique pour " + action.getResourceName();
            default -> "Action enregistrée. Intervention manuelle requise.";
        };
    }

    public List<HealingAction> getAll() {
        return repository.findTop500ByOrderByTriggeredAtDesc();
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
        String result = executeAction(saved, rule);
        saved.setResultMessage(result);
        saved.setStatus(ActionStatus.SUCCESS);
        saved.setCompletedAt(LocalDateTime.now());
        return repository.save(saved);
    }

    record HealingRule(ActionType actionType, String description, boolean isAutomatic) {}
}
