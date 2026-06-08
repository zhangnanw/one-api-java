package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisionFilterTest {

    private final VisionFilter filter = new VisionFilter();

    @Test
    void bodyWithImageUrl_setsCapabilityRequired() {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,xxx\"}}]}]}";
        RelayContext ctx = new RelayContext("test");
        ctx.setRawBody(body.getBytes());
        RelayContext result = filter.apply(ctx);
        assertEquals("vision", result.capabilityRequired());
    }

    @Test
    void bodyWithoutImageUrl_leavesCapabilityNull() {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
        RelayContext ctx = new RelayContext("test");
        ctx.setRawBody(body.getBytes());
        RelayContext result = filter.apply(ctx);
        assertNull(result.capabilityRequired());
    }

    @Test
    void emptyBody_leavesCapabilityNull() {
        RelayContext ctx = new RelayContext("test");
        ctx.setRawBody(new byte[0]);
        RelayContext result = filter.apply(ctx);
        assertNull(result.capabilityRequired());
    }

    @Test
    void nullBody_leavesCapabilityNull() {
        RelayContext ctx = new RelayContext("test");
        RelayContext result = filter.apply(ctx);
        assertNull(result.capabilityRequired());
    }

    @Test
    void malformedJson_stillDetectsImageUrl() {
        // contains "type":"image_url" even in broken JSON
        String body = "{\"broken\":true,\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"image_url\",\"image_url\":\"x\"}]}]";
        RelayContext ctx = new RelayContext("test");
        ctx.setRawBody(body.getBytes());
        RelayContext result = filter.apply(ctx);
        assertEquals("vision", result.capabilityRequired());
    }

    @Test
    void imageUrlAsTextOnly_ignored() {
        // image_url mentioned in plain text (not a content block type) → no vision
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"data:image_url:test\"}]}";
        RelayContext ctx = new RelayContext("test");
        ctx.setRawBody(body.getBytes());
        RelayContext result = filter.apply(ctx);
        assertNull(result.capabilityRequired());
    }
}
