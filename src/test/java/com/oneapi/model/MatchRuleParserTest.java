package com.oneapi.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MatchRuleParserTest {

    @Test
    void null_returnsAllMatch() {
        assertThat(MatchRuleParser.parse(null)).isInstanceOf(MatchRule.AllMatch.class);
    }

    @Test
    void empty_returnsAllMatch() {
        assertThat(MatchRuleParser.parse("")).isInstanceOf(MatchRule.AllMatch.class);
    }

    @Test
    void emptyObject_returnsAllMatch() {
        assertThat(MatchRuleParser.parse("{}")).isInstanceOf(MatchRule.AllMatch.class);
    }

    @Test
    void badJson_returnsAllMatch() {
        assertThat(MatchRuleParser.parse("not json")).isInstanceOf(MatchRule.AllMatch.class);
    }

    @Test
    void modelName_returnsNameMatch() {
        MatchRule rule = MatchRuleParser.parse("{\"model_name\":\"deepseek-v3\"}");
        assertThat(rule).isInstanceOf(MatchRule.NameMatch.class);
        assertThat(((MatchRule.NameMatch) rule).modelName()).isEqualTo("deepseek-v3");
    }

    @Test
    void capability_returnsCapabilityMatch() {
        MatchRule rule = MatchRuleParser.parse("{\"capability\":\"reasoning\"}");
        assertThat(rule).isInstanceOf(MatchRule.CapabilityMatch.class);
        assertThat(((MatchRule.CapabilityMatch) rule).capability()).isEqualTo("reasoning");
    }

    @Test
    void layer_returnsLayerMatch() {
        MatchRule rule = MatchRuleParser.parse("{\"layer\":\"premium\"}");
        assertThat(rule).isInstanceOf(MatchRule.LayerMatch.class);
        assertThat(((MatchRule.LayerMatch) rule).layer()).isEqualTo("premium");
    }

    @Test
    void tagMatch_allAndAny() {
        MatchRule rule = MatchRuleParser.parse("{\"all\":[\"tag-a\",\"tag-b\"],\"any\":[\"tag-c\"]}");
        assertThat(rule).isInstanceOf(MatchRule.TagMatch.class);
        MatchRule.TagMatch tm = (MatchRule.TagMatch) rule;
        assertThat(tm.allTags()).containsExactlyInAnyOrder("tag-a", "tag-b");
        assertThat(tm.anyTags()).containsExactly("tag-c");
    }

    @Test
    void tagMatch_allOnly() {
        MatchRule rule = MatchRuleParser.parse("{\"all\":[\"tag-a\"]}");
        assertThat(rule).isInstanceOf(MatchRule.TagMatch.class);
        assertThat(((MatchRule.TagMatch) rule).allTags()).containsExactly("tag-a");
        assertThat(((MatchRule.TagMatch) rule).anyTags()).isEmpty();
    }

    @Test
    void unknownKey_returnsAllMatch() {
        assertThat(MatchRuleParser.parse("{\"unknown\":123}")).isInstanceOf(MatchRule.AllMatch.class);
    }
}
