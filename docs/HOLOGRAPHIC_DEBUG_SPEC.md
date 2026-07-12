# 全息调试日志 — 实现 Spec

## 目标

为 one-api-java 增加全息调试日志，记录每次请求的完整生命周期数据，
方便排查"直通变不可靠"等中继问题。

## 约束

- **独立 DB**：`~/.one-api/holographic-debug.db`，独立连接池，不与现有 DB 共享
- **环形缓冲区**：最多保留最近 50 条记录，超出自动删除最旧
- **全量存储**：请求体 + 响应体全文存储（50 条上限保证空间可控）
- **零侵入**：不改任何现有类的方法签名、不碰 Filter/UpstreamClient/Router/RelayLogger
- **静默失败**：写入失败不影响主链路

## 新建文件

### 1. `com.oneapi.service.HolographicLogger`

```
职责：PostgreSQL 表创建 + 插入 + 环形清理
```

- `init()` — 建表（holographic_logs）
- `write(HolographicRecord)` — 插入记录，然后 `count > 50 → deleteOldest(count - 50)`
- 使用主 HikariCP 连接池

表结构：
```sql
CREATE TABLE IF NOT EXISTS holographic_logs (
    id              BIGSERIAL PRIMARY KEY,
    request_id      TEXT UNIQUE NOT NULL,
    timestamp_ms    BIGINT NOT NULL,
    requested_model TEXT,
    final_status    TEXT,          -- "success" / "failure"
    final_http_code INTEGER,
    total_latency_ms INTEGER,
    total_tokens    INTEGER,
    data            TEXT NOT NULL  -- JSON 全息数据
);
CREATE INDEX IF NOT EXISTS idx_hl_ts ON holographic_logs(timestamp_ms);
CREATE INDEX IF NOT EXISTS idx_hl_model ON holographic_logs(requested_model);
CREATE INDEX IF NOT EXISTS idx_hl_status ON holographic_logs(final_status);
```

### 2. `com.oneapi.model.HolographicRecord`

```
职责：Builder 模式数据模型，逐阶段填充
```

使用内部静态 Builder 类，链式调用：

```java
var record = HolographicRecord.builder(uuid)
    .requestInfo(req, rawBody)       // 阶段1后
    .routingInfo(relayCtx)           // 阶段2后
    .candidatesInfo(candidates, filterResults)  // 阶段3后
    .build();

// 阶段5：每次尝试
record.addAttempt(attempt);
record.addAttempt(attempt);

// 最终
record.finish(status, httpCode, totalLatency, totalTokens, responseBody);
```

内部数据结构（序列化为 JSON 存入 `data` 列）：

```json
{
  "request": {
    "client_ip": "...",
    "requested_model": "...",
    "stream": true,
    "body_size": 12345,
    "body_preview": "前200字符...",
    "messages_count": 10,
    "max_tokens": 4096,
    "temperature": 0.7,
    "auth_type": "Bearer",
    "full_body": "{...完整请求体...}"
  },
  "routing": {
    "virtual_model": "...",
    "match_rule_type": "ModelsMatch",
    "match_rule_detail": ["deepseek-v4-flash", "deepseek-v4-pro"],
    "routing_model_names": ["deepseek-v4-flash", "deepseek-v4-pro"],
    "capability_required": null,
    "filter_chain": [
      {"filter": "VirtualModelLookup", "action": "pass"},
      {"filter": "CapabilityInstanceFilter", "action": "pass"}
    ]
  },
  "candidates": {
    "total": 5,
    "before_filter": [
      {"vendor": "OpenRouter", "instance_id": 696, "layer": "free", "model": "kimi-k2.6", "pref": 0.5}
    ],
    "filter_results": [
      {"filter": "CooldownFilter", "removed": ["OpenRouter#696"], "reason": "in cooldown"}
    ],
    "after_filter": [...],
    "sort_order": [...]
  },
  "attempts": [
    {
      "n": 1,
      "vendor": "OpenRouter",
      "instance_id": 696,
      "upstream_model": "moonshotai/kimi-k2.6:free",
      "upstream_url": "https://...",
      "http_status": 429,
      "latency_ms": 1234,
      "ttfb_ms": 800,
      "error_type": "rate_limited",
      "error_body": "{...}",
      "cooldown_triggered": true,
      "cooldown_duration_sec": 60,
      "tokens": {"prompt": 0, "completion": 0}
    }
  ],
  "stream_metrics": {
    "chunk_count": 45,
    "stream_duration_ms": 15000,
    "first_chunk_ms": 200,
    "last_chunk_ms": 14800
  },
  "result": {
    "final_status": "success",
    "final_http_code": 200,
    "total_latency_ms": 7890,
    "error_stage": null,
    "error_detail": null,
    "retry_count": 1,
    "final_vendor": "火山引擎",
    "final_instance_id": 1128,
    "response_body": "{...}"
  }
}
```

## 修改文件

### 3. `Main.java` — 加 1 行

```java
// 在 RelayLogger.init() 之后
HolographicLogger.init();
```

### 4. `RelayCoordinator.java` — 加 ~25 行（观察点）

在 `execute()` 方法中插入以下观察点：

**阶段1后（parse 成功）：**
```java
var record = HolographicRecord.builder(UUID.randomUUID().toString())
    .requestInfo(req, rawBody);
```

**阶段2后（filter 链完成）：**
```java
record.routingInfo(relayCtx);
```

**阶段3后（候选加载+过滤完成）：**
```java
record.candidatesInfo(originalCandidates, stage3FilterResults, sorted);
```

**阶段5 — 每次尝试（onSuccess / onFailure 回调中）：**
```java
// 在 onSuccess 中：
record.addAttempt(HolographicRecord.Attempt.success(vendorName, instanceId, upstreamModel, upstreamUrl, httpStatus, latencyMs, ttfbMs, promptTokens, completionTokens));

// 在 onFailure 中：
record.addAttempt(HolographicRecord.Attempt.failure(vendorName, instanceId, upstreamModel, upstreamUrl, httpStatus, latencyMs, errorType, errorBody, cooldownTriggered));
```

**最终（请求结束时）：**
```java
record.finish(finalStatus, finalHttpCode, totalLatencyMs, totalTokens, responseBody);
HolographicLogger.write(record);
```

注意：`record` 对象通过 `ctx.put("holographicRecord", record)` 在异步回调间传递。

### 5. `UpstreamClient.java` — 不改

流式 relay 的响应体不捕获（chunk 太多，价值有限）。
流式场景只记录 chunk 数、首字节时间、token 数。

## 不做的

- 不提供 HTTP 查询接口（后续按需加）
- 不捕获流式响应体全文
- 不修改任何 Filter 接口
- 不修改现有 RelayLogger/RelayLog/RelayRecorder
- 不修改 RouterService
