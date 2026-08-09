package com.bct.collector.service;

import com.bct.collector.kafka.MetricEventProducer;
import com.bct.collector.model.LogEntry;
import com.bct.collector.model.LogLevel;
import com.bct.collector.model.MetricSnapshot;
import com.bct.collector.repository.LogEntryRepository;
import com.bct.collector.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CollectorServiceTest {

    private MetricSnapshotRepository metricRepository;
    private LogEntryRepository logRepository;
    private MetricEventProducer eventProducer;
    private CollectorService collectorService;

    @BeforeEach
    void setUp() {
        metricRepository = mock(MetricSnapshotRepository.class);
        logRepository = mock(LogEntryRepository.class);
        eventProducer = mock(MetricEventProducer.class);
        collectorService = new CollectorService(metricRepository, logRepository, eventProducer);
    }

    @Test
    void saveMetric_shouldPersistAndPublishToKafka() {
        MetricSnapshot metric = MetricSnapshot.builder().resourceId("srv-001").cpuUsage(85.0).build();
        when(metricRepository.save(metric)).thenReturn(metric);

        MetricSnapshot result = collectorService.saveMetric(metric);

        assertThat(result).isEqualTo(metric);
        verify(metricRepository, times(1)).save(metric);
        verify(eventProducer, times(1)).sendMetricCollected(metric);
    }

    @Test
    void saveLog_shouldPersistWithoutPublishing() {
        LogEntry entry = LogEntry.builder().resourceId("srv-001").level(LogLevel.ERROR).message("boom").build();
        when(logRepository.save(entry)).thenReturn(entry);

        LogEntry result = collectorService.saveLog(entry);

        assertThat(result).isEqualTo(entry);
        verify(logRepository, times(1)).save(entry);
        verifyNoInteractions(eventProducer);
    }

    @Test
    void getMetricsByResource_shouldDelegateToRepository() {
        List<MetricSnapshot> expected = List.of(MetricSnapshot.builder().id(1L).resourceId("srv-002").build());
        when(metricRepository.findByResourceIdOrderByCollectedAtDesc("srv-002")).thenReturn(expected);

        List<MetricSnapshot> result = collectorService.getMetricsByResource("srv-002");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getLatestMetrics_shouldRequestPagedResultsFromRepository() {
        List<MetricSnapshot> expected = List.of(MetricSnapshot.builder().id(2L).build());
        when(metricRepository.findLatestByResourceId(eq("srv-003"), any())).thenReturn(expected);

        List<MetricSnapshot> result = collectorService.getLatestMetrics("srv-003", 10);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getRecentErrors_shouldQueryErrorLevelSinceGivenWindow() {
        List<LogEntry> expected = List.of(LogEntry.builder().id(3L).level(LogLevel.ERROR).build());
        when(logRepository.findByLevelAndLogTimestampAfterOrderByLogTimestampDesc(eq(LogLevel.ERROR), any(LocalDateTime.class)))
                .thenReturn(expected);

        List<LogEntry> result = collectorService.getRecentErrors(60);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMonitoredResources_shouldReturnDistinctResourceIds() {
        List<String> expected = List.of("srv-001", "srv-002");
        when(metricRepository.findDistinctResourceIds()).thenReturn(expected);

        List<String> result = collectorService.getMonitoredResources();

        assertThat(result).isEqualTo(expected);
    }
}
