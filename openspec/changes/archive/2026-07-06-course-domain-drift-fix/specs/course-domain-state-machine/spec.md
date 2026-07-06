# 课程管理域 · 状态机统一入口 (course-domain-state-machine)

## Purpose

消除状态机守卫碎片化 (RCA 模式 2), 引入 `CourseStateMachine` 作为课程状态变更的**唯二入口**, 修复 4 项 P0 (S1/S2/S3/S4) + 3 项 P1-I (S5/S6/S7) 实现漂移。

## ADDED Requirements

### Requirement: CourseStateMachine 单一入口

系统 MUST 提供 `CourseStateMachine` 接口作为课程状态变更的**唯二入口**。所有课程状态变更 MUST 通过 `CourseStateMachine.transition(courseId, targetStatus, actor, context)` 调用。直接调用 `courseRepository.update(... WHERE status = X)` MUST 删除或仅限状态机内部使用。

#### Scenario: 状态变更调用统一入口
- WHEN CourseAuditServiceImpl.approve() / reject() / publish() / submitForReview() 被调用
- THEN 必须委托给 CourseStateMachine.transition()
- AND 不得直接使用 courseRepository.update() 修改 status 字段

#### Scenario: 通用端点拒绝 PUBLISHED/PENDING_REVIEW
- WHEN 调用 `PUT /api/courses/{id}/status?status=1` 或 `?status=4`
- THEN 返回 400, BusinessException(COURSE_STATUS_TRANSITION_NOT_ALLOWED, "请使用 /submit 或 /publish 专用端点")
- AND 不修改数据库

#### Scenario: 状态机守卫按状态对注册
- WHEN 系统启动时 CourseStateMachineConfig @Configuration bean 加载
- THEN DRAFT→PENDING_REVIEW, PENDING_REVIEW→REJECTED, CLOSED→PUBLISHED 等守卫被注册
- AND 守卫可在不修改主流程代码情况下替换

### Requirement: 4 项 P0 守卫补全与乐观锁

系统 MUST 补全以下 4 项 P0 状态机守卫缺失并实现乐观锁 + 自审批阻断。

#### Scenario: S1 - submitForReview 要求章节下有视频/练习
- WHEN 教师调用 `POST /api/courses/{id}/submit` 且课程无视频/练习/课件
- THEN 返回 400, BusinessException(CHAPTER_HAS_NO_CONTENT, "至少一个章节下必须有视频或练习")

#### Scenario: S2 - reject 要求驳回原因 ≥ 10 字符
- WHEN 管理员调用 `POST /api/courses/{id}/reject` 且 reason 长度 < 10
- THEN 返回 400, Validation Error "驳回原因不能少于 10 字符"

#### Scenario: S3 - CLOSED→PUBLISHED 要求此前为 PUBLISHED
- WHEN 课程从 DRAFT→CLOSED 或 REJECTED→CLOSED 后调用 `POST /api/courses/{id}/publish`
- THEN 返回 400, BusinessException(NOT_PREVIOUSLY_PUBLISHED, "只有曾经发布过的课程才能重新上架")

#### Scenario: S3 数据库支持 - lastPublishedAt 字段
- WHEN 本变更执行时
- THEN 新增 Flyway migration V155 添加 `courses.last_published_at TIMESTAMP` 字段
- AND publish() 时更新 lastPublishedAt = NOW()

#### Scenario: 乐观锁 CAS 更新
- WHEN CourseStateMachine.transition() 执行状态变更
- THEN 使用 `WHERE id=? AND status=current AND version=?` CAS 更新
- AND 若 rows=0 抛出 VERSION_CONFLICT

#### Scenario: 自审批阻断
- WHEN actor.id == course.teacherId 且目标状态是 APPROVED/PUBLISHED/REJECTED
- THEN 抛出 SELF_APPROVAL_BLOCKED (即使 ADMIN 也阻断)

### Requirement: ExhaustiveStateMachineTest 49 转换覆盖

测试套件 MUST 覆盖所有 7×7 = 49 个课程状态转换。

#### Scenario: 7×7 状态转换穷举测试
- WHEN ExhaustiveStateMachineTest 跑测试
- THEN 覆盖所有 7 状态 × 7 目标 = 49 个转换
- AND 每个转换验证: canTransitionTo / 业务守卫 / 乐观锁 / 自审批

## MODIFIED Requirements

### Requirement: 状态机设计文档 v1.0→v1.1

`docs/状态机设计.md` MUST 更新至 v1.1, §2.2 课程状态机 MUST 补全 3 项守卫定义 (S1/S2/S3), §2.4 MUST 记录"通用端点拒绝 PUBLISHED"约定。

#### Scenario: 状态机设计文档同步
- WHEN 本变更的阶段 8 完成时
- THEN docs/状态机设计.md v1.0→v1.1
- AND 包含 3 项新守卫定义
- AND 包含通用端点拒绝约定