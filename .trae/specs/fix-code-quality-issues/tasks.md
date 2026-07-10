# Tasks

## 第一批：正确性修复

- [ ] Task 2: MatchRuleParser 解析失败不静默降级
  - [ ] 区分 "空/默认匹配" 和 "JSON 解析失败" 两种情况
  - [ ] 解析失败时抛出 IllegalArgumentException，替代当前 catch-all 返回 AllMatch
  - **验证**：传入非法 JSON → 抛出异常，上游返回 400/500

- [ ] Task 3: DefaultRelay 三次解析 + hasChoices 字符串扫描修复
  - [ ] 将 hasChoices 改为解析一次 JsonObject 判断 choices 有效性
  - [ ] 统一在一次 JsonObject 解析中提取 prompt_tokens + completion_tokens
  - **验证**：含 `"choices"` 字面文本的用户消息不会误判

- [ ] Task 4: 所有 Repo 写操作异常传播
  - [ ] InstanceRepo.insert/update/delete SQLException 包装为 RuntimeException
  - [ ] VendorRepo.insert/update/delete 同上
  - [ ] VirtualModelRepo.insert/update/delete 同上
  - [ ] ModelCatalogRepo.insert/update/delete 同上
  - [ ] Controller 层写操作路由中捕获异常返回 500 JSON
  - **验证**：数据库不可达时 delete 返回 `{success: false}` + 500 而非 `{success: true}` + 200

- [ ] Task 5: VirtualModelController.update 空值检查 + 存在性校验
  - [ ] 增加 `ctx.getBody() == null` 检查
  - [ ] 增加 `repo.findById(id)` 存在性校验
  - **验证**：空 body → 400；不存在的 id → 404

- [ ] Task 6: SchemaManager 迁移失败传播 + PG identity 列识别
  - [ ] migrate() SQLException 包装为 RuntimeException 抛出
  - [ ] ensurePostgresSerial 增加 `information_schema.columns.is_identity` 检查
  - **验证**：启动时 schema 迁移失败 → 应用退出；identity 列不被重复设 default

## 第二批：性能优化

- [ ] Task 7: RouterConfig DB 路由改为 blockingHandler
  - [ ] 对 /api/vendors, /api/instances, /api/virtual-models, /api/model-catalog 等 DB 路由使用 blockingHandler
  - **验证**：API 响应正常，事件循环不被阻塞

- [ ] Task 8: HolographicLogger 环状缓冲改为定时清理
  - [ ] 新增定时任务（如每 10 分钟）执行 DELETE 清理超过 N 条之外的旧记录
  - [ ] 写入时只做 INSERT，不再 SELECT COUNT + DELETE
  - **验证**：写入耗时显著降低，表记录数不超过配置上限

- [ ] Task 9: VendorRefreshService 按 vendorId 查询实例
  - [ ] 为 InstanceRepo 增加 findByVendorId(int vendorId) 方法
  - [ ] refreshModels 改为按 vendorId 查询而非全量加载后内存过滤
  - **验证**：刷新耗时从 O(n*m) 降至 O(n)

- [ ] Task 10: SessionTracker.findBySessionId 增加二级索引
  - [ ] 新增 ConcurrentHashMap<sessionId, hash> 二级索引
  - [ ] 在记录条目时同步维护二级索引
  - **验证**：findBySessionId 从 O(n) 降至 O(1)

## 第三批：设计与整洁

- [ ] Task 11: Controller 层统一使用 BaseController 响应方法
  - [ ] VendorController 所有手动 JSON 信封替换为 ok/notFound/badRequest
  - [ ] VirtualModelController 同上
  - [ ] ModelCatalogController 同上
  - **验证**：所有 API 响应格式不变，代码量减少

- [ ] Task 12: RelayCoordinator Kimi 头部注入提取公共方法
  - [ ] 提取 injectVendorHeaders(Candidate, Vendor, RelayContext) 私有方法
  - [ ] tryBuffered 和 relayStream 两处调用统一
  - **验证**：功能不变，无重复代码

- [ ] Task 13: UpstreamClient 开启 HTTP keep-alive + 设置超时
  - [ ] rawClient 去掉 setKeepAlive(false)
  - [ ] HttpClientOptions 中设置 connectTimeout + idleTimeout
  - **验证**：请求正常，连接复用生效

- [ ] Task 14: SubstituteModel 改为字符串替换
  - [ ] 用正则替换 `"model":"xxx"` 替代 byte→JSON→JSON→byte 往返
  - **验证**：模型替换结果等价，无额外 JSON 解析开销

- [ ] Task 15: VendorRepo.findAllWithCounts 修复 status 过滤一致性
  - [ ] 将 LEFT JOIN 条件 `i.status = 1` 改为排除 STATUS_DISABLED 和 STATUS_DEPRECATED
  - **验证**：管理页面供应商实例数与实际查询一致

- [ ] Task 16: VirtualModelRepo 统一空值语义
  - [ ] findByName 改为返回 null（删除 NOT_FOUND 哨兵）
  - [ ] 所有调用方改为判 null
  - **验证**：findByName/findById 行为一致

- [ ] Task 17: Instance 状态常量移到 Instance 模型
  - [ ] STATUS_RAW/TAGGED/DISABLED/DEPRECATED/FAILED/UNKNOWN 从 InstanceRepo 移到 Instance
  - [ ] 所有引用方更新 import
  - **验证**：编译通过，功能不变

- [ ] Task 18: 删除 RelayRecorder 包装类
  - [ ] 将 RelayCoordinator 中对 RelayRecorder 的调用改为直接调 RelayLogger 静态方法
  - [ ] 删除 RelayRecorder.java
  - **验证**：编译通过，日志功能不变

- [ ] Task 19: HolographicRecord.toJson() 改用 Jackson
  - [ ] 用 @JsonProperty 注解 + mapper.writeValueAsString 替代手动 LinkedHashMap 拼装
  - **验证**：输出 JSON 结构不变

- [ ] Task 20: 中文字符编码损坏修复
  - [ ] ActiveStatusFilter/LayerFilter/TagFilter 中乱码 `鈫` 改为 `→`
  - **验证**：日志输出正常显示中文

- [ ] Task 21: 多个 filter 公共 filterWithTrace 提取
  - [ ] 在 RelayContext 或 Filter 抽象类中提供 filterWithTrace 方法
  - [ ] BodyLimitFilter/CooldownFilter/LayerFilter/TagFilter/CapabilityInstanceFilter 中去除 removedIds + addFilterAction 样板
  - **验证**：过滤行为不变，代码更简洁

# 任务依赖
- Task 11 依赖 Task 4（统一响应格式后才能改）
- Task 18 无外部依赖，可独立执行
- 第一批（2-6）全部独立，可并行
- 第二批（7-10）全部独立，可并行
- 第三批（11-21）全部独立，可并行
