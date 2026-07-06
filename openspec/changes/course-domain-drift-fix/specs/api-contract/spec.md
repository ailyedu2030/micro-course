# API 契约 v1.2 → v1.3 (api-contract)

## Purpose

课程管理域 API 契约整域 0% 覆盖, 85 端点全部缺失登记。本变更 MUST 创建专门的课程管理契约文档。

## ADDED Requirements

### Requirement: docs/API契约-课程管理.md 创建

项目 MUST 创建专门的课程管理域 API 契约文档, 覆盖课程管理域全部 85 个端点。每个端点 MUST 包含路径/方法/权限/请求/响应/错误码。

#### Scenario: 85 端点全部文档化
- WHEN 本变更完成
- THEN 创建 docs/API契约-课程管理.md 覆盖:
  - 课程 CRUD (/api/courses) - 23 端点
  - 章节管理 (/api/chapters) - 6 端点
  - 视频管理 (/api/videos) - 11 端点
  - 视频流 (/api/video-stream) - 1 端点
  - 课程分类 (/api/course-categories) - 5 端点
  - 标签 (/api/tags) - 7 端点
  - 课程套件 (/api/course-bundles) - 10 端点
  - 课时 (/api/lessons) - 6 端点
  - 课件 (/api/courses/{id}/slides) - 11 端点
  - 课程评价 (/api/courses/{id}/reviews) - 5 端点

每个端点 MUST 包含:
- 路径 / HTTP 方法
- 权限 (@PreAuthorize)
- 请求参数 / 请求体 (字段名 + 类型 + 必填)
- 响应格式 (字段名 + 类型)
- 错误码

#### Scenario: docs/API契约-Phase1.md 加引用
- WHEN 本变更完成
- THEN Phase1 文档 MUST 加章节 "课程管理域契约见 docs/API契约-课程管理.md"

### Requirement: 课程管理域错误码文档化

课程管理 API 契约文档 MUST 包含所有课程管理域错误码 (6001-18008) 定义, 与 ErrorCode.java 一致。

#### Scenario: 错误码 6001-18008 全部文档化
- WHEN 本变更完成
- THEN 课程管理契约文档 MUST 包含:
  - 6001-6009: 课程 (COURSE_*)
  - 6501-6503: 套餐 (BUNDLE_*)
  - 7001-7002: 章节 (CHAPTER_*)
  - 9004: 视频 (VIDEO_NOT_FOUND)
  - 12001-12007: 视频/评价
  - 14001-14002: 标签 (TAG_*)
  - 16001-16008: 幻灯片 (SLIDE_*)
  - 18001-18008: 课时/审核 (LESSON_*, REVIEW_*)