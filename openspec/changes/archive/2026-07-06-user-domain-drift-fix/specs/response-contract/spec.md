# 全局响应契约 (response-contract)

## Purpose

消除用户管理域发现的全局响应契约漂移 (模式 4 新), 包括 R.java timestamp 已删除但 API 契约-Phase1 仍记录 + 分页参数 0-based vs 1-based 不一致。

## ADDED Requirements

### Requirement: 全局响应规范统一

所有 API 响应 MUST 遵守 R.java 当前定义 (`{code, message, data}`), 禁止添加未经评审的额外字段。

#### Scenario: R.java 响应格式
- WHEN 任何 Controller 返回响应
- THEN MUST 包含 code (int), message (String), data (T) 三个字段
- AND MUST NOT 包含 timestamp 或其他额外字段 (除非 docs/API契约-Phase1.md 明确登记)

#### Scenario: API 契约-Phase1 响应示例同步
- WHEN 本变更完成
- THEN docs/API契约-Phase1.md 所有响应示例 MUST 从 `{code, message, data, timestamp}` 改为 `{code, message, data}`
- AND 文档与代码完全一致

### Requirement: 分页参数统一约定

所有分页参数 MUST 使用 0-based page + max 100 size 统一约定。

#### Scenario: PageRequest 统一
- WHEN 任何分页端点 (departments/majors/classes/users 等)
- THEN page MUST 从 0 开始 (0-based)
- AND size MUST 默认 20, 最大 100 (而非 10000)
- AND API 契约 MUST 明确登记此约定

#### Scenario: 4 个 list 端点 @Range 校验
- WHEN 本变更完成
- THEN DepartmentController/MajorController/ClassController/UserController 的 size 参数 MUST @Range(min=1, max=100)
- AND 前端 MUST 适配 0-based page

### Requirement: 8 个新错误码契约登记

8 个新错误码 MUST 在 API 契约-Phase1.md 错误码章节登记。

#### Scenario: 认证错误码登记
- WHEN 本变更完成
- THEN MUST 登记: 1007 OLD_PASSWORD_INCORRECT(400), 1008 SERVICE_UNAVAILABLE(503), 1009 USER_NOT_ACTIVE(403)

#### Scenario: 院系/专业/班级错误码登记
- WHEN 本变更完成
- THEN MUST 登记: 2003/2004 DEPARTMENT_NAME/CODE_EXISTS(400), 2005 DEPARTMENT_HAS_USERS(409)
- AND MUST 登记: 3003/3004 MAJOR_NAME/CODE_EXISTS(400)
- AND MUST 登记: 4003 CLASS_NAME_EXISTS(400)

#### Scenario: 用户错误码登记
- WHEN 本变更完成
- THEN MUST 登记: 5005 DELETED_USER_RETENTION_EXPIRED(400)

#### Scenario: 2 个错误码修正
- WHEN 本变更完成
- THEN MUST 修正: OLD_PASSWORD_INCORRECT 1001→1007, HTTP 401→400
- AND MUST 修正: CLASS_HAS_STUDENTS 业务码→4002 (HTTP 409 保持)

## MODIFIED Requirements

### Requirement: 开发规范 v1.5→v1.6 全局响应契约

`docs/开发规范.md` MUST 新增 §3.4.7 禁止项: 全局响应契约不可漂移。

#### Scenario: §3.4.7 全局响应契约禁止项
- WHEN 本变更完成
- THEN docs/开发规范.md v1.5→v1.6
- AND 新增 §3.4.7: 所有 API 响应 MUST 遵守 `{code, message, data}` 格式, 禁止添加额外字段; 分页参数 MUST 0-based, size 默认 20 最大 100; 错误码 MUST 在 ErrorCode.java 定义并契约登记