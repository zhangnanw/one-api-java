# One-API-Java V2 错误模型文档

> 描述当前实际实现的 `RelayError` sealed interface。最后更新：2026-06-29。

## 实际错误类型

```java
public sealed interface RelayError
    permits RelayError.ModelNotFound, RelayError.NoInstance,
            RelayError.AllVendorsBusy, RelayError.UpstreamFailure,
            RelayError.BodyTooLarge {

    record ModelNotFound(String requestedModel) implements RelayError {}
    record NoInstance(String model, String reason) implements RelayError {}
    record AllVendorsBusy(int retried) implements RelayError {}
    record UpstreamFailure(int httpCode, String responseBody) implements RelayError {}
    record BodyTooLarge(String model, int bodyBytes, int windowTokens) implements RelayError {}
}
```

| 类型 | 触发阶段 | 说明 |
|------|----------|------|
| `ModelNotFound` | 阶段 2 | 虚拟模型表无记录，或物理模型名被禁止直通 |
| `NoInstance` | 阶段 3 | 所有实例被过滤后无可用候选 |
| `AllVendorsBusy` | 阶段 5 | 所有候选实例均失败（缓冲式遍历完队列） |
| `UpstreamFailure` | 阶段 5 | 上游返回非 2xx 或空响应 |
| `BodyTooLarge` | 阶段 3 | [`BodyLimitFilter.java`](src/main/java/com/oneapi/filter/BodyLimitFilter.java) 判定请求体超过模型上下文窗口 |

## HTTP 状态码映射

```java
default int httpStatus() {
    if (this instanceof ModelNotFound) {
        return 404;
    } else if (this instanceof NoInstance) {
        return 503;
    } else if (this instanceof AllVendorsBusy) {
        return 503;
    } else if (this instanceof UpstreamFailure f) {
        return f.httpCode();
    } else if (this instanceof BodyTooLarge) {
        return 413;
    } else {
        return 500;  // 兜底
    }
}
```

| 错误类型 | HTTP 状态码 |
|----------|------------|
| `ModelNotFound` | 404 |
| `NoInstance` | 503 |
| `AllVendorsBusy` | 503 |
| `UpstreamFailure` | 上游返回码（如 429, 502, 503） |
| `BodyTooLarge` | 413 |

## JSON 响应格式

```json
{
    "error": {
        "message": "model not registered: deepseek-v4",
        "type": "relay_error",
        "code": 404
    }
}
```

`type` 固定为 `"relay_error"`，`code` 为 HTTP 状态码。

## 实际代码中的错误处理

不同于早期设计蓝图的 `Either<RelayError, T>` 模式，当前代码的错误处理采用**`RelayContext` 标记模式**：

- 阶段 2/3 的 `Filter` 通过 `ctx.markError(RelayError, message)` 标记错误，而非返回 `Either`
- 协调器在每个阶段后检查 `ctx.hasError()`，有错误时直接返回对应的 HTTP 响应
- 阶段 5 的上游错误通过 `RelayException` 包装 `RelayError.UpstreamFailure` 传递

### 各阶段错误触发点

| 阶段 | 文件 | 触发条件 | 返回错误 |
|------|------|----------|----------|
| 阶段 1 | [`RequestParser.java`](src/main/java/com/oneapi/coordinator/RequestParser.java) | body 为空或缺少 model 字段 | 直接返回 400（不走 RelayError） |
| 阶段 2 | [`VirtualModelLookup.java`](src/main/java/com/oneapi/filter/VirtualModelLookup.java) | 虚拟模型未找到 | `ModelNotFound` → 404 |
| 阶段 3 | [`RelayCoordinator.java`](src/main/java/com/oneapi/coordinator/RelayCoordinator.java) | 无候选实例（`loadCandidates` 返回空） | 直接返回 503（不走 RelayError） |
| 阶段 3 | [`BodyLimitFilter.java`](src/main/java/com/oneapi/filter/BodyLimitFilter.java) | 所有候选被体长过滤 | `BodyTooLarge` → 413 |
| 阶段 3 | 多个 filter | 过滤后候选为空 | `NoInstance` 或 直接 400 |
| 阶段 5 | [`DefaultRelay.java`](src/main/java/com/oneapi/relay/DefaultRelay.java) | 上游非 2xx 或空响应 | `UpstreamFailure` → 上游码 |
| 阶段 5 | [`RelayCoordinator.java`](src/main/java/com/oneapi/coordinator/RelayCoordinator.java) | 所有候选耗尽 | 直接返回 503 |

## 旁路日志

`RelayRecorder` 写入 `relay-log.db` 时保留 `error_code`（HTTP 状态码）+ `error_message`（message 字符串），不改为强类型。日志消费方向分析，格式不变好做现有查询。

## 与早期设计的差异

以下类型在早期设计蓝图（`ERROR-MODEL.md` v1）中被提及，但**当前代码中不存在**：

| 设计蓝图 | 当前状态 | 说明 |
|----------|----------|------|
| `ParseError` | 未实现 | 解析失败直接返回 400，不通过 RelayError |
| `MatchRuleError` | 未实现 | match JSON 解析异常由 `MatchRuleParser` 抛 `IllegalArgumentException` |
| `NoReasoningInstance` | 未实现 | reasoning 需求在阶段 2 标记，阶段 3 由 `CapabilityInstanceFilter` 处理，不单独报错 |
| `Timeout` | 未实现 | 超时由 `UpstreamClient` 处理，统一返回 502/503 |
