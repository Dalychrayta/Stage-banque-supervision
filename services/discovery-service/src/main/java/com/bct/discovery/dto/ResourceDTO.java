package com.bct.discovery.dto;

import com.bct.discovery.model.ResourceStatus;
import com.bct.discovery.model.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDTO {
    private Long id;
    private String resourceId;
    private String name;
    private ResourceType type;
    private String host;
    private String ipAddress;
    private Integer port;
    private String environment;
    private String description;
    private ResourceStatus status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;
    private String tags;
}
