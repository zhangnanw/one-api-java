# One-API Code Style

## 对象风格

- **DB 实体**（与 SQLite schema 一一对应，可变）→ Lombok `@Getter @Setter`，可空构造器
  - 例：`Vendor` / `Instance` / `VirtualModel`
- **DTO / 值对象 / 不可变** → `record`
  - 例：`Candidate` / `RelayRequest` / `RelayResult` / `MatchRule.*` 子类型
- **域模型 vs 传输**：
  - 域（业务）→ `model.RelayRequest`（inbound）
  - 传输（出站到上游）→ `handler.OutboundRequest`（outbound）
  - 两个 record 严格分文件，命名带方向

## boolean 字段

- record 风格：`xxx()` / `notXxx()`，不要 `isXxx()`
- Lombok 风格：保留 `isXxx()`（JavaBean 规范）

## 注释

- Javadoc 用英文 + 中文短摘要（双语）
- 业务注释用中文
- 日志字符串统一英文（便于检索 / 国际贡献者）
