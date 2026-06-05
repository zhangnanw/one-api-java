package com.oneapi.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import com.oneapi.repo.VirtualModelRepo;
import com.oneapi.controller.MiscController;
import com.oneapi.controller.VendorController;
import com.oneapi.controller.InstanceController;
import com.oneapi.controller.VirtualModelController;
import com.oneapi.controller.RelayController;
import com.oneapi.middleware.CORS;
import com.oneapi.middleware.RequestSetup;

public class RouterConfig {
    private final Vertx vertx;
    private final Router router;

    public RouterConfig(Vertx vertx) {
        this.vertx = vertx;
        this.router = Router.router(vertx);
    }

    public Router build() {
        // Global middleware
        router.route().handler(new CORS());

        registerApiRoutes();
        registerRelayRoutes();
        registerFallback();
        return router;
    }

    private void registerApiRoutes() {
        var misc = new MiscController();
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

    private void registerRelayRoutes() {
        // /v1/models — OpenAI-compatible model list
        router.get("/v1/models").handler(ctx -> {
            var repo = new VirtualModelRepo();
            var data = new io.vertx.core.json.JsonArray();
            for (var vm : repo.findAll()) {
                data.add(new JsonObject()
                    .put("id", vm.getName())
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

        var relayCtrl = new RelayController(vertx);
        var requestSetup = new RequestSetup();

        // All /v1/* paths go through request setup then relay
        router.route("/v1/*").handler(requestSetup);
        router.route("/v1/*").handler(relayCtrl::handle);
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
