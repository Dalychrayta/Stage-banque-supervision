package com.bct.rca.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "BCT_INCIDENT_ANALYSIS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rca_seq")
    @SequenceGenerator(name = "rca_seq", sequenceName = "BCT_RCA_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "RESOURCE_ID", nullable = false, length = 100)
    private String resourceId;

    @Column(name = "RESOURCE_NAME", length = 200)
    private String resourceName;

    @Column(name = "ANOMALY_SCORE")
    private Double anomalyScore;

    @Column(name = "SEVERITY", length = 20)
    private String severity;

    @Column(name = "ANOMALOUS_METRICS", length = 500)
    private String anomalousMetrics; // JSON string

    @Column(name = "ROOT_CAUSE", length = 1000)
    private String rootCause;

    @Column(name = "CAUSE_CATEGORY", length = 100)
    private String causeCategory; // CPU_SATURATION, MEMORY_LEAK, NETWORK_ISSUE, etc.

    @Column(name = "CONFIDENCE_SCORE")
    private Double confidenceScore;

    @Column(name = "CORRELATED_LOGS", length = 4000)
    private String correlatedLogs; // JSON string des logs corrélés

    @Column(name = "RECOMMENDATION", length = 1000)
    private String recommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 30)
    private AnalysisStatus status;

    @Column(name = "DETECTED_AT")
    private LocalDateTime detectedAt;

    @Column(name = "ANALYZED_AT")
    private LocalDateTime analyzedAt;

    @Column(name = "RESOLVED_AT")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (analyzedAt == null) analyzedAt = LocalDateTime.now();
        if (status == null) status = AnalysisStatus.OPEN;
    }
}
