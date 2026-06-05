# One-API-Java V2 架构拆分文档

> 只升级架构，不改动功能。先接口后搬迁。

## 核心约束

| # | 约束 | 原因 |
|---|------|------|
| 1 | 不引入 reflection / DI / 事件总线 | 保持热路径零额外开销 |
| 2 | 方法行长 ≤ 30 行 | checkstyle 硬约束 |
| 3 | 判断收敛到策略对象 | 消灭散落的 if 块 |
| 4 | `ctx.put/get` 禁用 | RoutingContext 不当 DTO |
| 5 | 依赖手写装配 | main() 里 new 对象图 |

## 设计模式

### 过滤器和装饰器

只有两个接口，不按阶段分：

| 接口 | 模式 | 类型参数 | 用在哪 |
|------|------|----------|--------|
| `Filter<T>` | 过滤器 | `T` = 阶段上下文 | 阶段 2、3 |
| `RelayExecutor` | 装饰器 | — | 阶段 5 |

```java
// 过滤器接口
interface Filter<T> {
    Either<RelayError, T> apply(T input);
}

// 装饰器接口
interface RelayExecutor {
    Either<RelayError, RelayResult> execute(Candidate c, RelayRequest req);
}
```

### 另外两个无需接口的独立组件

| 阶段 | 组件 | 原因 |
|------|------|------|
| 1 | `RequestParser` | 独立解析，不需要链 |
| 4 | `CompositeComparator` | `Comparator<Candidate>` 链，JDK 原生接口 |

## 阶段 2：虚拟模型 → 筛选条件

过滤器链：

```
NameMatcher → VirtualModelLookup → MatchRuleParser → CapabilityFilter → FilterSpec
```

| 过滤器 | 职责 |
|--------|------|
| `NameMatcher` | 判断物理名直通还是查虚拟模型 |
| `VirtualModelLookup` | 查虚拟模型表，得到 match 规则 + 上游模型名 |
| `MatchRuleParser` | 把 MatchRule folded class 解析成 FilterSpec（单次解析） |
| `CapabilityFilter` | 按 capability 标签追加筛选条件 |

**加新规则 = 实现新 Filter，插到链里。链在协调器里组装，config 注入。**

## 阶段 3：筛选实例

过滤器链：

```
CoolingFilter → TagFilter → LayerFilter → MaxPrefFilter → RawStatusFilter
```

| 过滤器 | 职责 |
|--------|------|
| `CoolingFilter` | 硬过滤冷却中的实例/供应商 |
| `TagFilter` | 按 capability 标签过滤 |
| `LayerFilter` | 按 layer（free/subscription/payg）过滤 |
| `MaxPrefFilter` | 保留 max_pref 最高的实例 |
| `RawStatusFilter` | 排除 RAW/DISABLED/DEPRECATED 状态 |

**所有 Filter<T> 用同一个接口，不同阶段传不同 T。**

## 阶段 4：排序

`Comparator<Candidate>` 链（JDK 原生接口，不定义新接口）：

```
RawStatusLast → ByVendorLayer → ByInstanceLayer → ByPref → ById
```

每个是一个独立 `Comparator<Candidate>` 实现，可单独测试，链用 `thenComparing`.

## 阶段 5：发起请求

装饰器链：

```
BaseRelay → ReasoningDecorator → HeaderDecorator → RetryDecorator → StreamingAdapter
```

| 装饰器 | 职责 |
|--------|------|
| `BaseRelay` | 构造 URL + 发送 HTTP 请求 |
| `ReasoningDecorator` | 注入 `X-Reasoning-Effort: max` |
| `HeaderDecorator` | 注入 User-Agent / 自定义 header |
| `RetryDecorator` | 429 → 冷却 + 重试，4xx → 不重试，MAX_RETRIES=2 |
| `StreamingAdapter` | 流式 vs 非流式分叉 |

## 协调器

```java
class RelayCoordinator {
    // 注入
    RequestParser parser;
    List<Filter<ModelContext>> stage2Filters;
    List<Filter<List<Candidate>>> stage3Filters;
    Comparator<Candidate> sorter;
    RelayExecutor relayChain;
    RelayRecorder recorder;  // 旁路

    Either<RelayError, RelayResult> execute(RoutingContext ctx) {
        // 阶段 1
        var req = parser.parse(ctx);
        if (req.isLeft()) return req;

        // 阶段 2：过滤器链
        var ctx2 = new ModelContext(req.get().requestedModel());
        for (var f : stage2Filters) {
            var r = f.apply(ctx2);
            if (r.isLeft()) return Either.left(r.getLeft());
        }

        // 阶段 3：过滤器链
        var candidates = new ListContext<Candidate>(ctx2.candidates);
        for (var f : stage3Filters) {
            var r = f.apply(candidates);
            if (r.isLeft()) return Either.left(r.getLeft());
        }

        // 阶段 4：排序
        candidates.list.sort(sorter);

        // 阶段 5：装饰器链执行
        return relayChain.execute(candidates.list.get(0), req.get());
    }
}
```

## 类清单

| 类 | 类型 | 新/改 |
|----|------|:---:|
| `Filter<T>` | 接口 | 新 |
| `RelayExecutor` | 接口 | 新 |
| `RelayCoordinator` | 协调器 | 新 |
| `RequestParser` | 实现 | 新 |
| `RelayRecorder` | 旁路 | 改（从 RelayLogger） |
| `NameMatcher` | Filter | 新 |
| `VirtualModelLookup` | Filter | 新 |
| `MatchRuleParser` | Filter | 新 |
| `CapabilityFilter` | Filter | 新 |
| `CoolingFilter` | Filter | 新 |
| `TagFilter` | Filter | 新 |
| `LayerFilter` | Filter | 新 |
| `MaxPrefFilter` | Filter | 新 |
| `RawStatusFilter` | Filter | 新 |
| `RawStatusLast` | Comparator | 新 |
| `ByVendorLayer` | Comparator | 新 |
| `ByInstanceLayer` | Comparator | 新 |
| `ByPref` | Comparator | 新 |
| `ById` | Comparator | 新 |
| `BaseRelay` | RelayExecutor | 新 |
| `ReasoningDecorator` | RelayExecutor | 新 |
| `HeaderDecorator` | RelayExecutor | 新 |
| `RetryDecorator` | RelayExecutor | 新 |
| `StreamingAdapter` | RelayExecutor | 新 |

## V1 → V2 搬迁对照

| V1 代码 | 搬到 V2 |
|---------|---------|
| `doHandle` body 解析 | `RequestParser.parse()` |
| `buildFilters` → `fromMatchJson` | `VirtualModelLookup` + `MatchRuleParser` |
| `getBestVendor` 加载实例 | `RouterService` 变成 `InstanceRepo` + cache |
| `getBestVendor` filter 链 | 进阶段 3 Filter 链 |
| `sortInstances` lambda | 拆成 5 个 Comparator |
| `tryRelay` reasoning 判断 | `ReasoningDecorator` |
| `tryRelay` kimi UA | `HeaderDecorator` |
| `tryRelay` 429/5xx 冷却 | `RetryDecorator` |
| `tryRelay` stream 分叉 | `StreamingAdapter` |
| `RelayLogger` DDL | 拆到 `RelayLogSchema` |

## 重试

MAX_RETRIES=2，由 `RetryDecorator` 内部管理。失败后回到阶段 3 重查候选（冷却状态已变）。

## 配置化

`config.yaml`：

```yaml
relay:
  maxRetries: 2
  layerOrder: [free, subscription, payg]
  cacheTtlSeconds: 10

policies:
  reasoning:
    triggers:
      - kind: modelSuffix
        value: "-max"
        effect: { addHeader: { X-Reasoning-Effort: max } }

vendors:
  - id: kimi
    headers:
      User-Agent: KimiCLI/1.6
  - id: deepseek
    headers: {}
```

启动时加载一次，注入各个 Filter/Decorator 构造函数。

## 实施顺序

| 批次 | 内容 | 依赖 |
|:----:|------|------|
| 1 | 数据模型（Meta 对象化、MatchRule、RelayError） | 无 |
| 2 | 配置化（config.yaml + AppConfig） | 无 |
| 3 | Filter<T> 接口 + 阶段 2/3 过滤器 + 阶段 4 Comparator | 1、2 |
| 4 | RelayExecutor 接口 + 阶段 5 装饰器 | 1、2 |
| 5 | 协调器 + 搬迁 doHandle → RelayCoordinator | 3、4 |
| 6 | 数据库迁移（meta JSON → 扁平行） | 1 |
| 7 | 清理 V1 死代码（RouterService 透明代理等） | 5 |
