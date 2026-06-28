package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.repo.WindowCatalog;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BodyLimitFilterTest {
    private BodyLimitFilter filter;
    private WindowCatalog repo;

    @BeforeEach
    void setUp() {
        // Anonymous interface implementation with known context windows
        repo = new WindowCatalog() {
            @Override
            public int getContextWindow(String modelName) {
                return switch (modelName) {
                    case "small" -> 100;
                    case "large" -> 10000;
                    case "huge" -> 100000;
                    default -> 0; // unknown
                };
            }
        };
        filter = new BodyLimitFilter(repo);
    }

    private RoutedVendor rv(String modelName) {
        return new RoutedVendor(null, modelName, null, 0, null, null, 0, 0f, "payg");
    }

    private RelayContext ctx(List<RoutedVendor> candidates, byte[] body) {
        RelayContext ctx = new RelayContext("test");
        ctx.setRawBody(body);
        ctx.setCandidates(candidates);
        return ctx;
    }

    @Test
    void bodyWithinWindow_kept() {
        List<RoutedVendor> candidates = List.of(rv("small"));
        RelayContext ctx = ctx(candidates, new byte[50]);
        ctx = filter.apply(ctx);
        assertFalse(ctx.hasError());
        assertEquals(1, ctx.candidates().size());
    }

    @Test
    void bodyExceedsWindow_filteredOut() {
        List<RoutedVendor> candidates = List.of(rv("small"), rv("large"));
        RelayContext ctx = ctx(candidates, new byte[500]);
        ctx = filter.apply(ctx);
        assertFalse(ctx.hasError());
        assertEquals(1, ctx.candidates().size());
        assertEquals("large", ctx.candidates().get(0).modelName());
    }

    @Test
    void allCandidatesTooLarge_error413() {
        List<RoutedVendor> candidates = List.of(rv("small"), rv("small"));
        RelayContext ctx = ctx(candidates, new byte[500]);
        ctx = filter.apply(ctx);
        assertTrue(ctx.hasError());
        assertInstanceOf(RelayError.BodyTooLarge.class, ctx.error());
        assertEquals(413, ctx.error().httpStatus());
    }

    @Test
    void unknownModel_kept() {
        List<RoutedVendor> candidates = List.of(rv("unknown"));
        RelayContext ctx = ctx(candidates, new byte[999999]);
        ctx = filter.apply(ctx);
        assertFalse(ctx.hasError());
        assertEquals(1, ctx.candidates().size());
    }

    @Test
    void nullBody_allKept() {
        List<RoutedVendor> candidates = List.of(rv("small"), rv("large"));
        RelayContext ctx = ctx(candidates, null);
        ctx = filter.apply(ctx);
        assertFalse(ctx.hasError());
        assertEquals(2, ctx.candidates().size());
    }

    @Test
    void emptyBody_allKept() {
        List<RoutedVendor> candidates = List.of(rv("small"));
        RelayContext ctx = ctx(candidates, new byte[0]);
        ctx = filter.apply(ctx);
        assertFalse(ctx.hasError());
        assertEquals(1, ctx.candidates().size());
    }
}
