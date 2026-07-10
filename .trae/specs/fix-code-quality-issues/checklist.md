# Checklist

## 第一批：正确性

- [x] ~~排序包含 ById tiebreaker~~（有意保留随机性，不修复）
- [x] MatchRuleParser 非法 JSON 抛异常而非降级到 AllMatch
- [x] DefaultRelay.hasChoices 正确判断 choices 有效性，不含用户文本误判
- [x] 所有 Repo 写操作失败传异常到 Controller，Controller 返回 500
- [x] VirtualModelController.update 空 body 返回 400，不存在 id 返回 404
- [x] SchemaManager 迁移失败让应用退出；PG identity 列被正确识别

## 第二批：性能

- [x] /api/* 路由使用 blockingHandler 不阻塞事件循环
- [x] HolographicLogger 写入不再每次 SELECT COUNT + DELETE
- [x] VendorRefreshService 按 vendorId 查询而非全量加载
- [x] SessionTracker.findBySessionId O(1) 而非 O(n)

## 第三批：设计与整洁

- [x] 所有 Controller 统一用 BaseController 响应方法
- [x] Kimi 头部注入代码不重复
- [x] UpstreamClient 开启 keep-alive + 超时
- [x] SubstituteModel 用字符串替换无 JSON 往返
- [x] VendorRepo.findAllWithCounts status 过滤与 InstanceRepo.findAll 一致
- [x] VirtualModelRepo findByName 和 findById 空值语义一致
- [x] Instance 状态常量在 Instance 模型而非 Repo
- [x] RelayRecorder 已删除
- [x] HolographicRecord.toJson() 用 Jackson
- [x] 中文字符编码无乱码
- [x] 多个 filter 无 removedIds + addFilterAction 重复样板

## 整体

- [x] mvn test 全部通过（含新增/修改的测试）
- [x] /status 页面数据加载正常
- [x] 中继功能正常（流式 + 非流式均可用）
