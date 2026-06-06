package com.oneapi.middleware;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CORSTest {

    @Mock
    RoutingContext ctx;
    @Mock
    HttpServerResponse response;
    @Mock
    HttpServerRequest request;

    CORS cors = new CORS();

    @Test
    void optionsRequest_returns204() {
        when(ctx.response()).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
        when(response.setStatusCode(204)).thenReturn(response);
        when(ctx.request()).thenReturn(request);
        when(request.method()).thenReturn(HttpMethod.OPTIONS);

        cors.handle(ctx);

        verify(response).setStatusCode(204);
        verify(response).end();
        verify(ctx, never()).next();
    }

    @Test
    void getRequest_setsHeadersAndCallsNext() {
        when(ctx.response()).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
        when(ctx.request()).thenReturn(request);
        when(request.method()).thenReturn(HttpMethod.GET);

        cors.handle(ctx);

        verify(response).putHeader("Access-Control-Allow-Origin", "*");
        verify(response).putHeader("Access-Control-Allow-Methods",
            "GET,POST,PUT,DELETE,OPTIONS,PATCH");
        verify(ctx).next();
    }

    @Test
    void postRequest_setsHeadersAndCallsNext() {
        when(ctx.response()).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
        when(ctx.request()).thenReturn(request);
        when(request.method()).thenReturn(HttpMethod.POST);

        cors.handle(ctx);

        verify(response).putHeader("Access-Control-Max-Age", "86400");
        verify(ctx).next();
    }
}
