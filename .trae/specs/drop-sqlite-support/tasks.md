# Tasks

- [x] Task 1: 简化 DatabaseConfig 为 PostgreSQL 专用
  - [x] SubTask 1.1: 删除 `createSqliteDataSource` 和 `initSqlite`
  - [x] SubTask 1.2: 删除 `isPostgreSQL()` 及相关分支判断
  - [x] SubTask 1.3: 保留并调整 `init(AppConfig.DatabaseYamlConfig)` 为只初始化 PostgreSQL
  - [x] SubTask 1.4: 调整 `init(String jdbcUrl)` 测试入口，改为创建 PostgreSQL 兼容的测试数据源（先使用 H2 PostgreSQL 模式或 Testcontainers）

- [x] Task 2: 移除日志写入中的 SQLite 分支
  - [x] SubTask 2.1: `RelayLogger.insert` 只保留 PostgreSQL 路径（`to_timestamp(...)` + `RETURNING id`）
  - [x] SubTask 2.2: `HolographicLogger.insert` 只保留 PostgreSQL 路径（`INSERT ... ON CONFLICT ...` + `?::jsonb`）
  - [x] SubTask 2.3: 删除 `isPg` 成员变量及判断

- [x] Task 3: 简化 SchemaManager 为 PostgreSQL 专用
  - [x] SubTask 3.1: 删除 SQLite 迁移逻辑和 `isPostgres` 字段
  - [x] SubTask 3.2: 保留 `ensurePostgresSerial` 和 `resetPostgresSequence`
  - [x] SubTask 3.3: 调整 `Main.java` 中调用方式

- [x] Task 4: 更新 AppConfig 数据库配置
  - [x] SubTask 4.1: 从 `DatabaseYamlConfig` 移除 `type`/`path` 字段及对应 getter/setter
  - [x] SubTask 4.2: 确保默认值仍指向本地 PostgreSQL 或合理测试值

- [x] Task 5: 更新测试基础设施
  - [x] SubTask 5.1: 统计所有测试中使用 SQLite 的代码位置
  - [x] SubTask 5.2: 将测试数据源切换为 H2 PostgreSQL 兼容模式或 Testcontainers PostgreSQL
  - [x] SubTask 5.3: 调整 SQL 使其在 PostgreSQL 兼容模式下可运行（如需要）
  - [x] SubTask 5.4: 运行全部测试并修复失败项

- [x] Task 6: 更新部署脚本和配置示例
  - [x] SubTask 6.1: 从 `deploy.bat`/`start.bat` 删除 SQLite 路径和默认路径
  - [x] SubTask 6.2: 确保启动参数使用 PostgreSQL 配置

- [x] Task 7: 构建、部署和回归验证
  - [x] SubTask 7.1: 运行 `mvn clean test`
  - [x] SubTask 7.2: 打包并启动服务
  - [x] SubTask 7.3: 访问 `/status` 页面并验证供应商/实例/虚拟模型/画像可正常展示与操作
  - [x] SubTask 7.4: 提交并推送代码

# Task Dependencies
- Task 2 和 Task 3 依赖 Task 1 完成（`DatabaseConfig` 先定型为 PostgreSQL 专用）
- Task 5 依赖 Task 1、2、3 完成（测试需要新的代码形态）
- Task 7 依赖所有前置任务完成
