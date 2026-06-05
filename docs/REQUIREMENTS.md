# one-api-java 需求文档

> Go → Java 重写。对照 [Go 版 docs/REQUIREMENTS.md](../../one-api/docs/REQUIREMENTS.md)。
> 架构决策已更新为 Java 实现。

---

## 迁移状态

| 模块 | 状态 | 技术栈 |
|------|------|--------|
| 路由 + 冷却 + 过滤 | ✅ | Caffeine cache (Go: sync.Map) |
| 虚拟模型 | ✅ | Vert.x Router |
| CRUD 管理 API | ✅ | Vert.x Router + HikariCP SQLite |
| Relay 转发 | ✅ | WebClient (buffered) + HttpClient (pipe) |
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
- 不引入 jOOQ / MyBatis — 表少（3 张核心表），手写 SQL 最清晰

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
| 虚拟模型 CRUD | ✅ | ✅ |
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
| 亲和路由（已关闭） | 代码保留 | ❌ |

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
    │   ├── DatabaseConfig.java   HikariCP + SQLite
    │   └── RouterConfig.java     Vert.x Router
    ├── model/
    │   ├── Vendor.java
    │   ├── Instance.java
    │   └── VirtualModel.java
    ├── repo/
    │   ├── VendorRepo.java       手写 SQL
    │   ├── InstanceRepo.java
    │   └── VirtualModelRepo.java
    ├── middleware/
    │   ├── CORS.java
    │   └── RequestSetup.java     tokenHash
    ├── service/
    │   ├── CooldownService.java  Caffeine
    │   ├── FilterService.java
    │   ├── RouterService.java
    │   ├── SessionTracker.java   SHA256
    │   └── VendorRefreshService.java
    ├── handler/
    │   └── OpenAICompatHandler.java  WebClient + HttpClient
    └── controller/
        ├── MiscController.java
        ├── VendorController.java
        ├── InstanceController.java
        ├── VirtualModelController.java
        └── RelayController.java
```

---

_基于 Go 版 docs/REQUIREMENTS.md 更新。Java 版特有决策标 §J1–§J6。_
