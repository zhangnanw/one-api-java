package com.oneapi.controller;

import com.oneapi.service.VirtualModelService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

/**
 * {@code /v1/models}：返回 OpenAI 兼容的模型列表。
 */
@RequiredArgsConstructor
public class ModelsController {

    private final VirtualModelService virtualModelService;

    /** 列出所有已注册的虚拟模型。*/
    public void list(RoutingContext ctx) {
        var data = new JsonArray();
        for (var virtualModel : virtualModelService.findAll()) {
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
    }
}
