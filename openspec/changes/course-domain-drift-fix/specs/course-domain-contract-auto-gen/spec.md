# 课程管理域 · API 契约自动化 (course-domain-contract-auto-gen)

## Purpose

消除 Spec-Code 双向熵增 (RCA 模式 1), 通过 SpringDoc OpenAPI 自动生成 + CI 门禁, 一次性修复 85 项 API 契约漂移 + 防止新增。

## ADDED Requirements

### Requirement: SpringDoc OpenAPI 自动生成

后端 MUST 集成 SpringDoc OpenAPI 以自动生成 API 契约规范, 规范 MUST 检入版本控制, CI MUST 验证生成结果与检入版本一致。

#### Scenario: 启动后端可访问 OpenAPI 规范
- WHEN 微课后端启动
- THEN `GET /v3/api-docs` 返回完整 OpenAPI 规范 JSON
- AND `GET /swagger-ui.html` 可访问 Swagger UI

#### Scenario: docs/api/openapi.yaml 检入版本控制
- WHEN 本变更的阶段 4 完成时
- THEN `docs/api/openapi.yaml` 创建并检入 git
- AND CI 跑 `bash scripts/openapi-gen.sh` 验证生成结果与检入一致

### Requirement: 85 端点添加 OpenAPI 注解

每个课程管理端点 MUST 包含完整 OpenAPI 注解 (Tag/Operation/Parameter/ApiResponse)。

#### Scenario: 课程管理 11 个 Controller 全部添加 OpenAPI 注解
- WHEN 本变更完成时
- THEN CourseController (23) + CourseChapterController (6) + VideoController (11) + VideoStreamController (1) + CourseCategoryController (5) + TagController (7) + CourseBundleController (10) + LessonController (6) + SlideController (11) + CourseReviewController (5) = 85 端点 MUST 全部添加注解

### Requirement: ContractEndpointCoverageTest

测试套件 MUST 自动验证所有 Controller 端点都在 OpenAPI 中注册, 任何遗漏 MUST 阻止合并。

#### Scenario: 所有 Controller 端点都在 OpenAPI 中注册
- WHEN ContractEndpointCoverageTest 跑测试
- THEN 反射扫描所有 @RestController 的 @RequestMapping/@GetMapping/@PostMapping 等
- AND 解析 docs/api/openapi.yaml 的 paths
- AND 断言 Controller 端点 ⊆ OpenAPI paths (无遗漏)
- AND 断言 OpenAPI paths ⊆ Controller 端点 (无虚构)

#### Scenario: CI 门禁: 新增端点必须同步 OpenAPI
- WHEN 开发者新增 Controller 端点但未添加 @Operation 注解
- THEN ContractEndpointCoverageTest 失败
- AND CI MUST 不允许合并

### Requirement: 错误码 6001-18008 文档化

OpenAPI 规范 MUST 包含所有课程管理域错误码, 错误码 MUST 包含 code/message 字段。

#### Scenario: 课程管理域错误码全部进入 OpenAPI 规范
- WHEN OpenAPI 规范生成
- THEN 包含 ErrorCode.java 中所有课程管理域错误码: 6001-6009, 6501-6503, 7001-7002, 9004, 12001-12007, 14001-14002, 16001-16008, 18001-18008
- AND 包含 code/message 字段