# One-API-Java V2 迁移计划

> 只升级架构，不改功能。分 7+1 批实施。最后更新：2026-06-29。

## 批次依赖关系

```
批次 0（数据库迁移）────已完成
  │
  ▼
批次 1（数据模型）─┬─→ 批次 3（过滤器）
批次 2（配置化）───┘   └─→ 批次 4（执行器）
                              └─→ 批次 5（协调器）
                                    └─→ 批次 7（清理）
批次 6（扁平化迁移）──独立，等稳定后执行
```

## 批次 0 — 数据库迁移（SQLite → PostgreSQL）✅ 已完成

**目标**：将 SQLite 三个文件迁移到 PostgreSQL，统一 Schema，利用 PG 原生能力。

| 来源 | 原文件 | 目标 PG 表 |
|------|--------|-----------|
| 主数据 | `one-api.db` | `channels`, `tokens`, `users`, `vendors`, `virtual_models`, `model_catalog`, `instances`, `abilities`, `logs`, `options`, `redemptions` |
| 请求日志 | `relay-log.db` | `relay_logs` |
| 全息调试 | `holographic-debug.db` | `holographic_logs` |

### 迁移后的 PG 优化

| 字段 | SQLite 类型 | PostgreSQL 类型 | 优化说明 |
|------|------------|-----------------|----------|
| `relay_logs.ts` | TEXT（混合 Unix 秒 + ISO 字符串） | `TIMESTAMPTZ` | BRIN 索引 + B-tree 索引，时区感知 |
| `holographic_logs.timestamp_ms` | BIGINT | `TIMESTAMPTZ` | 毫秒转秒，时区感知 |
| `holographic_logs.data` | TEXT | `JSONB` | GIN 索引，支持 `data->>'errorType'` 查询 |
| `instances.meta` | TEXT | `JSONB` | GIN 索引，结构化查询 |
| `channels.meta/model_mapping/config` | TEXT | `JSONB` | 结构化存储 |
| 所有 `created_time` | BIGINT | `TIMESTAMPTZ`（新列 `created_at`） | 时区化，支持日期函数 |

### 连接配置

```yaml
# config.yaml
database:
  type: postgresql
  host: 10.0.0.147
  port: 5432
  database: oneapi
  user: oneapi
  password: OneApi_PG_2026
```

**安全**：PG 监听 `10.0.0.147`，`pg_hba.conf` 限制 `10.0.0.0/24` 访问。

## 批次 1 — 数据模型类型化 ✅ 已完成

**依赖**：无
**目标**：V1 数据模型不动，新建 `MetaView` / `MatchRule` / `RelayError` 类型

| 文件 | 内容 |
|------|------|
| `model/VendorCaps.java` | record：layer, tags, nocool |
| `model/InstanceCaps.java` | record：tags, layer, maxPref |
| `model/MetaView.java` | 解析一次，vendors 和 instances 共用 |
| `model/MatchRule.java` | sealed class：AllMatch, NameMatch, TagMatch, CapabilityMatch, LayerMatch, **ModelsMatch** |
| `model/MatchRuleParser.java` | 从 JSON 字符串解析 MatchRule（兼容 V1 format，含 `models` 列表） |
| `model/RelayError.java` | sealed interface：ModelNotFound, NoInstance, AllVendorsBusy, UpstreamFailure, BodyTooLarge |
| `model/RelayRequest.java` | record：model, rawBody, streaming |
| `model/RelayResult.java` | record：status, body, upstreamModel, promptTokens, completionTokens |
| `model/RelayContext.java` | 阶段间传递的上下文（含 modelNames, upstreamModel, capabilityRequired, candidates, error 等） |

**不碰**：任何现有 class，只新增。

## 批次 2 — 配置化 ✅ 已完成

**依赖**：无（与批次 1 并行）
**目标**：`config.yaml` + `AppConfig` 加载器

| 文件 | 内容 |
|------|------|
| `config.yaml` | reasoning 策略、vendor 自定义 header、layerOrder、maxRetries |
| `config/AppConfig.java` | YAML → 强类型 config record |
| `config/ConfigLoader.java` | 启动时加载，有错即退出（fail-fast） |

**硬编码常量不再写死**：

| V1 硬编码 | V2 来源 |
|----------|---------|
| `MAX_RETRIES = 2` | `config.relay.maxRetries` |
| `LAYER_ORDER = [free, subscription, payg]` | `config.relay.layerOrder` |
| `"User-Agent: KimiCLI/1.6"` | 协调器内联注入（config 中 vendors 配置） |
| `Cache TTL = 10s` | `config.relay.cacheTtlSeconds` |

## 批次 3 — 过滤器 + 排序 ✅ 已完成

**依赖**：批次 1（数据模型）、批次 2（配置）
**目标**：新建 `Filter` 接口 + 阶段 2/3 过滤器 + 阶段 4 Comparator

| 文件 | 内容 | 状态 |
|------|------|:----:|
| `filter/Filter.java` | 接口 `Filter`（非泛型，`RelayContext → RelayContext`） | ✅ |
| `filter/NameMatcher.java` | 阶段 2：物理名检测 | ✅ |
| `filter/VirtualModelLookup.java` | 阶段 2：查虚拟模型表，处理 ModelsMatch | ✅ |
| `filter/CapabilityRequirementMarker.java` | 阶段 2：标记 capability 需求 | ✅ |
| `filter/VisionFilter.java` | 阶段 2：检测 image_url | ✅ |
| `filter/CooldownFilter.java` | 阶段 3：冷却过滤 | ✅ |
| `filter/CapabilityInstanceFilter.java` | 阶段 3：按 ModelCatalog 能力过滤 | ✅ |
| `filter/BodyLimitFilter.java` | 阶段 3：按上下文窗口过滤 | ✅ |
| `filter/TagFilter.java` | 阶段 3：标签过滤 | ✅ |
| `filter/LayerFilter.java` | 阶段 3：层过滤 | ✅ |
| `filter/ActiveStatusFilter.java` | 阶段 3：活跃状态过滤 | ✅ |
| `comparator/ByScore.java` | 阶段 4：按 layer + pref 排序 | ✅ |
| `comparator/ByStatusDesc.java` | 阶段 4：TAGGED 排 RAW 前面 | ✅ |
| `comparator/ById.java` | 阶段 4：按 ID 稳定排序（备用） | ✅ |

**以下类在蓝图中有但未实现**：

| 蓝图类 | 说明 |
|--------|------|
| `filter/MaxPrefFilter.java` | 逻辑已并入 `ByScore` |
| `filter/RawStatusFilter.java` | 由 `ActiveStatusFilter` 替代 |
| `comparator/RawStatusLast.java` | 由 `ByStatusDesc` 替代 |
| `comparator/ByVendorLayer.java` | 未实现 |
| `comparator/ByInstanceLayer.java` | 未实现 |
| `sort/InstanceRepo.java` | 仍复用 `RouterService` |

**不碰**：RouterService 保留（等批次 5 搬迁）。

## 批次 4 — 执行器 ✅ 已完成

**依赖**：批次 1（RelayError）、批次 2（配置）
**目标**：新建 `RelayExecutor` 接口 + 终端执行器

| 文件 | 内容 | 状态 |
|------|------|:----:|
| `relay/RelayExecutor.java` | 接口 | ✅ |
| `relay/DefaultRelay.java` | 核心：构造 URL + 发 HTTP 请求 + 空响应检测 + token 解析 | ✅ |
| `handler/UpstreamClient.java` | HTTP 客户端（缓冲 + 流式） | ✅ |

**以下装饰器在蓝图中有但未实现，逻辑已内联到 `RelayCoordinator`**：

| 蓝图类 | 内联位置 |
|--------|----------|
| `relay/ReasoningDecorator.java` | `RelayCoordinator.relayBufferedInner` / `relayStream`（第 239-249 行、381-388 行） |
| `relay/HeaderDecorator.java` | 同上（Kimi UA 注入） |
| `relay/RetryDecorator.java` | `relayBufferedInner` 递归遍历候选队列 |
| `relay/StreamingAdapter.java` | `executePipeline` 条件分支 + `relayStream` 递归 fallback |

## 批次 5 — 协调器 + 搬迁 ✅ 已完成

**依赖**：批次 3、批次 4
**目标**：新建 `RelayCoordinator`，把 V1 的 `doHandle` / `tryRelay` 逻辑搬迁到新类

| 文件 | 内容 |
|------|------|
| `coordinator/RelayCoordinator.java` | 串联 5 个阶段，含全息日志、会话跟踪、软粘性 boost |
| `coordinator/RelayRecorder.java` | 旁路日志（委托 `RelayLogger`） |
| `coordinator/RequestParser.java` | 静态工具类，解析 HTTP → RelayRequest |
| `controller/RelayControllerV2.java` | V2 控制器，只做 HTTP 边界 |

**路由**：`/v1/chat/completions` 已直接指向 V2 控制器，无需 `/v2` 过渡。

## 批次 6 — 数据库扁平化迁移 ⏳ 待执行

**依赖**：批次 5 上线稳定后
**目标**：`ALTER TABLE` vendors / instances / virtual_models

| 步骤 | 内容 |
|------|------|
| 备份 | `copy one-api.db one-api.db.v1.bak` |
| 迁移 | Python 脚本：`migrate_meta.py`（读 meta JSON → 写入新列） |
| 验证 | `SELECT COUNT(*) FROM vendors WHERE vendor_layer != ''` |
| 清理 | 删除 MetaView 中 JSON 解析兼容逻辑 |

**可回滚**：旧 `meta` 列不删除，回滚只需恢复旧代码。

## 批次 7 — 清理 V1 死代码 ⏳ 待执行

**依赖**：批次 5 稳定后
**目标**：删除不再使用的 V1 代码

| 删除 | 理由 | 状态 |
|------|------|:----:|
| `RouterService.vendorCooldown/instanceCooldown/clearCooldown` | 透明代理，V2 直接用 CooldownService | 部分完成 |
| `RouterService.getBestVendor` | 逻辑已搬到 Coordinator + 过滤器 | ✅ 已完成 |
| `RouterService.buildFilters` | 逻辑已搬到 VirtualModelLookup + MatchRuleParser | ✅ 已完成 |
| `RouterService.sortInstances` | 逻辑已搬到 Comparator 链 | ✅ 已完成 |
| `FilterUtils.parseMeta/parseTags/parseVendorLayer/applyLayer/applyMaxPref/fromMatchJson/parseMatchLayer/parseMatchMaxPref` | 全部搬到 Filter 实现 | ✅ 已完成 |
| `RelayController.doHandle/tryRelay`（旧版） | 已删除，路由由 RelayControllerV2 接管 | ✅ 已完成 |
| `RelayLogger` DDL + DML | 仍由 `RelayRecorder` 委托，未拆分 `RelayLogSchema` | ⏳ 待批次 6 后 |

## 每批验收标准

| 批次 | 验收 |
|:----:|------|
| 1 | 编译通过，新类 100% 覆盖 |
| 2 | 启动加载 `config.yaml`，错误的 YAML 立即退出 |
| 3 | 每个 Filter/Comparator 独立 unit test |
| 4 | DefaultRelay + UpstreamClient 独立 unit test |
| 5 | `curl localhost:13000/v1/chat/completions` 返回正确响应 |
| 6 | `SELECT * FROM vendors` 新列有数据，回滚脚本可运行 |
| 7 | grep 旧类名 返回 0 |
