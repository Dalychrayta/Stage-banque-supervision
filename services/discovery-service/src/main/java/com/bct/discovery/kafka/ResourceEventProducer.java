package com.bct.discovery.kafka;

import com.bct.discovery.dto.ResourceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.resource-discovered}")
    private String resourceDiscoveredTopic;

    @Value("${kafka.topics.resource-status-changed}")
    private String resourceStatusChangedTopic;

    public void sendResourceDiscovered(ResourceDTO resource) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "RESOURCE_DISCOVERED");
        event.put("resource", resource);
        kafkaTemplate.send(resourceDiscoveredTopic, resource.getResourceId(), event);
        log.info("Event RESOURCE_DISCOVERED envoyé pour: {}", resource.getResourceId());
    }

    public void sendResourceStatusChanged(ResourceDTO resource, String previousStatus) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "RESOURCE_STATUS_CHANGED");
        event.put("resource", resource);
        event.put("previousStatus", previousStatus);
        event.put("newStatus", resource.getStatus().name());
        kafkaTemplate.send(resourceStatusChangedTopic, resource.getResourceId(), event);
        log.info("Event RESOURCE_STATUS_CHANGED envoyé pour: {} ({} -> {})",
                resource.getResourceId(), previousStatus, resource.getStatus());
    }
}
