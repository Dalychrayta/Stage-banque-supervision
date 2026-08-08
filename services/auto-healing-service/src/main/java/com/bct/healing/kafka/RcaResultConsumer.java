package com.bct.healing.kafka;

import com.bct.healing.service.HealingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RcaResultConsumer {

    private final HealingService healingService;

    @KafkaListener(topics = "${kafka.topics.rca-result}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeRcaResult(Map<String, Object> event) {
        try {
            log.info("Résultat RCA reçu pour: {} — catégorie: {}", event.get("resourceId"), event.get("causeCategory"));
            healingService.triggerHealing(event);
        } catch (Exception e) {
            log.error("Erreur lors du déclenchement de l'auto-healing: {}", e.getMessage(), e);
        }
    }
}
