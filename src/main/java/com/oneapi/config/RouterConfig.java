package com.oneapi.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;

import com.oneapi.repo.InstanceRepo;
import com.oneapi.repo.VendorRepo;
import com.oneapi.repo.VirtualModelRepo;
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
import com.oneapi.core.CooldownService;
import com.oneapi.core.HolographicLogRecorder;
import com.oneapi.core.RouterService;
import com.oneapi.core.SessionTracker;
import com.oneapi.background.VendorRefreshService;
import com.oneapi.background.BalanceQueryService;

import javax.sql.DataSource;
import java.io.Closeable;
import java.util.List;

public class RouterConfig implements Closeable {
    private final Vertx vertx;
    private final Router router;
    private final AppConfig config;
    private final DataSource dataSource;

    // Shared across builds so /api/status and relay routes see the same cooldown state.
    private CooldownService cooldown;
    private UpstreamClient upstreamClient;

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
        if (cooldown == null) {
            cooldown = new CooldownService();
        }

        // DataSource init — single source for all repos
        var ds = dataSource != null ? dataSource : DatabaseConfig.getDataSource();

        var instanceRepo = new InstanceRepo(ds);
        var vendorRepo = new VendorRepo(ds);
        var vmRepo = new VirtualModelRepo(ds);
        var catalogRepo = new ModelCatalogRepo(ds);
        var vendorRefreshSvc = new VendorRefreshService(instanceRepo, vendorRepo);
        var balanceQuerySvc = new BalanceQueryService(vendorRepo);

        // 定时轮询供应商余额（每 5 分钟）
        vertx.setPeriodic(5 * 60 * 1000, id -> {
            vertx.executeBlocking(() -> {
                balanceQuerySvc.queryAll();
                return null;
            }).onFailure(err -> {});
        });
        // 启动后 3 秒执行一次
        vertx.setTimer(3000, id -> {
            vertx.executeBlocking(() -> {
                balanceQuerySvc.queryAll();
                return null;
            }).onFailure(err -> {});
        });

        registerStaticRoutes();
        registerApiRoutes(cooldown, vendorRepo, instanceRepo, vmRepo, catalogRepo, vendorRefreshSvc, balanceQuerySvc);
        registerRelayRoutes(cooldown, vmRepo, instanceRepo, vendorRepo, catalogRepo);
        registerFallback();
        return router;
    }

    @Override
    public void close() {
        if (upstreamClient != null) {
            upstreamClient.close();
            upstreamClient = null;
        }
    }

    /** Serve static files from classpath:/static/ */
    private void registerStaticRoutes() {
        router.get("/status").handler(ctx -> {
            try (var is = getClass().getClassLoader().getResourceAsStream("static/status.html")) {
                if (is == null) {
                    ctx.response().setStatusCode(404).end("status page not found");
                    return;
                }
                ctx.response().putHeader("Content-Type", "text/html").end(new String(is.readAllBytes()));
            } catch (Exception e) {
                ctx.response().setStatusCode(500).end("failed to load status page");
            }
        });
    }

    /** API routes — DB-backed CRUD, run on worker pool. */
    private void registerApiRoutes(CooldownService cooldown, VendorRepo vendorRepo,
                                    InstanceRepo instanceRepo, VirtualModelRepo vmRepo,
                                    ModelCatalogRepo catalogRepo, VendorRefreshService vendorRefreshSvc,
                                    BalanceQueryService balanceQuerySvc) {
        // BodyHandler for all /api/* routes so controllers can use ctx.body().
        router.route("/api/*").handler(BodyHandler.create());

        var misc = new MiscController(cooldown);
        router.get("/api/status").blockingHandler(misc::status);

        var vendorCtrl = new VendorController(vendorRepo, vendorRefreshSvc, balanceQuerySvc);
        router.get("/api/vendors").blockingHandler(vendorCtrl::getAll);
        router.get("/api/vendors/:id").blockingHandler(vendorCtrl::getOne);
        router.get("/api/vendors/:id/balance").blockingHandler(vendorCtrl::getBalance);
        router.post("/api/vendors").blockingHandler(vendorCtrl::create);
        router.put("/api/vendors/:id").blockingHandler(vendorCtrl::update);
        router.delete("/api/vendors/:id").blockingHandler(vendorCtrl::delete);
        router.post("/api/vendors/:id/refresh-models").blockingHandler(vendorCtrl::refreshModels);

        var instanceCtrl = new InstanceController(instanceRepo, vendorRepo);
        router.get("/api/instances").blockingHandler(instanceCtrl::getAll);
        router.get("/api/instances/:id").blockingHandler(instanceCtrl::getOne);
        router.post("/api/instances").blockingHandler(instanceCtrl::create);
        router.put("/api/instances/:id").blockingHandler(instanceCtrl::update);
        router.post("/api/instances/:id/toggle").blockingHandler(instanceCtrl::toggle);
        router.delete("/api/instances/:id").blockingHandler(instanceCtrl::delete);

        var vmCtrl = new VirtualModelController(vmRepo);
        router.get("/api/virtual-models").blockingHandler(vmCtrl::getAll);
        router.get("/api/virtual-models/:id").blockingHandler(vmCtrl::getOne);
        router.post("/api/virtual-models").blockingHandler(vmCtrl::create);
        router.put("/api/virtual-models/:id").blockingHandler(vmCtrl::update);
        router.delete("/api/virtual-models/:id").blockingHandler(vmCtrl::delete);

        var mcCtrl = new ModelCatalogController(catalogRepo);
        router.get("/api/model-catalog").blockingHandler(mcCtrl::getAll);
        router.get("/api/model-catalog/:name").blockingHandler(mcCtrl::getOne);
        router.post("/api/model-catalog").blockingHandler(mcCtrl::create);
        router.put("/api/model-catalog/:name").blockingHandler(mcCtrl::update);
        router.delete("/api/model-catalog/:name").blockingHandler(mcCtrl::delete);
    }

    /** Relay routes — event-loop based async pipeline. */
    private void registerRelayRoutes(CooldownService cooldown, VirtualModelRepo vmRepo,
                                    InstanceRepo instanceRepo, VendorRepo vendorRepo,
                                    ModelCatalogRepo catalogRepo) {
        // Body is read directly by RelayControllerV2 to avoid double-read with BodyHandler.
        router.post("/v1/chat/completions")
            .handler(new RequestSetup())
            .handler(buildV2Controller(cooldown, vmRepo, instanceRepo, vendorRepo, catalogRepo)::handle);

        var modelsCtrl = new com.oneapi.controller.ModelsController(vmRepo);
        router.get("/v1/models")
            .handler(modelsCtrl::list);
    }

    private RelayControllerV2 buildV2Controller(CooldownService cooldown, VirtualModelRepo vmRepo,
                                               InstanceRepo instanceRepo, VendorRepo vendorRepo,
                                               ModelCatalogRepo catalogRepo) {
        var routerSvc = new RouterService(instanceRepo);
        routerSvc.setCooldownService(cooldown); // 冷却预过滤（排序前生效）
        var sessions = new SessionTracker();

        FilterSets filters = buildFilters(cooldown, instanceRepo, vmRepo, catalogRepo);

        // 第五阶段：上游客户端
        var upstreamClient = new UpstreamClient(
            WebClient.create(vertx, new io.vertx.ext.web.client.WebClientOptions()
                .setConnectTimeout(30000)
                .setIdleTimeout(120)
                .setUserAgentEnabled(false)), vertx);
        this.upstreamClient = upstreamClient;
        var baseRelay = new DefaultRelay(upstreamClient);

        // 协调器
        var holographicRecorder = new HolographicLogRecorder();
        var coordinator = new RelayCoordinator(
            routerSvc, cooldown, sessions, upstreamClient,
            filters.stage2, filters.stage3, baseRelay, config,
            holographicRecorder);
        return new RelayControllerV2(coordinator);
    }

    /** Exposed for testing — assembles filter chain without Vert.x dependency. */
    FilterSets buildFilters(CooldownService cooldown,
                           InstanceRepo instanceRepo,
                           VirtualModelRepo vmRepo,
                           ModelCatalogRepo catalogRepo) {
        boolean requireVirtualModel = config != null && config.getRelay() != null
            ? config.getRelay().isRequireVirtualModel() : true;
        var nameMatcher = new NameMatcher(instanceRepo, requireVirtualModel);
        var vmLookup = new VirtualModelLookup(vmRepo,
            config.getPolicies() != null && config.getPolicies().getReasoning() != null
                ? config.getPolicies().getReasoning().getTriggerSuffix()
                : "-max");
        var capMarker = new CapabilityRequirementMarker();
        var visionFilter = new VisionFilter();

        List<Filter> stage2 = List.of(
            nameMatcher,
            vmLookup,
            capMarker,
            visionFilter
        );

        var cooldownFilter = new CooldownFilter(cooldown);
        var capInstanceFilter = new CapabilityInstanceFilter(catalogRepo);
        var bodyLimitFilter = new BodyLimitFilter(catalogRepo);
        var tagFilter = new TagFilter();
        var layerFilter = new LayerFilter();
        var activeStatusFilter = new ActiveStatusFilter();

        List<Filter> stage3 = List.of(
            cooldownFilter,
            capInstanceFilter,
            bodyLimitFilter,
            tagFilter,
            layerFilter,
            activeStatusFilter
        );

        return new FilterSets(stage2, stage3);
    }

    private void registerFallback() {
        router.errorHandler(404, ctx -> {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "not found")
                    .toString());
        });
    }

    public static class FilterSets {
        public final List<Filter> stage2;
        public final List<Filter> stage3;
        public FilterSets(List<Filter> stage2, List<Filter> stage3) {
            this.stage2 = stage2;
            this.stage3 = stage3;
        }
    }
}
