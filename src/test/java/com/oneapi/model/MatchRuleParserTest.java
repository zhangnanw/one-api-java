package com.oneapi.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MatchRuleParser#parse(String)} — ModelsMatch paths.
 */
class MatchRuleParserTest {

    @Test
    void modelsMatch_happyPath() {
        MatchRule rule = MatchRuleParser.parse("{\"models\":[\"a\",\"b\",\"c\"]}");
        assertInstanceOf(MatchRule.ModelsMatch.class, rule);
        var mm = (MatchRule.ModelsMatch) rule;
        assertEquals(3, mm.modelNames().size());
        assertEquals("a", mm.modelNames().get(0));
        assertEquals("b", mm.modelNames().get(1));
        assertEquals("c", mm.modelNames().get(2));
    }

    @Test
    void modelsMatch_emptyList_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[]}"));
    }

    @Test
    void modelsMatch_mutualExclusion_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[\"a\"],\"model_name\":\"x\"}"));
    }

    @Test
    void modelsMatch_invalidElement_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[123]}"));
    }

    @Test
    void modelsMatch_nullElement_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[null]}"));
    }

    @Test
    void modelsMatch_emptyStringElement_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[\"\"]}"));
    }

    // ── edge cases (previously untested) ──

    @Test
    void modelsMatch_mixedCasePreserved() {
        MatchRule rule = MatchRuleParser.parse("{\"models\":[\"DeepSeek-V4-Flash\",\"kimi-K2.6\",\"C\"]}");
        var mm = (MatchRule.ModelsMatch) rule;
        assertEquals("DeepSeek-V4-Flash", mm.modelNames().get(0));
        assertEquals("kimi-K2.6", mm.modelNames().get(1));
        assertEquals("C", mm.modelNames().get(2));
    }

    @Test
    void modelsMatch_singleElement() {
        MatchRule rule = MatchRuleParser.parse("{\"models\":[\"solo\"]}");
        var mm = (MatchRule.ModelsMatch) rule;
        assertEquals(1, mm.modelNames().size());
        assertEquals("solo", mm.modelNames().get(0));
    }

    @Test
    void allMatch_nullInput() {
        assertInstanceOf(MatchRule.AllMatch.class, MatchRuleParser.parse(null));
    }

    @Test
    void allMatch_emptyString() {
        assertInstanceOf(MatchRule.AllMatch.class, MatchRuleParser.parse(""));
    }

    @Test
    void allMatch_emptyObject() {
        assertInstanceOf(MatchRule.AllMatch.class, MatchRuleParser.parse("{}"));
    }

    @Test
    void allMatch_malformedJson() {
        // malformed JSON falls back to AllMatch (doesn't crash)
        assertInstanceOf(MatchRule.AllMatch.class, MatchRuleParser.parse("{bad"));
    }

    @Test
    void allMatch_unknownField() {
        // unknown top-level field not in known set → AllMatch
        assertInstanceOf(MatchRule.AllMatch.class, MatchRuleParser.parse("{\"foo\":\"bar\"}"));
    }

    @Test
    void nameMatch() {
        MatchRule rule = MatchRuleParser.parse("{\"model_name\":\"deepseek-v4-pro\"}");
        assertInstanceOf(MatchRule.NameMatch.class, rule);
        assertEquals("deepseek-v4-pro", ((MatchRule.NameMatch) rule).modelName());
    }

    @Test
    void capabilityMatch() {
        MatchRule rule = MatchRuleParser.parse("{\"capability\":\"vision\"}");
        assertInstanceOf(MatchRule.CapabilityMatch.class, rule);
        assertEquals("vision", ((MatchRule.CapabilityMatch) rule).capability());
    }

    @Test
    void layerMatch() {
        MatchRule rule = MatchRuleParser.parse("{\"layer\":\"payg\"}");
        assertInstanceOf(MatchRule.LayerMatch.class, rule);
        assertEquals("payg", ((MatchRule.LayerMatch) rule).layer());
    }

    @Test
    void tagMatch_allTags() {
        MatchRule rule = MatchRuleParser.parse("{\"all\":[\"production\",\"gpu\"]}");
        assertInstanceOf(MatchRule.TagMatch.class, rule);
        var tm = (MatchRule.TagMatch) rule;
        assertEquals(2, tm.allTags().size());
        assertTrue(tm.allTags().contains("production"));
        assertTrue(tm.allTags().contains("gpu"));
    }

    @Test
    void tagMatch_anyTags() {
        MatchRule rule = MatchRuleParser.parse("{\"any\":[\"vision\",\"reasoning\"]}");
        assertInstanceOf(MatchRule.TagMatch.class, rule);
        var tm = (MatchRule.TagMatch) rule;
        assertTrue(tm.allTags().isEmpty());
        assertEquals(2, tm.anyTags().size());
    }

    @Test
    void tagMatch_emptyArrays_isAllMatch() {
        // empty all + empty any → semantically AllMatch (no filter)
        assertInstanceOf(MatchRule.AllMatch.class,
            MatchRuleParser.parse("{\"all\":[],\"any\":[]}"));
    }

    @Test
    void tagMatch_allAndAnyCombined() {
        MatchRule rule = MatchRuleParser.parse("{\"all\":[\"production\"],\"any\":[\"vision\",\"code\"]}");
        assertInstanceOf(MatchRule.TagMatch.class, rule);
        var tm = (MatchRule.TagMatch) rule;
        assertEquals(1, tm.allTags().size());
        assertEquals(2, tm.anyTags().size());
    }

    @Test
    void modelsMatch_mutualExclusionWithTags() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[\"a\"],\"all\":[\"t\"]}"));
    }

    @Test
    void modelsMatch_mutualExclusionWithCapability() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[\"a\"],\"capability\":\"vision\"}"));
    }

    @Test
    void modelsMatch_mutualExclusionWithLayer() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchRuleParser.parse("{\"models\":[\"a\"],\"layer\":\"payg\"}"));
    }
}
