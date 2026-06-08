package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.repo.ModelCatalogRepo;
import com.oneapi.service.RouterService;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapabilityInstanceFilterTest {

    private ModelCatalogRepo catalogRepo;
    private CapabilityInstanceFilter filter;

    @BeforeEach
    void setUp() {
        catalogRepo = mock(ModelCatalogRepo.class);
        when(catalogRepo.hasCapability(eq("model-a"), eq("vision"))).thenReturn(true);
        when(catalogRepo.hasCapability(eq("model-b"), eq("vision"))).thenReturn(false);
        filter = new CapabilityInstanceFilter(catalogRepo);
    }

    @Test
    void visionRequired_modelHasVision_kept() {
        var ctx = ctxWithVision("vision", List.of(rv(1, "model-a")));
        ctx = filter.apply(ctx);
        assertEquals(1, ctx.candidates().size());
    }

    @Test
    void visionRequired_modelMissingVision_filtered() {
        var ctx = ctxWithVision("vision", List.of(rv(1, "model-b")));
        ctx = filter.apply(ctx);
        assertEquals(0, ctx.candidates().size());
    }

    @Test
    void noCapabilityRequired_allKept() {
        var ctx = ctxWithVision(null, List.of(rv(1, "model-a"), rv(2, "model-b")));
        ctx = filter.apply(ctx);
        assertEquals(2, ctx.candidates().size());
    }

    @Test
    void capabilityRequired_modelNotInCatalog_filtered() {
        var ctx = ctxWithVision("vision", List.of(rv(1, "no-such-model")));
        ctx = filter.apply(ctx);
        assertEquals(0, ctx.candidates().size());
    }

    @Test
    void capabilityRequired_emptyCandidates_unchanged() {
        var ctx = ctxWithVision("vision", List.of());
        ctx = filter.apply(ctx);
        assertTrue(ctx.candidates().isEmpty());
    }

    // ── helpers ────────────────────────────────────────────────

    private static RoutedVendor rv(int id, String modelName) {
        return new RouterService.RoutedVendor(
            null,        // vendor
            modelName,   // modelName
            null,        // upstreamModel
            id,          // instanceId
            "",          // instanceTags
            null,        // instanceMeta
            1            // instanceStatus
        );
    }

    private static RelayContext ctxWithVision(String capability, List<RoutedVendor> candidates) {
        var ctx = new RelayContext("test-model");
        ctx.setCapabilityRequired(capability);
        ctx.setCandidates(new ArrayList<>(candidates));
        return ctx;
    }
}
