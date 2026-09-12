package com.bct.healing.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "BCT_HEALING_ACTIONS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "healing_seq")
    @SequenceGenerator(name = "healing_seq", sequenceName = "BCT_HEALING_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "RESOURCE_ID", nullable = false, length = 100)
    private String resourceId;

    @Column(name = "RESOURCE_NAME", length = 200)
    private String resourceName;

    @Column(name = "INCIDENT_ID")
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTION_TYPE", nullable = false, length = 50)
    private ActionType actionType;

    @Column(name = "CAUSE_CATEGORY", length = 100)
    private String causeCategory;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 30)
    private ActionStatus status;

    @Column(name = "RESULT_MESSAGE", length = 1000)
    private String resultMessage;

    @Column(name = "TRIGGERED_AT")
    private LocalDateTime triggeredAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column(name = "IS_AUTOMATIC")
    private Boolean isAutomatic;

    /**
     * L'acteur de l'action, jamais vide : soit "utilisateur:<nom>" pour une
     * action humaine, soit "systeme:auto-healing" quand la plateforme a décidé
     * seule. Le nom d'un humain vient du jeton Keycloak vérifié par ce service
     * lui-même — il est donc prouvé, pas simplement déclaré.
     */
    @Column(name = "TRIGGERED_BY", length = 100)
    private String triggeredBy;

    /**
     * Pourquoi l'action a eu lieu. Pour un humain, la justification qu'il a
     * saisie. Pour la machine, la règle appliquée et l'incident d'origine —
     * la preuve que la décision a suivi une règle déclarée, et non improvisé.
     */
    @Column(name = "TRIGGER_REASON", length = 500)
    private String triggerReason;

    /** Acteur enregistré pour toute action décidée par la plateforme elle-même. */
    public static final String SYSTEM_ACTOR = "systeme:auto-healing";

    /** Préfixe des acteurs humains, suivi du nom d'utilisateur Keycloak. */
    public static final String USER_ACTOR_PREFIX = "utilisateur:";

    @PrePersist
    protected void onCreate() {
        if (triggeredAt == null) triggeredAt = LocalDateTime.now();
        if (status == null) status = ActionStatus.PENDING;
        if (isAutomatic == null) isAutomatic = true;
        // Filet de sécurité : une action sans acteur n'a aucune valeur pour un
        // auditeur. Si un chemin de code oubliait de le renseigner, l'action
        // est attribuée à la plateforme plutôt que laissée anonyme.
        if (triggeredBy == null || triggeredBy.isBlank()) triggeredBy = SYSTEM_ACTOR;
    }
}
