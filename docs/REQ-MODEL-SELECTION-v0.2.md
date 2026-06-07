# REQ-MODEL-SELECTION-v0.2 — 用例虚拟模型与多维排序

> **依赖**：[REQUIREMENTS.md](./REQUIREMENTS.md)（one-api-java 主体，唯一主版本）
> **状态**：草案 v0.2，整体设计中
> **创建**：2026-06-07
> **前版本**：v0.1（已废弃于本次重写）

## 0. 背景声明

Go 版 one-api（`project/one-api/`）已废弃。**本文档及后续所有相关决策仅以 Java 版为准。** Go 版代码、文档、配置不再维护、不再参考。

## 术语

| 概念 | 是什么 | 例子 |
|------|--------|------|
| **虚拟模型** | 用户看到的 API 入口名。`/v1/chat/completions` 的 `model` 参数。在 `virtual_models` 表中有一条记录 | `deepseek-v4-pro`、`coding`、`auto` |
| **match 规则** | 虚拟模型关联实例的筛选条件，存在 `virtual_models.match` 字段（JSON）。**不是名字绑定，是过滤器：可按 model_name / tag / capability / layer 筛选** | `{"model_name":"deepseek-v4-pro"}` — 名字匹配；`{"all":["capability:reasoning"]}` — 标签匹配 |
| **模型实例** | 实际运行 LLM 的服务端点。有 `modelName`、`tags`、`vendor`、`meta`、`status` 等属性。存在 `instances` 表中 | vendor=deepseek 的 deepseek-v4-pro 实例，vendor=volcengine 的 doubao 实例 |
| **上游模型名** | 中继时发给上游 API 的 `model` 参数。存于 `instances.upstream_model`，**可以跟虚拟模型名不同** | 虚拟模型叫 `doubao-seed-2.0-pro`，上游模型名叫 `doubao-seed-2.0-pro-260215` |
| **逻辑模型** | 跨越 vendor 的抽象模型概念。多个 vendor 可以提供同一个逻辑模型的不同实例。存于 `model_catalog` 表 | `deepseek-v4-pro` 是一个逻辑模型，deepseek/v3 和 volcengine 都可以部署它的实例 |
| **用例虚拟模型** | 按使用场景命名的虚拟模型。match 规则筛选出多个逻辑模型的实例，排序后依次尝试 | `coding` → 包含 mimo-v2.5、minimax-m2.7、deepseek-v4-flash 等多个逻辑模型的实例 |

**关联关系：**

```
虚拟模型 ──(match 规则)──→ 模型实例 ──(upstreamModel)──→ 上游 API
                │
                │  模型实例属于某个逻辑模型
                ↓
          逻辑模型 (model_catalog)
                │
                │  提供 cost / context_window / capabilities 等事实数据
                ↓
            排序 / 过滤
```

- 虚拟模型和实例之间 **不是通过名字关联**，而是通过 match 规则（过滤器）
- 一个虚拟模型可以匹配多个实例（通过 tag/capability/layer 等维度）
- 同一个逻辑模型可以有多个 vendor 的多个实例

## 1. 背景

one-api-java 当前已支持"虚拟模型 → 实际模型实例"的路由。但 `auto` 虚拟模型不可用：
- 候选模型混在一起无法排序
- 不区分用例（coding / chat / reasoning）
- 不利用多 vendor 的差异
- 同一会话可能路由到不同实例，破坏连续性

## 2. 目标

实现"用例虚拟模型"+"多维度实例排序"+"会话软粘性"：
1. 用户请求 `coding` / `chat` / `reasoning` 等用例虚拟模型
2. 系统按用例自动筛选候选模型（能力匹配）
3. 候选实例按多维度排序（layer / cost / reliability / latency）
4. 简单任务用便宜模型，复杂任务自动升级
5. 同一会话优先复用上次实例（软粘性，丢了不影响功能）

## 3. 已落地的支撑能力

| 能力 | 状态 | 引用 |
|------|------|------|
| 虚拟模型 match 规则 | ✅ 已有 | 主需求 §功能对照 |
| 能力过滤 | ✅ 已有 | filter 包 |
| 实例 layer 排序 | ✅ 已有 | ByPref comparator |
| 会话追踪 | ✅ 已有 | SessionTracker |
| Cooldown 过滤 | ✅ 已有 | CooldownFilter |
| relay-log.db（运行时指标） | ⚠️ 部分 | 主需求 §已知差异 |
| model_catalog 表 | ✅ 已建 | commit d736c07 |
| ModelSpec / ModelCatalogRepo | ✅ 已建 | commit d736c07 |
| scoring 包（InstanceScorer/Noop） | ⚠️ 弃用 | c11d887，v0.2 起回归到现有 comparator/ 包 |
| SessionTracker 软粘性字段 | ❌ 待加 | §5 操作清单 |

## 4. 设计决策

### 4.1 虚拟模型结构

虚拟模型统一用"候选模型列表"表示：

| 类型 | 例子 | match.models |
|------|------|--------------|
| 多模型候选 | `coding` | `[mimo-v2.5, minimax-2.7, minimax-m3, deepseek-v4-flash, deepseek-v4-pro, ...]` |
| 单模型 | `deepseek-v4-pro` | `[deepseek-v4-pro]` |

**决策：**
- 两种类型走相同逻辑（无差别）
- `auto` 虚拟模型**删除**——太泛，无法推断用例
- 列表顺序 = 弱→强（先试便宜的/简单的，失败再升级）
- 列表顺序由人工手写——不依赖字母序或自动推断
- 能力过滤作用于列表中的每个模型
- 过滤后列表为空 → 返回错误（不静默 fallback）

**例子：**
- 请求 `deepseek-v4-pro` 虚拟模型，body 含图片
- 列表 = `[deepseek-v4-pro]`
- 过滤（需要 `capability:vision`）→ 列表为空
- 返回错误："virtual model 'deepseek-v4-pro' has no model supporting required capabilities"

### 4.2 实例路由流程

**完整流程（含软粘性）：**

```
1. 加载候选          loadCandidates(targetModel)
2. stage3 过滤        能力 / Cooldown / 软粘性 boost
3. 排序              按 Comparator 链
4. 队列试错          失败切下一个候选
```

### 4.3 排序链（Comparator 链）

按优先级从高到低：

| # | 维度 | 数据源 | 类 |
|---|------|--------|-----|
| 1 | 能力匹配 | model_catalog | 已在 stage3 过滤中 |
| 2 | layer | instance meta | ByPref（已有） |
| 3 | pref | instance meta | ByPref（已有） |
| 4 | cost | model_catalog | ByCost（待加） |
| 5 | reliability | relay-log.db | ByReliability（待加） |
| 6 | latency | relay-log.db | ByLatency（待加） |
| 7 | model 弱→强 | virtual_model.match | ByModelWeakness（待加） |
| 8 | id | instance.id | ById（已有） |

**关键洞察：** 实例层（layer / pref / cost / 指标）比模型层（弱→强）优先级高。"免费的先用"是 layer 维度的具体表现。

### 4.4 软粘性

**算法：**

```
1. 取 session 记录的"上次用过的实例 ID"（含 TTL）
2. 正常筛选 + 排序候选
3. 检查上次实例是否在候选里
   - 在 → 移到队首
   - 不在 → 顺序不变
```

**TTL：** 默认 5 分钟，可配置。

**写入时机：**
- 同步路径（tryBuffered）成功 → `recordInstance`
- 流式路径（tryStream）首个 chunk 200 → `recordInstance`
- 失败 → 不更新

**降级原则：** 软粘性缺失或损坏不影响功能，只损失效率。

### 4.5 集成方式（v0.2 修正）

**v0.1 错误的"架构隔离"思路已弃用。** v0.2 采用直接集成：

- ❌ 不新建 `scoring` 包 + `InstanceScorer` 接口
- ❌ 不加 `scoring.enabled` 配置开关
- ✅ 新 Comparator 直接加到现有 `com/oneapi/comparator/` 包
- ✅ 直接修改 `RouterConfig` 串入新 Comparator
- ✅ 直接修改 `RelayCoordinator` 串入软粘性逻辑
- ✅ 直接修改 `SessionTracker` 加软粘性字段

**c11d887 的 `scoring` 包待清理（删 `InstanceScorer.java` 和 `NoopInstanceScorer.java`）。**

## 5. 操作清单

| # | 操作 | 文件 | 状态 |
|---|------|------|------|
| 1 | 扩 virtual_model.match JSON 加 `models` 字段 | VirtualModel.java + match 解析 | ❌ |
| 2 | 现有 15 个虚拟模型 match JSON 全部加 `models` 字段 | 数据库 UPDATE | ❌ |
| 3 | 删除 `auto` 虚拟模型（id=待查） | 数据库 DELETE | ❌ |
| 4 | SessionTrack 加 `lastInstanceId` + `lastUsedAt` 字段 | SessionTracker.java | ❌ |
| 5 | SessionTracker 加 `getPreferredInstance` / `recordInstance` 方法 | SessionTracker.java | ❌ |
| 6 | RelayCoordinator 在 stage3 后插入软粘性 boost | RelayCoordinator.java | ❌ |
| 7 | RelayCoordinator 在成功中继后调用 `recordInstance` | RelayCoordinator.java | ❌ |
| 8 | 加 `ByCost` Comparator | comparator/ByCost.java | ❌ |
| 9 | 加 `ByReliability` Comparator | comparator/ByReliability.java | ❌ |
| 10 | 加 `ByLatency` Comparator | comparator/ByLatency.java | ❌ |
| 11 | 加 `ByModelWeakness` Comparator（按 match.models 顺序） | comparator/ByModelWeakness.java | ❌ |
| 12 | RouterConfig 串入新 Comparator | RouterConfig.java | ❌ |
| 13 | 清理 c11d887 的 scoring 包 | 删除 InstanceScorer.java / NoopInstanceScorer.java / NoopInstanceScorerTest.java | ❌ |
| 14 | model_catalog 录入 15+ 个逻辑模型 | 数据库 INSERT | ❌ |
| 15 | 测试：能力过滤、软粘性、cost/reliability/latency 计算 | test/ | ❌ |

## 6. 待解决问题

| # | 问题 | 状态 |
|---|------|------|
| 1 | relay-log.db schema 现状？需要补哪些字段？ | 未查 |
| 2 | 列表为空错误码和错误信息格式 | ✅ 决策：OpenAI 兼容 `400` + 标准错误 JSON |

### 6.1 列表为空错误格式（已定）

```json
{
  "error": {
    "message": "The virtual model 'deepseek-v4-pro' has no model supporting the requested capabilities",
    "type": "invalid_request_error",
    "param": null,
    "code": "unsupported_capability"
  }
}
```

HTTP 状态码 `400`，与 OpenAI 的 `invalid_request_error` 一致。

## 7. 不在本版本

- benchmark 数据采集
- 幻觉维度评估
- 任务画像（task profile）
- ML 模型路由
- 跨 vendor 价格协商
- vendor 自身的 cost/quality 跟踪（用 model_catalog 的成本即可）

## 8. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 改 RelayCoordinator 主路径 | 已有逻辑回归 | 全量回归测试 + 单测覆盖 |
| 改 SessionTracker 数据结构 | 老数据不兼容 | 字段加 default（null/0） |
| cost/reliability/latency 数据源延迟 | 排序可能用旧数据 | TTL 短（5min） |
| 现有 15 个虚拟模型改造 | 数据迁移 | SQL 脚本一次性更新 |
| 用户预期与系统行为不一致 | 中 | 文档说明 + 错误码清晰 |

---

_本版本基于 2026-06-07 当日讨论。v0.1 因"架构隔离"过度设计被废弃，整体设计回归集成。_
