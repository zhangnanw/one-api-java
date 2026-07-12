package com.oneapi.handler;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UpstreamClientTest {

    private static Vertx vertx;

    @BeforeAll
    static void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void tearDown() {
        vertx.close();
    }

    // ── isApiVersionEnding ──

    @Test
    @DisplayName("v1 → version ending")
    void isApiVersionEnding_basic() throws Exception {
        assertTrue(invokeIsApiVersionEnding("v1"));
        assertTrue(invokeIsApiVersionEnding("v12"));
        assertTrue(invokeIsApiVersionEnding("v1beta")); // only checks first 2 chars
    }

    @Test
    @DisplayName("non-version → not version ending")
    void isApiVersionEnding_notVersion() throws Exception {
        assertFalse(invokeIsApiVersionEnding("v"));
        assertFalse(invokeIsApiVersionEnding(""));
        assertFalse(invokeIsApiVersionEnding("abc"));
        assertFalse(invokeIsApiVersionEnding("V1"));
        assertFalse(invokeIsApiVersionEnding("1v"));
    }

    private boolean invokeIsApiVersionEnding(String v) throws Exception {
        Method m = UpstreamClient.class.getDeclaredMethod("isApiVersionEnding", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, v);
    }

    // ── isApiVersionPath ──

    @Test
    @DisplayName("/v1 → version path")
    void isApiVersionPath_basic() throws Exception {
        assertTrue(invokeIsApiVersionPath("/v1"));
        assertTrue(invokeIsApiVersionPath("/v1/chat"));
        assertTrue(invokeIsApiVersionPath("/v12"));
    }

    @Test
    @DisplayName("non-version path → false")
    void isApiVersionPath_notVersion() throws Exception {
        assertFalse(invokeIsApiVersionPath("/abc"));
        assertFalse(invokeIsApiVersionPath(""));
        assertFalse(invokeIsApiVersionPath("v1"));
        assertFalse(invokeIsApiVersionPath("/V1"));
        assertFalse(invokeIsApiVersionPath("/v"));
    }

    private boolean invokeIsApiVersionPath(String p) throws Exception {
        Method m = UpstreamClient.class.getDeclaredMethod("isApiVersionPath", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p);
    }

    // ── buildUrl ──

    @Test
    @DisplayName("deduplicate matching version prefix")
    void buildUrl_dedupMatchingPrefix() throws Exception {
        assertEquals("https://api.openai.com/v1/chat/completions",
            invokeBuildUrl("https://api.openai.com/v1", "/v1/chat/completions"));
        assertEquals("https://api.openai.com/v1",
            invokeBuildUrl("https://api.openai.com/v1", "/v1"));
        assertEquals("https://api.minimax.com/v1/text/chatcompletion_v2",
            invokeBuildUrl("https://api.minimax.com/v1", "/v1/text/chatcompletion_v2"));
    }

    @Test
    @DisplayName("different version path → cut first segment")
    void buildUrl_differentVersion() throws Exception {
        assertEquals("https://api.openai.com/v1/chat",
            invokeBuildUrl("https://api.openai.com/v1", "/v2/chat"));
    }

    @Test
    @DisplayName("no version in base → simple concat")
    void buildUrl_noVersion() throws Exception {
        assertEquals("https://example.com/v1/chat",
            invokeBuildUrl("https://example.com", "/v1/chat"));
        assertEquals("https://api/chat",
            invokeBuildUrl("https://api", "/chat"));
    }

    @Test
    @DisplayName("null path → base only")
    void buildUrl_nullPath() throws Exception {
        assertEquals("https://api.openai.com/v1",
            invokeBuildUrl("https://api.openai.com/v1", null));
    }

    @Test
    @DisplayName("url with port")
    void buildUrl_withPort() throws Exception {
        assertEquals("http://localhost:8080/v1/chat",
            invokeBuildUrl("http://localhost:8080/v1", "/v1/chat"));
    }

    private String invokeBuildUrl(String base, String path) throws Exception {
        Method m = UpstreamClient.class.getDeclaredMethod("buildUrl", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(new UpstreamClient(null, vertx), base, path);
    }

    // ── parseTokensFromBody ──

    @Test
    @DisplayName("extract total_tokens from SSE chunk")
    void parseTokensFromBody_basic() throws Exception {
        assertEquals(42, invokeParseTokensFromBody(
            "data: {\"usage\":{\"total_tokens\":42}}\n"));
    }

    @Test
    @DisplayName("empty string → 0")
    void parseTokensFromBody_empty() throws Exception {
        assertEquals(0, invokeParseTokensFromBody(""));
    }

    @Test
    @DisplayName("no data lines → 0")
    void parseTokensFromBody_noDataLines() throws Exception {
        assertEquals(0, invokeParseTokensFromBody("some text\nmore text\n"));
    }

    @Test
    @DisplayName("[DONE] line ignored")
    void parseTokensFromBody_doneIgnored() throws Exception {
        assertEquals(0, invokeParseTokensFromBody("data: [DONE]\n"));
    }

    @Test
    @DisplayName("takes last non-zero total_tokens")
    void parseTokensFromBody_lastWins() throws Exception {
        assertEquals(25, invokeParseTokensFromBody(
            "data: {\"usage\":{\"total_tokens\":10}}\n" +
            "data: {\"usage\":{\"total_tokens\":25}}\n" +
            "data: {\"usage\":{\"total_tokens\":0}}\n"));
    }

    @Test
    @DisplayName("invalid JSON → 0")
    void parseTokensFromBody_invalidJson() throws Exception {
        assertEquals(0, invokeParseTokensFromBody("data: not-json\n"));
        assertEquals(0, invokeParseTokensFromBody("data: {\"usage\":\n"));
    }

    @Test
    @DisplayName("real SSE stream with mixed content")
    void parseTokensFromBody_mixedContent() throws Exception {
        assertEquals(88, invokeParseTokensFromBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n" +
            "data: {\"choices\":[],\"usage\":{\"total_tokens\":88}}\n" +
            "data: [DONE]\n"));
    }

    @Test
    @DisplayName("no usage field → 0")
    void parseTokensFromBody_noUsage() throws Exception {
        assertEquals(0, invokeParseTokensFromBody("data: {\"choices\":[]}\n"));
    }

    @Test
    @DisplayName("no trailing newline -> last line still parsed")
    void parseTokensFromBody_noTrailingNewline() throws Exception {
        assertEquals(42, invokeParseTokensFromBody(
            "data: {\"usage\":{\"total_tokens\":42}}"));
    }

    private int invokeParseTokensFromBody(String body) {
        return UpstreamClient.parseTokensFromBody(body);
    }

    // ── split-line buffering simulation ──

    @Test
    @DisplayName("split across two chunks: partial mid-token in chunk1, remainder in chunk2")
    void parseTokensFromBody_splitAcrossChunks() {
        // Simulates the line-buffering logic in relayStream:
        // chunk1 = partial SSE line (no newline), chunk2 = remainder + newline
        StringBuilder lineBuffer = new StringBuilder();
        int[] tokensHolder = {0};

        // chunk 1: partial line
        String chunk1 = "data: {\"usage\":{\"tot";
        lineBuffer.append(chunk1);
        int lastNewline = lineBuffer.lastIndexOf("\n");
        if (lastNewline >= 0) {
            String completeLines = lineBuffer.substring(0, lastNewline + 1);
            lineBuffer.delete(0, lastNewline + 1);
            int t = UpstreamClient.parseTokensFromBody(completeLines);
            if (t > 0) tokensHolder[0] = t;
        }
        // no newline found → lineBuffer retains partial line
        assertThat(lineBuffer.toString()).isEqualTo("data: {\"usage\":{\"tot");
        assertThat(tokensHolder[0]).isZero();

        // chunk 2: completes the line
        String chunk2 = "al_tokens\":42}}\n";
        lineBuffer.append(chunk2);
        lastNewline = lineBuffer.lastIndexOf("\n");
        if (lastNewline >= 0) {
            String completeLines = lineBuffer.substring(0, lastNewline + 1);
            lineBuffer.delete(0, lastNewline + 1);
            int t = UpstreamClient.parseTokensFromBody(completeLines);
            if (t > 0) tokensHolder[0] = t;
        }
        // complete line parsed → 42
        assertThat(tokensHolder[0]).isEqualTo(42);
        assertThat(lineBuffer.toString()).isEmpty();
    }
}
