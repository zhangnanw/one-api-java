package com.oneapi.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;

import com.oneapi.repo.VirtualModelRepo;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.repo.ModelCatalogRepo;
import com.oneapi.controller.MiscController;
import com.oneapi.controller.VendorController;
import com.oneapi.controller.InstanceController;
import com.oneapi.controller.VirtualModelController;
import com.oneapi.controller.ModelCatalogController;
import com.oneapi.controller.RelayControllerV2;
import com.oneapi.coordinator.RelayCoordinator;
import com.oneapi.middleware.CORS;
import com.oneapi.middleware.RequestSetup;
import com.oneapi.filter.Filter;
import com.oneapi.filter.NameMatcher;
import com.oneapi.filter.VirtualModelLookup;
import com.oneapi.filter.CapabilityRequirementMarker;
import com.oneapi.filter.CapabilityInstanceFilter;
import com.oneapi.filter.CooldownFilter;
import com.oneapi.filter.TagFilter;
import com.oneapi.filter.LayerFilter;
import com.oneapi.filter.ActiveStatusFilter;
import com.oneapi.filter.VisionFilter;
import com.oneapi.filter.BodyLimitFilter;
import com.oneapi.handler.UpstreamClient;
import com.oneapi.relay.DefaultRelay;
import com.oneapi.service.CooldownService;
import com.oneapi.service.RouterService;
import com.oneapi.service.SessionTracker;

import javax.sql.DataSource;
import java.util.List;

public class RouterConfig {
    private final Vertx vertx;
    private final Router router;
    private final AppConfig config;
    private final DataSource dataSource;

    public RouterConfig(Vertx vertx, AppConfig config) {
        this(vertx, config, null);
    }

    /** For testing — inject an in-memory DataSource. */
    public RouterConfig(Vertx vertx, AppConfig config, DataSource dataSource) {
        this.vertx = vertx;
        this.config = config;
        this.dataSource = dataSource;
        this.router = Router.router(vertx);
    }

    public Router build() {
        // 全局中间件
        router.route().handler(new CORS());

        // CooldownService 在所有路由之前单例化，/api/status 与 /v1/chat/completions 共用
        var cooldown = new CooldownService();

        registerStaticRoutes();
        registerApiRoutes(cooldown);
        registerRelayRoutes(cooldown);
        registerFallback();
        return router;
    }

    /** Serve static files from classpath:/static/ */
    private void registerStaticRoutes() {
        router.get("/status").handler(ctx -> {
            try (var is = getClass().getClassLoader().getResourceAsStream("static/status.html")) {
                if (is == null) {
                    ctx.response().setStatusCode(404).end("status page not found");
                    return;
                }
                ctx.response()
                    .putHeader("Content-Type", "text/html; charset=utf-8")
                    .end(new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                ctx.response().setStatusCode(500).end("error loading status page");
            }
        });
    }

    private void registerApiRoutes(CooldownService cooldown) {
        var misc = new MiscController(cooldown);
        router.get("/api/status").handler(misc::status);

        var vendorCtrl = new VendorController();
        router.get("/api/vendors").handler(vendorCtrl::getAll);
        router.get("/api/vendors/:id").handler(vendorCtrl::getOne);
        router.post("/api/vendors").handler(BodyHandler.create()).handler(vendorCtrl::create);
        router.put("/api/vendors/:id").handler(BodyHandler.create()).handler(vendorCtrl::update);
        router.delete("/api/vendors/:id").handler(vendorCtrl::delete);
        router.post("/api/vendors/refresh-models").handler(vendorCtrl::refreshModels);

        var instanceCtrl = new InstanceController();
        router.get("/api/instances").handler(instanceCtrl::getAll);
        router.get("/api/instances/:id").handler(instanceCtrl::getOne);
        router.post("/api/instances").handler(BodyHandler.create()).handler(instanceCtrl::create);
        router.put("/api/instances/:id").handler(BodyHandler.create()).handler(instanceCtrl::update);
        router.delete("/api/instances/:id").handler(instanceCtrl::delete);
        router.put("/api/instances/:id/toggle").handler(instanceCtrl::toggle);

        var vmCtrl = new VirtualModelController();
        router.get("/api/virtual-models").handler(vmCtrl::getAll);
        router.get("/api/virtual-models/:id").handler(vmCtrl::getOne);
        router.post("/api/virtual-models").handler(BodyHandler.create()).handler(vmCtrl::create);
        router.put("/api/virtual-models/:id").handler(BodyHandler.create()).handler(vmCtrl::update);
        router.delete("/api/virtual-models/:id").handler(vmCtrl::delete);

        // DataSource fallback: test-route uses injected ds, prod uses DatabaseConfig
        var ds = dataSource != null ? dataSource : DatabaseConfig.getDataSource();
        var mcCtrl = new ModelCatalogController(new ModelCatalogRepo(ds));
        router.get("/api/model-catalog").handler(mcCtrl::getAll);
        router.get("/api/model-catalog/:name").handler(mcCtrl::getOne);
        router.post("/api/model-catalog").handler(BodyHandler.create()).handler(mcCtrl::create);
        router.put("/api/model-catalog/:name").handler(BodyHandler.create()).handler(mcCtrl::update);
        router.delete("/api/model-catalog/:name").handler(mcCtrl::delete);
    }

    private void registerRelayRoutes(CooldownService cooldown) {
        // /v1/models — OpenAI 兼容模型列表
        router.get("/v1/models").handler(ctx -> {
            var repo = new VirtualModelRepo();
            var data = new io.vertx.core.json.JsonArray();
            for (var virtualModel : repo.findAll()) {
                data.add(new JsonObject()
                    .put("id", virtualModel.getName())
                    .put("object", "model")
                    .put("created", 1700000000)
                    .put("owned_by", "one-api"));
            }
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("object", "list")
                    .put("data", data)
                    .toString());
        });

        // /v1/chat/completions — V2 控制器
        var requestSetup = new RequestSetup();
        var v2Ctrl = buildV2Controller(cooldown);
        router.route("/v1/chat/completions").handler(requestSetup);
        router.route("/v1/chat/completions").handler(v2Ctrl::handle);
    }

    private RelayControllerV2 buildV2Controller(CooldownService cooldown) {
        // ── 装配：组装所有 V2 依赖 ──

        // 服务层（cooldown 由 RouterConfig.build() 单例传入）
        var routerSvc = dataSource != null ? new RouterService(dataSource) : new RouterService();
        routerSvc.setCooldownService(cooldown); // 冷却预过滤（排序前生效）
        var sessions = new SessionTracker();

        FilterSets filters = buildFilters(cooldown);

        // 第五阶段：上游客户端
        var upstreamClient = new UpstreamClient(
            WebClient.create(vertx, new io.vertx.ext.web.client.WebClientOptions()
                .setConnectTimeout(30000)
                .setIdleTimeout(120)
                .setUserAgentEnabled(false)), vertx);
        var baseRelay = new DefaultRelay(upstreamClient);

        // 协调器
        var coordinator = new RelayCoordinator(
            routerSvc, cooldown, sessions, upstreamClient,
            filters.stage2, filters.stage3, baseRelay, config);
        return new RelayControllerV2(coordinator);
    }

    /** Exposed for testing — assembles filter chain without Vert.x dependency. */
    FilterSets buildFilters(CooldownService cooldown) {
        // DataSource fallback: test-route uses injected ds, prod uses DatabaseConfig
        var ds = dataSource != null ? dataSource : DatabaseConfig.getDataSource();

        // 第二阶段过滤器（模型解析）
        List<Filter> stage2 = List.of(
            new NameMatcher(new InstanceRepo(ds)),
            new VirtualModelLookup(new VirtualModelRepo(ds),
                config.getPolicies().getReasoning().getTriggerSuffix()),
            new CapabilityRequirementMarker(),
            new VisionFilter()
        );

        // 第三阶段过滤器（候选实例筛选）
        var catalogRepo = new ModelCatalogRepo(ds);
        List<Filter> stage3 = List.of(
            new CooldownFilter(cooldown),
            new CapabilityInstanceFilter(catalogRepo),
            new BodyLimitFilter(catalogRepo),
            new TagFilter(),
            new LayerFilter(),
            new ActiveStatusFilter()
        );

        return new FilterSets(stage2, stage3);
    }

    static class FilterSets {
        final List<Filter> stage2;
        final List<Filter> stage3;
        FilterSets(List<Filter> stage2, List<Filter> stage3) {
            this.stage2 = stage2;
            this.stage3 = stage3;
        }
    }

    private void registerFallback() {
        router.route().last().handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", "not found")
                    .toString());
        });
    }
}
