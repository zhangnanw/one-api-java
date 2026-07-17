package com.oneapi.model;

import com.oneapi.entity.Instance;
import com.oneapi.entity.Vendor;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateTest {

    @Test
    void threeArgConstructor_defaultEmptyHeaders() {
        Vendor v = new Vendor();
        v.setName("test-vendor");
        Instance i = new Instance();
        i.setId(1);

        Candidate c = new Candidate(v, i, "upstream-model");

        assertThat(c.vendor()).isEqualTo(v);
        assertThat(c.instance()).isEqualTo(i);
        assertThat(c.upstreamModel()).isEqualTo("upstream-model");
        assertThat(c.extraHeaders()).isNotNull();
        assertThat(c.extraHeaders().isEmpty()).isTrue();
    }

    @Test
    void fourArgConstructor_customHeaders() {
        Vendor v = new Vendor();
        Instance i = new Instance();
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("X-Custom", "value");

        Candidate c = new Candidate(v, i, "model", headers);

        assertThat(c.extraHeaders().get("X-Custom")).isEqualTo("value");
    }

    @Test
    void extraHeaders_caseInsensitive() {
        Candidate c = new Candidate(new Vendor(), new Instance(), "model");
        c.extraHeaders().add("X-Foo", "bar");

        assertThat(c.extraHeaders().get("x-foo")).isEqualTo("bar");
        assertThat(c.extraHeaders().get("X-FOO")).isEqualTo("bar");
    }
}
