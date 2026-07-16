package com.oneapi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaViewTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fromInstanceMeta_parsesTags() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, "{\"tags\":[\"tag-a\",\"tag-b\"]}");
        assertThat(mv.instanceHasTag("tag-a")).isTrue();
        assertThat(mv.instanceHasTag("tag-b")).isTrue();
        assertThat(mv.instanceHasTag("tag-c")).isFalse();
    }

    @Test
    void fromInstanceMeta_parsesLayer() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, "{\"layer\":\"premium\"}");
        assertThat(mv.instanceLayer()).isEqualTo("premium");
    }

    @Test
    void fromInstanceMeta_parsesMaxTokens() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, "{\"max_tokens\":4096}");
        assertThat(mv.instanceMaxTokens()).isEqualTo(4096);
    }

    @Test
    void fromInstanceMeta_parsesPref() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, "{\"pref\":80}");
        assertThat(mv.instancePref()).isEqualTo(80);
    }

    @Test
    void fromInstanceMeta_null_returnsEmpty() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, null);
        assertThat(mv.instanceLayer()).isEmpty();
        assertThat(mv.instanceTags()).isEmpty();
        assertThat(mv.instancePref()).isEqualTo(0);
    }

    @Test
    void fromInstanceMeta_empty_returnsEmpty() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, "");
        assertThat(mv.instanceLayer()).isEmpty();
        assertThat(mv.instanceTags()).isEmpty();
    }

    @Test
    void fromInstanceMeta_badJson_returnsEmpty() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, "not json");
        assertThat(mv.instanceLayer()).isEmpty();
    }

    @Test
    void fromInstanceMeta_missingFields_defaultsToZero() {
        MetaView mv = MetaView.fromInstanceMeta(MAPPER, "{\"tags\":[\"x\"]}");
        assertThat(mv.instancePref()).isEqualTo(0);
        assertThat(mv.instanceMaxTokens()).isEqualTo(0);
        assertThat(mv.instanceLayer()).isEmpty();
    }
}
