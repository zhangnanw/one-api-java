# 模型目录（model_catalog）设计讨论

> 状态：草稿，讨论中
> 创建：2026-06-07

## 背景

我们 fork 的 one-api-java 想做"统一入口跨模型排序"——用户请求 auto 或某个虚拟模型时，系统按多维度（成本、延迟、可靠性、任务类型）从多个候选模型里选最优。

这需要两件事：
1. **候选列表**：从虚拟模型 match 规则里捞出符合的实例
2. **每个实例的元信息**：能力、上下文、当前成本、当前延迟

**问题：当前实例的 meta 不全。** vendors / instances 表的 meta 字段只有 layer / pref / tags / max_tokens，没成本、没上下文长度。

## 候选方案

### 方案 A：实例 meta 里加字段

每个实例的 meta JSON 增加 `cost`、`context_window` 等字段。

**优点：**
- 改动小，只扩 meta 字段
- 实例级别精确

**缺点：**
- 同一模型多个 vendor 提供时，每个 vendor 都要填一遍
- 数据冗余

### 方案 B：新建 model_catalog 表

存"逻辑模型名 → 规范属性"的映射，键是逻辑模型名（如 `deepseek-v4-pro`），不绑定具体 vendor。

**优点：**
- 一个模型一份数据，多个 vendor 共享
- 评分模块只查 model_catalog，不依赖实例 meta
- 维护简单

**缺点：**
- 多一张表
- 同一模型名不同 vendor 价格可能不同（如果 vendor 有自定义价）

### 方案 C：硬编码在代码里

`Map<String, ModelSpec>` 写在 Java 代码里。

**优点：**
- 最简单
- 编译期类型检查

**缺点：**
- 改值要发版
- 15+ 个模型的硬编码不好看

### 方案 D：YAML/JSON 配置文件

`model-catalog.yaml` 在 resources 目录，启动加载。

**优点：**
- 比硬编码好改
- 版本控制

**缺点：**
- 改值要重启
- 配置文件和数据库数据分开管理

### 方案 E：扩展 VirtualModel.match JSON

在现有 `virtual_models` 表的 `match` JSON 里加 `context_window` 和 `cost` 字段。

**优点：**
- 复用现有表，不新增
- 一个 SQL UPDATE 就能改值

**缺点：**
- `match` 的语义是"匹配规则"，加成本/上下文混淆了语义
- virtual_model 关注路由，不是模型事实

## 决策记录

| 日期 | 决策 | 理由 |
|------|------|------|
| 2026-06-07 | 不需要单独建标维护（最初）| 误判，后修正 |
| 2026-06-07 | 用配置文件 | 模型能力固定 |
| 2026-06-07 | 改用数据库 | 可热改、可对接管理界面 |
| 2026-06-07 | 选用方案 B | virtual_models 是路由规则，不混语义 |

## 待解决问题

1. **数据来源**：model_catalog 表的数据怎么录？手动 SQL？admin UI？导入脚本？
2. **vendor 自定义价格**：如果某 vendor 的 kimi-2.6 比市场价贵，model_catalog 存哪一份？
3. **模型目录和实例 meta 的优先级**：评分时是优先用 model_catalog 还是实例 meta？
4. **未在 model_catalog 的模型**：新模型进来没登记，评分怎么处理？降级到 ByPref？还是只查 instance meta？
5. **能力标签标准化**：当前用 `capability:tools` `capability:vision` 这种字符串，有没有规范化？

## 已实现

- 方案 B 已建表（commit d736c07）
- ModelSpec record
- ModelCatalogRepo（init / findByName / findAll / upsert）
- Main.java 启动时 init

但讨论还没结束，上线前可能改方案。
