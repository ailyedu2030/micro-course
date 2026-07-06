# 权限矩阵 v4.0 → v4.1 (permission-matrix)

## Purpose

修复用户管理域 12 项权限漂移, 补充 10+ 端点登记, 实现权限矩阵可执行化。

## MODIFIED Requirements

### Requirement: ACADEMIC 状态变更权限收窄

`PUT /api/users/{id}/status` MUST 仅 ADMIN 可操作。

#### Scenario: PUT /api/users/{id}/status 收窄为 ADMIN
- WHEN 权限矩阵 v4.1 发布
- THEN POST /api/users/{id}/status (sic. 应为 PUT) MUST 仅 ADMIN (移除 ACADEMIC)
- AND 代码 @PreAuthorize MUST 同步为 `hasRole('ADMIN')`

### Requirement: 2 个矩阵端点补全

本节定义用户管理域的关键规范要求。系统 MUST 实现上述场景, 不得偏离。所有变更 MUST 同步更新对应文档与测试。

#### Scenario: GET /api/departments/{id}/majors 端点补全
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 新增此端点, 权限 STUDENT/TEACHER/ADMIN/ACADEMIC
- AND DepartmentController MUST 实现此端点

#### Scenario: GET /api/majors/{id}/classes 端点补全
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 新增此端点, 权限 STUDENT/TEACHER/ADMIN/ACADEMIC
- AND MajorController MUST 实现此端点

### Requirement: 路径同步

本节定义用户管理域的关键规范要求。系统 MUST 实现上述场景, 不得偏离。所有变更 MUST 同步更新对应文档与测试。

#### Scenario: /api/users/{id}/learning-progress 路径同步
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 标注实际路径为 `/api/learning-progress/progress?userId=&courseId=`
- AND MUST 保留 `/api/users/{id}/learning-progress` 作为路由别名 (向后兼容)

### Requirement: 10+ 端点补充登记

`docs/权限矩阵.md` v4.1 MUST 补充以下 10+ 端点登记 (代码有, v4.0 矩阵遗漏)。

#### Scenario: 认证端点补充
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 补充: POST /api/auth/registration-status, POST /api/auth/register, GET /api/auth/cas, POST /api/auth/refresh, POST /api/auth/logout, GET /api/auth/me, PUT /api/auth/me, PUT /api/auth/me/password, POST /api/auth/me/avatar

#### Scenario: 用户管理端点补充
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 补充: PUT /api/users/{id}, PUT /api/users/{id}/teacher-status, POST /api/users/batch, POST /api/users/{id}/avatar, GET /api/users/{id}/public-profile

#### Scenario: 院系/班级端点补充
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 补充: GET /api/departments/{id}/stats, GET /api/classes/{id}/students, POST /api/departments, PUT /api/departments/{id}, DELETE /api/departments/{id}

### Requirement: TEACHER 角色数据范围定义

GET /api/users/{id} MUST 明确 TEACHER 角色访问他人时的数据范围限制。

#### Scenario: TEACHER 访问他人用户需课程所有者
- WHEN 权限矩阵 v4.1 发布
- THEN GET /api/users/{id} MUST 标注: TEACHER 可查自己 (创建者) + 自己的学生 (任课) + ADMIN/ACADEMIC 可查全部
- AND 代码 MUST 在 Service 层做 isCourseOwner 校验