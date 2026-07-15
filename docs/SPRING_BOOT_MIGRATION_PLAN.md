# one-api-java Spring Boot 迁移计划（草案）

## 目标
- 主线业务（Vert.x HTTP/Relay）保持高可靠、不改动核心逻辑。
- 引入 Spring Boot 作为依赖注入、配置、数据源、事务、缓存的管理骨架。
- 为后续附加业务（余额查询、调用统计、历史评估、供应商/模型画像、智能路由）打好基础。
- 项目可频繁升级、低耦合扩展。

---

## 原则
1. **主线不动**：Vert.x 的 HTTP 服务、路由、事件循环、Relay 流水线保持不变。
2. **渐进迁移**：每个阶段都能编译、能运行、能测试。
3. **业务代码不改**：先只动骨架和依赖装配，不改动 Controller/Service/Repo 内部逻辑。
4. **可回滚**：每步提交独立，随时可回退到上一阶段。

---

## 阶段划分

### Step 1：引入 Spring Boot 骨架与依赖

- 在 `pom.xml` 中新增 Spring Boot parent / dependencies：
  - `spring-boot-starter`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-cache`
  - `spring-boot-starter-aop`
  - `spring-boot-starter-validation`
  - `flyway-core`（或 `flyway-database-postgresql`）
  - 可选 `spring-boot-starter-actuator`（监控）
- 保留 Vert.x 依赖和现有 HikariCP / Caffeine / Lombok / Jackson。
- 新增 `OneApiApplication` 主类（`@SpringBootApplication`），同时保留现有 `Main` 作为 Vert.x 启动入口。
- 设计：Spring Boot 启动时初始化 Spring 上下文，Vert.x 在 Spring 上下文就绪后启动。

### Step 2：将现有 bean 纳入 Spring 容器管理

- 让 Spring 管理以下对象：
  - `AppConfig` → `@ConfigurationProperties(prefix = "oneapi")`
  - `DataSource` → Spring Boot 自动配置，或显式 `@Bean`
  - `InstanceRepo`、`VendorRepo`、`VirtualModelRepo`、`ModelCatalogRepo` → `@Repository`
  - `VendorRefreshService`、`BalanceQueryService` → `@Service`
  - `CooldownService`、`RouterService` → `@Component` 或 `@Service`
  - `RelayCoordinator` 等由 Spring 装配的组件 → 视情况调整
- 修改 `RouterConfig`：不再手动 `new` 所有依赖，改为构造器注入。
- 修改 `Main`：从 Spring 上下文中获取 `RouterConfig`，再启动 Vert.x。
- 保留所有现有类名、方法名、调用方式不变，只改构造和注入方式。

### Step 3：建立 Service 层

- 在 Controller 和 Repo 之间引入 Service 层，承担业务编排和事务边界。
- 新增 Service：
  - `InstanceService`
  - `VendorService`（可合并余额刷新、模型刷新等逻辑）
  - `VirtualModelService`
  - `ModelCatalogService`
- 第一步先只做**转发式 Service**：方法签名与现有 Repo 对齐，内部直接调用 Repo，不改动业务逻辑。
- Controller 改为调用 Service，不再直接调用 Repo。
- 原有 `VendorRefreshService`、`BalanceQueryService` 保持独立，但可纳入 `VendorService` 统一调度。
- 后续 JPA 迁移时，只需要把 Service 内部的 Repo 调用切换到 JPA 即可，Controller 不变。

### Step 4：引入 AOP 日志切面（附加业务）

- 新增 `spring-boot-starter-aop` 依赖。
- 定义通用注解 `@LogOperation`，用于标记需要记录的 Service/Controller 方法。
- 实现日志切面：
  - 记录方法名、参数、返回值、执行耗时、异常信息。
  - 只切 Spring 管理的 bean（附加业务），不切 Vert.x Relay 主链路。
- 目标场景：
  - 余额查询
  - 调用统计
  - 供应商/模型画像
  - 入口画像
  - 其他后续扩展的附加业务
- 注意：异步方法（返回 `Future` / `CompletionStage`）需要特殊处理，避免只记录 Future 创建而不记录实际完成。
- Relay 主链路保持现有手动埋点（`HolographicLogRecorder` / `RelayLogger`），不迁移到 AOP。

### Step 5：数据源、事务与 Flyway 接入 Spring

- 移除 `DatabaseConfig` 的静态单例模式，改为 Spring 管理的 `DataSource` Bean。
- 保留 HikariCP 配置，可用 Spring Boot `spring.datasource.*` 配置替代。
- 引入 Flyway 一步到位：
  - 将现有 `SchemaManager` 的建表 SQL 整理成第一个 Flyway 脚本：`V1__baseline_schema.sql`。
  - 配置 `spring.flyway.baseline-on-migrate=true` 和 `spring.flyway.baseline-version=1`。
  - 新环境自动执行 baseline；已有数据的环境通过 baseline 标记当前版本，不重复执行。
  - JPA 配置 `spring.jpa.hibernate.ddl-auto=none`，完全由 Flyway 控制 schema。
- 在 Service 层需要事务的方法上引入 `@Transactional`（例如多个写操作组合）。
- 读操作暂时不加事务，保持现有行为。
- 注意：此步骤保留 `SchemaManager` 作为备份，直到 Step 8 清理阶段确认 Flyway 稳定后再移除。

### Step 6：逐步将 Repo 迁移到 JPA

- 新增 JPA Entity：
  - `InstanceEntity`
  - `VendorEntity`
  - `VirtualModelEntity`
  - `ModelCatalogEntity`
  - `RelayLogEntity`（可选，先不改日志）
- 为每个 Entity 新增 JPA Repository 接口，继承 `JpaRepository`。
- 保留原有 JDBC Repo 作为过渡层，新增 `*JpaRepository` 和 `*JpaStore`。
- 在 Service 中逐步切换调用：从 `JdbcRepo` 切到 `JpaRepo`。
- 每次迁移一个 Repo，跑完全部测试后再迁下一个。
- 重点：
  - 实体字段和现有数据库 schema 对齐。
  - 处理 Lombok/record 与 JPA 无参构造的冲突。
  - `findAllWithVendor` 等复杂 JOIN 用 `@Query` 或 Specification 实现。

### Step 7：引入 Spring Cache 替代手动缓存

- 启用缓存：`@EnableCaching`
- 配置缓存管理器：Caffeine Cache Manager（保留 Caffeine，只是由 Spring 管理）。
- 在 Service 层热点查询方法上加 `@Cacheable`：
  - `Instance` 列表 / 单条
  - `Vendor` 列表 / 单条
  - `VirtualModel` 列表
  - `ModelCatalog`
- 在 Service 更新/删除方法上加 `@CacheEvict` / `@CachePut`。
- 移除 `RouterService` 中手动的 `Caffeine` 缓存，改用 Spring Cache 注解。
- 注意：缓存只在 Spring 管理的 bean 外部调用时生效，避免内部调用绕过代理。

### Step 8：安全清理旧代码遗留

- 目标：移除迁移过程中产生的过渡代码，让代码库对后续开发/AI Agent 友好。
- 清理范围：
  - 已废弃的 JDBC Repo 实现（确认所有调用方已切换到 JPA 后删除）。
  - 旧的静态单例 `DatabaseConfig`（如果已完全切换到 Spring 管理的 DataSource）。
  - 手写的 `SchemaManager`（确认 Flyway 稳定后移除）。
  - 过渡期的 `*JpaStore` 或 `*JdbcAdapter` 等中间层。
  - 重复定义的 model 类（如旧 POJO 与新 JPA Entity 合并）。
  - 手动缓存工具类（如 `RouterService` 中手写的 Caffeine 缓存，已用 Spring Cache 替代）。
  - 旧的入口 `Main`（如果 `OneApiApplication` 已稳定运行）。
- 清理原则：
  - 每删除一个旧类，必须跑一遍测试，确认无回归。
  - 不一次性全删，按模块逐步清理。
  - 保留版本历史，必要时可从 Git 恢复。
- 清理完成后，代码结构应只剩：
  - `Main` 或 `OneApiApplication` 单一入口
  - Spring 管理的 Service/Repository/Component
  - Vert.x 相关 handler/controller/router
  - 领域模型（model / entity / dto 边界清晰）

### Step 9：测试回归与性能基准

- 跑通现有全部测试：
  - `mvnw test`
- 跑端到端测试 / 回归脚本：
  - `test/regression_test.py`
  - `test-siliconflow.ps1` 等
- 性能基准：
  - 启动时间对比
  - `/v1/chat/completions` 延迟和吞吐对比
  - 内存占用对比
- 记录结果，确认无回归后，合并到主分支。

---

## 风险与应对

| 风险 | 应对 |
|------|------|
| Vert.x 事件循环被 Spring JPA 阻塞 | Relay 主链路的数据库操作继续走 worker 线程 / `executeBlocking`，保持异步 |
| JPA 实体和现有 schema/record 冲突 | 先建立独立的 Entity，不破坏现有 model 类，逐步替换 |
| 缓存一致性问题 | 所有写操作统一加 `@CacheEvict`，写测试验证 |
| 测试启动变慢 | 轻量级测试用 `@DataJpaTest`，重型集成测试单独分类 |
| 启动时间变长 | 只引入必要 starter，关闭不必要的 Spring Boot 自动配置 |
| 包体积变大 | 可接受，但监控最终 jar 大小 |
| 配置来源混乱 | 统一迁移到 `application.yml`，过渡期内兼容旧 `config.yaml` |
| Schema 版本管理失控 | 引入 Flyway 一步到位，从 baseline 开始管理所有 schema 变更 |

---

## 补充设计决策

### 1. 配置迁移

- 当前使用自定义 `config.yaml`（`~/.one-api/config.yaml` 或 classpath）。
- 迁移目标：统一使用 Spring Boot `application.yml`。
- 目录约定：
  - `src/main/resources/application.yml`：放所有非敏感配置，随 jar 打包。
  - 数据库密码**不**放进该文件，单独外部化。
- 数据库密码外部化方案（任选其一）：
  - **环境变量**：`SPRING_DATASOURCE_PASSWORD=xxx`。
  - **命令行参数**：`--spring.datasource.password=xxx`。
  - **外部小文件**：`config/application-secret.yml`，由 `.gitignore` 排除，通过 `spring.config.import=optional:file:./config/application-secret.yml` 引入。
  - **运行时目录**：生产环境通过 `spring.config.location=/etc/one-api/application-secret.yml` 加载。
- 过渡方案：
  - Step 1 中 `AppConfig` 先改为读取 `application.yml` 的 `@ConfigurationProperties`。
  - 保留旧 `ConfigLoader` 作为兼容层，直到 Step 8 清理阶段再移除。
- 安全原则：
  - 数据库密码只存在于运行环境或外部文件，不进入 jar，不进入 Git。
  - 其他配置可以随代码一起管理。

### 2. 数据库 Schema 管理（一步到位）

- 从迁移开始就引入 Flyway，不走临时过渡方案。
- 具体步骤：
  1. 把 `SchemaManager` 当前管理的所有表结构导出为 `src/main/resources/db/migration/V1__baseline_schema.sql`。
  2. 配置 `spring.flyway.baseline-on-migrate=true`，让 Flyway 在已有数据的数据库上标记 baseline，不重复执行 V1。
  3. 新数据库启动时自动执行 V1，然后按版本递增执行后续脚本。
  4. JPA 永远使用 `spring.jpa.hibernate.ddl-auto=none`，由 Flyway 独占 schema 管理权。
- 新增表/字段：只能通过 `V2__xxx.sql`、`V3__xxx.sql` 等脚本。
- 旧 `SchemaManager` 在 Step 5 保留作为备份，Step 8 清理阶段移除。
- 注意：如果后续做画像/智能路由需要新增业务表，一律先写 Flyway 脚本，再写 JPA Entity。

### 3. 启动流程

- 单一入口：`OneApiApplication` 启动 Spring 上下文。
- Vert.x 启动方式：
  - 方案 A：`ApplicationRunner` / `CommandLineRunner` 在 Spring 上下文就绪后启动 Vert.x。
  - 方案 B：`OneApiApplication` 中 `SpringApplication.run()` 后手动启动 Vert.x。
- 推荐方案 A，更符合 Spring Boot 生命周期，也便于测试控制。
- 旧 `Main` 在过渡期内保留，Step 8 清理时移除。

### 4. 后台任务定时机制

- 现状：`VendorRefreshService`、`BalanceQueryService` 目前没有定时调度代码，需要调用方触发或外部轮询。
- 迁移后：可统一用 Spring `@Scheduled` 做定时调度，例如每 5 分钟刷新余额。
- 注意：这些任务涉及外部 HTTP 调用，是阻塞的，应放在 `ThreadPoolTaskScheduler` 或 Vert.x worker 线程执行。

### 5. 测试策略

- 单元测试：纯业务逻辑，不启动 Spring 上下文。
- 切片测试：`@DataJpaTest` 测试 Repository，`@WebMvcTest` 测试 Controller（如后续迁移）。
- 集成测试：启动完整 Spring 上下文 + 内存数据库，验证 Service 和 JPA。
- 端到端测试：启动完整应用（Vert.x + Spring），跑 `regression_test.py` 等脚本。
- Vert.x 路由测试：通过 Spring 上下文注入 `Router` 或 `Vertx` bean 进行测试。

### 6. Package 结构建议（Step 8 逐步整理）

```txt
com.oneapi
  ├── OneApiApplication.java
  ├── config              # Spring 配置、ConfigurationProperties
  ├── domain              # 业务模型（JPA Entity + 领域对象）
  │     ├── entity
  │     ├── model
  │     └── service
  ├── infra                 # 基础设施（缓存、数据库、日志、HTTP客户端）
  │     ├── jpa
  │     ├── cache
  │     └── audit
  ├── relay                 # Vert.x Relay 主链路（保持不变）
  │     ├── controller
  │     ├── filter
  │     ├── core
  │     └── handler
  ├── api                   # 对外 API / 管理后台（Controller）
  └── background            # 后台任务（余额刷新、模型刷新）
```

- 迁移期间允许新旧 package 并存，Step 8 统一整理。

### 7. `@Cacheable` 与 `@Transactional` 的顺序

- Spring 默认代理顺序：事务先，缓存后。
- 意味着事务提交后，结果才会写入缓存。
- 这通常是安全的，但如果是写后立刻读，可能读到缓存旧值，需要配合 `@CacheEvict` 或 `cacheManager` 显式刷新。

### 8. AOP 异步方法处理

- 如果 Service 方法返回 `Future` / `CompletableFuture` / `CompletionStage`，`@Around` 切面需要：
  - 先记录开始时间和参数。
  - 在返回的异步结果上注册 `whenComplete` 回调，记录完成/异常和耗时。
- 避免只记录到 Future 创建，而没记录到实际执行结果。

---

## 优先级建议

- 先做 Step 1 + Step 2，让 Spring Boot 能启动并管理现有 bean，这是基础。
- 再做 Step 3，建立 Service 层，让 Controller 和 Repo 解耦。
- 再做 Step 4，引入 AOP 日志切面，统一附加业务日志。
- 再做 Step 5，让 Spring 接管数据源。
- Step 6 和 Step 7 可以并行按模块推进：迁一个 Repo，同时加缓存。
- 再做 Step 8，安全清理旧代码遗留。
- 最后 Step 9 全面回归。

---

## 后续扩展（暂不实现，但需预留）

- 附加业务：余额查询、调用统计、历史评估、供应商画像、模型画像、入口画像。
- 智能路由：基于成本、延迟、能力、历史成功率的综合评分。
- 这些功能在 Spring 框架下更容易通过 JPA + Cache + Service 层快速实现。
