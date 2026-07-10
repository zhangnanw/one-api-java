# 移除 SQLite 支持 Spec

## Why
当前代码同时维护 SQLite 和 PostgreSQL 两条路径，增加了复杂度和测试成本。用户明确希望只保留 PostgreSQL，以简化系统。

## What Changes
- **BREAKING** DatabaseConfig 仅支持 PostgreSQL，移除 SQLite 数据源、初始化路径和回退逻辑。
- 移除 RelayLogger 中的 SQLite 分支，统一使用 PostgreSQL SQL。
- 移除 HolographicLogger 中的 SQLite 分支，统一使用 PostgreSQL SQL。
- SchemaManager 简化为仅 PostgreSQL 迁移/校验。
- 更新测试：使用 H2 的 PostgreSQL 兼容模式或 Testcontainers 替换 SQLite 内存数据库。
- 更新 AppConfig 数据库配置，移除 `type`/`path` 等 SQLite 相关字段，只保留 PostgreSQL 连接参数。
- 更新部署脚本和配置示例，删除 SQLite 相关路径和依赖。
- 保留 `pom.xml` 中 SQLite JDBC 依赖（测试阶段），待测试完全迁移后再清理。

## Impact
- Affected specs: 数据库配置、日志写入、schema 迁移、测试基础设施。
- Affected code: `DatabaseConfig`, `RelayLogger`, `HolographicLogger`, `SchemaManager`, `AppConfig`, `ConfigLoader`, `Main`, 测试代码，部署脚本。
- Breaking: 任何仍在使用 SQLite 的部署必须迁移到 PostgreSQL。

## ADDED Requirements
### Requirement: PostgreSQL Only
The system SHALL only support PostgreSQL as the persistent database.

#### Scenario: Startup
- **WHEN** the application starts
- **THEN** it connects to PostgreSQL using the configured host/port/database/user/password
- **AND** it fails fast if the connection cannot be established

### Requirement: Simplified Logging SQL
The system SHALL use PostgreSQL-compatible SQL for all log writes.

#### Scenario: Relay log insert
- **WHEN** a relay log is written
- **THEN** the SQL uses `to_timestamp(...)` and `RETURNING id`
- **AND** no SQLite branch is executed

#### Scenario: Holographic log upsert
- **WHEN** a holographic log is written
- **THEN** the SQL uses `INSERT ... ON CONFLICT (request_id) DO UPDATE ...`
- **AND** `?::jsonb` is used for the JSON column

## MODIFIED Requirements
### Requirement: SchemaManager
The schema migration utility SHALL only validate and migrate PostgreSQL schema.

- Remove SQLite DDL detection and table recreation.
- Keep sequence reset for `instances`, `virtual_models`, `vendors`.
- On startup, ensure the three tables use `SERIAL` or equivalent identity default.

## REMOVED Requirements
### Requirement: SQLite Runtime Support
**Reason**: Reduce dual-path maintenance and align with PostgreSQL-only deployment.
**Migration**: Existing SQLite deployments must export data and import into PostgreSQL before upgrading.
