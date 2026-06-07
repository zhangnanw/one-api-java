# ModelsMatch 设计 — v0.2 虚拟模型列表支持

> 状态：已审核（r1 打回，7 修复待确认）
> 依赖：REQUIREMENTS.md 术语章节、REQ-MODEL-SELECTION-v0.2.md §4.1

## 用户故事

用户请求虚拟模型 `deepseek` → 系统按 `models` 列表顺序加载实例：先 `deepseek-v4-flash`（便宜快），无可用实例时升级到 `deepseek-v4-pro`，再不行到 `deepseek-v4-pro-max`（最强推理）。一个入口自动 fallback，用户不关心具体实例。

**核心术语**：见 [REQUIREMENTS.md 术语章节](./REQUIREMENTS.md#术语)。速查：

| 术语 | 一句话 |
|------|--------|
| 虚拟模型 | 用户请求的入口名（API `model` 参数） |
| 逻辑模型 | 跨 vendor 的模型概念（如 `deepseek-v4-pro`），多个 vendor 可部署其实例 |
| match 规则 | 虚拟模型 → 实例的过滤器（JSON），**不是名字绑定** |

## 1. 问题

当前 `MatchRule` 只支持单模型匹配（`NameMatch`、`TagMatch`、`CapabilityMatch`、`LayerMatch`），不能表达"一个虚拟模型对应多个逻辑模型名"。

v0.2 要求虚拟模型的 match JSON 支持 `models` 字段：

```json
{"models": ["deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-pro-max"]}
```

路由时按列表顺序加载所有模型的实例，然后再走统一的过滤/排序链。

## 2. 改动范围

7 个文件，不改行为（现有 NameMatch/TagMatch 等路径不受影响）。

| # | 文件 | 改动 | 类型 |
|---|------|------|------|
| 1 | `model/MatchRule.java` | 加 `ModelsMatch` record | 数据 |
| 2 | `model/MatchRuleParser.java` | 解析 `models` JSON 字段（含校验） | 数据 |
| 3 | `model/RelayContext.java` | 加 `modelNames` 列表字段 | 数据 |
| 4 | `filter/VirtualModelLookup.java` | ModelsMatch → 设 `modelNames` 而非单个 `upstreamModel` | 逻辑 |
| 5 | `coordinator/RelayCoordinator.java` | stage3 按 `modelNames` 列表加载候选（存在时优先，否则走原 `upstreamModel` 路径） | 逻辑 |
| 6 | `test/MatchRuleParserTest.java` | ModelsMatch 解析 + 边界测试 | 测试 |
| 7 | `test/VirtualModelLookupTest.java` | ModelsMatch context 设置测试 | 测试 |

## 3. 详细改动

### 3.1 MatchRule — 加 ModelsMatch

```java
// 在现有 permits 行追加 ModelsMatch
public sealed interface MatchRule
    permits MatchRule.AllMatch, MatchRule.NameMatch,
            MatchRule.TagMatch, MatchRule.CapabilityMatch,
            MatchRule.LayerMatch, MatchRule.ModelsMatch {

    // ... 现有 record 不变 ...

    /**
     * 按逻辑模型名列表匹配（v0.2）。
     * 列表顺序 = 弱→强：便宜快的在前，强能力的在后。
     */
    record ModelsMatch(List<String> modelNames) implements MatchRule {}
}
```

### 3.2 MatchRuleParser — 解析 models 字段（含校验）

在 `parse()` 方法中，**最先**检查 `models` 字段。核心约束：

- `models` **与** `model_name`/`capability`/`layer`/`tags` **互斥** — 共存时抛异常
- **空列表 `[]` 拒绝** — 配置错误，抛异常，不进入路由
- **元素校验** — 逐元素 `instanceof String`，跳过 null/空串
- **不可变列表** — `List.copyOf()` 构造

```java
if (m.containsKey("models")) {
    // 互斥检测
    if (m.containsKey("model_name") || m.containsKey("capability")
        || m.containsKey("layer") || m.containsKey("tags")) {
        throw new IllegalArgumentException(
            "models 字段不能与 model_name/capability/layer/tags 共存");
    }
    Object modelsObj = m.get("models");
    if (!(modelsObj instanceof List<?> list) || list.isEmpty()) {
        throw new IllegalArgumentException("models 必须是非空列表");
    }
    List<String> names = new ArrayList<>();
    for (Object item : list) {
        if (!(item instanceof String s) || s.isEmpty()) {
            throw new IllegalArgumentException(
                "models 列表中每个元素必须是非空字符串，收到: " + item);
        }
        names.add(s);
    }
    return new MatchRule.ModelsMatch(List.copyOf(names));
}
```

位置：在现有的 `model_name`/`capability`/`layer`/`tags` 检查之前。

### 3.3 RelayContext — 加 modelNames 列表

```java
// 新增字段
private List<String> modelNames;   // ModelsMatch 用，为 null 时走原 upstreamModel 路径

// 新增 getter/setter
public List<String> modelNames() { return modelNames; }
public void setModelNames(List<String> v) { this.modelNames = v; }
```

### 3.4 VirtualModelLookup — 处理 ModelsMatch

**合约：** `ModelsMatch` 时 `modelNames` 设列表，`upstreamModel` **故意不设**（RelayCoordinator 识别 modelNames 非空后走多模型路径，不再读 upstreamModel）。其他 rule 类型不受影响。

```java
if (rule instanceof MatchRule.ModelsMatch mm) {
    ctx.setModelNames(mm.modelNames());
    // upstreamModel 不设 — RelayCoordinator 在 modelNames 非空时走列表路径
} else if (rule instanceof MatchRule.NameMatch nm) {
    ctx.setUpstreamModel(nm.modelName());
} else {
    ctx.setUpstreamModel(lookupName);
}
```

`log.debug` 行更新，对 ModelsMatch 输出 `rule=ModelsMatch(N names)`。

### 3.5 RelayCoordinator — 列表加载候选

```java
List<String> modelNames = relayCtx.modelNames();
List<RoutedVendor> candidates;

if (modelNames != null && !modelNames.isEmpty()) {
    // ModelsMatch：按列表顺序逐模型加载，instanceId 去重
    // 去重是防御性的 — 正常情况下不同 modelName 不会共享 instanceId
    // 列表顺序被 Java List.sort (TimSort, 稳定) 保留在相同 layer/pref 的实例间
    var seen = new HashSet<Integer>();
    candidates = new ArrayList<>();
    for (String name : modelNames) {
        for (var c : router.loadCandidates(name)) {
            if (seen.add(c.instanceId())) {
                candidates.add(c);
            }
        }
    }
} else {
    // 原路径：单 upstreamModel
    String targetModel = relayCtx.upstreamModel();
    if (targetModel == null || targetModel.isEmpty()) {
        targetModel = req.requestedModel();
    }
    candidates = router.loadCandidates(targetModel);
}
```

**关键行为：**
- 列表顺序保留（第一个模型的实例排前面，TimSort 稳定排序不改变同 layer/pref 的顺序）
- instanceId 去重防御（同一实例被多个模型名匹配时只保留首次出现的）
- 某模型无实例 → 静默跳过，继续下一个

### 3.6 测试

| 测试 | 覆盖 |
|------|------|
| `modelsMatch_setsModelNames` | Happy path：三模型列表 → ctx.modelNames 正确，upstreamModel 为 null |
| `modelsMatch_emptyList_throws` | `{"models":[]}` → 抛异常 |
| `modelsMatch_mutualExclusion_throws` | `{"models":[...],"model_name":"x"}` 共存 → 抛异常 |
| `modelsMatch_invalidElement_throws` | 元素含数字/null/空串 → 抛异常 |

### 3.7 （新增）MatchRuleParserTest

以上测试放在 `MatchRuleParserTest.java`（新建，`VirtualModelLookupTest` 不变）。

## 4. 不变的部分

| 模块 | 理由 |
|------|------|
| `RouterService.loadCandidates(String)` | 保持单模型加载接口，多模型循环在 RelayCoordinator 做 |
| `stage3Filters` | 过滤/排序逻辑不变——候选列表格式相同（`List<RoutedVendor>`） |
| `MatchRuleParser.parse()` 的现有分支 | NameMatch/TagMatch/CapabilityMatch/LayerMatch 行为不受影响 |
| `RouterConfig` | 过滤器链装配不变 |

## 5. 数据迁移

代码就绪后，`virtual_models` 表迁移（代码通过后单独做）：

```sql
-- 步骤1：删除旧虚拟模型
DELETE FROM virtual_models WHERE name IN (
    'deepseek-v4-flash','deepseek-v4-pro','deepseek-v4-pro-max',
    'kimi-k2.6',
    'doubao-seed-2.0-pro','doubao-seed-2.0-code',
    'minimax-m2.5','minimax-m2.7','minimax-m2.7-highspeed','minimax-m3',
    'mimo-v2.5','mimo-v2.5-pro'
);

-- 步骤2：插入新合并入口
INSERT INTO virtual_models (name, match) VALUES ('deepseek', '{"models":["deepseek-v4-flash","deepseek-v4-pro","deepseek-v4-pro-max"]}');
INSERT INTO virtual_models (name, match) VALUES ('kimi',     '{"models":["kimi-k2.6","kimi-for-coding"]}');
INSERT INTO virtual_models (name, match) VALUES ('doubao',   '{"models":["doubao-seed-2.0-pro","doubao-seed-2.0-code"]}');
INSERT INTO virtual_models (name, match) VALUES ('minimax',  '{"models":["minimax-m2.5","minimax-m2.7","minimax-m3"]}');
INSERT INTO virtual_models (name, match) VALUES ('mimo',     '{"models":["mimo-v2.5","mimo-v2.5-pro"]}');
```

> **注意：不在本次设计范围。** 迁移脚本会单独验证（幂等性：DELETE 不存在也不报错；INSERT 重复会唯一约束冲突，届时处理）。

## 6. 风险

| 风险 | 缓解 |
|------|------|
| 列表中的模型名无实例 | `loadCandidates` 返回空，循环跳过，全空时最终 503 "no instances available" |
| 运维手抖配错字段名 / 空列表 / 与旧字段共存 | parser 阶段 fail-fast 抛异常，不进入路由 |
| 同一实例支持多个模型名 | instanceId 去重防御，model 维度由列表控制（按首次出现保留） |
| 同 layer 实例被 TimSort 保持插入顺序 | 列表顺序 = 插入顺序 = 弱→强。ByModelWeakness 追加后进一步强化 |

## 7. 依赖

- ⚠️ **ByModelWeakness**（v0.2 §5 #11，**未实现**）：按 models 列表顺序排序候选，避免同 layer/pref 实例被 TimSort stability 之外的路径打乱。当前在同 layer/pref 内 TimSort 稳定排序保留插入顺序，基本可用；ByModelWeakness 追加后进一步强化。下一个设计。

---

_创建：2026-06-07 | 修订：r1（审核打回修复）_
