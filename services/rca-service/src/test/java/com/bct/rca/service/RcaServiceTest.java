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

    // --- Diagnostic quand aucun seuil absolu n'est franchi ---
    //
    // Le moteur d'IA compare au comportement NORMAL APPRIS de la ressource, pas
    // à des seuils universels. Une mémoire qui double par rapport à son habitude
    // est une vraie alerte, même à 40 % — et c'est le seul moyen de voir venir
    // une panne avant qu'elle n'atteigne 90 %. Sans ces règles, ces anomalies
    // tombaient toutes en UNKNOWN, l'incident restait ouvert, et la plateforme
    // noyait l'opérateur sous des notifications sans cause.

    @Test
    void analyzeAnomaly_shouldUseTheMostDeviantMetricWhenNoAbsoluteThresholdIsCrossed() {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-002");
        event.put("memoryUsage", 41.0);   // très en dessous du seuil absolu de 90 %
        event.put("cpuUsage", 12.0);
        event.put("anomalousMetrics", List.of(
                "memory_usage=41.0 (4.2 ecarts-types au-dessus de la normale)",
                "cpu_usage=12.0 (3.1 ecarts-types au-dessus de la normale)"));

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result.getCauseCategory()).isEqualTo("MEMORY_EXHAUSTION");
        // Moins sûr qu'un dépassement de seuil absolu : on sait QUOI dévie,
        // moins bien POURQUOI.
        assertThat(result.getConfidenceScore()).isLessThan(0.88);
    }

    @Test
    void analyzeAnomaly_shouldStillPreferAnAbsoluteThresholdOverADeviation() {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-002");
        event.put("diskUsage", 96.0);
        event.put("anomalousMetrics", List.of("cpu_usage=12.0 (3.1 ecarts-types au-dessus de la normale)"));

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result.getCauseCategory()).isEqualTo("DISK_FULL");
    }

    @Test
    void analyzeAnomaly_shouldStayUnknownWhenTheEngineNamesNoMetric() {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-002");
        event.put("cpuUsage", 12.0);
        event.put("anomalousMetrics", List.of());

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result.getCauseCategory()).isEqualTo("UNKNOWN");
    }

    @Test
    void firstFlaggedMetric_shouldAcceptBothAListAndItsTextForm() {
        // Le champ arrive en liste dans le message Kafka, en texte quand on
        // relit un incident enregistré. Les deux doivent marcher.
        assertThat(RcaService.firstFlaggedMetric(
                Map.of("anomalousMetrics", List.of("memory_usage=41.0 (4.2 ecarts-types)"))))
                .isEqualTo("memory_usage");
        assertThat(RcaService.firstFlaggedMetric(
                Map.of("anomalousMetrics", "[disk_usage=93.0 (5.0 ecarts-types)]")))
                .isEqualTo("disk_usage");
        assertThat(RcaService.firstFlaggedMetric(Map.of("anomalousMetrics", "[]"))).isNull();
        assertThat(RcaService.firstFlaggedMetric(Map.of())).isNull();
    }

    // --- Seuils alignés avec prediction-engine ---
    //
    // Ces seuils doivent rester identiques à _absolute_threshold_breaches dans
    // prediction_service.py. Sans cet alignement, une urgence réelle (CPU à
    // 87 %, déjà classée CRITICAL par le moteur) ne franchissait pas le seuil
    // du RCA (>90), et retombait sur le chemin "déviation" à confiance 0.65
    // au lieu du chemin "seuil direct" à confiance 0.90 — même catégorie,
    // mais confiance sous-évaluée pour une chose déjà confirmée dangereuse.

    @ParameterizedTest(name = "{1} a confiance elevee (seuil direct) pour {0}")
    @MethodSource("boundaryScenarios")
    void analyzeAnomaly_shouldUseHighConfidenceAtTheSameBoundaryAsPredictionEngine(
            Map<String, Object> metrics, String expectedCategory) {
        IncidentAnalysis result = rcaService.analyzeAnomaly(new HashMap<>(metrics));

        assertThat(result.getCauseCategory()).isEqualTo(expectedCategory);
        // >= 0.80 : chemin "seuil direct", pas le repli "déviation" (0.65).
        assertThat(result.getConfidenceScore()).isGreaterThanOrEqualTo(0.80);
    }

    static Stream<Arguments> boundaryScenarios() {
        return Stream.of(
                // 87% : CRITICAL cote prediction-engine (>85), mais AURAIT ete
                // sous le seuil RCA d'avant ce correctif (>90).
                Arguments.of(Map.of("cpuUsage", 87.0), "CPU_SATURATION"),
                // 2500ms : CRITICAL cote prediction-engine (>2000), mais AURAIT
                // ete sous l'ancien seuil RCA (>3000).
                Arguments.of(Map.of("responseTimeMs", 2500.0), "HIGH_LATENCY"),
                // 7% : CRITICAL cote prediction-engine (>5), mais AURAIT ete
                // sous l'ancien seuil RCA (>10).
                Arguments.of(Map.of("errorRate", 7.0), "HIGH_ERROR_RATE")
        );
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

    // --- Dérive persistante : mettre à jour au lieu de dupliquer ---
    //
    // Constaté en conditions réelles : une dérive qui ne se résorbe jamais
    // toute seule (disque légèrement hors de la plage apprise par le modèle)
    // est re-détectée à CHAQUE cycle de collecte (30 s), sans jamais changer
    // de cause ni de ressource — 91 incidents DISK_FULL identiques ouverts en
    // une heure. Un incident déjà OUVERT pour la même ressource + même cause
    // doit être mis à jour, pas dupliqué.

    @Test
    void analyzeAnomaly_shouldUpdateTheExistingOpenIncidentInsteadOfDuplicatingIt() {
        IncidentAnalysis ongoing = IncidentAnalysis.builder()
                .id(500L).resourceId("srv-002").causeCategory("DISK_FULL")
                .status(AnalysisStatus.OPEN).occurrenceCount(1)
                .anomalyScore(-0.5).build();
        when(repository.findFirstByResourceIdAndCauseCategoryAndStatusOrderByDetectedAtDesc(
                "srv-002", "DISK_FULL", AnalysisStatus.OPEN))
                .thenReturn(java.util.Optional.of(ongoing));

        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-002");
        event.put("diskUsage", 95.0); // > 90 -> DISK_FULL, comme la 1ère fois
        event.put("metricId", 42);

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        // Le même objet, pas une nouvelle instance créée par builder() : c'est
        // la preuve d'une mise à jour en place, pas d'une ligne dupliquée.
        assertThat(result).isSameAs(ongoing);
        verify(repository, times(1)).save(ongoing);
        assertThat(result.getOccurrenceCount()).isEqualTo(2);
        // Aucune republication : la première détection a déjà donné sa vraie
        // chance à auto-healing ; republier à chaque mise à jour ne ferait que
        // réévaluer la même action en boucle contre son délai de garde
        // (SKIPPED en boucle).
        verify(rcaResultProducer, never()).sendRcaResult(any());
    }

    @Test
    void analyzeAnomaly_shouldNotUpdateAResolvedIncidentButOpenANewOne() {
        // Un incident déjà RESOLVED ne doit jamais être rouvert silencieusement
        // par une réoccurrence : findFirst...Status(...OPEN) ne le trouvera pas,
        // donc le chemin normal (nouvel incident) s'applique.
        when(repository.findFirstByResourceIdAndCauseCategoryAndStatusOrderByDetectedAtDesc(
                "srv-002", "DISK_FULL", AnalysisStatus.OPEN))
                .thenReturn(java.util.Optional.empty());

        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-002");
        event.put("diskUsage", 95.0);

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result.getCauseCategory()).isEqualTo("DISK_FULL");
        verify(rcaResultProducer, times(1)).sendRcaResult(result);
    }

    @Test
    void analyzeAnomaly_shouldTreatADifferentCauseOnTheSameResourceAsASeparateIncident() {
        // Une cause DIFFERENTE (ex. CPU_SATURATION) sur la même ressource ne
        // doit pas être confondue avec un DISK_FULL déjà ouvert : la clé de
        // déduplication est (ressource + cause), pas seulement la ressource.
        when(repository.findFirstByResourceIdAndCauseCategoryAndStatusOrderByDetectedAtDesc(
                "srv-002", "CPU_SATURATION", AnalysisStatus.OPEN))
                .thenReturn(java.util.Optional.empty());

        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", "srv-002");
        event.put("cpuUsage", 95.0);

        IncidentAnalysis result = rcaService.analyzeAnomaly(event);

        assertThat(result.getCauseCategory()).isEqualTo("CPU_SATURATION");
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
    void correctCategory_shouldOverrideAutomaticDiagnosisAndBecomeEffective() {
        IncidentAnalysis existing = IncidentAnalysis.builder()
                .id(7L)
                .resourceId("srv-002")
                .causeCategory("CPU_SATURATION")
                .build();
        when(repository.findById(7L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(IncidentAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentAnalysis corrected = rcaService.correctCategory(7L, "MEMORY_EXHAUSTION", "admin");

        assertThat(corrected.getCorrectedCategory()).isEqualTo("MEMORY_EXHAUSTION");
        assertThat(corrected.getCorrectedBy()).isEqualTo("admin");
        assertThat(corrected.getCorrectedAt()).isNotNull();
        assertThat(corrected.getCauseCategory()).isEqualTo("CPU_SATURATION"); // le diagnostic d'origine est conservé
        assertThat(corrected.getEffectiveCategory()).isEqualTo("MEMORY_EXHAUSTION"); // mais la correction fait foi
    }

    @Test
    void correctCategory_shouldRejectEmptyCategory() {
        assertThatThrownBy(() -> rcaService.correctCategory(7L, "  ", "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any(IncidentAnalysis.class));
    }

    @Test
    void correctCategory_shouldThrowWhenIncidentNotFound() {
        when(repository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> rcaService.correctCategory(999L, "DISK_FULL", "admin"))
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
