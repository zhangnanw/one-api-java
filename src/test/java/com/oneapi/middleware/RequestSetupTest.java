package com.oneapi.middleware;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestSetupTest {

    @Mock
    RoutingContext ctx;
    @Mock
    HttpServerRequest request;

    RequestSetup setup = new RequestSetup();

    @Test
    void withAuthorization_hashesToken() {
        when(ctx.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn("Bearer sk-test-key");

        setup.handle(ctx);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx).put(eq("tokenHash"), hashCaptor.capture());
        String hash = hashCaptor.getValue();

        // SHA-256 should be 64 hex chars
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }

    @Test
    void withoutAuthorization_hashesEmpty() {
        when(ctx.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn(null);

        setup.handle(ctx);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx).put(eq("tokenHash"), hashCaptor.capture());
        String hash = hashCaptor.getValue();

        assertThat(hash).hasSize(64);
        // Should be SHA-256 of empty string
        assertThat(hash).isEqualTo(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void setsUserId() {
        when(ctx.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn("Bearer test");

        setup.handle(ctx);

        verify(ctx).put("userId", 1);
    }

    @Test
    void callsNext() {
        when(ctx.request()).thenReturn(request);
        when(request.getHeader("Authorization")).thenReturn(null);

        setup.handle(ctx);

        verify(ctx).next();
    }
}
