package com.oneapi.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaViewTest {

    @Test
    void fromInstanceMeta_parsesTags() {
        MetaView mv = MetaView.fromInstanceMeta("{\"tags\":[\"tag-a\",\"tag-b\"]}");
        assertThat(mv.instanceHasTag("tag-a")).isTrue();
        assertThat(mv.instanceHasTag("tag-b")).isTrue();
        assertThat(mv.instanceHasTag("tag-c")).isFalse();
    }

    @Test
    void fromInstanceMeta_parsesLayer() {
        MetaView mv = MetaView.fromInstanceMeta("{\"layer\":\"premium\"}");
        assertThat(mv.instanceLayer()).isEqualTo("premium");
    }

    @Test
    void fromInstanceMeta_parsesMaxTokens() {
        MetaView mv = MetaView.fromInstanceMeta("{\"max_tokens\":4096}");
        assertThat(mv.instanceMaxTokens()).isEqualTo(4096);
    }

    @Test
    void fromInstanceMeta_parsesPref() {
        MetaView mv = MetaView.fromInstanceMeta("{\"pref\":80}");
        assertThat(mv.instancePref()).isEqualTo(80);
    }

    @Test
    void fromInstanceMeta_null_returnsEmpty() {
        MetaView mv = MetaView.fromInstanceMeta(null);
        assertThat(mv.instanceLayer()).isEmpty();
        assertThat(mv.instanceTags()).isEmpty();
        assertThat(mv.instancePref()).isEqualTo(0);
    }

    @Test
    void fromInstanceMeta_empty_returnsEmpty() {
        MetaView mv = MetaView.fromInstanceMeta("");
        assertThat(mv.instanceLayer()).isEmpty();
        assertThat(mv.instanceTags()).isEmpty();
    }

    @Test
    void fromInstanceMeta_badJson_returnsEmpty() {
        MetaView mv = MetaView.fromInstanceMeta("not json");
        assertThat(mv.instanceLayer()).isEmpty();
    }

    @Test
    void fromInstanceMeta_missingFields_defaultsToZero() {
        MetaView mv = MetaView.fromInstanceMeta("{\"tags\":[\"x\"]}");
        assertThat(mv.instancePref()).isEqualTo(0);
        assertThat(mv.instanceMaxTokens()).isEqualTo(0);
        assertThat(mv.instanceLayer()).isEmpty();
    }
}
