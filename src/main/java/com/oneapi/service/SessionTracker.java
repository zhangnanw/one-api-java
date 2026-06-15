package com.oneapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.OptionalLong;

/**
 * Session tracker — identifies agents by conversation content hash.
 * Matches Go's model/session_track.go (DefaultTracker).
 *
 * Key behavior:
 * - Incremental SHA256 over normalized message lines
 * - MinMatchLines=3: at least 3 consecutive lines must match
 * - On match → returns same sessionID, updates hash
 * - On miss → creates new UUID sessionID
 */
public class SessionTracker {
    private static final Logger log = LoggerFactory.getLogger(SessionTracker.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final int MIN_MATCH_LINES = 3;
    private static final long STICKY_TTL_MS = 300_000; // 5 minutes
    private static final Pattern TEXT_LINE_RE = Pattern.compile("^\\[text\\]: (.+)");

    // key = hex(SHA256), value = SessionTrack
    private final Cache<String, SessionTrack> store = Caffeine.newBuilder()
        .maximumSize(500)
        .build();

    public record SessionTrack(String sessionId, String hash, int updateCount,
                                Long lastInstanceId, long lastUsedAt) {
        /** Backward-compatible constructor (defaults for soft-stickiness fields). */
        public SessionTrack(String sessionId, String hash, int updateCount) {
            this(sessionId, hash, updateCount, null, 0L);
        }
    }

    /**
     * Match conversation to a session.
     * Returns sessionID (new or existing).
     */
    public String match(List<Message> messages) {
        List<String> lines = normalize(messages);
        if (lines.isEmpty()) return "";

        SessionTracker.MatchResult result = incrementalHash(lines);
        SessionTrack matched = result.matched();
        int matchedAt = result.matchedAt();
        MessageDigest digest = result.digest();

        if (matched != null) {
            for (int i = matchedAt + 1; i < lines.size(); i++) {
                digest.update(lines.get(i).getBytes(StandardCharsets.UTF_8));
            }
        }

        return lookupOrCreate(matched, hex(digest.digest()));
    }

    /** 归一化消息 → 文本行。 */
    private List<String> normalize(List<Message> messages) {
        List<String> lines = new ArrayList<>();
        for (Message m : messages) {
            if ("system".equals(m.role)) continue;
            String content = extractContentText(m.content);
            if (content.isEmpty()) continue;

            if (content.contains("# conversation")) {
                lines.addAll(extractFromConversation(content));
            } else {
                lines.add(content);
            }
        }
        return lines;
    }

    /** 增量哈希前 N 行，命中 store 即停。返回 digest 状态。 */
    private MatchResult incrementalHash(List<String> lines) {
        MessageDigest digest = sha256();
        SessionTrack matched = null;
        int matchedAt = -1;

        for (int i = 0; i < lines.size(); i++) {
            digest.update(lines.get(i).getBytes(StandardCharsets.UTF_8));
            if (i < MIN_MATCH_LINES - 1) continue;

            MessageDigest snapshot = copyDigest(digest);
            String hexHash = hex(snapshot.digest());
            // digest unchanged, continues accumulating

            SessionTrack found = store.getIfPresent(hexHash);
            if (found != null) {
                matched = found;
                matchedAt = i;
                break;
            }
        }
        return new MatchResult(matched, matchedAt, digest);
    }

    /** 命中即复用，miss 即创建新会话；写回 store。 */
    private synchronized String lookupOrCreate(SessionTrack matched, String finalHash) {
        if (matched == null) {
            String sessionId = UUID.randomUUID().toString();
            store.put(finalHash, new SessionTrack(sessionId, finalHash, 0));
            return sessionId;
        }
        // 复用已有会话：重扫描找当前 hash（incrementalHash 不在锁内，可能被 captureSummary 改过）
        var entry = findBySessionId(matched.sessionId);
        if (entry != null) {
            SessionTrack existing = entry.getValue();
            store.invalidate(entry.getKey());
            store.put(finalHash, new SessionTrack(matched.sessionId, finalHash,
                existing.updateCount() + 1,
                existing.lastInstanceId(), existing.lastUsedAt()));
            return matched.sessionId;
        }
        // 会话在 incrementalHash → lookupOrCreate 之间被驱逐了，仍返回原 sessionId
        return matched.sessionId;
    }

    /**
     * Scan store by sessionId. O(n), n≤500 (bounded by Caffeine maximumSize) —
     * per-scan cost negligible vs LLM inference (~2000ms). Returns null if not found.
     */
    private Map.Entry<String, SessionTrack> findBySessionId(String sessionId) {
        for (var entry : store.asMap().entrySet()) {
            if (entry.getValue().sessionId().equals(sessionId)) return entry;
        }
        return null;
    }

    /** 内部记录：哈希过程中的（命中状态 + digest 指针）。 */
    private record MatchResult(SessionTrack matched, int matchedAt, MessageDigest digest) {}

    /**
     * Update session hash with response summary (compressed conversation).
     */
    public synchronized void captureSummary(String sessionId, String summary) {
        if (summary == null || summary.isEmpty()) return;

        String newHash = hex(sha256().digest(summary.getBytes(StandardCharsets.UTF_8)));

        var entry = findBySessionId(sessionId);
        if (entry != null) {
            SessionTrack existing = entry.getValue();
            store.invalidate(entry.getKey());
            store.put(newHash, new SessionTrack(sessionId, newHash,
                existing.updateCount() + 1,
                existing.lastInstanceId(), existing.lastUsedAt()));
        }
    }

    /**
     * Lookup session by ID.
     */
    public SessionTrack lookup(String sessionId) {
        var entry = findBySessionId(sessionId);
        return entry != null ? entry.getValue() : null;
    }

    /**
     * 记录会话使用的实例（成功中继后调用）。
     * 如果 session 不在缓存中，静默忽略。
     */
    public synchronized void recordInstance(String sessionId, long instanceId) {
        var entry = findBySessionId(sessionId);
        if (entry != null) {
            SessionTrack existing = entry.getValue();
            store.put(entry.getKey(), new SessionTrack(
                existing.sessionId(), existing.hash(), existing.updateCount(),
                instanceId, System.currentTimeMillis()));
        }
    }

    /**
     * 查询会话的上次实例（软粘性）。
     * 返回 OptionalLong.empty() 如果：
     * - 无记录
     * - lastInstanceId 为 null
     * - 已过期（超过 STICKY_TTL_MS）
     *
     * 注意：每次查询都会刷新 lastUsedAt，延长粘性窗口。
     */
    public synchronized OptionalLong getPreferredInstance(String sessionId) {
        var entry = findBySessionId(sessionId);
        if (entry != null) {
            SessionTrack track = entry.getValue();
            if (track.lastInstanceId() == null) return OptionalLong.empty();
            if ((System.currentTimeMillis() - track.lastUsedAt()) > STICKY_TTL_MS)
                return OptionalLong.empty();
            // 刷新 TTL：命中时更新 lastUsedAt，防止粘性提前失效
            touch(entry);
            return OptionalLong.of(track.lastInstanceId());
        }
        return OptionalLong.empty();
    }

    /** 刷新会话的 lastUsedAt（内部方法，调用方须持有锁）。 */
    private void touch(Map.Entry<String, SessionTrack> entry) {
        SessionTrack existing = entry.getValue();
        store.put(entry.getKey(), new SessionTrack(
            existing.sessionId(), existing.hash(), existing.updateCount(),
            existing.lastInstanceId(), System.currentTimeMillis()));
    }

    // --- Message model ---

    public record Message(String role, Object content) {}

    // --- Parsing ---

    /**
     * Parse messages array from JSON request body.
     */
    @SuppressWarnings("unchecked")
    public static List<Message> parseMessages(byte[] rawBody) {
        List<Message> result = new ArrayList<>();
        try {
            var root = mapper.readTree(rawBody);
            var messages = root.get("messages");
            if (messages == null || !messages.isArray()) return result;

            for (JsonNode m : messages) {
                String role = m.has("role") ? m.get("role").asText() : "";
                Object content = null;
                if (m.has("content")) {
                    JsonNode c = m.get("content");
                    if (c.isTextual()) {
                        content = c.asText();
                    } else if (c.isArray()) {
                        content = mapper.treeToValue(c, List.class);
                    } else {
                        content = c.asText();
                    }
                }
                result.add(new Message(role, content));
            }
        } catch (Exception e) {
            log.debug("parseMessages: {}", e.getMessage());
        }
        return result;
    }

    // --- Normalization ---

    @SuppressWarnings("unchecked")
    private static String extractContentText(Object content) {
        if (content == null) return "";
        if (content instanceof String s) return s;
        if (content instanceof List<?> list) {
            var parts = new ArrayList<String>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    if ("text".equals(m.get("type"))) {
                        Object text = m.get("text");
                        if (text instanceof String t && !t.isEmpty()) {
                            parts.add(t);
                        }
                    }
                }
            }
            return String.join("\n", parts);
        }
        return content.toString();
    }

    private static List<String> extractFromConversation(String conv) {
        List<String> lines = new ArrayList<>();
        for (String line : conv.split("\n")) {
            var m = TEXT_LINE_RE.matcher(line);
            if (m.find()) {
                String text = m.group(1).trim();
                if (!text.isEmpty()) {
                    lines.add(text);
                }
            }
        }
        return lines;
    }

    // --- Crypto utilities ---

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static MessageDigest copyDigest(MessageDigest md) {
        try {
            return (MessageDigest) md.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String hex(byte[] bytes) {
        var sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
