package com.oneapi.controller;

import com.oneapi.service.CooldownService;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MiscControllerTest {

    @Mock
    RoutingContext ctx;
    @Mock
    HttpServerResponse response;

    @Test
    void status_returnsSuccessWithData() {
        when(ctx.response()).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        MiscController controller = new MiscController(new CooldownService());
        controller.status(ctx);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());

        JsonObject json = new JsonObject(bodyCaptor.getValue());
        assertThat(json.getBoolean("success")).isTrue();
        assertThat(json.getJsonObject("data")).isNotNull();
        assertThat(json.getJsonObject("data").getString("system_name")).isEqualTo("one-api-java");
        assertThat(json.getJsonObject("data").getString("version")).isEqualTo("1.0.0");
        assertThat(json.getJsonObject("data").getJsonObject("cooldown")).isNotNull();
    }

    @Test
    void status_cooldownSection_hasAllFields() {
        when(ctx.response()).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        MiscController controller = new MiscController(new CooldownService());
        controller.status(ctx);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());

        JsonObject cooldown = new JsonObject(bodyCaptor.getValue())
            .getJsonObject("data").getJsonObject("cooldown");
        assertThat(cooldown.containsKey("hitCount")).isTrue();
        assertThat(cooldown.containsKey("missCount")).isTrue();
        assertThat(cooldown.containsKey("evictionCount")).isTrue();
        assertThat(cooldown.containsKey("estimatedSize")).isTrue();
    }

    @Test
    void err_setsErrorStatus() {
        when(ctx.response()).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        // Use MiscController (extends BaseController) to test err() indirectly
        MiscController controller = new MiscController(new CooldownService());
        // Call the inherited err() via a subclass trick
        // MiscController doesn't expose err() directly, but we can verify
        // the response format via the status endpoint instead.
        // For direct err() testing, verify the BaseController.json() contract:
        controller.status(ctx); // status returns 200

        // Now test error path: create a testable subclass
        var testCtrl = new MiscController(new CooldownService()) {
            public void testErr(RoutingContext c, int status, String msg) {
                err(c, status, msg);
            }
        };
        reset(response);
        when(ctx.response()).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
        testCtrl.testErr(ctx, 404, "not found");

        verify(response).setStatusCode(404);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());

        JsonObject json = new JsonObject(bodyCaptor.getValue());
        assertThat(json.getBoolean("success")).isFalse();
        assertThat(json.getString("message")).isEqualTo("not found");
    }
}
