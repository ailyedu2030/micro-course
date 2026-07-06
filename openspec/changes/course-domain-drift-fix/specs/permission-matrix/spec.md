# 权限矩阵 v2.0 → v4.0 (permission-matrix)

## Purpose

修复 11 项权限漂移 + 补充 30 端点 (分类/课时/课件/定价/批量/状态/复制/封面), 实现权限矩阵可执行化。

## MODIFIED Requirements

### Requirement: 11 项权限漂移修复

`docs/权限矩阵.md` v4.0 MUST 与 Java Controller @PreAuthorize 注解保持一致, 修复全部 11 项漂移。

#### Scenario: submit 端点仅 TEACHER
- WHEN 权限矩阵 v4.0 发布
- THEN POST /api/courses/{id}/submit MUST 仅 TEACHER (创建者)

#### Scenario: 收藏端点路径统一 + 仅 STUDENT
- WHEN 权限矩阵 v4.0 发布
- THEN 收藏端点路径 MUST 统一为 /api/courses/{id}/favorite
- AND MUST 仅 STUDENT

#### Scenario: 5 端点补全或废弃
- WHEN 权限矩阵 v4.0 发布
- THEN 5 端点确认状态:
  - GET /api/courses/teacher/{teacherId}: MUST 实现
  - POST /api/videos/{id}/retry: MUST 实现
  - GET /api/videos/{id}/analytics: MUST 实现
  - POST /api/videos/batch-upload: MUST 实现
  - 或标记 deprecated 移除

#### Scenario: reviews 端点权限修正
- WHEN 权限矩阵 v4.0 发布
- THEN POST /api/courses/{id}/reviews MUST 仅 STUDENT
- AND DELETE /api/courses/{id}/reviews/{reviewId} MUST 仅 ADMIN

#### Scenario: 章节路径统一
- WHEN 权限矩阵 v4.0 发布
- THEN 章节路径 MUST 为:
  - 创建: POST /api/chapters (courseId in body)
  - 排序: PUT /api/chapters/sort (集合级)

### Requirement: 30 端点补充登记

`docs/权限矩阵.md` v4.0 MUST 补充 30 端点, 完整覆盖课程管理域全部上线端点 (课程分类 5 + 课时 6 + 课件/幻灯片 11 + 定价 5 + 批量 2 + 状态/复制/封面 3)。

#### Scenario: 课程分类管理 5 端点
- WHEN 权限矩阵 v4.0 发布
- THEN MUST 补充:
  - GET /api/course-categories - 所有角色
  - POST /api/course-categories - ADMIN/ACADEMIC
  - PUT /api/course-categories/{id} - ADMIN/ACADEMIC
  - DELETE /api/course-categories/{id} - ADMIN/ACADEMIC

#### Scenario: 课时管理 6 端点
- WHEN 权限矩阵 v4.0 发布
- THEN MUST 补充:
  - POST /api/lessons - TEACHER/ADMIN
  - PUT /api/lessons/{id} - TEACHER/ADMIN
  - DELETE /api/lessons/{id} - TEACHER/ADMIN
  - PUT /api/lessons/sort - TEACHER/ADMIN
  - GET /api/lessons/chapter/{chapterId} - 所有角色
  - GET /api/lessons/{id} - 所有角色

#### Scenario: 课件/幻灯片 11 端点
- WHEN 权限矩阵 v4.0 发布
- THEN MUST 补充 /api/courses/{courseId}/slides/* 全部 11 端点

#### Scenario: 定价系列 5 端点
- WHEN 权限矩阵 v4.0 发布
- THEN MUST 补充:
  - PUT /api/courses/{id}/pricing - TEACHER/ADMIN/ACADEMIC
  - GET /api/courses/{id}/pricing-for-adopter - TEACHER
  - GET /api/courses/{id}/my-price - 所有角色
  - POST /api/courses/{id}/pricing/submit-review - TEACHER/ADMIN/ACADEMIC
  - POST /api/courses/{id}/pricing/review - ADMIN/ACADEMIC

#### Scenario: 批量/状态/复制/封面 6 端点
- WHEN 权限矩阵 v4.0 发布
- THEN MUST 补充:
  - POST /api/courses/batch-approve - ADMIN/ACADEMIC
  - POST /api/courses/batch-reject - ADMIN/ACADEMIC
  - PUT /api/courses/{id}/status - ADMIN only (拒绝 status=1/4)
  - POST /api/courses/{id}/copy - TEACHER/ADMIN
  - POST /api/courses/{id}/cover - TEACHER/ADMIN
  - GET /api/courses/{id}/stats - TEACHER/ADMIN/ACADEMIC

### Requirement: Owner 校验下沉约定文档化

`docs/权限矩阵.md` MUST 显式记录 Controller @PreAuthorize 与 Service isOwnerOrAdmin 的分层约定。

#### Scenario: 权限矩阵 §5 实施建议补全
- WHEN 权限矩阵 v4.0 发布
- THEN §5 MUST 明确记录:
  - @PreAuthorize 仅做"角色门禁"粗粒度
  - 对象级 Owner 校验下沉到 Service 层 (SecurityUtil.isOwnerOrAdmin)
  - 任何"创建者本人"约束都意味着 Service 层有 isOwnerOrAdmin 校验