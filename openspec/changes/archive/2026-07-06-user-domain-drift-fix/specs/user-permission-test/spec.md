# 用户管理域 · 权限矩阵可执行化 (user-permission-test)

## Purpose

消除用户管理域权限与校验层杂交 (模式 3), 将权限矩阵从静态 Markdown 升级为机器可读 + CI 门禁, 修复 4 项 P1-C (ACADEMIC 越权 + 3 个矩阵端点缺失/路径漂移) + 补充 10+ 端点登记。

## ADDED Requirements

### Requirement: 权限矩阵 v4.1 机器可读 YAML

系统 MUST 提供权限矩阵的机器可读 YAML 格式, 同时保留人类可读 Markdown 版本, 两者 MUST 保持一致。

#### Scenario: docs/permission-matrix-v4.1.yaml 创建
- WHEN 本变更完成
- THEN 创建 `docs/permission-matrix-v4.1.yaml`
- AND 包含用户管理域全部 30+ 端点 × 4 角色矩阵
- AND 与 `docs/权限矩阵.md` v4.1 人类可读版保持一致

### Requirement: ACADEMIC 状态变更权限收窄

`PUT /api/users/{id}/status` MUST 仅 ADMIN 可操作, ACADEMIC MUST NOT 可用。

#### Scenario: PUT /api/users/{id}/status 权限收窄
- WHEN 修复后
- THEN @PreAuthorize MUST 为 `hasRole('ADMIN')`
- AND ACADEMIC 调用 MUST 返回 403

### Requirement: 2 个矩阵端点补全

权限矩阵 v4.0 声明存在但代码缺失的 2 个端点 MUST 在本变更中补全。系统 MUST 实现这两个端点并保持与权限矩阵声明的角色权限一致, 任何不一致 MUST 在测试中失败。

#### Scenario: GET /api/departments/{id}/majors 端点补全
- WHEN 本变更完成
- THEN DepartmentController MUST 新增此端点
- AND @PreAuthorize 为 `isAuthenticated()` (4 角色都可读)
- AND 返回 List<MajorVO>

#### Scenario: GET /api/majors/{id}/classes 端点补全
- WHEN 本变更完成
- THEN MajorController MUST 新增此端点
- AND @PreAuthorize 为 `isAuthenticated()` (4 角色都可读)
- AND 返回 List<ClassVO>

### Requirement: 学习进度路径同步

`/api/users/{id}/learning-progress` 路径 MUST 与实际代码一致, 保留路由别名或更新矩阵。

#### Scenario: 学习进度端点路径同步
- WHEN 修复后
- THEN 矩阵 MUST 标注实际路径为 `/api/learning-progress/progress?userId=&courseId=`
- AND 保留 `/api/users/{id}/learning-progress` 作为路由别名 (向后兼容)

### Requirement: 10+ 端点补充登记

权限矩阵 MUST 补充以下 10+ 端点 (代码有, 矩阵 v4.0 遗漏):

#### Scenario: 认证端点补充
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 补充: POST /api/auth/registration-status, register, cas (3 端点)

#### Scenario: 用户管理端点补充
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 补充: PUT /api/users/{id}/teacher-status, POST /api/users/batch, POST /api/users/{id}/avatar, GET /api/users/{id}/public-profile (4 端点)

#### Scenario: 院系/班级端点补充
- WHEN 权限矩阵 v4.1 发布
- THEN MUST 补充: GET /api/departments/{id}/stats, GET /api/classes/{id}/students (2 端点)

### Requirement: UserPermissionTest 自动校验

CI MUST 自动验证权限矩阵与代码实现的一致性。

#### Scenario: CI 运行 UserPermissionTest
- WHEN CI 跑 `bash scripts/check-permission.sh`
- THEN UserPermissionTest 加载权限矩阵 v4.1 YAML 为预期表
- AND 反射扫描用户管理域 Controller @PreAuthorize 为实际表
- AND 断言两张表 0 差异