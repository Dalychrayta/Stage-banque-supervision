package com.bct.collector.kafka;

import com.bct.collector.client.DiscoveryServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Consomme les métriques collectées, les envoie au Prediction Engine
 * et publie les anomalies détectées vers le topic anomaly-detected.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricEventConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DiscoveryServiceClient discoveryClient;

    @Value("${prediction.engine.url:http://localhost:8000}")
    private String predictionEngineUrl;

    @KafkaListener(topics = "${kafka.topics.metrics-collected}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeMetric(Map<String, Object> event) {
        try {
            WebClient client = WebClient.create(predictionEngineUrl);
            Map<String, Object> predictionRequest = buildPredictionRequest(event);

            Map result = client.post()
                    .uri("/api/prediction/analyze")
                    .bodyValue(predictionRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String resourceId = (String) event.get("resourceId");
            if (result != null && Boolean.TRUE.equals(result.get("is_anomaly"))) {
                publishAnomalyDetected(event, result);
                String severity = String.valueOf(result.get("severity"));
                discoveryClient.updateStatus(resourceId, "CRITICAL".equals(severity) ? "DOWN" : "DEGRADED");
            } else {
                discoveryClient.updateStatus(resourceId, "UP");
            }
        } catch (Exception e) {
            log.warn("Impossible de contacter le Prediction Engine: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildPredictionRequest(Map<String, Object> event) {
        Map<String, Object> req = new HashMap<>(event);
        req.put("resource_id",      event.get("resourceId"));
        req.put("resource_name",    event.get("resourceName"));
        req.put("resource_type",    event.getOrDefault("resourceType", "SERVER"));
        req.put("cpu_usage",        event.getOrDefault("cpuUsage", 0.0));
        req.put("memory_usage",     event.getOrDefault("memoryUsage", 0.0));
        req.put("disk_usage",       event.getOrDefault("diskUsage", 0.0));
        req.put("network_in_mbps",  event.getOrDefault("networkInMbps", 0.0));
        req.put("network_out_mbps", event.getOrDefault("networkOutMbps", 0.0));
        req.put("response_time_ms", event.getOrDefault("responseTimeMs", 0.0));
        req.put("error_rate",       event.getOrDefault("errorRate", 0.0));
        return req;
    }

    private void publishAnomalyDetected(Map<String, Object> metric, Map<String, Object> prediction) {
        Map<String, Object> anomalyEvent = new HashMap<>(metric);
        anomalyEvent.put("eventType",        "ANOMALY_DETECTED");
        anomalyEvent.put("anomalyScore",     prediction.get("anomaly_score"));
        anomalyEvent.put("severity",         prediction.get("severity"));
        anomalyEvent.put("anomalousMetrics", prediction.get("anomalous_metrics"));
        anomalyEvent.put("recommendation",   prediction.get("recommendation"));
        kafkaTemplate.send("anomaly-detected", (String) metric.get("resourceId"), anomalyEvent);
        log.info("Anomalie publiée pour: {} — sévérité: {}", metric.get("resourceId"), prediction.get("severity"));
    }
}
