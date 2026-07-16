package com.oneapi.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;

import com.oneapi.jpa.InstanceJpaRepository;
import com.oneapi.jpa.ModelCatalogJpaRepository;
import com.oneapi.jpa.VendorJpaRepository;
import com.oneapi.jpa.VirtualModelJpaRepository;
import com.oneapi.service.InstanceService;
import com.oneapi.service.VendorService;
import com.oneapi.service.VirtualModelService;
import com.oneapi.service.ModelCatalogService;
import com.oneapi.service.RelayLogService;
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

import org.springframework.context.annotation.Configuration;

import java.io.Closeable;
import java.util.List;

/**
 * Vert.x 路由配置。
 * <p>
 * 所有数据访问统一通过 Spring 注入的 JPA Repository/Service 获取，
 * 不再使用 {@link org.springframework.context.ApplicationContext#getBean(Class)}。
 */
@Configuration
public class RouterConfig implements Closeable {

    private final Vertx vertx;
    private final Router router;
    private final AppConfig config;
    private final CooldownService cooldown;
    private final HolographicLogRecorder holographicRecorder;
    private final RelayLogService relayLogService;

    private final InstanceJpaRepository instanceJpaRepo;
    private final VendorJpaRepository vendorJpaRepo;
    private final VirtualModelJpaRepository virtualModelJpaRepo;
    private final ModelCatalogJpaRepository modelCatalogJpaRepo;

    private final InstanceService instanceService;
    private final VendorService vendorService;
    private final VirtualModelService virtualModelService;
    private final ModelCatalogService modelCatalogService;
    private final VendorRefreshService vendorRefreshService;
    private final BalanceQueryService balanceQueryService;

    private UpstreamClient upstreamClient;

    public RouterConfig(Vertx vertx, AppConfig config,
                        CooldownService cooldown,
                        HolographicLogRecorder holographicRecorder,
                        RelayLogService relayLogService,
                        InstanceJpaRepository instanceJpaRepo,
                        VendorJpaRepository vendorJpaRepo,
                        VirtualModelJpaRepository virtualModelJpaRepo,
                        ModelCatalogJpaRepository modelCatalogJpaRepo,
                        InstanceService instanceService,
                        VendorService vendorService,
                        VirtualModelService virtualModelService,
                        ModelCatalogService modelCatalogService,
                        VendorRefreshService vendorRefreshService,
                        BalanceQueryService balanceQueryService) {
        this.vertx = vertx;
        this.config = config;
        this.cooldown = cooldown;
        this.holographicRecorder = holographicRecorder;
        this.relayLogService = relayLogService;
        this.instanceJpaRepo = instanceJpaRepo;
        this.vendorJpaRepo = vendorJpaRepo;
        this.virtualModelJpaRepo = virtualModelJpaRepo;
        this.modelCatalogJpaRepo = modelCatalogJpaRepo;
        this.instanceService = instanceService;
        this.vendorService = vendorService;
        this.virtualModelService = virtualModelService;
        this.modelCatalogService = modelCatalogService;
        this.vendorRefreshService = vendorRefreshService;
        this.balanceQueryService = balanceQueryService;
        this.router = Router.router(vertx);
    }

    public Router build() {
        // 全局中间件
        router.route().handler(new CORS());

        registerStaticRoutes();
        registerApiRoutes();
        registerRelayRoutes();
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
    private void registerApiRoutes() {
        // BodyHandler for all /api/* routes so controllers can use ctx.body().
        router.route("/api/*").handler(BodyHandler.create());

        var misc = new MiscController(cooldown);
        router.get("/api/status").blockingHandler(misc::status);

        var vendorCtrl = new VendorController(vendorService, vendorRefreshService, balanceQueryService);
        router.get("/api/vendors").blockingHandler(vendorCtrl::getAll);
        router.get("/api/vendors/:id").blockingHandler(vendorCtrl::getOne);
        router.get("/api/vendors/:id/balance").blockingHandler(vendorCtrl::getBalance);
        router.post("/api/vendors/balance-query").blockingHandler(vendorCtrl::queryAllBalances);
        router.post("/api/vendors").blockingHandler(vendorCtrl::create);
        router.put("/api/vendors/:id").blockingHandler(vendorCtrl::update);
        router.delete("/api/vendors/:id").blockingHandler(vendorCtrl::delete);
        router.post("/api/vendors/:id/refresh-models").blockingHandler(vendorCtrl::refreshModels);

        var instanceCtrl = new InstanceController(instanceService, vendorService);
        router.get("/api/instances").blockingHandler(instanceCtrl::getAll);
        router.get("/api/instances/:id").blockingHandler(instanceCtrl::getOne);
        router.post("/api/instances").blockingHandler(instanceCtrl::create);
        router.put("/api/instances/:id").blockingHandler(instanceCtrl::update);
        router.post("/api/instances/:id/toggle").blockingHandler(instanceCtrl::toggle);
        router.delete("/api/instances/:id").blockingHandler(instanceCtrl::delete);

        var vmCtrl = new VirtualModelController(virtualModelService);
        router.get("/api/virtual-models").blockingHandler(vmCtrl::getAll);
        router.get("/api/virtual-models/:id").blockingHandler(vmCtrl::getOne);
        router.post("/api/virtual-models").blockingHandler(vmCtrl::create);
        router.put("/api/virtual-models/:id").blockingHandler(vmCtrl::update);
        router.delete("/api/virtual-models/:id").blockingHandler(vmCtrl::delete);

        var mcCtrl = new ModelCatalogController(modelCatalogService);
        router.get("/api/model-catalog").blockingHandler(mcCtrl::getAll);
        router.get("/api/model-catalog/:name").blockingHandler(mcCtrl::getOne);
        router.post("/api/model-catalog").blockingHandler(mcCtrl::create);
        router.put("/api/model-catalog/:name").blockingHandler(mcCtrl::update);
        router.delete("/api/model-catalog/:name").blockingHandler(mcCtrl::delete);
    }

    /** Relay routes — event-loop based async pipeline. */
    private void registerRelayRoutes() {
        // Body is read directly by RelayControllerV2 to avoid double-read with BodyHandler.
        router.post("/v1/chat/completions")
            .handler(new RequestSetup())
            .handler(buildV2Controller()::handle);

        var modelsCtrl = new com.oneapi.controller.ModelsController(virtualModelJpaRepo);
        router.get("/v1/models")
            .handler(modelsCtrl::list);
    }

    private RelayControllerV2 buildV2Controller() {
        var routerSvc = new RouterService(instanceJpaRepo, cooldown);
        var sessions = new SessionTracker();

        FilterSets filters = buildFilters();

        var upstreamClient = new UpstreamClient(
            WebClient.create(vertx, new io.vertx.ext.web.client.WebClientOptions()
                .setConnectTimeout(30000)
                .setIdleTimeout(120)
                .setUserAgentEnabled(false)), vertx);
        this.upstreamClient = upstreamClient;
        var baseRelay = new DefaultRelay(upstreamClient);

        var coordinator = new RelayCoordinator(
            routerSvc, cooldown, sessions, upstreamClient,
            filters.stage2, filters.stage3, baseRelay, config,
            holographicRecorder, relayLogService);
        return new RelayControllerV2(coordinator);
    }

    private FilterSets buildFilters() {
        boolean requireVirtualModel = config != null && config.getRelay() != null
            ? config.getRelay().isRequireVirtualModel() : true;
        var nameMatcher = new NameMatcher(instanceJpaRepo, requireVirtualModel);
        var vmLookup = new VirtualModelLookup(virtualModelJpaRepo,
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
        var capInstanceFilter = new CapabilityInstanceFilter(modelCatalogService);
        var bodyLimitFilter = new BodyLimitFilter(modelCatalogService);
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
