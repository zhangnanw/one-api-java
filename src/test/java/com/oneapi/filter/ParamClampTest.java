package com.oneapi.filter;

import com.oneapi.model.InstanceCaps;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ParamClampTest {

    private static final int DEFAULT_CAP = 16384;

    @Test
    void defaultCap_whenNoMeta() {
        JsonObject body = new JsonObject().put("max_tokens", 100000);
        byte[] result = ParamClamp.clamp(body.encode().getBytes(StandardCharsets.UTF_8), null);
        JsonObject out = new JsonObject(new String(result, StandardCharsets.UTF_8));
        assertThat(out.getInteger("max_tokens")).isEqualTo(DEFAULT_CAP);
    }

    @Test
    void defaultCap_whenMaxTokensZero() {
        InstanceCaps caps = new InstanceCaps(java.util.Set.of(), "", 0, 0);
        JsonObject body = new JsonObject().put("max_tokens", 100000);
        byte[] result = ParamClamp.clamp(body.encode().getBytes(StandardCharsets.UTF_8), caps);
        JsonObject out = new JsonObject(new String(result, StandardCharsets.UTF_8));
        assertThat(out.getInteger("max_tokens")).isEqualTo(DEFAULT_CAP);
    }

    @Test
    void respectsConfiguredCap() {
        InstanceCaps caps = new InstanceCaps(java.util.Set.of(), "", 0, 8192);
        JsonObject body = new JsonObject().put("max_tokens", 50000);
        byte[] result = ParamClamp.clamp(body.encode().getBytes(StandardCharsets.UTF_8), caps);
        JsonObject out = new JsonObject(new String(result, StandardCharsets.UTF_8));
        assertThat(out.getInteger("max_tokens")).isEqualTo(8192);
    }

    @Test
    void noClamp_whenUnderCap() {
        JsonObject body = new JsonObject().put("max_tokens", 1000);
        byte[] result = ParamClamp.clamp(body.encode().getBytes(StandardCharsets.UTF_8), null);
        JsonObject out = new JsonObject(new String(result, StandardCharsets.UTF_8));
        assertThat(out.getInteger("max_tokens")).isEqualTo(1000);
    }

    @Test
    void noClamp_whenNoMaxTokensField() {
        JsonObject body = new JsonObject().put("model", "kimi-k2.6");
        byte[] result = ParamClamp.clamp(body.encode().getBytes(StandardCharsets.UTF_8), null);
        JsonObject out = new JsonObject(new String(result, StandardCharsets.UTF_8));
        assertThat(out.containsKey("max_tokens")).isFalse();
    }

    @Test
    void nullBody_returnsNull() {
        byte[] result = ParamClamp.clamp(null, null);
        assertThat(result).isNull();
    }
}
