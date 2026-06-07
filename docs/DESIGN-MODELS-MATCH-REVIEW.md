# ModelsMatch 设计 — 审核结果汇总

| 审核员 | 角色 | 致命 | 建议 | 结论 |
|--------|------|------|------|------|
| minimax | 小白 | 6 | 8 | 打回 |
| doubao | 找茬 | 7 | 7 | 打回 |
| mimo | 测试 | 4 | 5 | 打回 |

## 共同缺陷 → 修复

| # | 缺陷 | 三个都提 | 修复 |
|---|------|----------|------|
| 1 | `models` 与 `model_name` 等共存时不报错 | ✅ | parser：检测到 models 且其他字段存在 → 抛异常 |
| 2 | `models: []` 空列表静默走 503 | ✅ | parser：空列表抛异常，"models 不能为空" |
| 3 | SQL 迁移多行改同名 | ✅ | 分两步：DELETE 旧 → INSERT 新 |
| 4 | `item.toString()` 静默吞错 | minimax+doubao | instanceof String 校验，非 String 抛异常 |
| 5 | 列表元素不校验（空串/null/重复） | doubao+mimo | parser：校验每个元素非空非 null |
| 6 | `upstreamModel=null` 隐式契约 | minimax+doubao | 文档 + Javadoc 显式标注契约。设置 `modelNames` 时同时设 `upstreamModel` 为 `""` (sentinel) |
| 7 | 去重 `instanceId` 语义不清 | doubao+mimo | 保留去重，注释写清：同一实例不重复，model 维度由名单控制 |

## 处理范围

设计文档修订 + 代码实施合并在一次 agent 任务中。
