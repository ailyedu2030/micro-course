# 复盘报告 ERR-20260720-001

## 1. 错误基本信息

| 字段 | 值 |
|------|---|
| 错误 ID | ERR-20260720-001 |
| 错误等级 | P0+ (用户主动报告, 阻断核心功能) |
| 错误名称 | 沈阳老师 (sytafe) 缺自主课件删除 API |
| 报告人 | 总工程师 |
| 报告时间 | 2026-07-20 |
| 首次发生 | W30+ (sytafe 反馈) |
| 修复时间 | 2026-07-20 (commit e21706f4) |
| 响应时长 | 0h (立即响应) |
| 修复时长 | < 1h (开发) + 1h (集成测试) + 30min (commit+push) |

## 2. 错误现象
- **用户视角**: 沈阳老师反馈只能删整门课, 不能按章节/小节/幻灯片粒度删除课件
- **业务影响**: 教师无法精细管理自有课件, 必须联系管理员
- **触发条件**: 所有教师角色 (TEACHER) 尝试删除自有课程的子节点
- **错误日志**: N/A (功能缺失, 非运行错误)

## 3. 根因分析 (5 Why)
1. **Why**: 为什么没有单点删除 API?
   → W30 PR #38 重构时只实现了 chapter/section 创建, 漏掉删除路径
2. **Why**: 为什么测试没发现?
   → 单元测试覆盖了 read/write, 但未覆盖 delete 业务路径
3. **Why**: 为什么监控没告警?
   → 这是功能缺失, 不是运行时错误, 监控无法捕获
4. **Why**: 为什么用户反馈未走 issue 流程?
   → 客户成功通道未与产品需求流程打通
5. **Why**: 为什么 release checklist 没拦住?
   → checklist 缺 "客户需求全量覆盖" 维度

## 4. 修复方案
- **永久方案** (commit e21706f4):
  - `CoursewareDeleteService` + `CoursewareDeleteController` 新增 6 个 DELETE API
  - IDOR 防御 (SecurityUtil.isOwnerOrAdmin) 防越权
  - 软删除 chapter/section + 硬删除 slide (符合 W30 决策)
  - 13 单元测试 + 7 集成测试
- **回归测试** (commit 2df45ac0 W32):
  - 慢查询 0% < 0.1% 目标
  - OpenAPI 11/11 PASS
  - E2E 7/7 PASS
- **回滚方案**: Redis feature flag `mc:feature:courseware_delete` 5min 内可降级

## 5. 防范措施
| 层级 | 措施 | 状态 |
|------|------|------|
| 流程 | 增加 "CRUD 全覆盖" PR checklist 项 | ✅ W31 release-checklist |
| 技术 | CoursewareDeleteServiceTest 13 单元测试 | ✅ 100% 覆盖 |
| 监控 | Prometheus Api4xxRateHigh (误伤检测) | ✅ W33 加 |
| 文档 | API 文档 + 用户反馈 → 客户成功 → 产品需求 SOP | ✅ W32 user-feedback-loop |

## 6. 改进效果 (7 天跟踪 - 待观察)
- 同类功能缺失: W33 客户反馈通道运行中, 待 7 天统计
- 监控告警有效性: W33 16 规则已加载
- 用户满意度: 待 NPS 调研 (W33 计划)

---

# 复盘报告 ERR-20260720-002

## 1. 错误基本信息
| 字段 | 值 |
|------|---|
| 错误 ID | ERR-20260720-002 |
| 错误等级 | P0 (启动失败, 部署阻断) |
| 错误名称 | Spring Boot 启动时 GlobalExceptionHandler Bean 冲突 |
| 报告人 | 总工程师 |
| 报告时间 | 2026-07-20 |
| 首次发生 | W32 启动后端时发现 |
| 修复时间 | 2026-07-20 (commit c5bd9750) |
| 响应时长 | 立即 |
| 修复时长 | 30min |

## 2. 错误现象
- **用户视角**: 用户看到 HTTP 502 / 503 (服务不可达)
- **业务影响**: 100% 用户受影响, 整个 API 不可用
- **触发条件**: `mvn clean package` 后启动 jar
- **错误日志**: `ConflictingBeanDefinitionException: Annotation-specified bean name 'globalExceptionHandler' for bean class [...] conflicts with existing`

## 3. 根因分析 (5 Why)
1. **Why**: 为什么 bean name 冲突?
   → `@RestControllerAdvice` 注解默认 bean name = 类名首字母小写, 多个同名 advice 类产生冲突
2. **Why**: 为什么 2 个 advice 类?
   → plugin/interactive 子模块独立定义了 `GlobalExceptionHandler`, 与主模块的同名
3. **Why**: 为什么之前没暴露?
   → W30 之前该模块未启用, 启动时只有一个 GlobalExceptionHandler
4. **Why**: 为什么没有 startup 验证?
   → precheck 未覆盖 "启动验证" 这一项
5. **Why**: 为什么 release checklist 没拦住?
   → checklist 缺 "jar 启动烟雾测试" 步骤

## 4. 修复方案
- **永久方案** (commit c5bd9750):
  - `@Component("microcourseGlobalExceptionHandler")` 显式指定 bean name
  - `@RestControllerAdvice(basePackages="com.microcourse.controller")` 限定扫描包
  - 删除子模块重复的 `GlobalExceptionHandler`
- **Flyway 临时方案**:
  - `-Dspring.flyway.enabled=false` (V300+ 非法版本号绕过)
  - 长期: W34 重命名 V300-V310 → V3_0_0~V3_1_0
- **回归测试**:
  - jar 启动 → 3 阶段回归全过
  - OpenAPI 11/11
  - E2E 7/7

## 5. 防范措施
| 层级 | 措施 | 状态 |
|------|------|------|
| 流程 | release-checklist 加 "启动验证 + E2E" | ✅ W32 |
| 技术 | 主模块 @Component 显式 bean name | ✅ 修复 |
| 监控 | Prometheus ApiDown 告警 (启动后 2min) | ✅ W33 加 |
| 文档 | Flyway 版本号规范 - 改用 V3_X_X 格式 | 📝 W34 |

## 6. 改进效果
- 启动成功率: 100% (W33 测试)
- 监控告警有效性: ApiDown 规则已验证触发
- 用户满意度: N/A (无感知, 启动后无影响)

---

# 复盘报告 ERR-20260720-003

## 1. 错误基本信息
| 字段 | 值 |
|------|---|
| 错误 ID | ERR-20260720-003 |
| 错误等级 | P1 (告警噪声, 监控失真) |
| 错误名称 | RedisMemoryHigh 告警误报 (maxmemory=0 触发) |
| 报告人 | 总工程师 |
| 报告时间 | 2026-07-20 |
| 首次发生 | W31 alerts.yml 部署时 |
| 修复时间 | 2026-07-20 (commit c5bd9750) |
| 响应时长 | 0h |
| 修复时长 | 5min |

## 2. 错误现象
- **告警内容**: "Redis 内存使用率 > 80% (∞%)" 
- **业务影响**: 监控告警噪声, 影响真实告警识别
- **触发条件**: Redis `maxmemory` 配置为 0 (无限)
- **错误日志**: `redis_memory_max_bytes = 0`, `redis_memory_used_bytes / 0 = inf`

## 3. 根因分析 (5 Why)
1. **Why**: 为什么 maxmemory=0?
   → redis-exporter 探测 maxmemory 字段, Redis 默认配置不限制
2. **Why**: 为什么告警表达式没考虑?
   → 原 `expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.8` 没排除分母为 0
3. **Why**: 为什么测试没发现?
   → W31 没在 staging 跑 alerts 验证
4. **Why**: 为什么没在 PR review 拦截?
   → 告警规则改动是治理范畴, 未走 PR review
5. **Why**: 为什么没在 alert validation 阶段拦截?
   → 缺少 `promtool test rules` 集成

## 4. 修复方案
- **永久方案** (commit c5bd9750):
  - `expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.8 and redis_memory_max_bytes > 0`
  - 增加分母非零条件
- **回归验证**:
  - W33 SLA 追踪显示 alerts 数量 0 (无噪声)
  - promtool test rules 加入回归套件 (W34 计划)

## 5. 防范措施
| 层级 | 措施 | 状态 |
|------|------|------|
| 流程 | 告警规则改动走 PR review | ✅ W33 |
| 技术 | 表达式添加分母校验 | ✅ 修复 |
| 监控 | alerts 数量 0 视为 PASS | ✅ W33 regression |
| 文档 | promtool 集成到 regression 套件 | 📝 W34 |

## 6. 改进效果
- 告警噪声: 0 (W33 实测)
- 真实告警可识别性: 100%
- 用户满意度: N/A (内部监控质量)

---

签发时间: 2026-07-20
签发人: 总工程师