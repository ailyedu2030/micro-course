# 状态机设计 v1.0 → v1.1 (business-logic)

## Purpose

补全 4 项 P0 守卫定义 + 通用端点拒绝约定 + 实现方式统一规定。

## MODIFIED Requirements

### Requirement: 课程状态机守卫补全

`docs/状态机设计.md` MUST 补全以下 4 项 P0 守卫定义 (§2.3)。

#### Scenario: DRAFT → PENDING_REVIEW 守卫 5 项
- WHEN 状态机设计 v1.1 发布
- THEN §2.3 MUST 补全 DRAFT→PENDING_REVIEW 守卫:
  - 1. title 非空
  - 2. cover_url 已上传
  - 3. category_id 已选择
  - 4. 至少一个章节
  - **5. 至少一个视频或练习** (修复 S1)

#### Scenario: PENDING_REVIEW → REJECTED 守卫 3 项
- WHEN 状态机设计 v1.1 发布
- THEN §2.3 MUST 补全 PENDING_REVIEW→REJECTED 守卫:
  - 1. 自审批阻断
  - **2. reject_reason 必填**
  - **3. reject_reason ≥ 10 字符** (修复 S2)

#### Scenario: CLOSED → PUBLISHED 守卫 "此前 PUBLISHED" 前提
- WHEN 状态机设计 v1.1 发布
- THEN §2.3 MUST 补全 CLOSED→PUBLISHED 守卫:
  - 1. MUST 满足 lastPublishedAt != null (修复 S3)
  - 2. 定价审核通过 (非免费)
  - 3. 互动课件就绪 (INTERACTIVE 类型)

#### Scenario: 通用端点拒绝 PUBLISHED 约定
- WHEN 状态机设计 v1.1 发布
- THEN §2.4 MUST 记录约定:
  - `PUT /api/courses/{id}/status` 拒绝 status=1 (PENDING_REVIEW) 和 status=4 (PUBLISHED)
  - 必须走专用端点 /submit 或 /publish (修复 S4)

### Requirement: 状态机实现方式统一

`docs/状态机设计.md` MUST 规定所有状态变更的统一入口与禁用实现方式, §3 实现约定 MUST 明确 canTransitionTo 是单一白名单。

#### Scenario: approve/reject/publish 使用 canTransitionTo
- WHEN 状态机设计 v1.1 发布
- THEN §3 实现约定 MUST 规定:
  - 所有状态变更 MUST 通过 CourseStateMachine.transition()
  - MUST NOT 使用 courseRepository.update(... WHERE status = X) 原始硬编码
  - canTransitionTo 是单一白名单 (修复 S5/S6/S7)