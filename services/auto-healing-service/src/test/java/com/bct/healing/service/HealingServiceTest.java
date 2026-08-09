package com.bct.healing.service;

import com.bct.healing.client.RcaServiceClient;
import com.bct.healing.model.ActionStatus;
import com.bct.healing.model.ActionType;
import com.bct.healing.model.HealingAction;
import com.bct.healing.repository.HealingActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HealingServiceTest {

    private HealingActionRepository repository;
    private RcaServiceClient rcaServiceClient;
    private HealingService healingService;

    @BeforeEach
    void setUp() {
        repository = mock(HealingActionRepository.class);
        rcaServiceClient = mock(RcaServiceClient.class);
        healingService = new HealingService(repository, rcaServiceClient);

        // Simule le comportement de JPA : save() renvoie l'entité telle quelle
        when(repository.save(any(HealingAction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    static Stream<Arguments> ruleScenarios() {
        return Stream.of(
                Arguments.of("MEMORY_EXHAUSTION", ActionType.RESTART_SERVICE, true),
                Arguments.of("CPU_SATURATION", ActionType.KILL_PROCESS, true),
                Arguments.of("DISK_FULL", ActionType.FREE_DISK_SPACE, true),
                Arguments.of("HIGH_LATENCY", ActionType.CLEAR_CACHE, true),
                Arguments.of("HIGH_ERROR_RATE", ActionType.RESTART_SERVICE, true),
                Arguments.of("UNKNOWN", ActionType.NOTIFY_TEAM, false)
        );
    }

    @ParameterizedTest(name = "{0} -> {1} (automatique={2})")
    @MethodSource("ruleScenarios")
    void triggerHealing_shouldSelectCorrectActionForCause(String causeCategory, ActionType expectedAction, boolean expectedAutomatic) {
        Map<String, Object> event = baseEvent(causeCategory, 10L);

        HealingAction result = healingService.triggerHealing(event);

        assertThat(result.getActionType()).isEqualTo(expectedAction);
        assertThat(result.getIsAutomatic()).isEqualTo(expectedAutomatic);
        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCESS);
        assertThat(result.getResultMessage()).isNotBlank();
    }

    @Test
    void triggerHealing_shouldResolveIncidentWhenActionIsAutomatic() {
        Map<String, Object> event = baseEvent("CPU_SATURATION", 42L);

        healingService.triggerHealing(event);

        verify(rcaServiceClient, times(1)).resolveIncident(42L);
    }

    @Test
    void triggerHealing_shouldNotResolveIncidentWhenActionIsManualNotification() {
        Map<String, Object> event = baseEvent("UNKNOWN", 43L);

        healingService.triggerHealing(event);

        verify(rcaServiceClient, never()).resolveIncident(any());
    }

    @Test
    void triggerHealing_shouldNotResolveIncidentWhenIncidentIdMissing() {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-001");
        event.put("resourceName", "test-server");
        event.put("causeCategory", "CPU_SATURATION");
        event.put("severity", "CRITICAL");
        // pas d'incidentId

        healingService.triggerHealing(event);

        verify(rcaServiceClient, never()).resolveIncident(any());
    }

    @Test
    void triggerManual_shouldNeverBeMarkedAutomaticAndNeverResolveIncident() {
        HealingAction result = healingService.triggerManual("srv-005", "manual-server", ActionType.CLEAR_CACHE);

        assertThat(result.getIsAutomatic()).isFalse();
        assertThat(result.getActionType()).isEqualTo(ActionType.CLEAR_CACHE);
        verify(rcaServiceClient, never()).resolveIncident(any());
    }

    @Test
    void getAll_shouldDelegateToRepository() {
        List<HealingAction> expected = List.of(HealingAction.builder().id(1L).build());
        when(repository.findTop500ByOrderByTriggeredAtDesc()).thenReturn(expected);

        List<HealingAction> result = healingService.getAll();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getStats_shouldAggregateCountsFromRepository() {
        when(repository.count()).thenReturn(20L);
        when(repository.countByStatus(ActionStatus.SUCCESS)).thenReturn(15L);
        when(repository.countByStatus(ActionStatus.FAILED)).thenReturn(2L);
        when(repository.countByStatus(ActionStatus.PENDING)).thenReturn(3L);

        Map<String, Long> stats = healingService.getStats();

        assertThat(stats)
                .containsEntry("total", 20L)
                .containsEntry("success", 15L)
                .containsEntry("failed", 2L)
                .containsEntry("pending", 3L);
    }

    private Map<String, Object> baseEvent(String causeCategory, Long incidentId) {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-001");
        event.put("resourceName", "test-server");
        event.put("causeCategory", causeCategory);
        event.put("severity", "CRITICAL");
        event.put("incidentId", incidentId);
        return event;
    }
}
