# API 契约 v2.1 → v2.2 (api-contract)

## Purpose

用户管理域 API 契约 9 个端点缺失 + 8 个错误码缺失 + 25+ 字段不一致。本变更 MUST 创建/补充这些契约。

## MODIFIED Requirements

### Requirement: docs/API契约-Phase1.md v2.2 升级

本节定义用户管理域的关键规范要求。系统 MUST 实现上述场景, 不得偏离。所有变更 MUST 同步更新对应文档与测试。

#### Scenario: 9 个新端点契约补全
- WHEN 本变更完成
- THEN MUST 在 API 契约-Phase1.md 补充以下 9 个端点:
  - POST /api/auth/registration-status (查询注册开关)
  - POST /api/auth/register (学生自助注册)
  - GET /api/auth/cas (CAS 单点登录回调)
  - PUT /api/users/{id}/teacher-status (教师审核)
  - POST /api/users/batch (批量导入, ADMIN only)
  - POST /api/users/{id}/avatar (头像上传)
  - GET /api/users/{id}/public-profile (公开信息)
  - GET /api/departments/{id}/stats (院系统计)
  - GET /api/classes/{id}/students (班级学生列表)

每个端点 MUST 包含: 路径/方法/权限/@PreAuthorize/请求 DTO/响应 VO/错误码

#### Scenario: 8 个新错误码契约登记
- WHEN 本变更完成
- THEN MUST 在错误码章节登记:
  - 1007 OLD_PASSWORD_INCORRECT(400)
  - 1008 SERVICE_UNAVAILABLE(503)
  - 1009 USER_NOT_ACTIVE(403)
  - 2003 DEPARTMENT_NAME_EXISTS(400)
  - 2004 DEPARTMENT_CODE_EXISTS(400)
  - 2005 DEPARTMENT_HAS_USERS(409)
  - 3003 MAJOR_NAME_EXISTS(400)
  - 3004 MAJOR_CODE_EXISTS(400)
  - 4003 CLASS_NAME_EXISTS(400)
  - 5005 DELETED_USER_RETENTION_EXPIRED(400)

#### Scenario: 2 个错误码修正
- WHEN 本变更完成
- THEN MUST 修正: OLD_PASSWORD_INCORRECT 1001→1007, HTTP 401→400
- AND MUST 修正: CLASS_HAS_STUDENTS 业务码→4002 (HTTP 409 保持)

#### Scenario: 响应字段补全
- WHEN 本变更完成
- THEN MUST 补充 GET /api/users/{id} 缺失字段: bio, studentNo, teacherNo, politicalStatus, graduationYear, teacherStatus, statusText (7 字段)
- AND MUST 补充 GET /api/departments/{id} 缺失字段: parentName
- AND MUST 补充 GET /api/classes/{id} 缺失字段: departmentName, counselorId, counselorName, studentCount (4 字段)
- AND MUST 补充 GET /api/majors/{id} 缺失字段: classes 数组

#### Scenario: 请求过滤参数补全
- WHEN 本变更完成
- THEN MUST 补充 GET /api/majors 请求参数: name, code, departmentId
- AND MUST 补充 GET /api/classes 请求参数: name, majorId, grade, counselorId
- AND MUST 补充 GET /api/users 请求参数: username, realName, majorId, classId, includeDeleted

#### Scenario: 全局响应示例删除 timestamp
- WHEN 本变更完成
- THEN docs/API契约-Phase1.md 所有响应示例 MUST 从 `{code, message, data, timestamp}` 改为 `{code, message, data}`
- AND 文档与 R.java v1.5 实际格式完全一致

#### Scenario: 分页参数统一约定
- WHEN 本变更完成
- THEN docs/API契约-Phase1.md MUST 新增全局约定章节:
  - 所有分页参数 page MUST 0-based (默认 0)
  - 所有分页参数 size MUST 默认 20, 最大 100
- AND MUST 删除所有 1-based page 描述