# One-API-Java V2 架构文档

> 描述当前实际实现的 V2 架构。最后更新：2026-07-12（补充技术决策记录 ADR-1~8）。

## 技术决策记录

> 以下决策在代码审核期间做出，记录于此以避免将来被误认为 BUG。

### ADR-1: tryBuffered / relayStream lambda 合并

**背景：** `tryBuffered` 中 `startLogAsync` 有两条分支（日志成功/失败），每条分支里 `baseRelay.execute` 又有两条分支（中继成功/失败），形成 2×2 = 4 个几乎完全相同的 lambda。`relayStream` 同理有 2 个重复 lambda。

**决策：** 使用 `Future.otherwise(-1L).onComplete()` 将日志分支合并。日志失败时返回哨兵值 `-1`，在 `onComplete` 回调中用 `if (logId >= 0)` guard 控制 `updateStreamResultAsync` 是否执行。

**效果：** tryBuffered 4→2 lambda，relayStream 2→1 lambda。行为完全一致——`updateStreamResultAsync` 在日志写入失败时仍不被调用。

**风险评估：** 这是每个 API 请求的必经路径。修改是机械性的（`.onSuccess().onFailure()` → `.otherwise(-1L).onComplete()`），内部逻辑不变，仅结构调整。

### ADR-2: SessionTracker match() 刷新 lastUsedAt

**背景：** `SessionTracker.lookupOrCreate()` 复用已有会话时原样复制 `existing.lastUsedAt()`，不刷新时间戳。导致会话间隔 > TTL（5 分钟）后首次 `match()` 不刷新时间，下一次 `getPreferredInstance()` 必定返回空（粘性丢失）。

**决策：** `lookupOrCreate()` 中复用会话时将 `lastUsedAt` 设为 `System.currentTimeMillis()`。

**效果：** `match()` 找到会话即视为"活跃"，刷新 TTL。会话间隔 > TTL 后首次请求不再丢失软粘性。

### ADR-3: 流式 fallback 不需要手动 setChunked

**背景：** `relayStream` 在上游请求前调用 `ctx.response().setChunked(true)`，上游返回非 200 时 fallback 需要 `setChunked(false)` + `headers().clear()`。

**决策：** 删除 `setChunked(true)` 的预调用和 fallback 中的 `setChunked(false)` + `headers().clear()`。`UpstreamClient` 在 `statusCode == 200` 路径内已正确调用 `sink.setChunked(true)`。

**效果：** fallback 时 response 从未进入 chunked 模式，无需回退。行为不变，消除了 Vert.x 中途切换 chunked 模式的脆弱性。

### ADR-4: /api/* 管理端点不加认证

**背景：** `/api/vendors`、`/api/instances`、`/api/virtual-models` 等管理端点无应用层认证。

**决策：** 不加认证。部署方式为内网/VPN，靠网络隔离保护。

**理由：** 这些端点仅用于运维管理（增删供应商/实例/虚拟模型），不面向终端用户。内网部署下应用层认证是多余的安全层。

### ADR-5: 装饰器链（R1）暂不实现

**背景：** ROADMAP R1 提出将 `RelayCoordinator` 中的 Header 注入、参数限制、重试等逻辑提取为装饰器链。

**决策：** 暂不实现。当前内联方式工作正常，重复代码量小（3 行准备步骤），提取后增加 5-6 个新类 + 1 个新接口，收益有限但风险高。

**触发条件：** 当需要流式自动重试（R1 核心诉求）时再做。

### ADR-6: CooldownService 两条入口语义不同（by design）

**背景：** `checkAndSetInstanceCooldown` 和 `setInstanceCooldown` 都会增加冷却计数器 `n`，但行为不同。

**决策：** 保留两条入口，语义差异如下：
- `checkAndSetInstanceCooldown`: 检查是否冷却中 → 已冷却则 **不增加 n**（避免窗口过长） → 设置冷却
- `setInstanceCooldown`: **无条件 n = old.n + 1**（强制把窗口推远）

`setInstanceCooldown` 用于流式 200 空响应场景（`relayStream` 中 `statusCode == 200` 但 tokens == 0），需要更强的冷却力度。两条都合法，不合并。

### ADR-7: 不做程序内数据安全处理

**背景：** 全息调试日志 (`holographic_logs`) 记录完整请求/响应 body，可能包含 API key 等敏感信息。

**决策：** 不做程序内的数据安全处理（Redactor、RetentionService 等）。数据安全由部署层面（内网/VPN、数据库访问控制）负责。

**理由：** 
- 部署方式为内网/VPN，只有内部人员能访问
- 程序内安全措施（mask、自动清理）与"内网即安全"的前提矛盾
- 数据库访问控制、备份策略等基础设施层面的安全更可靠

### ADR-8: relay.requireVirtualModel 配置开关

**背景：** `NameMatcher` 在匹配失败时是否报错（无虚拟模型 → 503）需要可配置。

**决策：** 新增 `relay.requireVirtualModel` 配置项（默认 true）。设为 false 时 NameMatcher 匹配失败不报错，允许物理模型名直通。

## 核心约束

| # | 约束 | 原因 |
|---|------|------|
| 1 | 不引入 reflection / DI / 事件总线 | 保持热路径零额外开销 |
| 2 | 方法行长 ≤ 30 行 | checkstyle 硬约束 |
| 3 | 依赖手写装配 | [`Main.java`](src/main/java/com/oneapi/Main.java) 里 new 对象图 |
| 4 | 判断收敛到过滤器对象 | 消除散落的 if 块，统一用 `Filter` 接口表达 |

## 设计模式

| 接口 | 模式 | 类型参数 | 用在哪 |
|------|------|----------|--------|
| `Filter` | 过滤器 | — | 阶段 2、3 |
| `RelayExecutor` | 策略/执行器 | — | 阶段 5（终端执行器） |
| `Comparator<RoutedVendor>` | JDK 原生 | — | 阶段 4 |

```java
// 过滤器接口（非泛型，统一返回 RelayContext）
@FunctionalInterface
public interface Filter {
    RelayContext apply(RelayContext ctx);

    default Filter andThen(Filter after) {
        return ctx -> after.apply(this.apply(ctx));
    }
}

// 执行器接口
public interface RelayExecutor {
    Future<RelayResult> execute(Candidate c, RelayRequest req);
}
```

## 5 阶段流水线

```
/v1/chat/completions
  → RequestParser.parse()              (Stage 1 — 解析)
  → NameMatcher → VirtualModelLookup → CapabilityRequirementMarker → VisionFilter
                                        (Stage 2 — 模型解析)
  → CooldownFilter → CapabilityInstanceFilter → BodyLimitFilter → TagFilter → LayerFilter → ActiveStatusFilter
                                        (Stage 3 — 候选过滤)
  → ByScore.thenComparing(ByStatusDesc) (Stage 4 — 排序)
  → DefaultRelay.execute()             (Stage 5 — 上游请求)
```

## 阶段 2：虚拟模型 → 筛选条件

过滤器链：

```
NameMatcher → VirtualModelLookup → CapabilityRequirementMarker → VisionFilter
```

| 过滤器 | 职责 |
|--------|------|
| `NameMatcher` | 判断物理名直通还是查虚拟模型 |
| `VirtualModelLookup` | 查虚拟模型表，得到 match 规则；处理 `-max` 后缀触发 reasoning；[`MatchRule.java`](src/main/java/com/oneapi/model/MatchRule.java) 时设 `modelNames` 列表 |
| `CapabilityRequirementMarker` | 仅标记 `ctx.capabilityRequired`（如 `vision`），不实际过滤 |
| `VisionFilter` | 检查请求体是否包含 `image_url`，设 `capabilityRequired="vision"` |

## 阶段 3：筛选实例

过滤器链：

```
CooldownFilter → CapabilityInstanceFilter → BodyLimitFilter → TagFilter → LayerFilter → ActiveStatusFilter
```

| 过滤器 | 职责 |
|--------|------|
| `CooldownFilter` | 硬过滤冷却中的实例/供应商 |
| `CapabilityInstanceFilter` | 按 `capabilityRequired` 和 `ModelCatalog` 过滤（如 vision 能力） |
| `BodyLimitFilter` | 按请求体长度 vs 模型上下文窗口过滤，超限返回 413 [`RelayError.java`](src/main/java/com/oneapi/model/RelayError.java) |
| `TagFilter` | 按 capability 标签过滤 |
| `LayerFilter` | 按 layer（free/subscription/payg）过滤 |
| `ActiveStatusFilter` | 仅保留 `STATUS_RAW` 或 `STATUS_TAGGED` 实例，移除 DISABLED/DEPRECATED |

所有 `Filter` 用同一个接口，不同阶段通过 `RelayContext` 传递状态。

## 阶段 4：排序

```java
Comparator<RoutedVendor> sorter = new ByScore()
    .thenComparing(new ByStatusDesc());
```

| 比较器 | 职责 |
|--------|------|
| `ByScore` | 按 `layerBase + instancePref` 升序（free=0, subscription=1, payg=2） |
| `ByStatusDesc` | `STATUS_TAGGED` (2) 排在 `STATUS_RAW` (1) 前面 |

## 阶段 5：发起请求

终端执行器 [`DefaultRelay.java`](src/main/java/com/oneapi/relay/DefaultRelay.java) 实现 `RelayExecutor` 接口：

- 构造 `OutboundRequest` → 调用 `UpstreamClient.relay()`
- 空响应检测（200 但 choices 无效 → 视为失败）
- 解析 prompt_tokens / completion_tokens

**重试、流式分叉、Header 注入**由 [`RelayCoordinator.java`](src/main/java/com/oneapi/coordinator/RelayCoordinator.java) 内联管理：
- 重试：缓冲式 `tryBuffered` 自我尾调用遍历候选队列（等价于 while 循环，避免栈累积）
- 流式：`relayStream` 单候选 + fallback 尾调用
- `X-Reasoning-Effort: max` 和 `User-Agent: KimiCLI/1.6` 在协调器中直接注入

## 协调器

```java
class RelayCoordinator {
    // 注入（RouterConfig 手写装配）
    RouterService router;
    CooldownService cooldown;
    SessionTracker sessions;
    UpstreamClient upstreamClient;
    List<Filter> stage2Filters;
    List<Filter> stage3Filters;
    DefaultRelay baseRelay;
    HolographicLogRecorder holographicRecorder;

    void execute(RoutingContext ctx, byte[] rawBody) {
        // 阶段 1
        RelayRequest req = RequestParser.parse(ctx, rawBody);
        ...

        // 阶段 2：过滤器链
        RelayContext relayCtx = new RelayContext(req.requestedModel());
        for (Filter f : stage2Filters) {
            relayCtx = f.apply(relayCtx);
            if (relayCtx.hasError()) { ... return; }
        }

        // 阶段 3：加载候选 + 过滤
        List<RoutedVendor> candidates = ...; // modelNames 列表 or 单 upstreamModel
        relayCtx.setCandidates(candidates);
        for (Filter f : stage3Filters) {
            relayCtx = f.apply(relayCtx);
        }
        ...

        // 阶段 4：排序
        queue.sort(sorter);

        // 阶段 5：中继
        executePipeline(ctx, req, queue, relayCtx);
    }
}
```

## 类清单（实际存在）

| 类 | 类型 | 说明 |
|----|------|------|
| `Filter` | 接口 | 阶段 2/3 过滤器 |
| `RelayExecutor` | 接口 | 阶段 5 执行器 |
| `RelayCoordinator` | 协调器 | 串联 5 阶段 |
| `RequestParser` | 工具类 | 静态方法 `parse(ctx, rawBody)` |
| `RelayRecorder` | 旁路 | 即发即忘的日志写入 |
| `NameMatcher` | Filter | 阶段 2 |
| `VirtualModelLookup` | Filter | 阶段 2 |
| `CapabilityRequirementMarker` | Filter | 阶段 2：标记能力需求 |
| `VisionFilter` | Filter | 阶段 2：检测 image_url |
| `CooldownFilter` | Filter | 阶段 3 |
| `CapabilityInstanceFilter` | Filter | 阶段 3：按能力目录过滤 |
| `BodyLimitFilter` | Filter | 阶段 3：按上下文窗口过滤 |
| `TagFilter` | Filter | 阶段 3 |
| `LayerFilter` | Filter | 阶段 3 |
| `ActiveStatusFilter` | Filter | 阶段 3 |
| `ByScore` | Comparator | 阶段 4 |
| `ByStatusDesc` | Comparator | 阶段 4 |
| `ById` | Comparator | 阶段 4（备用，未在默认 sorter 中使用） |
| `DefaultRelay` | RelayExecutor | 阶段 5 终端执行器 |
| `UpstreamClient` | 客户端 | HTTP 请求收发 |
| `RouterService` | 服务 | 候选实例加载（V1 保留，V2 复用） |
| `CooldownService` | 服务 | 冷却管理 |
| `SessionTracker` | 服务 | 会话粘性 |
| `HolographicRecord` | 记录 | 全息调试日志结构 |
| `HolographicLogger` | 日志 | 全息调试日志写入 |

## 配置化

`config.yaml`：

```yaml
relay:
  maxRetries: 2
  layerOrder: [free, subscription, payg]
  cacheTtlSeconds: 10

database:
  type: postgresql
  host: 10.0.0.147
  port: 5432
  database: oneapi
  user: oneapi
  password: OneApi_PG_2026

policies:
  reasoning:
    triggerSuffix: "-max"
    triggers:
      - kind: modelSuffix
        value: "-max"
        effect: { addHeader: { X-Reasoning-Effort: max } }

vendors:
  - id: kimi
    headers:
      User-Agent: KimiCLI/1.6
```

启动时加载一次，注入各个 Filter/Coordinator 构造函数。

## 重试

缓冲式请求：候选队列为空时返回 503；任何上游错误（非 200）自动尝试下一个候选，直到队列耗尽。流式请求：非 200 时冷却当前 vendor 并递归尝试下一个候选。

冷却触发：429 / 403 / 200 空响应。

## 与早期 V2 蓝图的区别

以下组件在**早期设计文档**中被提及，但**当前代码中未实现**，逻辑已内联到 `RelayCoordinator`：

| 设计蓝图 | 当前实现 |
|----------|----------|
| `MaxPrefFilter` | 未实现；`ByScore` 已包含 pref 排序逻辑 |
| `RawStatusFilter` | 未实现；由 `ActiveStatusFilter` 替代 |
| `RawStatusLast` / `ByVendorLayer` / `ByInstanceLayer` | 未实现；`ByStatusDesc` 替代 |
| `BaseRelay` / `ReasoningDecorator` / `HeaderDecorator` / `RetryDecorator` / `StreamingAdapter` | 未实现；逻辑内联在 `RelayCoordinator` 中 |
| `RelayLogSchema` | 未实现；`RelayRecorder` 直接委托 `RelayLogger` |
| `Filter<T>` 泛型 + `Either` | 退化为 `Filter`（非泛型）+ `RelayContext` 错误标记 |
| `RequestParser` 注入 | 实际为静态工具类 |
| `InstanceRepo` + cache 替代 `RouterService` | 仍使用 `RouterService.loadCandidates()` |

## 实施状态

| 批次 | 内容 | 状态 |
|:----:|------|:----:|
| 1 | 数据模型（Meta 对象化、MatchRule、RelayError） | ✅ 已完成 |
| 2 | 配置化（config.yaml + AppConfig） | ✅ 已完成 |
| 3 | Filter 接口 + 阶段 2/3 过滤器 + 阶段 4 Comparator | ✅ 已完成（与设计略有差异） |
| 4 | RelayExecutor 接口 + DefaultRelay | ✅ 已完成（装饰器链未实现） |
| 5 | 协调器 + 搬迁 doHandle → RelayCoordinator | ✅ 已完成 |
| 6 | 数据库扁平化迁移（meta JSON → 独立列） | ⏳ 待执行（ROADMAP R4） |
| 7 | 清理 V1 死代码 | ✅ 已完成（`RelayController`/`doHandle`/`tryRelay`/`getBestVendor` 全部删除） |
