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

    @Test
    void checkAndSet_doesNotReIncrementWhileCooling() {
        // 关键不变量：在冷却窗口内重复调用 checkAndSet 不会让 n 增长，
        // 否则反复失败的同一个实例的冷却窗口会被错误延长到上限。
        cooldown.checkAndSetInstanceCooldown(1, "");              // n: 0 -> 1
        for (int i = 0; i < 10; i++) {
            cooldown.checkAndSetInstanceCooldown(1, "");          // 全部返回 true（旧已在冷却）
        }
        long remaining = cooldown.getCooldownRemaining(1);
        // 仍然只有一个失败循环，remaining 应当接近 5*(1-0.95)/0.05 = 5 分钟（按秒）
        assertThat(remaining).isLessThanOrEqualTo(5L * 60 + 5);
        assertThat(remaining).isPositive();
    }

    @Test
    void setInstanceCooldown_unconditionalIncrement() {
        // setInstanceCooldown 总是 +1：不管现在在不在冷却。
        cooldown.setInstanceCooldown(1, "");        // n -> 1
        long r1 = cooldown.getCooldownRemaining(1);
        cooldown.setInstanceCooldown(1, "");        // n -> 2，until 推到更远
        long r2 = cooldown.getCooldownRemaining(1);
        assertThat(r2).isGreaterThanOrEqualTo(r1);
    }

    @Test
    void setAndCheckAndSet_behaveDifferentlyOnRepeatedFails() {
        // 同一个实例在已经被 setInstanceCooldown 推到 n=5 时，
        // checkAndSet 看到 n=5 仍在冷却，不会让 n 变成 6。
        for (int i = 0; i < 5; i++) cooldown.setInstanceCooldown(1, "");
        long beforeProbe = cooldown.getCooldownRemaining(1);
        boolean wasCooling = cooldown.checkAndSetInstanceCooldown(1, "");
        long afterProbe = cooldown.getCooldownRemaining(1);
        assertThat(wasCooling).isTrue();
        // checkAndSet 在已冷却时不应让剩余时间显著拉长
        assertThat(afterProbe).isLessThanOrEqualTo(beforeProbe + 1);
    }
}
