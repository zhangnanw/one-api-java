# 需求文档：标签驱动的路由增强 + 死代码清理

> 状态：已完成（短期目标全部验收通过）  
> 最后更新：2026-06-29

---

## 1. 背景

当前 one-api-java 的 5 阶段流水线已稳定运行（37 测试类、216 用例全部通过）。排序阶段使用 `ByScore` Comparator，计算 `layer + pref` 作为综合分数，按此升序排列。

`ByPref` 命名与数据库 `pref` 字段同名，容易混淆。代码中的比较器已改名为 `ByScore`，强调"按分数排序"，与数据库字段脱钩。

后续计划引入**小模型请求内容分类**功能，给请求打标签（如 `"coding"`、`"vision"`），用于：
- **过滤**：不具备标签对应能力的实例被筛除
- **排序**：具备能力的实例在底分基础上获得加分，不具备的不减分（即不打标签不影响现有排序）

---

## 2. 术语词汇表

| 术语 | 定义 | 代码对应 | 注意 |
|------|------|----------|------|
| **layer**（数据库字段） | 实例的层级，表现形式为分数，静态设置 | `instances.layer` 字段 | 合并到最终分 |
| **pref**（数据库字段） | 实例的偏好值（0~1 浮点数），静态设置，根据收费模式（免费/收费/预付费） | `instances.pref` 字段 | 不可重命名（量太大） |
| **标签权重**（tagWeight） | 实例在某个标签上的适配度得分，可正可负。可配置在三个层级（逻辑模型 → 供应商 → 实例），**就近覆盖**：实例配置优先于供应商，供应商优先于逻辑模型。**可选，不配不影响正常排序** | 待新增（多层级） | 多标签累加，不打标签=0，无权重配置=0 |
| **调整分**（adjustment） | 运行时动态计算：所有动态来源的累加值。当前实现为标签权重累加，后续可扩展统计分（如余额低、故障率高、延迟高等） | 待实现 | 不打标签 = 0，无动态来源 = 0 |
| **最终分**（finalScore） | `layer + pref + adjustment` | 排序时使用的分数 | 无动态来源时就是 `layer + pref` |
| **标签**（contentTag） | 请求内容分类标签，如 `coding`、`vision`、`reasoning` | 待加入 `RelayContext` | 来源：小模型分类或请求体特征检测 |
| **过滤** | 根据条件移除候选实例 | `Filter` 链（阶段 3） | 与排序是独立阶段 |
| **排序** | 按最终分升序排列 | `Comparator` 链（阶段 4） | 只看最终分 |

---

## 3. 目标

### 3.1 短期目标：清理死代码 + 排序器改名 + 递归改循环

**清理 `scoring` 包**（三个文件）：
- `src/main/java/com/oneapi/scoring/InstanceScorer.java`
- `src/main/java/com/oneapi/scoring/NoopInstanceScorer.java`
- `src/test/java/com/oneapi/scoring/NoopInstanceScorerTest.java`

**理由**：文档 v0.2 已明确弃用 `scoring` 包，且当前代码中没有任何引用。`NoopInstanceScorer` 的 `score()` 方法从未被调用。

**排序器改名**（`ByPref` → `ByScore`）：
- `src/main/java/com/oneapi/comparator/ByPref.java` → 重命名为 `ByScore.java`
- 更新所有引用：`RelayCoordinator.java`、`ComparatorTest.java`（同步重命名）
- 更新文档：`REQUIREMENTS.md`、`ARCHITECTURE-V2.md`、`MIGRATION.md`、`README.md` 中所有 `ByPref` → `ByScore`

**递归改循环**（`relayBufferedInner`）：
- `RelayCoordinator.relayBufferedInner()` 当前为递归实现：失败时调用自身取下一个实例
- 改为显式循环：`while` 循环从队列中逐个尝试，直到成功或队列耗尽
- 消除递归深度风险，避免极端场景下（如大量实例全部失败）可能导致的栈溢出或不可控行为
- 保持行为一致：失败处理、冷却阻尼、日志记录逻辑不变

### 3.2 中期目标：标签路由增强（设计阶段）

为 5 阶段流水线引入内容标签机制：

```
1. 收到请求      → RequestParser.parse()
2. 分析请求      → stage2 Filter 链（含可选的 ContentTagger，给请求打标签）
3. 过滤          → stage3 Filter 链（新增 ContentTagFilter：按标签筛除不匹配实例）
4. 排序          → stage4 Comparator（按最终分 = 底分 + 调整分）
5. 拿第一个      → 队列.poll()
6. 失败下一个    → 循环遍历，失败则取下一个实例，直到队列耗尽
```

**核心约束**：
- 不打标签时，整个系统行为与现在完全一致（零影响）
- 标签来源暂定为小模型分类（具体实现方式待定）
- 加减法规则待定（如 `coding` 标签给具备 `code` 能力的实例 +X 分）

---

## 4. 范围

**本次（短期）**：
- 删除 `scoring` 包
- `ByPref` → `ByScore` 改名（代码 + 测试 + 文档）
- `relayBufferedInner` 递归 → 循环（行为不变，消除栈溢出风险）
- 确保测试全部通过

**本次不碰（中期，待后续需求）**：
- 不引入小模型分类逻辑
- 不修改 `RelayContext` 结构
- 不修改 `RoutedVendor` 结构
- 不修改 `Filter` 链或 `Comparator` 链

---

## 5. 验收标准

| # | 验收项 | 检查方式 |
|---|--------|----------|
| 1 | `scoring` 包三个文件已删除 | `ls src/main/java/com/oneapi/scoring/` 不存在 |
| 2 | `ByPref.java` 已重命名为 `ByScore.java` | 文件不存在/存在检查 |
| 3 | `relayBufferedInner` 无递归调用 | 代码审查：无自身调用 |
| 4 | `REQUIREMENTS.md` 中 "清理 c11d887 的 scoring 包" 标记为完成 | 文档更新 |
| 5 | 编译通过 | `mvnw compile` 成功 |
| 6 | 测试全部通过 | `mvnw test` 37 类 216 用例 0 失败 |
| 7 | 无残留引用 | `grep -r "InstanceScorer\|NoopInstanceScorer\|ByPref" src/` 返回空 |

---

## 6. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 误删被引用文件 | 编译失败 | 删除前 grep 全项目确认无引用 |
| ByPref 改名遗漏引用 | 编译失败 | grep 全项目 + 测试验证 |
| 递归改循环引入行为差异 | 重试逻辑异常 | 保留完整日志，行为不变，仅结构改变 |
| 测试意外失败 | 回归质量下降 | 删除后跑完整测试套件 |

---

## 7. 验收记录

**执行时间：** 2026-06-29  
**执行方式：** 主协调 + 双 agent 评审（小白评审 + 找茬评审）

| # | 验收项 | 结果 |
|---|--------|------|
| 1 | `scoring` 包三个文件已删除 | ✅ |
| 2 | `ByPref.java` 已重命名为 `ByScore.java` | ✅ |
| 3 | `relayBufferedInner` 改为 `tryBuffered` 尾调用（无递归） | ✅ |
| 4 | `ARCHITECTURE-V2.md` 同步更新（"递归" → "尾调用"） | ✅ |
| 5 | 编译通过 | ✅ `BUILD SUCCESS` |
| 6 | 测试全部通过 | ✅ 33 类 228 用例 0 失败 |
| 7 | 无残留引用 | ✅ `grep "ByPref\|InstanceScorer\|NoopInstanceScorer" src/` 返回空（测试中发现的 `ComparatorTest.byPref_lowerFirst` 残留已修复） |

**额外发现并修复的问题（评审驱动）：**
- `ComparatorTest.byPref_lowerFirst` 仍引用 `ByPref` → 改名 `byScore_lowerPrefFirst` ✅
- `ByScore` 注释精度（pref 范围） → 明确说明"理论 0~1，代码不夹紧" ✅
- `REQ-CLEANUP-SCORING.md` 路径错误（filter→comparator） → 修正 ✅
- `README.md` 残留 `ByPref` → 改为 `ByScore` ✅
- `RelayCoordinator.java` 注释与实现不符 → 修正 ✅

**评审指出但本需求范围外、留待后续需求处理：**
- C2：`lastUpstreamError` 透传（行为变更，需新需求）
- H1：`tryBuffered` 最大尝试次数 / 总超时控制
- H2：`tryBuffered` 单元测试覆盖
- M1：反射式 `ctx.get("relayContext")` 与 `tryStream` 风格统一（重构）

---

_状态：已完成。后续需求：标签路由增强（§3.2）。_
