package com.oneapi.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class SessionTrackTest {

    private SessionTracker sessions;

    @BeforeEach
    void init() {
        sessions = new SessionTracker();
    }

    @Test
    void testGetPreferredInstance_NoRecord() {
        // 无记录时返回 empty
        assertTrue(sessions.getPreferredInstance("nonexistent").isEmpty());
    }

    @Test
    void testGetPreferredInstance_WithRecord() {
        // 创建会话
        String sid = sessions.match(List.of(
            new SessionTracker.Message("user", "hello")
        ));

        // 记录实例
        sessions.recordInstance(sid, 42L);

        // 应返回正确 ID
        assertEquals(OptionalLong.of(42L), sessions.getPreferredInstance(sid));
    }

    @Test
    void testGetPreferredInstance_Expired() throws Exception {
        // 创建会话并记录
        String sid = sessions.match(List.of(
            new SessionTracker.Message("user", "hello")
        ));
        sessions.recordInstance(sid, 42L);

        // 用反射把 lastUsedAt 设为 epoch 0（远超 TTL）
        Field storeField = SessionTracker.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, SessionTracker.SessionTrack> store =
            (Cache<String, SessionTracker.SessionTrack>) storeField.get(sessions);

        for (var entry : store.asMap().entrySet()) {
            if (entry.getValue().sessionId().equals(sid)) {
                var expired = new SessionTracker.SessionTrack(
                    sid, entry.getValue().hash(), entry.getValue().updateCount(),
                    42L, 0L  // lastUsedAt=0, 一定过期
                );
                store.put(entry.getKey(), expired);
                break;
            }
        }

        // TTL 过期后返回 empty
        assertTrue(sessions.getPreferredInstance(sid).isEmpty());
    }

    @Test
    void testGetPreferredInstance_Updated() {
        // 创建会话
        String sid = sessions.match(List.of(
            new SessionTracker.Message("user", "hello")
        ));

        // 记录实例 42
        sessions.recordInstance(sid, 42L);
        assertEquals(OptionalLong.of(42L), sessions.getPreferredInstance(sid));

        // 更新为 99
        sessions.recordInstance(sid, 99L);
        assertEquals(OptionalLong.of(99L), sessions.getPreferredInstance(sid));
    }

    // --- lookup ---

    @Test
    void testLookup_ReturnsSessionTrack() {
        String sid = sessions.match(List.of(
            new SessionTracker.Message("user", "hello")
        ));
        var track = sessions.lookup(sid);
        assertNotNull(track);
        assertEquals(sid, track.sessionId());
        assertEquals(0, track.updateCount());
    }

    @Test
    void testLookup_NotFound_ReturnsNull() {
        assertNull(sessions.lookup("nonexistent"));
    }

    // --- recordInstance lastUsedAt ---

    @Test
    void testRecordInstance_UpdatesLastUsedAt() {
        String sid = sessions.match(List.of(
            new SessionTracker.Message("user", "hello")
        ));

        sessions.recordInstance(sid, 1L);
        long firstUsedAt = sessions.lookup(sid).lastUsedAt();
        assertTrue(firstUsedAt > 0, "lastUsedAt should be set");

        // 等 1ms 确保时间戳不同，再记录另一个实例
        try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        sessions.recordInstance(sid, 2L);
        long secondUsedAt = sessions.lookup(sid).lastUsedAt();
        assertTrue(secondUsedAt > firstUsedAt, "lastUsedAt should be refreshed");
    }
}