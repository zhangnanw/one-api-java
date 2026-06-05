# One-API-Java V2 数据模型拆分文档

## 数据模型改造

以下 3 个实体从 JSON 字符串变成类型化对象：

| 实体 | V1 | V2 | 理由 |
|------|----|----|------|
| `Vendor.meta` | JSON 字符串 | `vendor_caps` / `vendor_tags` / `vendor_layer` / `nocool` 列 | 散落 7 处解析，排查靠 grep |
| `Instance.meta` | JSON 字符串 | `instance_tags` / `instance_layer` / `instance_max_pref` 列 | 同上 |
| `VirtualModel.match` | JSON 字符串 | `match_type` + `match_value` 列 → 解析为一个 MatchRule | 散落 3 处解析，之前 deepseek-v3 静默挂掉 |

## Vendor 新 Schema

### 数据库迁移

```sql
-- 新增列（从 meta JSON 拆出）
ALTER TABLE vendors ADD COLUMN vendor_layer TEXT DEFAULT '';
ALTER TABLE vendors ADD COLUMN vendor_tags TEXT DEFAULT '';
ALTER TABLE vendors ADD COLUMN vendor_nocool INTEGER DEFAULT 0;

-- 迁移现有数据（Python 一次性脚本）
-- UPDATE vendors SET
--   vendor_layer = json_extract(meta, '$.layer'),
--   vendor_tags = json_extract(meta, '$.tags'),
--   vendor_nocool = CASE WHEN json_extract(meta, '$.nocool') = 'true' THEN 1 ELSE 0 END
-- WHERE meta IS NOT NULL AND meta != '';

-- 旧列保留（meta），不删除，回滚可用
```

### Java Record

```java
record VendorCaps(
    String layer,        // "free" | "subscription" | "payg"
    Set<String> tags,    // ["capability:reasoning", "capability:vision"]
    boolean nocool       // 冷却豁免
) {}
```

### MetaView（解析一次，多处使用）

```java
class MetaView {
    private final VendorCaps parsed;  // 数据库行 → record，解析一次

    boolean hasTag(String tag) { return parsed.tags().contains(tag); }
    boolean isNoCool() { return parsed.nocool(); }
    String layer() { return parsed.layer(); }
}
```

`RouterService`、`CooldownService`、`FilterUtils` 全部通过 `MetaView` 读，不再各自 `mapper.readTree(meta)`。

## Instance 新 Schema

### 数据库迁移

```sql
ALTER TABLE instances ADD COLUMN instance_tags TEXT DEFAULT '';
ALTER TABLE instances ADD COLUMN instance_layer TEXT DEFAULT '';
ALTER TABLE instances ADD COLUMN instance_max_pref INTEGER DEFAULT 0;

-- Python 迁移脚本同上
```

### Java Record

```java
record InstanceCaps(
    Set<String> tags,
    String layer,
    int maxPref
) {}
```

## VirtualModel.match → MatchRule sealed class

### V1 格式（保留兼容）

数据库 `match` 列仍为 JSON 字符串，但解析只做一次，产出 `MatchRule` 对象。

### 数据库迁移（可选，等 P0 完成后）

```sql
ALTER TABLE virtual_models ADD COLUMN match_type TEXT DEFAULT 'tag';
ALTER TABLE virtual_models ADD COLUMN match_value TEXT DEFAULT '';
```

旧 `match` 列保留不删。

### sealed class

```java
sealed interface MatchRule {
    record AllMatch() implements MatchRule {}                       // 兜底，匹配所有
    record NameMatch(String modelName) implements MatchRule {}       // {"model_name": "deepseek-v3"}
    record TagMatch(String tag) implements MatchRule {}             // {"tag": "capability:reasoning"}
    record CapabilityMatch(String capability) implements MatchRule {} // {"capability": "vision"}
    record LayerMatch(Layer layer) implements MatchRule {}          // {"layer": "payg"}
}
```

### MatchRuleParser（解析一次 → 三个消费者）

```java
class MatchRuleParser {
    static MatchRule parse(String matchJson) {
        // 解析一次即可产出 MatchRule
    }

    // 消费者：阶段 2 的 CapabilityFilter、阶段 3 的 TagFilter，都从同一个 MatchRule 读
}
```

**不再出现** `FilterUtils.fromMatchJson()` → `FilterUtils.parseMatchLayer()` ↓ `FilterUtils.parseMatchMaxPref()` 三个独立调用。

## 错误类型化

```java
sealed interface RelayError {
    record ParseError(String detail) implements RelayError {}
    record ModelNotFound(String model) implements RelayError {}
    record NoInstance(String model, String reason) implements RelayError {}
    record NoReasoningInstance(String model) implements RelayError {}
    record AllVendorsBusy(int retried) implements RelayError {}
    record UpstreamFailure(int httpCode, String responseBody) implements RelayError {}
    record Timeout(Duration duration) implements RelayError {}
}
```

**各处不再构造** `error(ctx, 503, "no reasoning-capable instance available")`——改为返回 `Either.left(new NoReasoningInstance(model))`。

HTTP 状态码映射集中在 `RelayError.toHttpStatus()`（一个静态方法）。

## V1 过渡兼容

V1 数据模型不动。V2 实现类通过 `MetaView` / `MatchRuleParser` / `RelayError` 读 V1 的数据。

数据库迁移（`ALTER TABLE`）放最后一批（批次 6），做完后删除旧兼容代码。
