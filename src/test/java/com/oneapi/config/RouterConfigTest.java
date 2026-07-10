package com.oneapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.oneapi.filter.*;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.repo.ModelCatalogRepo;
import com.oneapi.repo.VirtualModelRepo;
import com.oneapi.service.CooldownService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouterConfigTest {
    private static final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private static InstanceRepo instanceRepo;
    private static VirtualModelRepo vmRepo;
    private static ModelCatalogRepo catalogRepo;

    @BeforeAll
    static void initDb() throws Exception {
        DatabaseConfig.init("test");
        var ds = DatabaseConfig.getDataSource();
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS model_catalog (" +
                "name TEXT PRIMARY KEY," +
                "capabilities TEXT," +
                "context_window INTEGER," +
                "input_price REAL," +
                "output_price REAL," +
                "reference_notes TEXT" +
                ")");
        }
        instanceRepo = new InstanceRepo(ds);
        vmRepo = new VirtualModelRepo(ds);
        catalogRepo = new ModelCatalogRepo(ds);
    }

    // ── helpers ─────────────────────────────────────────────

    private RouterConfig.FilterSets buildFilters() throws Exception {
        String y = """
            server:
              port: 8080
            relay:
              maxRetries: 5
              cacheTtlSeconds: 100
              layerOrder:
                - subscription
                - free
                - payg
            policies:
              reasoning:
                triggerSuffix: ":max"
            """;
        AppConfig config = yaml.readValue(y, AppConfig.class);
        var rc = new RouterConfig(io.vertx.core.Vertx.vertx(), config);
        return rc.buildFilters(new CooldownService(), instanceRepo, vmRepo, catalogRepo);
    }

    // ── stage2 filter chain tests ────────────────────────────

    @Test
    void stage2_containsExpectedFilters_inOrder() throws Exception {
        List<Filter> stage2 = buildFilters().stage2;

        assertThat(stage2).hasSize(4);
        assertThat(stage2.get(0)).isInstanceOf(NameMatcher.class);
        assertThat(stage2.get(1)).isInstanceOf(VirtualModelLookup.class);
        assertThat(stage2.get(2)).isInstanceOf(CapabilityRequirementMarker.class);
        assertThat(stage2.get(3)).isInstanceOf(VisionFilter.class);
    }

    @Test
    void stage2_filtersAreNonNull() throws Exception {
        List<Filter> stage2 = buildFilters().stage2;

        stage2.forEach(f -> assertThat(f).isNotNull());
    }

    // ── stage3 filter chain tests ────────────────────────────

    @Test
    void stage3_containsExpectedFilters_inOrder() throws Exception {
        List<Filter> stage3 = buildFilters().stage3;

        assertThat(stage3).hasSize(6);
        assertThat(stage3.get(0)).isInstanceOf(CooldownFilter.class);
        assertThat(stage3.get(1)).isInstanceOf(CapabilityInstanceFilter.class);
        assertThat(stage3.get(2)).isInstanceOf(BodyLimitFilter.class);
        assertThat(stage3.get(3)).isInstanceOf(TagFilter.class);
        assertThat(stage3.get(4)).isInstanceOf(LayerFilter.class);
        assertThat(stage3.get(5)).isInstanceOf(ActiveStatusFilter.class);
    }

    @Test
    void stage3_filtersAreNonNull() throws Exception {
        List<Filter> stage3 = buildFilters().stage3;

        stage3.forEach(f -> assertThat(f).isNotNull());
    }

    // ── filter ordering semantics ────────────────────────────

    @Test
    void capabilityFilter_before_bodyLimit() throws Exception {
        List<Filter> stage3 = buildFilters().stage3;

        int capIdx = -1, bodyIdx = -1;
        for (int i = 0; i < stage3.size(); i++) {
            if (stage3.get(i) instanceof CapabilityInstanceFilter) capIdx = i;
            if (stage3.get(i) instanceof BodyLimitFilter) bodyIdx = i;
        }
        assertThat(capIdx).isLessThan(bodyIdx)
            .as("CapabilityInstanceFilter must be before BodyLimitFilter (capability reduces candidates first)");
    }
}
