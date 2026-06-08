# one-api-java 需求文档

> **Java 版为唯一主版本。** Go 版（`project/one-api/`）已废弃，仅作历史参考，不再维护。
> 架构决策、需求变更、代码演进以本文档和 Java 源码为准。

---

## 核心概念

### 关系

```
用户请求 model="deepseek"
         │
         ▼
   模型入口 ──(models 列表)──→ 逻辑模型（模型画像）
         │                         │  能力/价格/窗口
         │                         │  每个画像有多个实例
         │                         ▼
         │                    模型实例（队列）
         │                         │  vendor 绑在实例上，不单独选
         │                         ▼
         │                    排序 → 依次尝试
         │                         │
         ◄─────────────────────────┘
              上游模型名替换，发请求
```

### 四个概念

| 概念 | 等价名 | 一句话 | 例子 |
|------|--------|--------|------|
| **模型入口** | 虚拟模型 | 用户请求的 model 参数，指定"要用哪些模型" | `deepseek`、`coding` |
| **逻辑模型** | 模型画像 | 描述模型能干什么、多贵、多大窗口。挑画像比挑实例更专注 | `deepseek-v4-flash`：能力=["code"]，窗口=131072，输入=1.0¥/M token |
| **模型实例** | — | 实际跑 LLM 的端点。vendor 绑在实例上 | 火山引擎部署的 deepseek-v4-pro 端点 |
| **上游模型名** | — | 发请求时替换给上游 API 的 model 值 | `doubao-seed-2.0-pro-260215` |

**关系：** 入口说"谁行"（逻辑模型名列表）→ 画像说"什么属性"→ 实例说"走哪条路"（vendor/端点）。**入口不直接挑实例。**

> **入口名和逻辑模型名是两个命名空间。** 入口名是用户起的（`deepseek`、`coding`），逻辑模型名是系统注册的（`deepseek-v4-flash`）。用户可以把入口名起得和逻辑模型名一样（如入口 `deepseek-v4-pro` 的 models 列表里包含 `deepseek-v4-pro`），也可以不一样（如入口 `deepseek` 的列表里包含 `deepseek-v4-flash`）。一样是用户的选择，不是系统约束。

---

## 迁移状态

| 模块 | 状态 | 技术栈 |
|------|------|--------|
| 路由 + 冷却 + 过滤 | ✅ | Caffeine cache (Go: sync.Map) |
| 模型入口 | ✅ | Vert.x Router |
| CRUD 管理 API | ✅ | Vert.x Router + HikariCP SQLite |
| Relay 转发 | ✅ | WebClient (buffered) + HttpClient (pipe) |
| **VisionFilter (动态 图像检测)** | ❌ | Go→Java 迁移丢失，见 §F |
| **BodyLimitFilter (>100KB)** | ❌ | Go→Java 迁移丢失，见 §F |
| CORS + tokenHash | ✅ | Vert.x Route handler |
| 会话追踪 (SHA256) | ✅ | ConcurrentHashMap (Go: sync.Map) |
| KimiCode UA 伪装 | ✅ | 内联 RelayController |
| reasoning:max 推理增强 | ✅ | MultiMap extraHeaders |
| vendor 自动刷新 | ✅ | executeBlocking 异步 |
| 流式 SSE real pipe | ✅ | HttpClient.pipeTo (Go: http.Response) |
| relay-log.db | ❌ | (后续) |
| GPU worker 双探针 | ❌ | (后续) |
| 请求日志 (request_logs) | ❌ | (后续) |

---

## 术语

| 概念 | 是什么 | 例子 |
|------|--------|------|
| **模型入口**（即虚拟模型） | 用户看到的 API 入口名。`/v1/chat/completions` 的 `model` 参数。在 `virtual_models` 表中有一条记录 | `deepseek-v4-pro`、`coding` |
| **match 规则** | 模型入口的 `models` 字段，列出逻辑模型名列表。**入口不绑定实例，只声明"哪些画像可用"** | `{"models":["deepseek-v4-flash","deepseek-v4-pro"]}` — 列表顺序=弱→强，在同 layer/pref 的实例间作为排序依据 |
| **模型实例** | 实际运行 LLM 的服务端点。有 `modelName`、`tags`、`vendor`、`meta`、`status` 等属性。存在 `instances` 表中 | vendor=deepseek 的 deepseek-v4-pro 实例，vendor=volcengine 的 doubao 实例 |
| **上游模型名** | 中继时发给上游 API 的 `model` 参数。存于 `instances.upstream_model`，**可以跟入口名不同** | 入口叫 `doubao-seed-2.0-pro`，上游模型名叫 `doubao-seed-2.0-pro-260215` |
| **逻辑模型**（模型画像，model_catalog） | 跨越 vendor 的抽象模型概念。描述能力属性：能力标签、上下文窗口、输入/输出价格。存于 `model_catalog` 表 | `deepseek-v4-flash`：capabilities=`["code"]`, context=131072, input=1.0 ¥/M token，deepseek 和 volcengine 都可以部署它的实例 |
| **用例模型入口** | 按使用场景命名的模型入口。match 规则筛选出多个逻辑模型的实例，排序后依次尝试 | `coding` → 包含 mimo-v2.5、minimax-m2.7、deepseek-v4-flash 等多个逻辑模型的实例 |

**关联关系：**

```
模型入口 ──(models 列表)──→ 逻辑模型（画像）
    │                             │  能力/价格/窗口
    │                             ▼
    │                        模型实例（队列）
    │                             │  vendor 绑在实例上
    │                             ▼
    │                        排序 → 依次尝试
    │                             │
    ◄─────────────────────────────┘
          upstreamModel 替换，发请求
```

- 入口不直接挑实例。入口列逻辑模型名，画像指向实例
- 一个画像可以有多个 vendor 的多个实例

---

## 架构决策（Java 版）

### §J1 框架选型：Vert.x 裸写

**选择：** Vert.x 4.5 + Java 21，不使用 Spring Boot / Quarkus。

| 考量 | Vert.x | Spring Boot | Quarkus |
|------|--------|-------------|---------|
| 启动速度 | 2s | 8s+ | 3s |
| 内存 | 50MB | 200MB+ | 80MB |
| 路由可见性 | Router 一行一 handler | 注解分散 | 注解分散 |
| SSE 直通 | native pipeTo | 需要额外配置 | 需要额外配置 |
| 哲学匹配 | 事件循环 ≈ Go goroutine | DI/IOC | CDI |

**决策：** Vert.x 裸写。路由 + handler 一目了然，与 Go 版 `gin.Engine` 最接近。

**§J1-D1（否决 Quarkus）：** Quarkus 的 DI/IOC/CDI 对单机网关是负资产。注解过多、代码追踪困难、冷启动不比 Vert.x 快。不加糖。

### §J2 数据库复用

**选择：** 直接读写 `~/.one-api/one-api.db`（HikariCP + JDBC SQLite 手写 SQL）。

- Go 版使用 GORM，Java 版手写 SQL（`jdbc:sqlite:`）
- 两版本可**并行运行**（不同端口），共享同一 DB
- 不引入 jOOQ / MyBatis — 表少（4 张核心表），手写 SQL 最清晰

**§J2-D1：** Repository 层使用 `HikariCP` 连接池（`maximumPoolSize=1`，SQLite 单写）。

### §J3 冷却：Caffeine 替代 sync.Map

**选择：** Caffeine 本地缓存，键结构 `cooldown:vendor:N` / `cooldown:instance:N`。

- **阻尼公式**：与 Go 完全一致 — `5 * (1 - 0.95^N) / 0.05` 分钟，上限 100 分钟
- **nocool 豁免**：检查 Instance meta 的 `nocool:true` 标签，两端都堵 — `isCooling` 返回 false + `setCooling` 不写入
- **重启清零**：同 Go sync.Map，自愈机制

### §J4 Handler 链简化

**选择：** Go 版 4 层装饰器链 → Java 版内联 RelayController。

| Go | Java | 理由 |
|-----|------|------|
| RelayLogger | 日志（控制台） | relay-log.db 未迁移 |
| DeepSeekThinking | MultiMap extraHeaders | reasoning:max 在路由前注入 |
| KimiCode | MultiMap extraHeaders | User-Agent 在路由前注入 |
| OpenAICompat | OpenAICompatHandler | 终端透传 |

**决策：** 不在 Java 版重建装饰器链。Go 版 `chain.Ctx` 的 `Meta/ReqHdr/Body` 三个槽，等价于 Java 的 `extraHeaders` + `body` + `vendor` 三个变量，data flow 完全一致。

### §J5 流式 SSE 实现

**选择：** 双 back-end — WebClient（buffered） + HttpClient（pipe）。

```
非流式: WebClient.sendBuffer → Future<HttpResponse<Buffer>> → retry 友好
流  式: HttpClient.request.send → pipeTo(HttpServerResponse) → 实时
```

- **检测**：body 含 `"stream":true`
- **pipe 失败**：502 返回给客户端，不重试（pipe 无法重试）

### §J6 零依赖部署

**选择：** `init.bat` 自动下载 JDK 21 到 `.\jdk\`，`mvnw`（Maven Wrapper）自带构建工具。

```
deploy-java.bat
  ├── 检查 .\jdk\ → 不存在则 call init.bat（下载 Temurin JDK 21）
  ├── JAVA_HOME=.\jdk\ （不碰系统 Java）
  ├── mvnw package → java -jar
  └── port 13000
```

---

## 功能对照

| 需求 | Go | Java |
|------|-----|------|
| 模型入口 CRUD | ✅ | ✅ |
| Vendor CRUD | ✅ | ✅ |
| Instance CRUD | ✅ | ✅ |
| vendor/refresh-models | ✅ | ✅（executeBlocking） |
| 标签路由 (all/any/layer) | ✅ | ✅ |
| nocool 豁免 | ✅ | ✅ |
| reasoning:max | ✅ | ✅ |
| KimiCode UA | ✅ | ✅ |
| 会话追踪 | ✅ | ✅ |
| CORS | ✅ | ✅ |
| 流式 SSE | ✅ pipe | ✅ pipe |
| relay-log.db | ✅ | ❌ |
| request_logs | ✅ | ❌ |
| GPU 双探针 | ✅ | ❌ |
| 上下文压缩识别 | ✅ | ❌ |
| 亲和路由（软粘性） | ✅ | ✅ |

---

## 端点清单

| 方法 | 路径 | Java |
|------|------|------|
| `GET` | `/api/status` | ✅ |
| `GET` | `/api/vendors` | ✅ |
| `POST` | `/api/vendors` | ✅ |
| `PUT` | `/api/vendors/:id` | ✅ |
| `DELETE` | `/api/vendors/:id` | ✅ |
| `POST` | `/api/vendors/refresh-models` | ✅ |
| `GET` | `/api/instances` | ✅ |
| `PUT` | `/api/instances/:id` | ✅ |
| `DELETE` | `/api/instances/:id` | ✅ |
| `GET` | `/api/virtual-models` | ✅ |
| `POST` | `/api/virtual-models` | ✅ |
| `PUT` | `/api/virtual-models/:id` | ✅ |
| `DELETE` | `/api/virtual-models/:id` | ✅ |
| `POST` | `/v1/chat/completions` | ✅ |
| `GET` | `/v1/models` | ❌ |

---

## 已知差异

| 差异 | 影响 |
|------|------|
| 无 Redis 分桶 | 单机部署无影响。多实例需后续加 |
| 无 relay-log.db | token 统计缺失，调试需看控制台日志 |
| 无 request_logs | 完整请求体不可追溯 |
| 无 GPU 双探针 | 能力标签需手动维护 |
| 无 /v1/models | OpenAI SDK 的 model list 不可用 |

---

## 测试矩阵

| 场景 | 结果 |
|------|------|
| deepseek-v3 非流式 | ✅ |
| deepseek-v4-pro 非流式 | ✅ |
| deepseek-v4-pro-max 非流式 | ✅ |
| deepseek-v3 流式 SSE | ✅ 5 chunks |
| deepseek-v4-pro-max 流式 SSE | ✅ 23 chunks |
| CORS preflight | ✅ 5 headers |
| vendor/refresh-models | ⚠️ 3 known errors (ALI/Baidu/Groq) |
| 会话追踪 (两 agent 不同对话) | ✅ |

---

## 项目结构

```
one-api-java/
├── pom.xml                  Maven + Vert.x 4.5
├── init.bat                 下载 JDK 21 到 .\jdk\
├── deploy-java.bat          原子化部署
├── mvnw / mvnw.cmd          Maven Wrapper
├── .mvn/
└── src/main/java/com/oneapi/
    ├── Main.java
    ├── config/
    │   ├── AppConfig.java
    │   ├── ConfigLoader.java
    │   ├── DatabaseConfig.java   HikariCP + SQLite
    │   └── RouterConfig.java     Vert.x Router
    ├── model/
    │   ├── Vendor.java / VendorCaps.java / VendorWithCount.java
    │   ├── Instance.java / InstanceCaps.java / MetaKeys.java / MetaView.java
    │   ├── VirtualModel.java
    │   ├── MatchRule.java / MatchRuleParser.java
    │   ├── ModelSpec.java
    │   ├── Candidate.java
    │   ├── RelayContext.java / RelayRequest.java / RelayResult.java
    │   ├── RelayLog.java
    │   └── RelayError.java / RelayException.java
    ├── repo/
    │   ├── BaseRepo.java
    │   ├── VendorRepo.java       手写 SQL
    │   ├── InstanceRepo.java
    │   ├── VirtualModelRepo.java
    │   └── ModelCatalogRepo.java
    ├── middleware/
    │   ├── CORS.java
    │   └── RequestSetup.java     tokenHash
    ├── comparator/               ← 排序链
    │   ├── ById.java
    │   ├── ByPref.java
    │   ├── ByStatusDesc.java
    │   ├── ByCost.java          （待加）
    │   ├── ByReliability.java   （待加）前置：relay-log.db
    │   ├── ByLatency.java       （待加）前置：relay-log.db
    │   └── ByModelWeakness.java （待加）
    ├── filter/                   ← stage3 过滤
    │   ├── Filter.java
    │   ├── ActiveStatusFilter.java
    │   ├── CapabilityInstanceFilter.java
    │   ├── CapabilityRequirementMarker.java
    │   ├── CooldownFilter.java
    │   ├── LayerFilter.java
    │   ├── NameMatcher.java
    │   ├── ParamClamp.java
    │   ├── TagFilter.java
    │   └── VirtualModelLookup.java
    ├── coordinator/              ← 中继编排
    │   ├── RelayCoordinator.java
    │   ├── RelayRecorder.java
    │   └── RequestParser.java
    ├── relay/                    ← 上游转发
    │   ├── RelayExecutor.java
    │   └── DefaultRelay.java
    ├── service/
    │   ├── CooldownService.java  Caffeine
    │   ├── FilterUtils.java
    │   ├── RouterService.java
    │   ├── SessionTracker.java   SHA256 + 软粘性
    │   ├── VendorRefreshService.java
    │   └── RelayLogger.java
    ├── handler/
    │   └── UpstreamClient.java   WebClient + HttpClient
    ├── controller/
    │   ├── BaseController.java
    │   ├── MiscController.java
    │   ├── VendorController.java
    │   ├── InstanceController.java
    │   ├── VirtualModelController.java
    │   └── RelayControllerV2.java
    └── scoring/                  ← 待清理（c11d887 遗留）
        ├── InstanceScorer.java
        └── NoopInstanceScorer.java
```

## §F Filter 链

> Go 版有完整的动态 Filter 链（VisionFilter、BodyLimitFilter），Java 迁移过程中静默丢失。
> 本节恢复 Go 版需求，作为实施基准。

### §F.1 架构

```
请求 → 阶段2（模型解析）→ 阶段3（候选过滤）→ 排序 → 转发
              │                    │
     NameMatcher              CooldownFilter
     VirtualModelLookup       TagFilter
     CapabilityRequirementMarker（已存在）
     VisionFilter（待实现）     LayerFilter
     (TODO) BodyLimitFilter   ActiveStatusFilter
                              CapabilityInstanceFilter
```

**Filter 采用 `@FunctionalInterface`，链式组合（`andThen`），AND 语义。**

### §F.2 两大来源

| 来源 | 激活方式 | 例子 |
|------|---------|------|
| **静态** | 始终激活 | CooldownFilter、LayerFilter、ActiveStatusFilter |
| **动态** | 检查请求体内容后激活 | VisionFilter（含 image_url）、BodyLimitFilter（>100KB） |

### §F.3 动态 Filter

#### §F.3.1 VisionFilter

**触发条件：** 请求体 `messages` 中包含 `image_url` 类型内容。

**行为：** 阶段2 设置 `capabilityRequired = "vision"`，阶段3 `CapabilityInstanceFilter` 过滤候选实例——只保留 `model_catalog.capabilities` 包含 `"vision"` 的模型对应的实例。无候选时返回 400（与 §M.6 统一）。

**示例：**

```
请求: model="minimax", messages=[{"type":"image_url", ...}]
minimax 入口 models: [minimax-m2.7, minimax-m3, minimax-m2.5]
  minimax-m2.7 → catalog: ["chat","code"]              ❌ 筛掉
  minimax-m3   → catalog: ["chat","code","vision"]      ✅ 保留
  minimax-m2.5 → catalog: ["chat"]                      ❌ 筛掉
→ 只剩 minimax-m3 的实例参与路由
```

#### §F.3.2 架构变更：实例→画像

**Go 版：** VisionFilter 筛选**实例**——检查实例 meta 是否有 `capability:vision` 标签。

**Java 版（新）：** 有了模型画像概念后，筛选**画像**——检查 `model_catalog.capabilities` 是否包含 `"vision"`。不支持的画像对应的所有实例均被排除。更上层、更早中断。

> 此变更适用于所有能力过滤场景，不仅限于 vision。

**理由：** 画像标注的是模型本身的属性（能否看图），实例 meta 标注的是部署属性（哪个供应商、什么 plan）。能力是模型层面的概念，应放在画像里。

**前提：** `RoutedVendor` 已暴露 `modelName` 字段（`RouterService.java` 中定义），CapabilityInstanceFilter 可通过它按画像名查 catalog。

#### §F.3.3 BodyLimitFilter → 待办

**状态：** 待讨论。

> 模型画像引入后，请求体大小限制是否应该进入画像（不同模型窗口不同）、还是全局一刀切、还是由供应商限制——需要重新讨论。暂不入实现计划。

### §F.4 Go→Java 迁移差异

| Go | Java 现状 | 处置 |
|----|----------|------|
| VisionFilter 动态激活，检测 image_url | ❌ 不存在 | 待实现——改为筛画像 |
| CapabilityMatch 规则设 capability | ✅ CapabilityRequirementMarker | 保留 |
| CapabilityInstanceFilter 查实例 meta 标签 | ✅ 查实例 meta 标签 | 待改——改为查 model_catalog.capabilities |
| BodyLimitFilter (>100KB) | ❌ 不存在 | 待办，需重新讨论 |

### §F.5 实施计划

1. **VisionFilter（阶段2）**：检测请求体含 `image_url` → 设 `capabilityRequired = "vision"`
2. **CapabilityInstanceFilter 改造（阶段3）**：从查实例 meta 标签 → 查 `model_catalog.capabilities`（通过 `RoutedVendor.modelName()` → `ModelCatalogRepo`）
3. CapabilityRequirementMarker 保留（CapabilityMatch 规则场景）
4. 并发能力需求（CapabilityMatch + VisionFilter 同时激活）→ 当前取 last-write-wins，后续支持多值
5. `CapabilityInstanceFilterTest` 重写（4 个旧测试基于实例 meta，需改写为 catalog 查表）
6. 错误码：无候选 → 400（OpenAI 兼容），与 §M.6 统一
7. BodyLimitFilter → 待办（§F.3.3）

---

## §M 模型选择（v0.2）

> 状态：草案 v0.2，整体设计中
> 创建：2026-06-07
> 前版本：v0.1（已废弃于本次重写）

### §M.1 背景

one-api-java 当前已支持"虚拟模型 → 实际模型实例"的路由。但 `auto` 虚拟模型不可用：
- 候选模型混在一起无法排序
- 不区分用例（coding / chat / reasoning）
- 不利用多 vendor 的差异
- 同一会话可能路由到不同实例，破坏连续性

### §M.2 目标

实现"用例虚拟模型"+"多维度实例排序"+"会话软粘性"：
1. 用户请求 `coding` / `chat` / `reasoning` 等用例虚拟模型
2. 系统按用例自动筛选候选模型（能力匹配）
3. 候选实例按多维度排序（layer / cost / reliability / latency）
4. 简单任务用便宜模型，复杂任务自动升级
5. 同一会话优先复用上次实例（软粘性，丢了不影响功能）

### §M.3 已落地的支撑能力

| 能力 | 状态 | 引用 |
|------|------|------|
| 虚拟模型 match 规则 | ✅ 已有 | §功能对照 |
| 能力过滤 | ❌ 部分（CapabilityMatch 规则可用，图像请求动态检测缺失） | §F Filter 链 |
| 实例 layer 排序 | ✅ 已有 | ByPref comparator |
| 会话追踪 | ✅ 已有 | SessionTracker |
| Cooldown 过滤 | ✅ 已有 | CooldownFilter |
| relay-log.db（运行时指标） | ❌ | 主需求 §已知差异 |
| model_catalog 表 | ✅ 已建 | commit d736c07 |
| ModelSpec / ModelCatalogRepo | ✅ 已建 | commit d736c07 |
| SessionTracker 软粘性字段 | ✅ 已实现 | §M.5 操作清单 #4–#5 |

### §M.4 设计决策

#### §M.4.1 模型入口结构

模型入口统一用"候选模型列表"表示：

| 类型 | 例子 | match.models |
|------|------|--------------|
| 多模型候选 | `coding` | `[mimo-v2.5, minimax-m2.7, minimax-m3, deepseek-v4-flash, deepseek-v4-pro, ...]` |
| 单模型 | `deepseek-v4-pro` | `[deepseek-v4-pro]` |

**决策：**
- 两种类型走相同逻辑（无差别）
- `auto` 虚拟模型**删除**——太泛，无法推断用例
- 列表顺序 = 弱→强，**在同 layer/pref 的实例间作为排序依据**（由 ByModelWeakness Comparator 实现）。真正优先级由排序链（§M.4.3）决定——实例维度（layer/pref/cost/指标）优先于模型维度（弱→强）
- 列表顺序由人工手写——不依赖字母序或自动推断
- 能力过滤作用于列表中的每个模型
- 过滤后列表为空 → 返回错误（不静默 fallback）

**例子：**
- 请求 `deepseek-v4-pro` 模型入口，body 含图片
- 列表 = `[deepseek-v4-pro]`
- 过滤（需要 `capability:vision`）→ 列表为空
- 返回错误："The virtual model 'deepseek-v4-pro' has no model supporting the requested capabilities"

#### §M.4.2 实例路由流程

**完整流程（含软粘性）：**

```
1. 加载候选          loadCandidates(targetModel)
2. stage3 过滤        能力 / Cooldown
3. 排序              按 Comparator 链
4. 软粘性 boost      上次实例移到队首
5. 队列试错          失败切下一个候选
```

#### §M.4.3 排序链

能力匹配在 stage3 过滤阶段完成，不参与排序。以下为 Comparator 链：

| # | 维度 | 数据源 | 类 |
|---|------|--------|-----|
| 1 | layer | instance meta | ByPref（已有） |
| 2 | pref | instance meta | ByPref（已有） |
| 3 | cost | model_catalog | ByCost（待加） |
| 4 | reliability | relay-log.db | ByReliability（待加）<br>前置：relay-log.db |
| 5 | latency | relay-log.db | ByLatency（待加）<br>前置：relay-log.db |
| 6 | model 弱→强 | virtual_model.match | ByModelWeakness（待加） |

**关键洞察：** 实例层（layer / pref / cost / 指标）比模型层（弱→强）优先级高。"免费的先用"是 layer 维度的具体表现。

#### §M.4.4 软粘性

**状态：已实现 ✅**

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

#### §M.4.5 集成方式（v0.2 修正）

**v0.1 错误的"架构隔离"思路已弃用。** v0.2 采用直接集成：

- ❌ 不新建 `scoring` 包 + `InstanceScorer` 接口
- ❌ 不加 `scoring.enabled` 配置开关
- ✅ 新 Comparator 直接加到现有 `com/oneapi/comparator/` 包
- ✅ 直接修改 `RouterConfig` 串入新 Comparator
- ✅ 直接修改 `RelayCoordinator` 串入软粘性逻辑
- ✅ 直接修改 `SessionTracker` 加软粘性字段

**c11d887 的 `scoring` 包待清理（删 `InstanceScorer.java` 和 `NoopInstanceScorer.java`）。**

### §M.5 操作清单

| # | 操作 | 文件 | 状态 |
|---|------|------|------|
| 1 | 扩 virtual_model.match JSON 加 `models` 字段 | VirtualModel.java + match 解析 | 设计中(ModelsMatch) |
| 2 | 现有 15 个虚拟模型 match JSON 全部加 `models` 字段 | 数据库 UPDATE | 设计中(ModelsMatch) |
| 3 | 删除 `auto` 虚拟模型（id=待查） | 数据库 DELETE | ❌ |
| 4 | SessionTrack 加 `lastInstanceId` + `lastUsedAt` 字段 | SessionTracker.java | ✅ |
| 5 | SessionTracker 加 `getPreferredInstance` / `recordInstance` 方法 | SessionTracker.java | ✅ |
| 6 | RelayCoordinator 在 stage3 后插入软粘性 boost | RelayCoordinator.java | ✅ |
| 7 | RelayCoordinator 在成功中继后调用 `recordInstance` | RelayCoordinator.java | ✅ |
| 8 | 加 `ByCost` Comparator | comparator/ByCost.java | ❌ |
| 9 | 加 `ByReliability` Comparator | comparator/ByReliability.java | ❌ |
| 10 | 加 `ByLatency` Comparator | comparator/ByLatency.java | ❌ |
| 11 | 加 `ByModelWeakness` Comparator（按 match.models 顺序） | comparator/ByModelWeakness.java | ❌ |
| 12 | RouterConfig 串入新 Comparator | RouterConfig.java | ❌ |
| 13 | 清理 c11d887 的 scoring 包 | 删除 InstanceScorer.java / NoopInstanceScorer.java / NoopInstanceScorerTest.java | ❌ |
| 14 | model_catalog 录入 15+ 个逻辑模型 | 数据库 INSERT | ❌ |
| 15 | 测试：能力过滤、软粘性、cost/reliability/latency 计算 | test/ | ❌ |

### §M.6 待解决

| # | 问题 | 状态 |
|---|------|------|
| 1 | relay-log.db schema 现状？需要补哪些字段？ | 未查 |
| 2 | 列表为空错误码和错误信息格式 | ✅ 决策：OpenAI 兼容 `400` + 标准错误 JSON |

#### 列表为空错误格式（已定）

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

### §M.7 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 改 RelayCoordinator 主路径 | 已有逻辑回归 | 全量回归测试 + 单测覆盖 |
| 改 SessionTracker 数据结构 | 老数据不兼容 | 字段加 default（null/0） |
| cost/reliability/latency 数据源延迟 | 排序可能用旧数据 | TTL 短（5min） |
| 数据源不存在时 comparator 降级 | ByReliability/ByLatency 依赖 relay-log.db，不存在时降级为中性分值 | comparator 内部 null-safe + fallback 为 0 |
| 现有 15 个虚拟模型改造 | 数据迁移 | SQL 脚本一次性更新 |
| 用户预期与系统行为不一致 | 中 | 文档说明 + 错误码清晰 |

---

_基于 Go 版 docs/REQUIREMENTS.md 更新。Java 版特有决策标 §J1–§J6。_
