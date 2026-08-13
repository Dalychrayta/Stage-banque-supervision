package com.bct.collector.scheduler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie uniquement le calcul (pas les appels réseau vers PlatformeBack,
 * couverts par le test de bout en bout live).
 */
class RealTargetCollectorMathTest {

    @Test
    void percentOf_shouldComputeUsageRatio() {
        assertThat(RealTargetCollector.percentOf(50, 200)).isEqualTo(25.0);
    }

    @Test
    void percentOf_shouldReturnZeroWhenMaxIsZeroOrNegative() {
        assertThat(RealTargetCollector.percentOf(50, 0)).isZero();
        assertThat(RealTargetCollector.percentOf(50, -10)).isZero();
    }

    @Test
    void clampPercent_shouldCapAt100() {
        assertThat(RealTargetCollector.clampPercent(150)).isEqualTo(100.0);
    }

    @Test
    void clampPercent_shouldFloorAtZero() {
        assertThat(RealTargetCollector.clampPercent(-5)).isEqualTo(0.0);
    }

    @Test
    void clampPercent_shouldLeaveValidValuesUnchanged() {
        assertThat(RealTargetCollector.clampPercent(42.5)).isEqualTo(42.5);
    }
}
