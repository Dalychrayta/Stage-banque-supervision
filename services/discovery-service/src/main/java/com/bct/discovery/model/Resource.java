package com.bct.discovery.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "BCT_RESOURCES")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "resource_seq")
    @SequenceGenerator(name = "resource_seq", sequenceName = "BCT_RESOURCES_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "RESOURCE_ID", unique = true, nullable = false, length = 100)
    private String resourceId;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 50)
    private ResourceType type;

    @Column(name = "HOST", length = 255)
    private String host;

    @Column(name = "IP_ADDRESS", length = 50)
    private String ipAddress;

    @Column(name = "PORT")
    private Integer port;

    @Column(name = "ENVIRONMENT", length = 50)
    private String environment; // PROD, DEV, STAGING

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ResourceStatus status;

    @Column(name = "LAST_SEEN")
    private LocalDateTime lastSeen;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "TAGS", length = 500)
    private String tags; // JSON string: ["web","payment"]

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ResourceStatus.UNKNOWN;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
