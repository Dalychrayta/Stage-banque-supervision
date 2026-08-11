package com.bct.rca.service;

import com.bct.rca.kafka.RcaResultProducer;
import com.bct.rca.model.AnalysisStatus;
import com.bct.rca.model.IncidentAnalysis;
import com.bct.rca.repository.IncidentAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RcaServiceTest {

    private IncidentAnalysisRepository repository;
    private RcaResultProducer rcaResultProducer;
    private RcaService rcaService;

    @BeforeEach
    void setUp() {
        repository = mock(IncidentAnalysisRepository.class);
        rcaResultProducer = mock(RcaResultProducer.class);
        rcaService = new RcaService(repository, rcaResultProducer);

        // Simule le comportement de JPA : save() assigne un id et déclenche @PrePersist
        when(repository.save(any(IncidentAnalysis.class))).thenAnswer(invocation -> {
            IncidentAnalysis analysis = invocation.getArgument(0);
            analysis.setId(1L);
            if (analysis.getAnalyzedAt() == null) analysis.setAnalyzedAt(LocalDateTime.now());
            return analysis;
        });
    }

    static Stream<Arguments> rootCauseScenarios() {
        return Stream.of(
                Arguments.of(Map.of("cpuUsage", 95.0), "CPU_SATURATION"),
                Arguments.of(Map.of("memoryUsage", 92.0), "MEMORY_EXHAUSTION"),
                Arguments.of(Map.of("diskUsage", 91.0), "DISK_FULL"),
                Arguments.of(Map.of("responseTimeMs", 3500.0), "HIGH_LATENCY"),
                Arguments.of(Map.of("errorRate", 15.0), "HIGH_ERROR_RATE"),
                Arguments.of(Map.of("cpuUsage", 40.0, "memoryUsage", 50.0), "UNKNOWN")
        );
    }

    @ParameterizedTest(name = "{1} pour les métriques {0}")
    @MethodSource("rootCauseScenarios")
    void analyzeAnomaly_shouldDetermineCorrectRootCause(Map<String, Object> metrics, String expectedCategory) {
        Map<String, Object> event = new HashMap<>(metrics);
        event.put("resourceId", "srv-001");
        event.put("resourceName", "test-server");
        event.put("severity", "CRITICAL");

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result.getCauseCategory()).isEqualTo(expectedCategory);
        assertThat(result.getRootCause()).isNotBlank();
        assertThat(result.getRecommendation()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.OPEN);
    }

    @Test
    void analyzeAnomaly_shouldPrioritizeCpuOverOtherCauses() {
        // Quand plusieurs seuils sont dépassés, CPU doit être détecté en premier (ordre des règles)
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-002");
        event.put("cpuUsage", 95.0);
        event.put("memoryUsage", 95.0);
        event.put("diskUsage", 95.0);

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result.getCauseCategory()).isEqualTo("CPU_SATURATION");
    }

    @Test
    void analyzeAnomaly_shouldPersistAndPublishResult() {
        Map<String, Object> event = Map.of("resourceId", "srv-003", "cpuUsage", 99.0);

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        verify(repository, times(1)).save(any(IncidentAnalysis.class));
        verify(rcaResultProducer, times(1)).sendRcaResult(result);
    }

    @Test
    void analyzeAnomaly_shouldReuseExistingIncidentOnDuplicateMetricId() {
        IncidentAnalysis existing = IncidentAnalysis.builder().id(99L).sourceMetricId(555L).build();
        when(repository.findBySourceMetricId(555L)).thenReturn(java.util.Optional.of(existing));

        Map<String, Object> event = Map.of("resourceId", "srv-004", "cpuUsage", 99.0, "metricId", 555);

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any(IncidentAnalysis.class));
        verify(rcaResultProducer, never()).sendRcaResult(any());
    }

    @Test
    void analyzeAnomaly_shouldCreateNewIncidentWhenMetricIdNotYetSeen() {
        when(repository.findBySourceMetricId(777L)).thenReturn(java.util.Optional.empty());

        Map<String, Object> event = Map.of("resourceId", "srv-005", "cpuUsage", 99.0, "metricId", 777);

        rcaService.analyzeAnomaly(event);

        verify(repository, times(1)).save(any(IncidentAnalysis.class));
        verify(rcaResultProducer, times(1)).sendRcaResult(any());
    }

    @Test
    void resolve_shouldMarkIncidentAsResolved() {
        IncidentAnalysis existing = IncidentAnalysis.builder()
                .id(42L)
                .resourceId("srv-004")
                .status(AnalysisStatus.OPEN)
                .build();
        when(repository.findById(42L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(IncidentAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentAnalysis resolved = rcaService.resolve(42L);

        assertThat(resolved.getStatus()).isEqualTo(AnalysisStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_shouldThrowWhenIncidentNotFound() {
        when(repository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> rcaService.resolve(999L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getOpenIncidents_shouldDelegateToRepository() {
        List<IncidentAnalysis> expected = List.of(IncidentAnalysis.builder().id(1L).build());
        when(repository.findByStatusOrderByAnalyzedAtDesc(AnalysisStatus.OPEN)).thenReturn(expected);

        List<IncidentAnalysis> result = rcaService.getOpenIncidents();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getStats_shouldAggregateCountsFromRepository() {
        when(repository.countByStatus(AnalysisStatus.OPEN)).thenReturn(5L);
        when(repository.countByStatus(AnalysisStatus.RESOLVED)).thenReturn(12L);
        when(repository.count()).thenReturn(17L);

        Map<String, Long> stats = rcaService.getStats();

        assertThat(stats).containsEntry("open", 5L).containsEntry("resolved", 12L).containsEntry("total", 17L);
    }
}
