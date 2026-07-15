# 稳定性修复建议

> 本文档记录 `one-api-java` 当前代码中建议修复的稳定性问题。
> 余额查询功能（`com.oneapi.background.balance`）仍在开发中，本文档暂不涉及该模块的具体改动，仅列出非余额相关的核心路径风险。
>
> 生成时间：2026-07-14
> 基线：`mvn test` 239 个测试全部通过，`mvn package` 成功。

---

## 1. 高风险（建议优先修复）

### 1.1 `DefaultRelay` 非 2xx 响应 body 为空时 NPE

**位置**：`src/main/java/com/oneapi/relay/DefaultRelay.java:78-79`

**问题**：2xx 分支已经判断 `body == null`，但非 2xx 分支直接使用 `body.length()`，上游返回 502/503 且 body 为空时会抛 NPE。

**建议修改**：

```java
// 原代码
log.warn("upstream returned {}: {}", status,
    body.length() > 200 ? body.substring(0, 200) : body);

// 建议
String snippet = (body != null && body.length() > 200)
    ? body.substring(0, 200)
    : body;
log.warn("upstream returned {}: {}", status, snippet);
```

**影响**：核心 `/v1/chat/completions` 热路径，上游异常时可能把正常的 502 错误转成未捕获异常。

---

### 1.2 `UpstreamClient` 流式连接生命周期不完整

**位置**：`src/main/java/com/oneapi/handler/UpstreamClient.java:108-186`

**问题**：流式中继只注册了 `upstream.handler` 和 `upstream.endHandler`，未处理：
- 上游异常断连（`upstream.exceptionHandler`）
- 客户端主动断连（`sink.closeHandler`）

这会导致连接挂起或上游资源未释放。

**建议修改**（在 `upstream.resume()` 之前补充 handler）：

```java
upstream.exceptionHandler(err -> {
    log.warn("stream relay: upstream exception: {}", err.getMessage());
    if (!sink.ended()) sink.reset();
    onComplete.accept(502, 0);
});

sink.closeHandler(v -> {
    log.debug("stream relay: client closed connection");
    if (!sink.ended()) sink.reset();
    request.reset();
});

upstream.resume();
```

> 注意：`sink.reset()` 会关闭客户端响应；`request.reset()` 会取消上游请求。具体 API 需根据 Vert.x 版本确认。

**影响**：流式请求（SSE）在生产环境遇到网络抖动或客户端中断时可能泄漏连接。

---

## 2. 中风险（建议修复）

### 2.1 升级 `jackson-databind` 版本

**位置**：`pom.xml:22`

**问题**：当前 `jackson-databind 2.16.1` 存在已知反序列化/SSRF 类 CVE。

**建议**：升级至较新的补丁版本，例如：

```xml
<jackson.version>2.18.3</jackson.version>
```

或至少 `2.17.2`。升级后跑一遍全量测试，确认 JSON/YAML 解析行为一致。

---

### 2.2 `VendorController` 分页参数 int 溢出

**位置**：`src/main/java/com/oneapi/controller/VendorController.java:30-32`

**问题**：`page * pageSize` 未校验，极端参数下 int 溢出会产生负 `offset`，导致 SQL 异常。

**建议**：限制 `pageSize` 上限（如 1000）并校验 `page >= 1`。

---

### 2.3 集成测试依赖外部网络

**位置**：`src/test/java/com/oneapi/coordinator/RelayCoordinatorIntegrationTest.java`

**问题**：测试调用真实 deepseek/minimax URL，依赖网络可达和外部服务状态，CI 中不稳定。

**建议**：后续改为本地 mock HTTP server（如 Vert.x `HttpServer` 或 WireMock），只验证路由/重试/错误处理逻辑，不依赖真实上游。

---

## 3. 低风险（可选优化）

### 3.1 `HolographicLogger` / `RelayLogger` 使用 PostgreSQL 专属语法

**位置**：
- `src/main/java/com/oneapi/handler/HolographicLogger.java:31`
- `src/main/java/com/oneapi/handler/RelayLogger.java:26`

**问题**：使用 `::jsonb`、`to_timestamp` 等 PostgreSQL 专属语法，H2 兼容性受限。

**建议**：如果测试环境使用 H2，需为日志表提供 H2 兼容的 DDL 或抽象 SQL 方言层。

---

### 3.2 `maven-shade-plugin` 告警

**位置**：`rebuild.log` / `package` 阶段输出

**问题**：fat jar 打包时出现重叠 `module-info.class`、未声明的 `sqlite-jdbc` 传递依赖等告警。

**建议**：排查依赖树，排除无用传递依赖；为 `META-INF/services/java.sql.Driver` 等资源配置合并策略。

---

## 4. 余额功能开发完成后需补充的项（备忘）

以下问题属于正在开发中的 `com.oneapi.background.balance` 模块，待功能完成后统一处理：

- 余额模块零测试覆盖
- `BaseBalanceProvider.buildUrl()` 空 `base_url` NPE
- `/api/vendors/balance-query` 同步串行调用导致长时间阻塞
- `BalanceQueryService.queryAll()` 中 `errors` 使用非线程安全 `ArrayList`
- `BaseBalanceProvider.queryBalance()` 未恢复中断标志
- 各 Provider 硬编码官方域名、部分凭证解析缺少校验

---

## 5. 修复优先级建议

| 优先级 | 事项 | 原因 |
|--------|------|------|
| P0 | `DefaultRelay` body 判空 | 核心热路径，上游异常时可能 NPE |
| P0 | `UpstreamClient` 流式生命周期 | 生产环境连接泄漏风险 |
| P1 | 升级 Jackson | 安全 CVE |
| P1 | 分页参数校验 | API 健壮性 |
| P2 | 集成测试去外部依赖 | CI 稳定性 |
| P2 | H2/PostgreSQL 日志兼容 | 测试环境可移植性 |
| P3 | Shade 告警清理 | 打包卫生 |
