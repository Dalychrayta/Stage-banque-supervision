package com.bct.rca.kafka;

import com.bct.rca.model.IncidentAnalysis;
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
public class RcaResultProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.rca-result}")
    private String rcaResultTopic;

    public void sendRcaResult(IncidentAnalysis analysis) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType",       "RCA_COMPLETED");
        event.put("incidentId",      analysis.getId());
        event.put("resourceId",      analysis.getResourceId());
        event.put("resourceName",    analysis.getResourceName());
        event.put("severity",        analysis.getSeverity());
        event.put("rootCause",       analysis.getRootCause());
        event.put("causeCategory",   analysis.getCauseCategory());
        event.put("recommendation",  analysis.getRecommendation());
        event.put("confidenceScore", analysis.getConfidenceScore());
        event.put("analyzedAt",      analysis.getAnalyzedAt().toString());
        kafkaTemplate.send(rcaResultTopic, analysis.getResourceId(), event);
        log.info("Résultat RCA envoyé pour: {} — cause: {}", analysis.getResourceId(), analysis.getCauseCategory());
    }
}
