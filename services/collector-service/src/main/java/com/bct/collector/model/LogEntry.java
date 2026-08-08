package com.bct.collector.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "BCT_LOG_ENTRIES",
        indexes = {
                @Index(name = "idx_log_resource", columnList = "RESOURCE_ID"),
                @Index(name = "idx_log_level", columnList = "LOG_LEVEL"),
                @Index(name = "idx_log_timestamp", columnList = "LOG_TIMESTAMP")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "log_seq")
    @SequenceGenerator(name = "log_seq", sequenceName = "BCT_LOGS_SEQ", allocationSize = 50)
    private Long id;

    @Column(name = "RESOURCE_ID", nullable = false, length = 100)
    private String resourceId;

    @Column(name = "RESOURCE_NAME", length = 200)
    private String resourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "LOG_LEVEL", nullable = false, length = 20)
    private LogLevel level;

    @Column(name = "MESSAGE", length = 4000)
    private String message;

    @Column(name = "SOURCE", length = 200)
    private String source; // ex: application, system, database

    @Column(name = "LOG_TIMESTAMP", nullable = false)
    private LocalDateTime logTimestamp;

    @Column(name = "COLLECTED_AT", nullable = false)
    private LocalDateTime collectedAt;

    @PrePersist
    protected void onCreate() {
        if (collectedAt == null) collectedAt = LocalDateTime.now();
    }
}
