package com.bct.collector.kafka;

import com.bct.collector.model.MetricSnapshot;
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
public class MetricEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.metrics-collected}")
    private String metricsCollectedTopic;

    @Value("${kafka.topics.logs-collected}")
    private String logsCollectedTopic;

    public void sendMetricCollected(MetricSnapshot metric) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "METRIC_COLLECTED");
        // Identifiant stable de la métrique source : sert de clé d'idempotence
        // en aval (rca-service) en cas de redélivrance Kafka du même message.
        event.put("metricId", metric.getId());
        event.put("resourceId", metric.getResourceId());
        event.put("resourceName", metric.getResourceName());
        event.put("resourceType", metric.getResourceType());
        event.put("cpuUsage", metric.getCpuUsage());
        event.put("memoryUsage", metric.getMemoryUsage());
        event.put("diskUsage", metric.getDiskUsage());
        event.put("networkInMbps", metric.getNetworkInMbps());
        event.put("networkOutMbps", metric.getNetworkOutMbps());
        event.put("responseTimeMs", metric.getResponseTimeMs());
        event.put("errorRate", metric.getErrorRate());
        event.put("collectedAt", metric.getCollectedAt().toString());
        kafkaTemplate.send(metricsCollectedTopic, metric.getResourceId(), event);
        log.debug("Métrique envoyée vers Kafka pour: {}", metric.getResourceId());
    }
}
