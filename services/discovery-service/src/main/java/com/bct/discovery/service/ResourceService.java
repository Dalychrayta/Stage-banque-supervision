package com.bct.discovery.service;

import com.bct.discovery.dto.RegisterResourceRequest;
import com.bct.discovery.dto.ResourceDTO;
import com.bct.discovery.kafka.ResourceEventProducer;
import com.bct.discovery.model.Resource;
import com.bct.discovery.model.ResourceStatus;
import com.bct.discovery.model.ResourceType;
import com.bct.discovery.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceEventProducer eventProducer;

    @Transactional
    public ResourceDTO register(RegisterResourceRequest request) {
        if (resourceRepository.existsByResourceId(request.getResourceId())) {
            // Mise à jour si déjà existant
            Resource existing = resourceRepository.findByResourceId(request.getResourceId()).get();
            existing.setName(request.getName());
            existing.setHost(request.getHost());
            existing.setIpAddress(request.getIpAddress());
            existing.setPort(request.getPort());
            existing.setEnvironment(request.getEnvironment());
            existing.setDescription(request.getDescription());
            existing.setTags(request.getTags());
            if (request.getSimulated() != null) existing.setSimulated(request.getSimulated());
            existing.setLastSeen(LocalDateTime.now());
            Resource saved = resourceRepository.save(existing);
            return toDTO(saved);
        }

        Resource resource = Resource.builder()
                .resourceId(request.getResourceId())
                .name(request.getName())
                .type(request.getType())
                .host(request.getHost())
                .ipAddress(request.getIpAddress())
                .port(request.getPort())
                .environment(request.getEnvironment())
                .description(request.getDescription())
                .tags(request.getTags())
                .simulated(request.getSimulated() != null ? request.getSimulated() : true)
                .status(ResourceStatus.UNKNOWN)
                .lastSeen(LocalDateTime.now())
                .build();

        Resource saved = resourceRepository.save(resource);
        ResourceDTO dto = toDTO(saved);
        eventProducer.sendResourceDiscovered(dto);
        log.info("Nouvelle ressource enregistrée: {}", saved.getResourceId());
        return dto;
    }

    public List<ResourceDTO> findAll() {
        return resourceRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public ResourceDTO findById(String resourceId) {
        return resourceRepository.findByResourceId(resourceId)
                .map(this::toDTO)
                .orElseThrow(() -> new NoSuchElementException("Ressource non trouvée: " + resourceId));
    }

    public List<ResourceDTO> findByStatus(ResourceStatus status) {
        return resourceRepository.findByStatus(status).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ResourceDTO> findByType(ResourceType type) {
        return resourceRepository.findByType(type).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public ResourceDTO updateStatus(String resourceId, ResourceStatus newStatus) {
        Resource resource = resourceRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Ressource non trouvée: " + resourceId));

        String previousStatus = resource.getStatus().name();
        resource.setStatus(newStatus);
        resource.setLastSeen(LocalDateTime.now());
        Resource saved = resourceRepository.save(resource);
        ResourceDTO dto = toDTO(saved);

        if (!previousStatus.equals(newStatus.name())) {
            eventProducer.sendResourceStatusChanged(dto, previousStatus);
        }
        return dto;
    }

    @Transactional
    public void delete(String resourceId) {
        Resource resource = resourceRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Ressource non trouvée: " + resourceId));
        resourceRepository.delete(resource);
        log.info("Ressource supprimée: {}", resourceId);
    }

    public Map<String, Long> getStats() {
        return Map.of(
                "total", resourceRepository.count(),
                "up", resourceRepository.countByStatus(ResourceStatus.UP),
                "down", resourceRepository.countByStatus(ResourceStatus.DOWN),
                "degraded", resourceRepository.countByStatus(ResourceStatus.DEGRADED),
                "unknown", resourceRepository.countByStatus(ResourceStatus.UNKNOWN)
        );
    }

    // Ping périodique pour mettre à jour le lastSeen
    @Scheduled(fixedDelayString = "${collector.metrics.interval-ms:30000}")
    public void heartbeat() {
        log.debug("Discovery heartbeat — {} ressources enregistrées", resourceRepository.count());
    }

    private ResourceDTO toDTO(Resource r) {
        return ResourceDTO.builder()
                .id(r.getId())
                .resourceId(r.getResourceId())
                .name(r.getName())
                .type(r.getType())
                .host(r.getHost())
                .ipAddress(r.getIpAddress())
                .port(r.getPort())
                .environment(r.getEnvironment())
                .description(r.getDescription())
                .status(r.getStatus())
                .lastSeen(r.getLastSeen())
                .createdAt(r.getCreatedAt())
                .tags(r.getTags())
                .simulated(r.getSimulated())
                .build();
    }
}
