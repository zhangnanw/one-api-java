package com.oneapi.handler;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayContext;
import com.oneapi.entity.Vendor;

/**
 * Injects vendor-specific HTTP headers into a {@link Candidate}.
 * <p>
 * Currently handles:
 * <ul>
 *   <li>{@code X-Reasoning-Effort: max} — for reasoning-capable models</li>
 *   <li>{@code User-Agent: KimiCLI/1.6} — for Kimi/Moonshot API compatibility</li>
 * </ul>
 */
public class HeaderInjector {

    /** 注入供应商专用头（X-Reasoning-Effort + Kimi CLI User-Agent）。 */
    public static void inject(Candidate candidate, Vendor vendor, RelayContext relayCtx) {
        if (relayCtx != null && relayCtx.reasoning()) {
            candidate.extraHeaders().add("X-Reasoning-Effort", "max");
        }
        if (vendor != null && vendor.getBaseUrl() != null
                && isKimiOrMoonshot(vendor.getBaseUrl())) {
            candidate.extraHeaders().add("User-Agent", "KimiCLI/1.6");
        }
    }

    private static boolean isKimiOrMoonshot(String baseUrl) {
        String lower = baseUrl.toLowerCase();
        return lower.contains("kimi") || lower.contains("moonshot");
    }
}
