package com.bct.collector.service;

import com.bct.collector.kafka.MetricEventProducer;
import com.bct.collector.model.LogEntry;
import com.bct.collector.model.LogLevel;
import com.bct.collector.model.MetricSnapshot;
import com.bct.collector.repository.LogEntryRepository;
import com.bct.collector.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectorService {

    private final MetricSnapshotRepository metricRepository;
    private final LogEntryRepository logRepository;
    private final MetricEventProducer eventProducer;

    @Transactional
    public MetricSnapshot saveMetric(MetricSnapshot metric) {
        MetricSnapshot saved = metricRepository.save(metric);
        eventProducer.sendMetricCollected(saved);
        return saved;
    }

    /**
     * Enregistre la mesure sans la soumettre à l'analyse d'anomalie.
     *
     * Utilisé pendant la période de chauffe qui suit un redémarrage : une
     * application qui démarre consomme presque tout le CPU pendant quelques
     * secondes. C'est normal, ce n'est pas une panne — mais analysée comme
     * telle, cette mesure déclenchait un redémarrage, donc une nouvelle
     * période de chauffe, donc une nouvelle alerte, en boucle.
     *
     * La mesure est tout de même conservée : elle est vraie, et l'historique
     * doit rester complet.
     */
    @Transactional
    public MetricSnapshot saveMetricWithoutAnalysis(MetricSnapshot metric) {
        return metricRepository.save(metric);
    }

    @Transactional
    public LogEntry saveLog(LogEntry logEntry) {
        return logRepository.save(logEntry);
    }

    public List<MetricSnapshot> getMetricsByResource(String resourceId) {
        return metricRepository.findByResourceIdOrderByCollectedAtDesc(resourceId);
    }

    public List<MetricSnapshot> getMetricsByResourceAndRange(
            String resourceId, LocalDateTime from, LocalDateTime to) {
        return metricRepository.findByResourceIdAndCollectedAtBetweenOrderByCollectedAtAsc(resourceId, from, to);
    }

    public List<MetricSnapshot> getLatestMetrics(String resourceId, int limit) {
        return metricRepository.findLatestByResourceId(resourceId, PageRequest.of(0, limit));
    }

    public List<LogEntry> getLogsByResource(String resourceId) {
        return logRepository.findByResourceIdOrderByLogTimestampDesc(resourceId);
    }

    public List<LogEntry> getRecentErrors(int minutesBack) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        return logRepository.findByLevelAndLogTimestampAfterOrderByLogTimestampDesc(LogLevel.ERROR, since);
    }

    public List<String> getMonitoredResources() {
        return metricRepository.findDistinctResourceIds();
    }
}
