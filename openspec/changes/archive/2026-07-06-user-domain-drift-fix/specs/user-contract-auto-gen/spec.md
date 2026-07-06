# 用户管理域 · API 契约自动化 (user-contract-auto-gen)

## Purpose

消除用户管理域 Spec-Code 双向熵增 (模式 1), 通过 SpringDoc OpenAPI 自动生成 + CI 门禁, 一次性修复 9 个缺失端点 + 8 个缺失错误码 + 25+ 字段不一致 + 防止新增。

## ADDED Requirements

### Requirement: 9 个新端点 OpenAPI 注解

用户管理域 9 个新端点 MUST 包含完整 OpenAPI 注解 (Tag/Operation/Parameter/ApiResponse)。

#### Scenario: 5 个 Controller 全部加 @Tag
- WHEN 本变更完成
- THEN UserController/AuthController/DepartmentController/MajorController/ClassController 全部添加 @Tag
- AND 关键端点 (≥25) 添加 @Operation/@Parameter/@ApiResponse

#### Scenario: 9 个新端点注解完整
- WHEN 本变更完成
- THEN POST /api/auth/registration-status, register, cas
- AND PUT /api/users/{id}/teacher-status
- AND POST /api/users/batch, POST /api/users/{id}/avatar
- AND GET /api/users/{id}/public-profile
- AND GET /api/departments/{id}/stats
- AND GET /api/classes/{id}/students
- 全部 MUST 添加 @Operation 注解

### Requirement: UserContractCoverageTest

测试套件 MUST 自动验证所有 Controller 端点都在 OpenAPI 中注册, 任何遗漏 MUST 阻止合并。

#### Scenario: 用户管理域端点在 OpenAPI 中注册
- WHEN UserContractCoverageTest 跑测试
- THEN 反射扫描 5 个 Controller 的 @RequestMapping/@GetMapping/@PostMapping 等
- AND 解析 docs/api/openapi.yaml 的 paths
- AND 断言 Controller 端点 ⊆ OpenAPI paths (无遗漏)
- AND 断言 OpenAPI paths ⊆ Controller 端点 (无虚构)