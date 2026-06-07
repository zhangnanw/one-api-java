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
}
