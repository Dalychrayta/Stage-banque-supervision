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

    @PrePersist
    protected void onCreate() {
        if (triggeredAt == null) triggeredAt = LocalDateTime.now();
        if (status == null) status = ActionStatus.PENDING;
        if (isAutomatic == null) isAutomatic = true;
    }
}
