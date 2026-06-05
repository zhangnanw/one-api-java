# One-API-Java V2 错误模型文档

## 迁移范围

所有当前返回 `503` 的字符串错误 → sealed class `RelayError`，HTTP 状态码集中映射。

## 完整错误类型

```java
sealed interface RelayError {

    // ----- 阶段 1：请求解析 -----
    record ParseError(String detail) implements RelayError {}
    // 触发：body 为空、JSON 格式错误、缺少 model 字段

    // ----- 阶段 2：虚拟模型解析 -----
    record ModelNotFound(String requestedModel) implements RelayError {}
    // 触发：物理名无直通规则、虚拟模型表无记录

    record MatchRuleError(String model, String detail) implements RelayError {}
    // 触发：match JSON 格式错误 / 不支持的 match 类型

    // ----- 阶段 3：实例筛选 -----
    record NoInstance(String model, String reason) implements RelayError {}
    // 触发：所有实例被过滤（冷却、标签、层、优先级、raw 状态等）

    record NoReasoningInstance(String model) implements RelayError {}
    // 触发：需要推理增强（-max 后缀），但无 capability:reasoning 实例

    // ----- 阶段 5：上游执行 -----
    record AllVendorsBusy(int retried) implements RelayError {}
    // 触发：MAX_RETRIES 耗尽（当前 MAX_RETRIES=2）

    record UpstreamFailure(int httpCode, String responseBody) implements RelayError {}
    // 触发：上游返回 5xx 或非预期状态码

    record Timeout(Duration duration) implements RelayError {}
    // 触发：请求超时
}
```

## HTTP 状态码映射

```java
class RelayErrorMapper {
    static int toHttpStatus(RelayError e) {
        return switch (e) {
            case ParseError __        -> 400;
            case ModelNotFound __     -> 404;
            case MatchRuleError __    -> 400;
            case NoInstance __        -> 503;
            case NoReasoningInstance __ -> 503;
            case AllVendorsBusy __    -> 503;
            case UpstreamFailure f    -> f.httpCode();
            case Timeout __           -> 504;
        };
    }
}
```

## JSON 响应格式

```json
{
    "error": {
        "type": "NoReasoningInstance",
        "message": "no reasoning-capable instance available",
        "model": "mimo-v2.5-pro"
    }
}
```

`type` 字段 = sealed class 名称，客户端可 switch/match。

## V1 → V2 替换对照

| V1 | V2 |
|----|----|
| `error(ctx, 400, "body is empty")` | `Either.left(new ParseError("body is empty"))` |
| `error(ctx, 503, "no reasoning-capable instance available")` | `Either.left(new NoReasoningInstance(model))` |
| `error(ctx, 503, "all vendors busy")` | `Either.left(new AllVendorsBusy(retried))` |
| `error(ctx, 503, "model exhausted retries")` | `Either.left(new AllVendorsBusy(MAX_RETRIES))` |
| `throw new RuntimeException(...)` | 不出现——所有错误走 Either |

## 旁路日志保留旧格式

`RelayRecorder` 写入 `relay-log.db` 时保留 `error_code`（HTTP 状态码）+ `error_message`（message 字符串），不改为强类型。日志消费方向分析，格式不变好做现有查询。
