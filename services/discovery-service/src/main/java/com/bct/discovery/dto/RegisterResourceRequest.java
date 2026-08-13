package com.bct.discovery.dto;

import com.bct.discovery.model.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterResourceRequest {

    @NotBlank(message = "resourceId est obligatoire")
    private String resourceId;

    @NotBlank(message = "name est obligatoire")
    private String name;

    @NotNull(message = "type est obligatoire")
    private ResourceType type;

    private String host;
    private String ipAddress;
    private Integer port;
    private String environment;
    private String description;
    private String tags;
    private Boolean simulated;
}
