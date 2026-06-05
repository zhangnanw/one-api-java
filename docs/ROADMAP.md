# One-API-Java Roadmap

> V2 架构重构完成后的改进路线。最后更新：2026-06-05。

---

## ✅ 已完成 — V2 架构重构

### 架构

```
/v1/chat/completions
  → RequestParser (Stage 1)
  → NameMatcher → VirtualModelLookup → CapabilityFilter (Stage 2 — 模型解析)
  → CapabilityInstanceFilter → CoolingFilter → TagFilter → LayerFilter → MaxPrefFilter → RawStatusFilter (Stage 3 — 实例过滤)
  → RawStatusLast → ByVendorLayer → ByInstanceLayer → ByPref → ById (Stage 4 — 排序)
  → StreamingAdapter → RetryDecorator → HeaderDecorator → ReasoningDecorator → BaseRelay (Stage 5 — 请求)
```

### 核心接口

| 接口 | 模式 | 职责 |
|------|------|------|
| `Filter` | `RelayContext → RelayContext` | Stage 2/3，管道串联 |
| `RelayExecutor` | `(Candidate, RelayRequest) → Future<RelayResult>` | Stage 5，装饰器链 |
| `Comparator` | `(RoutedVendor, RoutedVendor) → int` | Stage 4，排序链 |

### 配置化

`~/.one-api/config.yaml`：
```yaml
server:
  port: 13000
relay:
  maxRetries: 2
  cacheTtlSeconds: 10
  layerOrder: [free, subscription, payg]
policies:
  reasoning:
    triggerSuffix: "-max"
```

### 数据模型

| 类型 | 定位 |
|------|------|
| `RelayContext` | 阶段间传递的状态对象 |
| `MatchRule` (sealed) | 虚拟模型匹配规则：AllMatch / NameMatch / TagMatch / CapabilityMatch / LayerMatch |
| `RelayError` (sealed) | 错误类型：UpstreamFailure / NoCandidates / FilterExhausted / RateLimited |
| `VendorCaps` / `InstanceCaps` | meta JSON → record 收敛 |
| `MetaView` | 解析一次，多处使用 |
| `Candidate` | 最终选中的路由目标 |
| `RoutedVendor` | RouterService 加载的原始候选 |

### V1 清理

- [x] `RelayController.doHandle / tryRelay` 删除
- [x] `RouterService.getBestVendor / buildFilters / sortInstances` 删除
- [x] `handler.CooldownService` 删除（统一到 `service.CooldownService`）
- [x] `FilterUtils` V1 方法删除
- [x] 路由 `/v1/chat/completions` 指向 V2 控制器

---

## 🔧 待改进 — 按优先级

### P1（下一批）

| # | 问题 | 影响 | 方案 |
|---|------|------|------|
| R1 | **Streaming 未走完整 decorator 链** | 流式无 RetryDecorator 重试；Header/Reasoning 手动重复注入 | `RelayExecutor` 增加 streaming 模式接口：`relayStream(candidate, req, response)` |
| R2 | **SessionTracker 无界增长** | `ConcurrentHashMap` 只增不减，长时间运行内存泄露 | 加 TTL 驱逐（Caffeine 或 `ScheduledExecutorService`） |
| R3 | **429 backoff 不可配置** | 固定 `1s * (attempt+1)`，某些上游需要更长 | 加入 `config.yaml`：`retry.backoffMs` |

### P2（规划中）

| # | 问题 | 影响 | 方案 |
|---|------|------|------|
| R4 | **meta JSON → 独立列**（批次 6） | 每次查询都解析 JSON，`LIKE '%tag%'` 扫描 | `ALTER TABLE` 加 `vendor_layer / instance_tags / instance_layer` 列 |
| R5 | **上游错误信息泄露** | 错误响应中包含上游原始 JSON → 客户端可能看到内部信息 | 错误层截断：只暴露 `provider_error`，不传原始 body |
| R6 | **parseTags NPE 风险** | `FilterUtils.parseTags(null)` → NPE | 空值守卫 |

### P3（将来）

| # | 问题 | 影响 | 方案 |
|---|------|------|------|
| R7 | **单元测试** | 0 个测试，重构回归靠手动 curl | JUnit 5 + mock RelayExecutor |
| R8 | **prometheus metrics** | 无监控：延迟分布、错误率、候选命中率 | `/metrics` 端点 |
| R9 | **Streaming 超时配置化** | 固定 60s，某些慢模型需要更长 | `config.yaml`：`relay.streamTimeoutMs` |

---

## 📋 已知技术债

| 项 | 说明 | 何时还 |
|----|------|--------|
| Streaming decorator 链 | `RelayCoordinator.relayStream()` 手动重复 Header/Reasoning 逻辑，标 `// TODO: V2.1 streaming decorator chain` | R1 |
| 502 vs 504 超时 | `UpstreamClient` 超时统一返回 502，应区分 connect 超时(502)和 idle 超时(504) | R4 之后 |
| `Filter` 泛型退化 | 已去掉 `<T>`，当前所有实现都传 `RelayContext`；如需支持其他 context 类型再考虑恢复泛型 | 看需求 |
| `RelayContext.setCandidates(List<?>)` | unchecked cast，`@SuppressWarnings` | R4 数据库列化时一起修 |

---

## 🗺️ 下一步

1. **R1 + R3**：Streaming decorator 链 + backoff 配置化（中等，3-5 spawn）
2. **R4**：数据库迁移（独立 SQL 脚本，等稳定 1 周后执行）
3. **R2 + R5 + R6**：SessionTracker TTL + 错误信息截断 + NPE 守卫（简单，1-2 spawn）
4. **R7**：单元测试覆盖核心路径（中等，持续进行）
