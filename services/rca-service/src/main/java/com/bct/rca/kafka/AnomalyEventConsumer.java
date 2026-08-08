package com.bct.rca.kafka;

import com.bct.rca.service.RcaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnomalyEventConsumer {

    private final RcaService rcaService;

    @KafkaListener(topics = "${kafka.topics.anomaly-detected}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeAnomaly(Map<String, Object> event) {
        try {
            log.info("Anomalie reçue pour analyse RCA: {}", event.get("resourceId"));
            rcaService.analyzeAnomaly(event);
        } catch (Exception e) {
            log.error("Erreur lors de l'analyse RCA: {}", e.getMessage(), e);
        }
    }
}
