package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VirtualModelLookup}.
 *
 * Three behaviors:
 * - registered virtual model → no error
 * - unregistered (strict mode) → ModelNotFound error
 * - requireVirtualModel=false → fallback to AllMatch (no error)
 */
@ExtendWith(MockitoExtension.class)
class VirtualModelLookupTest {

    @Mock
    VirtualModelRepo vmRepo;

    VirtualModelLookup strict;       // requireVirtualModel = true
    VirtualModelLookup permissive;   // requireVirtualModel = false

    @BeforeEach
    void setUp() {
        strict = new VirtualModelLookup(vmRepo, "-max", true);
        permissive = new VirtualModelLookup(vmRepo, "-max", false);
    }

    @Test
    void registeredModel_noError() {
        VirtualModel registered = new VirtualModel();
        registered.setId(1);
        registered.setName("kimi-k2.6");
        registered.setMatch("{\"type\":\"AllMatch\"}");
        when(vmRepo.findByName("kimi-k2.6")).thenReturn(registered);

        RelayContext ctx = new RelayContext("kimi-k2.6");
        RelayContext result = strict.apply(ctx);

        assertFalse(result.hasError(), "registered model should not produce error");
        assertNotNull(result.matchRule());
    }

    @Test
    void unregisteredModel_strictMode_setsModelNotFound() {
        when(vmRepo.findByName("not-in-db")).thenReturn(null);

        RelayContext ctx = new RelayContext("not-in-db");
        RelayContext result = strict.apply(ctx);

        assertTrue(result.hasError(), "strict mode should mark error");
        assertInstanceOf(RelayError.ModelNotFound.class, result.error());
        assertTrue(result.errorMessage().contains("not-in-db"));
    }

    @Test
    void unregisteredModel_permissive_fallbackToAllMatch() {
        when(vmRepo.findByName("physical-only")).thenReturn(null);

        RelayContext ctx = new RelayContext("physical-only");
        RelayContext result = permissive.apply(ctx);

        assertFalse(result.hasError(), "permissive mode should not error");
        assertNotNull(result.matchRule(), "permissive mode should set matchRule");
        assertEquals("physical-only", result.upstreamModel(),
            "permissive mode should use the requested name as upstream");
    }

    @Test
    void unmatchedPhysical_directBypass() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setMatchedPhysical(true);
        RelayContext result = strict.apply(ctx);

        assertFalse(result.hasError(), "matchedPhysical=true should bypass lookup");
    }

    @Test
    void reasoningSuffixStrippedBeforeLookup() {
        VirtualModel registered = new VirtualModel();
        registered.setId(1);
        registered.setName("kimi-k2.6");
        registered.setMatch("{\"type\":\"AllMatch\"}");
        // Repo called WITHOUT "-max" suffix
        when(vmRepo.findByName("kimi-k2.6")).thenReturn(registered);

        RelayContext ctx = new RelayContext("kimi-k2.6-max");
        RelayContext result = strict.apply(ctx);

        assertFalse(result.hasError());
        assertTrue(result.reasoning(), "should set reasoning=true when suffix stripped");
    }
}
