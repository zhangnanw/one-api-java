package com.oneapi.service;

import com.oneapi.service.SessionTracker.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTrackerTest {

    SessionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SessionTracker();
    }

    @Test
    void emptyMessages_returnsEmpty() {
        String sid = tracker.match(List.of());
        assertThat(sid).isEmpty();
    }

    @Test
    void sameConversation_returnsSameSession() {
        List<Message> msgs = messages("hello", "how are you", "fine thanks");
        String sid1 = tracker.match(msgs);
        String sid2 = tracker.match(msgs);
        assertThat(sid1).isNotEmpty();
        assertThat(sid1).isEqualTo(sid2);
    }

    @Test
    void differentConversation_returnsDifferentSession() {
        List<Message> msgs1 = messages("hello", "how are you", "fine thanks");
        List<Message> msgs2 = messages("goodbye", "see you later", "bye");
        String sid1 = tracker.match(msgs1);
        String sid2 = tracker.match(msgs2);
        assertThat(sid1).isNotEqualTo(sid2);
    }

    @Test
    void continuationOfSameConversation_sameSession() {
        List<Message> msgs1 = messages("line1", "line2", "line3");
        String sid1 = tracker.match(msgs1);

        // Add one more line — same prefix should match
        List<Message> msgs2 = messages("line1", "line2", "line3", "line4");
        String sid2 = tracker.match(msgs2);

        assertThat(sid1).isEqualTo(sid2);
    }

    @Test
    void systemMessages_ignored() {
        List<Message> withSystem = List.of(
            new Message("system", "you are a helpful assistant"),
            new Message("user", "hello"),
            new Message("assistant", "hi"),
            new Message("user", "how are you")
        );
        List<Message> withoutSystem = List.of(
            new Message("user", "hello"),
            new Message("assistant", "hi"),
            new Message("user", "how are you")
        );
        String sid1 = tracker.match(withSystem);
        String sid2 = tracker.match(withoutSystem);
        // Both should produce the same session since system messages are stripped
        assertThat(sid1).isEqualTo(sid2);
    }

    @Test
    void parseMessages_parsesJsonArray() {
        String json = new JsonObject()
            .put("messages", new JsonArray()
                .add(new JsonObject().put("role", "user").put("content", "hello"))
                .add(new JsonObject().put("role", "assistant").put("content", "hi")))
            .encode();

        List<Message> parsed = SessionTracker.parseMessages(json.getBytes());
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).role()).isEqualTo("user");
        assertThat(parsed.get(0).content()).isEqualTo("hello");
    }

    @Test
    void parseMessages_emptyBody_returnsEmpty() {
        List<Message> parsed = SessionTracker.parseMessages(new byte[0]);
        assertThat(parsed).isEmpty();
    }

    @Test
    void captureSummary_updatesHash() {
        List<Message> msgs = messages("line1", "line2", "line3");
        String sid = tracker.match(msgs);
        String oldHash = tracker.lookup(sid).hash();

        tracker.captureSummary(sid, "compressed summary");
        String newHash = tracker.lookup(sid).hash();

        assertThat(newHash).isNotEqualTo(oldHash);
        assertThat(tracker.lookup(sid).sessionId()).isEqualTo(sid);
    }

    // --- helpers ---

    private static List<Message> messages(String... lines) {
        return java.util.Arrays.stream(lines)
            .map(line -> new Message("user", line))
            .toList();
    }
}
