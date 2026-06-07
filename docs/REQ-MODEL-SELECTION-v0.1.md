# REQ-MODEL-SELECTION-v0.1 — 用例虚拟模型与多维排序

> **依赖**：[REQUIREMENTS.md](./REQUIREMENTS.md)（one-api-java 主体，唯一主版本）
> **状态**：草案 v0.1，商讨中
> **创建**：2026-06-07

## 0. 背景声明

Go 版 one-api（`project/one-api/`）已废弃。**本文档及后续所有相关决策仅以 Java 版为准。** Go 版代码、文档、配置不再维护、不再参考。

## 1. 背景

one-api-java 当前已支持"虚拟模型 → 实际模型实例"的路由。但 `auto` 虚拟模型不可用：
- 候选模型混在一起无法排序
- 不区分用例（coding / chat / reasoning）
- 不利用多 vendor 的差异

## 2. 目标

实现"用例虚拟模型"+"多维度实例排序"，使：
1. 用户请求 `coding` / `chat` / `reasoning` 等用例虚拟模型
2. 系统按用例自动筛选候选模型（能力匹配）
3. 候选实例按多维度排序（layer / cost / reliability / latency）
4. 简单任务用便宜模型，复杂任务自动升级

## 3. 已落地的支撑能力

| 能力 | 状态 | 引用 |
|------|------|------|
| 虚拟模型 match 规则 | ✅ 已有 | 主需求 §功能对照 |
| 能力过滤 | ✅ 已有 | filter 包 |
| 实例 layer 排序 | ✅ 已有 | ByPref comparator |
| 会话追踪 | ✅ 已有 | SessionTracker |
| relay-log.db（运行时指标） | ⚠️ 部分 | 主需求 §已知差异 |
| model_catalog 表 | ✅ 已建 | commit d736c07 |
| ModelSpec / ModelCatalogRepo | ✅ 已建 | commit d736c07 |
| scoring 包 + InstanceScorer 接口 | ✅ 已建 | commit c11d887 |

## 4. 设计决策（已对齐）

### 4.1 虚拟模型结构（设计层）

虚拟模型统一用"候选模型列表"表示：

| 类型 | 例子 | match.models |
|------|------|--------------|
| 多模型候选 | `coding` | `[mimo-v2.5, minimax-2.7, minimax-m3, deepseek-v4-flash, deepseek-v4-pro, ...]` |
| 单模型 | `deepseek-v4-pro` | `[deepseek-v4-pro]` |

**决策：**
- 两种类型走相同逻辑（无差别）
- `auto` 虚拟模型**删除**——太泛，无法推断用例
- 列表顺序 = 弱→强（先试便宜的/简单的，失败再升级）
- 能力过滤作用于列表中的每个模型
- 过滤后列表为空 → 返回错误，不静默 fallback

**例子：**
- 请求 `deepseek-v4-pro` 虚拟模型，body 含图片
- 列表 = `[deepseek-v4-pro]`
- 过滤（需要 `capability:vision`）→ 列表为空
- 返回 400/422 错误："virtual model 'deepseek-v4-pro' has no model supporting required capabilities"

### 4.2 实例排序优先级（执行层）

| 优先级 | 维度 | 数据源 | 状态 |
|--------|------|--------|------|
| 1 | 能力匹配 | model_catalog | ✅ 已实现（硬过滤） |
| 2 | layer | instance meta | ✅ 已实现（ByPref） |
| 3 | pref | instance meta | ✅ 已实现（ByPref） |
| 4 | cost | model_catalog | ❌ 待实现 |
| 5 | reliability | relay-log.db | ❌ 待实现 |
| 6 | latency | relay-log.db | ❌ 待实现 |
| 7 | model 弱→强 | virtual_model.match | ❌ 待实现 |
| 8 | id | instance.id | ✅ 已实现（ById） |

**关键洞察**：实例层（layer / pref / cost / 指标）比模型层（弱→强）优先级高。
"免费的先用"是 layer 维度的具体表现。

### 4.3 架构隔离

新增模块通过 `InstanceScorer` 接口与现有路由解耦：

```
请求 → 现有 Filter 链 → 候选列表 → InstanceScorer.score() → 排序后列表 → 现有路由
```

- `scoring.enabled` 配置开关
- 关闭时走现有 ByPref，零影响
- 新模块不修改任何现有文件

## 5. 待解决问题（待商讨）

| # | 问题 | 决策状态 |
|---|------|---------|
| 1 | 候选模型列表的来源：扩 virtual_model.match JSON 加 `models` 字段？还是另开机制？ | ✅ 决策：扩 match JSON 加 `models` 字段 |
| 2 | 弱→强排序依据：模型名？人工指定？benchmark？ | ✅ 决策：列表顺序由人工手写，作为弱→强的最终依据 |
| 3 | relay-log.db schema 现状？需要补哪些字段？ | 未查 |
| 4 | cost / reliability / latency 三个 Comparator 怎么写？ | 未设计 |
| 5 | "用例"如何识别？ | ✅ 决策：用户主动请求虚拟模型名，零识别开销 |
| 6 | 现有 15 个虚拟模型如何过渡到"按用例"？ | ✅ 决策：单/多模型虚拟模型同逻辑，无需迁移 |
| 7 | 用例虚拟模型和单模型虚拟模型如何共存？ | ✅ 决策：删除 `auto` 虚拟模型，其余共存 |
| 8 | 列表为空时的错误码和错误信息格式 | 未定 |

## 6. 已实现的临时措施

为支撑评分模块，新建了 `model_catalog` 表：
- `name` (TEXT PRIMARY KEY)：逻辑模型名
- `capabilities` (TEXT JSON)
- `context_window` (INTEGER)
- `input_rmb_per_m` (REAL)
- `output_rmb_per_m` (REAL)

**注意**：本表已建，但数据如何录入、何时启用评分模块、与现有架构如何衔接，**均未定**。

## 7. 不在本版本

- benchmark 数据采集
- 幻觉维度评估
- 任务画像（task profile）
- ML 模型路由

## 8. 风险

| 风险 | 影响 |
|------|------|
| 虚拟模型结构变更可能影响现有 15 个虚拟模型 | 大，可能需要双轨期 |
| relay-log.db 字段可能不足 | 中，需补表 |
| 多维度排序在实例数多时性能 | 中，需测试 |
| 用户预期与系统行为不一致 | 中，需文档说明 |

---

_本版本基于 2026-06-07 当日讨论。后续对齐后更新到 v0.2。_
