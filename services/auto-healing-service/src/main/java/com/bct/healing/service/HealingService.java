package com.bct.healing.service;

import com.bct.healing.client.RcaServiceClient;
import com.bct.healing.model.ActionStatus;
import com.bct.healing.model.ActionType;
import com.bct.healing.model.HealingAction;
import com.bct.healing.repository.HealingActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /** Durée pendant laquelle la plateforme ne rejoue pas la même action sur la même ressource. */
    @Value("${healing.cooldown-minutes:10}")
    private int cooldownMinutes = 10;

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

        // Délai de garde : on n'exécute pas deux fois la même action sur la même
        // ressource coup sur coup.
        //
        // Observé en vrai : un redémarrage fait démarrer la JVM, qui consomme
        // ~95 % de CPU pendant quelques secondes ; le collecteur mesure ce pic,
        // le modèle y voit une saturation CPU, le RCA diagnostique
        // CPU_SATURATION, la remédiation redémarre — et ainsi de suite. La
        // plateforme entretenait elle-même le problème qu'elle croyait soigner.
        // C'est le battement (flapping), et sans garde-fou il peut redémarrer un
        // serveur de production en boucle.
        if (isWithinCooldown(resourceId, rule.actionType())) {
            return saveSkippedAction(resourceId, resourceName, incidentId, causeCategory, rule);
        }

        HealingAction action = HealingAction.builder()
                .resourceId(resourceId)
                .resourceName(resourceName)
                .incidentId(incidentId)
                .actionType(rule.actionType())
                .causeCategory(causeCategory)
                .description(rule.description())
                .status(ActionStatus.IN_PROGRESS)
                // Décidée par la plateforme, donc automatique — quelle que soit
                // la règle. À ne pas confondre avec rule.fixesIncident(), qui dit
                // si l'action referme le problème toute seule.
                .isAutomatic(true)
                .triggeredBy(HealingAction.SYSTEM_ACTOR)
                .triggerReason(automaticReason(causeCategory, rule.actionType(), incidentId))
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

        // L'incident RCA n'est refermé que si l'action le répare vraiment et
        // qu'elle a réussi. NOTIFY_TEAM prévient un humain sans rien réparer :
        // l'incident reste donc ouvert, en attente d'intervention.
        if (rule.fixesIncident() && result.success() && incidentId != null) {
            rcaServiceClient.resolveIncident(incidentId);
        }

        return saved;
    }

    /**
     * Vrai si la même action a déjà été exécutée sur cette ressource il y a
     * moins de {@code cooldownMinutes}. Ne s'applique qu'aux actions décidées
     * par la plateforme : un humain qui déclenche une action, justification à
     * l'appui, sait ce qu'il fait et n'a pas à être bridé par ce garde-fou.
     */
    private boolean isWithinCooldown(String resourceId, ActionType actionType) {
        return repository
                .findFirstByResourceIdAndActionTypeAndStatusNotOrderByTriggeredAtDesc(
                        resourceId, actionType, ActionStatus.SKIPPED)
                .map(HealingAction::getTriggeredAt)
                .filter(last -> last.isAfter(LocalDateTime.now().minusMinutes(cooldownMinutes)))
                .isPresent();
    }

    /**
     * Enregistre l'action refusée au lieu de la passer sous silence : un
     * auditeur doit pouvoir voir que la plateforme a voulu agir, et pourquoi
     * elle s'en est abstenue.
     */
    private HealingAction saveSkippedAction(String resourceId, String resourceName, Long incidentId,
                                            String causeCategory, HealingRule rule) {
        log.info("Action {} sur {} ignorée — délai de garde de {} min non écoulé",
                rule.actionType(), resourceId, cooldownMinutes);
        HealingAction skipped = HealingAction.builder()
                .resourceId(resourceId)
                .resourceName(resourceName)
                .incidentId(incidentId)
                .actionType(rule.actionType())
                .causeCategory(causeCategory)
                .description(rule.description())
                .status(ActionStatus.SKIPPED)
                .isAutomatic(true)
                .triggeredBy(HealingAction.SYSTEM_ACTOR)
                .triggerReason(automaticReason(causeCategory, rule.actionType(), incidentId))
                .resultMessage(String.format(
                        "Action non exécutée : la même action a déjà été appliquée sur cette ressource "
                                + "il y a moins de %d minutes (délai de garde anti-battement).", cooldownMinutes))
                .triggeredAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        return repository.save(skipped);
    }

    /**
     * Motif d'une action décidée par la plateforme. On y fige la règle telle
     * qu'elle était au moment de la décision : si la table cause → action
     * change plus tard, les actions passées restent explicables.
     */
    static String automaticReason(String causeCategory, ActionType actionType, Long incidentId) {
        String base = "Regle " + causeCategory + " -> " + actionType;
        return incidentId == null ? base : base + ", incident #" + incidentId;
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
        // l'action au lieu de la simuler.
        if (realActionExecutor.isRealTarget(action.getResourceId())) {
            switch (rule.actionType()) {
                // RESTART et KILL reviennent au même ici : la cible est un
                // unique processus Java, on l'arrête et on le relance.
                case RESTART_SERVICE, KILL_PROCESS -> {
                    return realActionExecutor.restartRealService();
                }
                // Supprime réellement les logs archivés trop anciens.
                case FREE_DISK_SPACE -> {
                    return realActionExecutor.freeRealDiskSpace();
                }
                default -> { /* les autres actions restent simulées ci-dessous */ }
            }
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

    /**
     * Action déclenchée par un humain. Le nom vient du jeton Keycloak vérifié
     * par ce service, et la justification est obligatoire : devoir écrire
     * pourquoi on redémarre un serveur de production fait partie du contrôle.
     */
    @Transactional
    public HealingAction triggerManual(String resourceId, String resourceName, ActionType actionType,
                                       String username, String reason) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Action manuelle sans utilisateur identifie");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Une action manuelle doit etre justifiee");
        }
        HealingRule rule = new HealingRule(actionType, "Action manuelle declenchee par " + username, false);
        HealingAction action = HealingAction.builder()
                .resourceId(resourceId)
                .resourceName(resourceName)
                .actionType(actionType)
                .description(rule.description())
                .status(ActionStatus.IN_PROGRESS)
                .isAutomatic(false)
                .triggeredBy(HealingAction.USER_ACTOR_PREFIX + username)
                .triggerReason(reason.trim())
                .triggeredAt(LocalDateTime.now())
                .build();
        HealingAction saved = repository.save(action);
        RealActionExecutor.ActionResult result = executeAction(saved, rule);
        saved.setResultMessage(result.message());
        saved.setStatus(result.success() ? ActionStatus.SUCCESS : ActionStatus.FAILED);
        saved.setCompletedAt(LocalDateTime.now());
        return repository.save(saved);
    }

    /**
     * Une règle de remédiation. fixesIncident dit si l'action règle le problème
     * toute seule : NOTIFY_TEAM, par exemple, prévient un humain mais ne répare
     * rien, donc l'incident reste ouvert. Ce n'est PAS la même chose que
     * "déclenchée automatiquement" — cette confusion faisait afficher "Manuel"
     * sur des actions décidées par la plateforme.
     */
    record HealingRule(ActionType actionType, String description, boolean fixesIncident) {}
}
