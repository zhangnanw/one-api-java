# ModelsMatch 设计 — v0.2 虚拟模型列表支持

> 状态：待审核
> 依赖：REQUIREMENTS.md 术语章节、REQ-MODEL-SELECTION-v0.2.md §4.1

## 1. 问题

当前 `MatchRule` 只支持单模型匹配（`NameMatch`、`TagMatch`、`CapabilityMatch`、`LayerMatch`），不能表达"一个虚拟模型对应多个逻辑模型名"。

v0.2 要求虚拟模型的 match JSON 支持 `models` 字段：

```json
{"models": ["deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-pro-max"]}
```

路由时按列表顺序加载所有模型的实例，然后再走统一的过滤/排序链。

## 2. 改动范围

6 个文件，不改行为（现有 NameMatch/TagMatch 等路径不受影响）。

| # | 文件 | 改动 | 类型 |
|---|------|------|------|
| 1 | `model/MatchRule.java` | 加 `ModelsMatch` record | 数据 |
| 2 | `model/MatchRuleParser.java` | 解析 `models` JSON 字段 | 数据 |
| 3 | `model/RelayContext.java` | 加 `modelNames` 列表字段 | 数据 |
| 4 | `filter/VirtualModelLookup.java` | ModelsMatch → 设 `modelNames` 而非单个 `upstreamModel` | 逻辑 |
| 5 | `coordinator/RelayCoordinator.java` | stage3 按 `modelNames` 列表加载候选（存在时优先，否则走原 `upstreamModel` 路径） | 逻辑 |
| 6 | `test/.../VirtualModelLookupTest.java` | ModelsMatch 解析测试 | 测试 |

## 3. 详细改动

### 3.1 MatchRule — 加 ModelsMatch

```java
// 在现有 permits 行追加 ModelsMatch
public sealed interface MatchRule
    permits MatchRule.AllMatch, MatchRule.NameMatch,
            MatchRule.TagMatch, MatchRule.CapabilityMatch,
            MatchRule.LayerMatch, MatchRule.ModelsMatch {

    // ... 现有 record 不变 ...

    /** 按逻辑模型名列表匹配（v0.2）。列表顺序 = 弱→强。 */
    record ModelsMatch(List<String> modelNames) implements MatchRule {}
}
```

### 3.2 MatchRuleParser — 解析 models 字段

在 `parse()` 方法中，**最先**检查 `models` 字段（优先级最高，避免和其他字段混淆）：

```java
if (m.containsKey("models")) {
    Object modelsObj = m.get("models");
    if (modelsObj instanceof List<?> list) {
        List<String> names = new ArrayList<>();
        for (Object item : list) names.add(item.toString());
        return new MatchRule.ModelsMatch(Collections.unmodifiableList(names));
    }
}
```

位置：在现有的 `model_name`/`capability`/`layer`/`tags` 检查之前。

### 3.3 RelayContext — 加 modelNames 列表

```java
// 新增字段
private List<String> modelNames;   // ModelsMatch 用

// 新增 getter/setter
public List<String> modelNames() { return modelNames; }
public void setModelNames(List<String> v) { this.modelNames = v; }
```

### 3.4 VirtualModelLookup — 处理 ModelsMatch

当前逻辑（`apply()` 末尾）：

```java
// 当前：NameMatch → 设 upstreamModel，其他 → 用请求名
if (rule instanceof MatchRule.NameMatch nm) {
    ctx.setUpstreamModel(nm.modelName());
} else {
    ctx.setUpstreamModel(lookupName);
}
```

改为：

```java
if (rule instanceof MatchRule.ModelsMatch mm) {
    // ModelsMatch：设模型名列表，不设单个 upstreamModel
    ctx.setModelNames(mm.modelNames());
    // upstreamModel 留空 —— RelayCoordinator 会用 modelNames
} else if (rule instanceof MatchRule.NameMatch nm) {
    ctx.setUpstreamModel(nm.modelName());
} else {
    ctx.setUpstreamModel(lookupName);
}
```

`log.debug` 行也更新，对 ModelsMatch 输出 `rule=ModelsMatch(N names)`。

### 3.5 RelayCoordinator — 列表加载候选

当前 line ~108-114：

```java
String targetModel = relayCtx.upstreamModel();
if (targetModel == null || targetModel.isEmpty()) {
    targetModel = req.requestedModel();
}
var candidates = router.loadCandidates(targetModel);
```

改为：

```java
List<String> modelNames = relayCtx.modelNames();
List<RoutedVendor> candidates;

if (modelNames != null && !modelNames.isEmpty()) {
    // ModelsMatch：按列表顺序加载，去重（同一实例可能被多个模型名匹配）
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
- 列表顺序保留（第一个模型的实例排前面）
- 同实例不重复（`instanceId` 去重）
- 空列表或无实例 → 跳过该模型，继续下一个

### 3.6 测试

`VirtualModelLookupTest` 加一个测试：

```java
@Test
void modelsMatch_setsModelNames() {
    // 模拟虚拟模型 match = {"models":["a","b","c"]}
    // 验证 ctx.modelNames() == ["a","b","c"]
    // 验证 ctx.upstreamModel() == null
}
```

## 4. 不变的部分

以下模块**不改**：

| 模块 | 理由 |
|------|------|
| `RouterService.loadCandidates(String)` | 保持单模型加载接口，多模型循环在 RelayCoordinator 做 |
| `stage3Filters` | 过滤/排序逻辑不变——候选列表格式相同（`List<RoutedVendor>`） |
| `MatchRuleParser.parse()` 的现有分支 | NameMatch/TagMatch/CapabilityMatch/LayerMatch 行为不受影响 |
| `RouterConfig` | 过滤器链装配不变 |

## 5. 数据迁移

代码就绪后，`virtual_models` 表的 match JSON 改为：

```sql
-- 示例：deepseek 入口
UPDATE virtual_models SET name='deepseek', match='{"models":["deepseek-v4-flash","deepseek-v4-pro","deepseek-v4-pro-max"]}' WHERE name IN ('deepseek-v4-flash','deepseek-v4-pro','deepseek-v4-pro-max');

-- 删旧、去重（具体 SQL 在数据迁移阶段再写）
```

> 注意：数据迁移在代码评审通过后单独做，不在本次设计范围。

## 6. 风险

| 风险 | 缓解 |
|------|------|
| `ModelsMatch` 列表为空 | `parse()` 返回空列表，`RelayCoordinator` 走原 `upstreamModel` 路径 → 503 "no instances" |
| 列表中的模型名无实例 | `loadCandidates` 返回空，循环跳过，全空时最终 503 |
| 与现有 `AllMatch`/`TagMatch` 等冲突 | `models` 字段优先检查，不会和其他规则共存 |
| **排序链覆盖列表顺序** | 当前 Comparator 链（ByPref→ById）会在同 layer 实例中打乱 models 列表的弱→强顺序。需要 `ByModelWeakness` Comparator 来保证。**不阻塞本设计，但合并前须补齐。** |

## 7. 依赖

- **ByModelWeakness**（v0.2 §5 #11）：按 models 列表顺序对候选排序。ModelsMatch 合并候选后若不加此 Comparator，列表顺序只在同 layer 内生效（ByPref tie-break 后仍被 ById 打乱）。下一个设计。

---

_创建：2026-06-07，基于 v0.2 §4.1 和术语定义_
