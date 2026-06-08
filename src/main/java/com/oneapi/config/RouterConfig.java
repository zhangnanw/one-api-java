package com.oneapi.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;

import com.oneapi.repo.VirtualModelRepo;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.repo.ModelCatalogRepo;
import com.oneapi.controller.MiscController;
import com.oneapi.controller.VendorController;
import com.oneapi.controller.InstanceController;
import com.oneapi.controller.VirtualModelController;
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
import com.oneapi.handler.UpstreamClient;
import com.oneapi.relay.DefaultRelay;
import com.oneapi.service.CooldownService;
import com.oneapi.service.RouterService;
import com.oneapi.service.SessionTracker;

import java.util.List;

public class RouterConfig {
    private final Vertx vertx;
    private final Router router;
    private final AppConfig config;

    public RouterConfig(Vertx vertx, AppConfig config) {
        this.vertx = vertx;
        this.config = config;
        this.router = Router.router(vertx);
    }

    public Router build() {
        // 全局中间件
        router.route().handler(new CORS());

        // CooldownService 在所有路由之前单例化，/api/status 与 /v1/chat/completions 共用
        var cooldown = new CooldownService();

        registerApiRoutes(cooldown);
        registerRelayRoutes(cooldown);
        registerFallback();
        return router;
    }

    private void registerApiRoutes(CooldownService cooldown) {
        var misc = new MiscController(cooldown);
        router.get("/api/status").handler(misc::status);

        var vendorCtrl = new VendorController();
        router.get("/api/vendors").handler(vendorCtrl::getAll);
        router.get("/api/vendors/:id").handler(vendorCtrl::getOne);
        router.post("/api/vendors").handler(vendorCtrl::create);
        router.put("/api/vendors/:id").handler(vendorCtrl::update);
        router.delete("/api/vendors/:id").handler(vendorCtrl::delete);
        router.post("/api/vendors/refresh-models").handler(vendorCtrl::refreshModels);

        var instanceCtrl = new InstanceController();
        router.get("/api/instances").handler(instanceCtrl::getAll);
        router.get("/api/instances/:id").handler(instanceCtrl::getOne);
        router.put("/api/instances/:id").handler(instanceCtrl::update);
        router.delete("/api/instances/:id").handler(instanceCtrl::delete);
        router.put("/api/instances/:id/toggle").handler(instanceCtrl::toggle);

        var vmCtrl = new VirtualModelController();
        router.get("/api/virtual-models").handler(vmCtrl::getAll);
        router.get("/api/virtual-models/:id").handler(vmCtrl::getOne);
        router.post("/api/virtual-models").handler(vmCtrl::create);
        router.put("/api/virtual-models/:id").handler(vmCtrl::update);
        router.delete("/api/virtual-models/:id").handler(vmCtrl::delete);
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
        var routerSvc = new RouterService();
        var sessions = new SessionTracker();

        // 第二阶段过滤器（模型解析）
        List<Filter> stage2 = List.of(
            new NameMatcher(new InstanceRepo()),
            new VirtualModelLookup(new VirtualModelRepo(),
                config.getPolicies().getReasoning().getTriggerSuffix()),
            new CapabilityRequirementMarker(),
            new VisionFilter()
        );

        // 第三阶段过滤器（候选实例筛选）
        var catalogRepo = new ModelCatalogRepo(DatabaseConfig.getDataSource());
        List<Filter> stage3 = List.of(
            new CooldownFilter(cooldown),
            new CapabilityInstanceFilter(catalogRepo),
            new TagFilter(),
            new LayerFilter(),
            new ActiveStatusFilter()
        );

        // 第五阶段：上游客户端
        var upstreamClient = new UpstreamClient(
            WebClient.create(vertx), vertx);
        var baseRelay = new DefaultRelay(upstreamClient);

        // 协调器
        var coordinator = new RelayCoordinator(
            routerSvc, cooldown, sessions, upstreamClient,
            stage2, stage3, baseRelay, config);
        return new RelayControllerV2(coordinator);
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
