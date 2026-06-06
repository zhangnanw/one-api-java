package com.oneapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownServiceTest {

    CooldownService cooldown;

    @BeforeEach
    void setUp() {
        cooldown = new CooldownService();
    }

    @Test
    void notInCooldown_initially() {
        assertThat(cooldown.isInstanceInCooldown(1, "")).isFalse();
        assertThat(cooldown.isVendorInCooldown(1)).isFalse();
    }

    @Test
    void checkAndSet_setsInstanceCooldown() {
        boolean wasAlreadyCooling = cooldown.checkAndSetInstanceCooldown(1, "");
        assertThat(wasAlreadyCooling).isFalse(); // first time — not already cooling

        boolean secondCheck = cooldown.checkAndSetInstanceCooldown(1, "");
        assertThat(secondCheck).isTrue(); // second time — already cooling
    }

    @Test
    void checkAndSet_setsVendorCooldown() {
        boolean first = cooldown.checkAndSetVendorCooldown(1);
        assertThat(first).isFalse();

        boolean second = cooldown.checkAndSetVendorCooldown(1);
        assertThat(second).isTrue();
    }

    @Test
    void nocoolTag_bypassesCooldown() {
        cooldown.checkAndSetInstanceCooldown(1, "nocool:true");
        // nocool tag should prevent cooldown from being set
        assertThat(cooldown.isInstanceInCooldown(1, "nocool:true")).isFalse();
    }

    @Test
    void calcDuration_positive() {
        long dur1 = CooldownService.calcDuration(1);
        long dur5 = CooldownService.calcDuration(5);
        assertThat(dur1).isGreaterThan(0);
        assertThat(dur5).isGreaterThan(dur1); // more failures = longer cooldown
    }

    @Test
    void getStats_returnsNonNull() {
        assertThat(cooldown.getStats()).isNotNull();
    }
}
