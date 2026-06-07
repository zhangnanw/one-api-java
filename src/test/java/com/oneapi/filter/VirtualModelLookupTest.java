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
 * Behaviors:
 * - registered virtual model → no error
 * - unregistered → ModelNotFound error
 */
@ExtendWith(MockitoExtension.class)
class VirtualModelLookupTest {

    @Mock
    VirtualModelRepo vmRepo;

    VirtualModelLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new VirtualModelLookup(vmRepo, "-max");
    }

    @Test
    void registeredModel_noError() {
        VirtualModel registered = new VirtualModel();
        registered.setId(1);
        registered.setName("kimi-k2.6");
        registered.setMatch("{\"type\":\"AllMatch\"}");
        when(vmRepo.findByName("kimi-k2.6")).thenReturn(registered);

        RelayContext ctx = new RelayContext("kimi-k2.6");
        RelayContext result = lookup.apply(ctx);

        assertFalse(result.hasError(), "registered model should not produce error");
        assertNotNull(result.matchRule());
    }

    @Test
    void unregisteredModel_setsModelNotFound() {
        when(vmRepo.findByName("not-in-db")).thenReturn(null);

        RelayContext ctx = new RelayContext("not-in-db");
        RelayContext result = lookup.apply(ctx);

        assertTrue(result.hasError(), "unregistered model should mark error");
        assertInstanceOf(RelayError.ModelNotFound.class, result.error());
        assertTrue(result.errorMessage().contains("not-in-db"));
    }

    @Test
    void unmatchedPhysical_directBypass() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setMatchedPhysical(true);
        RelayContext result = lookup.apply(ctx);

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
        RelayContext result = lookup.apply(ctx);

        assertFalse(result.hasError());
        assertTrue(result.reasoning(), "should set reasoning=true when suffix stripped");
    }

    @Test
    void modelsMatch_setsModelNames() {
        VirtualModel registered = new VirtualModel();
        registered.setId(1);
        registered.setName("deepseek");
        registered.setMatch("{\"models\":[\"deepseek-v4-flash\",\"deepseek-v4-pro\"]}");
        when(vmRepo.findByName("deepseek")).thenReturn(registered);

        RelayContext ctx = new RelayContext("deepseek");
        RelayContext result = lookup.apply(ctx);

        assertFalse(result.hasError());
        // modelNames should be set
        assertNotNull(result.modelNames());
        assertEquals(2, result.modelNames().size());
        assertEquals("deepseek-v4-flash", result.modelNames().get(0));
        assertEquals("deepseek-v4-pro", result.modelNames().get(1));
        // routingModelName should NOT be set for ModelsMatch
        assertNull(result.routingModelName());
    }
}
