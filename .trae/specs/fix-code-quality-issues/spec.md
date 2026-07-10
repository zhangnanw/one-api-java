# 代码质量修复 Spec

## Why
全局代码审查发现 22 个设计/正确性/性能问题，影响运行时行为、数据一致性和可维护性。需要按优先级分批修复。

## What Changes

### 第一批：正确性修复（会影响线上行为）
- RelayCoordinator 排序增加 ById tiebreaker，保证同分同状态时顺序确定
- MatchRuleParser 解析失败不静默降级到 AllMatch，改为向上抛异常
- DefaultRelay.hasChoices 改字符串扫描为 JSON 解析判断
- 所有 Repo 写操作（insert/update/delete）失败时向上抛异常，Controller 返回 500
- VirtualModelController.update 增加 body null 检查 + 记录存在性校验
- SchemaManager 迁移失败向上抛异常，识别 PG identity 列

### 第二批：性能优化
- RouterConfig 中 DB 路由使用 blockingHandler 避免阻塞事件循环
- HolographicLogger 环状缓冲改为定时任务清理，不再每次写入都 SELECT COUNT + DELETE
- VendorRefreshService 改为按 vendorId 查询实例而非全量加载
- SessionTracker.findBySessionId 增加二级索引避免 O(n) 遍历

### 第三批：设计与整洁
- Controller 层统一使用 BaseController 响应方法
- RelayCoordinator Kimi UA/Reasoning 头部注入提取公共方法
- UpstreamClient 开启 keep-alive + 设置超时
- SubstituteModel 改为字符串替换避免 byte→JSON→byte 往返
- VendorRepo.findAllWithCounts 修复 status 过滤一致性
- VirtualModelRepo 统一空值语义（findByName 和 findById）
- 将 Instance 状态常量移到 Instance 模型
- 删除 RelayRecorder 无状态包装类
- HolographicRecord.toJson() 改用 Jackson
- 修复编码损坏的中文注释
- 多个 filter 抽取公共 filterWithTrace 方法

## Impact
- Affected specs: 无
- Affected code: RelayCoordinator, MatchRuleParser, DefaultRelay, InstanceRepo/VendorRepo/VirtualModelRepo, VirtualModelController, SchemaManager, RouterConfig, HolographicLogger, VendorRefreshService, SessionTracker, UpstreamClient, 所有 Controller, 所有 filter

## ADDED Requirements
（无新增功能需求，全部为现有行为的修复）

## MODIFIED Requirements

### Requirement: Repo 写操作错误传播
写操作（insert/update/delete）失败时 SHALL 向上抛出 RuntimeException 而非返回 false/null，Controller 层 SHALL 捕获并返回 HTTP 500。

### Requirement: 虚拟模型匹配规则解析
MatchRuleParser 解析 match JSON 失败时 SHALL 抛出异常，不再静默降级到 AllMatch。

### Requirement: 响应构建统一
所有 Controller SHALL 使用 BaseController.ok/notFound/badRequest 统一响应方法，不再手动构建 JSON 信封。
