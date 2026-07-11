package com.oneapi.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedactorTest — 验证 5 类敏感 pattern 都被替换为 [REDACTED]，且默认 disabled 不动 string。
 */
class RedactorTest {

    private static final String MASK = "[REDACTED]";

    @Test
    void redactsOpenaiKey() {
        var r = new Redactor();
        String input = "Authorization with sk-abcd1234EFGH5678ijkl9012mnop body";
        String out = r.redact(input);
        assertThat(out).contains(MASK).doesNotContain("sk-abcd1234EFGH5678ijkl9012mnop");
    }

    @Test
    void redactsBearer() {
        var r = new Redactor();
        String input = "request header: Bearer abc.def-ghi_jklMNOpqrSTU";
        String out = r.redact(input);
        assertThat(out).contains(MASK).doesNotContain("abc.def-ghi_jklMNOpqrSTU");
    }

    @Test
    void redactsEmail() {
        var r = new Redactor();
        String out = r.redact("contact ops@example.com for token");
        assertThat(out).contains(MASK).doesNotContain("ops@example.com");
    }

    @Test
    void redactsPhoneCn() {
        var r = new Redactor();
        String out = r.redact("phone 13812345678 is on file");
        assertThat(out).contains(MASK).doesNotContain("13812345678");
    }

    @Test
    void redactsCardLike() {
        var r = new Redactor();
        String out = r.redact("card 4111111111111111 verified");
        assertThat(out).contains(MASK).doesNotContain("4111111111111111");
    }

    @Test
    void disabledFlag_doesNothing() {
        // disabled 时所有 flag false → 不脱敏
        var r = new Redactor(false, false, false, false, false);
        String input = "keep sk-abcd1234EFGH5678ijkl9012mnop and ops@example.com";
        String out = r.redact(input);
        assertThat(out).isEqualTo(input);
    }

    @Test
    void emptyOrNull_returnedAsIs() {
        var r = new Redactor();
        assertThat(r.redact(null)).isNull();
        assertThat(r.redact("")).isEqualTo("");
    }

    @Test
    void nonSensitive_passThrough() {
        var r = new Redactor();
        String out = r.redact("hello world open to public");
        assertThat(out).isEqualTo("hello world open to public");
    }
}
