# 第五轮技术债清单 · 2026-07-20

> 由总工程师全权记录, 作为 Phase 5+ 治理依据.

## P1 级 (1 个月内修复)

### BUG #26 · DTO 命名混用
- 现状: 25 个 DTO 文件混用 DTO/VO/Request/Response 后缀
- 影响: 新开发者 onboarding 困难, 客户端 SDK 生成不一致
- 修复方案:
  - 统一为 *DTO.java (内部数据传输) + *Request/*Response (Controller 入出参) + *VO (View Object)
  - 写 .claude/skills/microcourse/dto-naming-check.sh 自动检查
- 涉及文件: 25 个

### BUG #27 · 旧 SlideServiceImpl 与新 PptCoursewareServiceImpl 重复
- 现状: 两套 service 共存 (slide_pages vs slide_ppt_*)
- 影响: 维护负担, 旧 service 长期失修会出 bug
- 修复方案: Phase 3 backfill 后删除旧 SlideService, 用 @Deprecated 标注过渡期
- 涉及文件: SlideServiceImpl.java + SlideService.java + SlideController.java

### BUG #28 · 旧 AudioUploadController/TtsController 残留
- 现状: 旧 controller 仍暴露 /audio/upload, /tts/* 路径
- 影响: 与新架构重叠, 接口混乱
- 修复方案: 加 mc:feature:courseware_v2 flag, 默认走新 API, 旧 controller 灰度下线

### BUG #29 · resolveAudioToken 每次流式 GET 都 2 SQL
- 现状: 流式 GET 一次音频 = findByToken + page.selectById (或 unit.selectById)
- 影响: 1000 QPS 流式 GET = 2000 SQL/s, 高负载下瓶颈
- 修复方案: 加 Redis cache (mc:audio:storage:{token} TTL 5min) 避免重复查 page/unit

## P2 级 (3 个月内规划)

### BUG #30 · 缺少全局异常处理 (ControllerAdvice)
- 现状: 每个 controller 内部 try-catch, 重复代码
- 修复方案: 加 @RestControllerAdvice 统一处理 BusinessException → R

### BUG #31 · 缺少请求链路追踪 (TraceId)
- 现状: 日志无 traceId, 多服务调用无法关联
- 修复方案: 加 MDC + spring-cloud-sleuth, 日志格式加 [traceId]

### BUG #32 · 缺少 API 限流
- 现状: 无任何限流, 攻击者可批量调用 streamAudio
- 修复方案: 加 Bucket4j 或 sentinel, 100 QPS / IP

### BUG #33 · 缺少 e2e 测试
- 现状: 单元测试 18 个, 无 Playwright/Cypress e2e
- 修复方案: 加 cypress e2e (登录 → 课件 → 试听 → 全链路)

## P3 级 (月度优化)

### BUG #34 · 前端组件无单元测试
- AudioManager/AudioPanel/ScriptEditor 等无 jest/vitest 测试
- 修复方案: 加 vitest + vue-test-utils, 至少给 AudioManager 5 用例

### BUG #35 · 前端 bundle size 警告
- micro-course-admin 主 bundle > 1000 kB
- 修复方案: code-splitting (按路由懒加载)

### BUG #36 · 缺少性能监控 (APM)
- 现状: 无 SkyWalking / Pinpoint
- 修复方案: 加 SkyWalking 8.x, 接入 openTelemetry

---

**总工程师签字**: 本清单作为治理依据, 后续 PR 必须引用.