package com.bct.healing.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealActionExecutorTest {

    private final RealActionExecutor executor = new RealActionExecutor();

    @Test
    void isRealTarget_shouldMatchOnlyTheKnownRealResourceId() {
        assertThat(executor.isRealTarget("srv-002")).isTrue();
    }

    @Test
    void isRealTarget_shouldRejectEveryOtherResource() {
        assertThat(executor.isRealTarget("srv-001")).isFalse();
        assertThat(executor.isRealTarget("app-001")).isFalse();
        assertThat(executor.isRealTarget("app-002")).isFalse();
        assertThat(executor.isRealTarget("db-001")).isFalse();
        assertThat(executor.isRealTarget(null)).isFalse();
        assertThat(executor.isRealTarget("")).isFalse();
    }
}
