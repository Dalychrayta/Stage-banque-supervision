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

    // --- Période de chauffe ---
    //
    // Une JVM qui démarre consomme presque tout le CPU pendant quelques
    // secondes. Analysée comme une panne, cette mesure déclenchait un
    // redémarrage, donc un nouveau démarrage, donc une nouvelle alerte : la
    // plateforme entretenait le problème qu'elle croyait soigner.

    @Test
    void isWarmingUp_shouldCoverTheSecondsRightAfterAStart() {
        assertThat(RealTargetCollector.isWarmingUp(5, 120)).isTrue();
        assertThat(RealTargetCollector.isWarmingUp(119, 120)).isTrue();
    }

    @Test
    void isWarmingUp_shouldEndExactlyAtTheThreshold() {
        assertThat(RealTargetCollector.isWarmingUp(120, 120)).isFalse();
        assertThat(RealTargetCollector.isWarmingUp(3600, 120)).isFalse();
    }

    @Test
    void isWarmingUp_shouldAnalyseTheMetricWhenUptimeCannotBeRead() {
        // Uptime nul ou négatif = lecture impossible. Dans le doute on analyse,
        // plutôt que de risquer d'ignorer une vraie panne.
        assertThat(RealTargetCollector.isWarmingUp(0, 120)).isFalse();
        assertThat(RealTargetCollector.isWarmingUp(-1, 120)).isFalse();
    }
}
