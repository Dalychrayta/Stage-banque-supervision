package com.bct.collector.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "BCT_METRIC_SNAPSHOTS",
        indexes = {
                @Index(name = "idx_metric_resource", columnList = "RESOURCE_ID"),
                @Index(name = "idx_metric_timestamp", columnList = "COLLECTED_AT")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "metric_seq")
    @SequenceGenerator(name = "metric_seq", sequenceName = "BCT_METRICS_SEQ", allocationSize = 50)
    private Long id;

    @Column(name = "RESOURCE_ID", nullable = false, length = 100)
    private String resourceId;

    @Column(name = "RESOURCE_NAME", length = 200)
    private String resourceName;

    @Column(name = "RESOURCE_TYPE", length = 50)
    private String resourceType;

    @Column(name = "CPU_USAGE")
    private Double cpuUsage;

    @Column(name = "MEMORY_USAGE")
    private Double memoryUsage;

    @Column(name = "DISK_USAGE")
    private Double diskUsage;

    @Column(name = "NETWORK_IN_MBPS")
    private Double networkInMbps;

    @Column(name = "NETWORK_OUT_MBPS")
    private Double networkOutMbps;

    @Column(name = "RESPONSE_TIME_MS")
    private Double responseTimeMs;

    @Column(name = "ERROR_RATE")
    private Double errorRate;

    @Column(name = "REQUEST_COUNT")
    private Integer requestCount;

    @Column(name = "COLLECTED_AT", nullable = false)
    private LocalDateTime collectedAt;

    @PrePersist
    protected void onCreate() {
        if (collectedAt == null) collectedAt = LocalDateTime.now();
    }
}
