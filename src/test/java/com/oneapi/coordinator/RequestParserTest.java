package com.oneapi.coordinator;

import com.oneapi.model.RelayRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestParserTest {

    @Mock
    RoutingContext ctx;
    @Mock
    HttpServerRequest request;

    @Test
    void nullBody_returnsNull() {
        RelayRequest result = RequestParser.parse(ctx, null);
        assertThat(result).isNull();
    }

    @Test
    void emptyBody_returnsNull() {
        RelayRequest result = RequestParser.parse(ctx, new byte[0]);
        assertThat(result).isNull();
    }

    @Test
    void modelInBody_parsed() {
        when(ctx.request()).thenReturn(request);
        when(request.getParam("model")).thenReturn(null);

        byte[] body = "{\"model\":\"kimi-k2.6\"}".getBytes();
        RelayRequest result = RequestParser.parse(ctx, body);

        assertThat(result).isNotNull();
        assertThat(result.requestedModel()).isEqualTo("kimi-k2.6");
        assertThat(result.streaming()).isFalse();
    }

    @Test
    void modelInUrlParam_parsed() {
        when(ctx.request()).thenReturn(request);
        when(request.getParam("model")).thenReturn("kimi-k2.6");

        byte[] body = "{}".getBytes();
        RelayRequest result = RequestParser.parse(ctx, body);

        assertThat(result).isNotNull();
        assertThat(result.requestedModel()).isEqualTo("kimi-k2.6");
    }

    @Test
    void streamTrue_parsed() {
        when(ctx.request()).thenReturn(request);
        when(request.getParam("model")).thenReturn(null);

        byte[] body = "{\"model\":\"kimi-k2.6\",\"stream\":true}".getBytes();
        RelayRequest result = RequestParser.parse(ctx, body);

        assertThat(result).isNotNull();
        assertThat(result.streaming()).isTrue();
    }

    @Test
    void noModel_returnsNull() {
        when(ctx.request()).thenReturn(request);
        when(request.getParam("model")).thenReturn(null);

        byte[] body = "{\"stream\":true}".getBytes();
        RelayRequest result = RequestParser.parse(ctx, body);

        assertThat(result).isNull();
    }

    @Test
    void emptyModelName_returnsNull() {
        when(ctx.request()).thenReturn(request);
        when(request.getParam("model")).thenReturn(null);

        byte[] body = "{\"model\":\"\"}".getBytes();
        RelayRequest result = RequestParser.parse(ctx, body);

        assertThat(result).isNull();
    }

    @Test
    void badJson_modelInParam_stillWorks() {
        when(ctx.request()).thenReturn(request);
        when(request.getParam("model")).thenReturn("kimi-k2.6");

        byte[] body = "not json".getBytes();
        RelayRequest result = RequestParser.parse(ctx, body);

        assertThat(result).isNotNull();
        assertThat(result.requestedModel()).isEqualTo("kimi-k2.6");
    }
}
