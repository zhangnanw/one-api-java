package com.oneapi.filter;

import com.oneapi.model.MatchRule;
import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TagFilter}.
 *
 * Stage 3 — when matchRule is {@link MatchRule.TagMatch}, filter candidates
 * whose instance tags satisfy the rule (ALL / ANY).
 */
class TagFilterTest {

    TagFilter filter = new TagFilter();

    @Test
    void noTagRule_passesThrough() {
        RelayContext ctx = ctx(new MatchRule.AllMatch(), rv(1, "tag-a"), rv(2, "tag-b"));
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(2);
    }

    @Test
    void allTags_matching_keeps() {
        RelayContext ctx = ctx(
            new MatchRule.TagMatch(Set.of("tag-a", "tag-b"), Set.of()),
            rv(1, "tag-a,tag-b,extra"),
            rv(2, "tag-a")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
        assertThat(ctx.candidates().get(0).instanceId()).isEqualTo(1);
    }

    @Test
    void allTags_noMatch_excludes() {
        RelayContext ctx = ctx(
            new MatchRule.TagMatch(Set.of("tag-a", "tag-b"), Set.of()),
            rv(1, "tag-a")  // missing tag-b
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    @Test
    void anyTags_matching_keeps() {
        RelayContext ctx = ctx(
            new MatchRule.TagMatch(Set.of(), Set.of("tag-a", "tag-b")),
            rv(1, "tag-a"),
            rv(2, "tag-b"),
            rv(3, "tag-c")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(2);
    }

    @Test
    void anyTags_noMatch_excludes() {
        RelayContext ctx = ctx(
            new MatchRule.TagMatch(Set.of(), Set.of("tag-x", "tag-y")),
            rv(1, "tag-a")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    @Test
    void emptyTagRule_passesThrough() {
        // empty Set — treated as "no constraint" per implementation
        RelayContext ctx = ctx(
            new MatchRule.TagMatch(Set.of(), Set.of()),
            rv(1, "tag-a")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
    }

    // --- helpers ---

    private static RoutedVendor rv(int instanceId, String tags) {
        // MetaView.parseTagsArray expects a JSON array. Build meta with tags array.
        String meta;
        if (tags.isEmpty()) {
            meta = "{}";
        } else {
            String[] parts = tags.split(",");
            StringBuilder sb = new StringBuilder("{\"tags\":[");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(parts[i]).append("\"");
            }
            sb.append("]}");
            meta = sb.toString();
        }
        return new RoutedVendor(
            null,
            "model-" + instanceId,
            "upstream-" + instanceId,
            instanceId,
            tags,
            meta,
            1
        , 0f, "payg");
    }

    private static RelayContext ctx(MatchRule rule, RoutedVendor... candidates) {
        RelayContext c = new RelayContext("kimi-k2.6");
        c.setMatchRule(rule);
        c.setCandidates(List.of(candidates));
        return c;
    }
}
