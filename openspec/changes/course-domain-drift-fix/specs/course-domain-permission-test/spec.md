# 课程管理域 · 权限矩阵可执行化 (course-domain-permission-test)

## Purpose

消除权限与校验层杂交 (RCA 模式 3 子项), 将权限矩阵从静态 Markdown 升级为机器可读 + CI 门禁, 修复 11 项权限漂移。

## ADDED Requirements

### Requirement: 权限矩阵 v4.0 YAML 格式

系统 MUST 提供权限矩阵的机器可读 YAML 格式, 同时保留人类可读 Markdown 版本, 两者 MUST 保持一致。

#### Scenario: YAML 格式权限矩阵
- WHEN 权限矩阵 v4.0 发布
- THEN 创建 `docs/permission-matrix-v4.0.yaml`
- AND 每个端点包含 path/method/roles/source 字段
- AND 与 `docs/权限矩阵.md` v4.0 人类可读版保持一致

### Requirement: EndpointPermissionTest 自动校验

CI MUST 自动验证权限矩阵与代码实现的一致性, 任何漂移 MUST 阻止合并。

#### Scenario: CI 运行 EndpointPermissionTest
- WHEN CI 跑 `bash scripts/check-permission.sh`
- THEN EndpointPermissionTest 加载权限矩阵 v4.0 YAML 为预期表
- AND 反射扫描所有 @RestController 的 @PreAuthorize 为实际表
- AND 断言两张表 0 差异

#### Scenario: 缺失端点检测
- WHEN 控制器有 @RequestMapping 但权限矩阵未登记
- THEN EndpointPermissionTest 失败并指出端点路径

#### Scenario: 角色漂移检测
- WHEN @PreAuthorize 与权限矩阵 YAML 不一致
- THEN 失败并指出端点路径与差异角色

### Requirement: 11 项权限漂移修复

本变更 MUST 修复全部 11 项权限漂移, 不留技术债。

#### Scenario: submit 端点仅 TEACHER
- WHEN 修复后 `POST /api/courses/{id}/submit` 端点
- THEN @PreAuthorize 为 `hasRole('TEACHER')` (移除 ADMIN)

#### Scenario: 收藏端点路径统一 + 仅 STUDENT
- WHEN 修复后收藏端点
- THEN 路径统一为 `/api/courses/{id}/favorite`
- AND @PreAuthorize 为 `hasRole('STUDENT')`

#### Scenario: 5 端点补全或废弃确认
- WHEN 修复后, 5 个权限矩阵声明存在但代码不存在的端点
- THEN 4 个端点必须实现: GET /api/courses/teacher/{teacherId}, POST /api/videos/{id}/retry, GET /api/videos/{id}/analytics, POST /api/videos/batch-upload
- AND 若决定废弃, 权限矩阵 v4.0 移除这些端点

#### Scenario: reviews 端点权限收紧
- WHEN 修复后
- THEN POST /api/courses/{id}/reviews 仅 STUDENT (移除 ADMIN)
- AND DELETE /api/courses/{id}/reviews/{reviewId} 仅 ADMIN (移除 ACADEMIC)

### Requirement: 30 端点补充登记

权限矩阵 v2.0 MUST 补充 30 端点, 完整覆盖课程管理域全部上线端点。补充范围 MUST 涵盖课程分类管理 (5) + 课时管理 (6) + 课件/幻灯片 (11) + 定价系列 (5) + 批量审核 (2) + 状态/复制/封面 (3)。

#### Scenario: 课程分类管理 5 端点登记
- WHEN 权限矩阵 v4.0 发布
- THEN 课程分类管理 5 端点 MUST 全部登记

#### Scenario: 课时/课件/定价/批量端点登记
- WHEN 权限矩阵 v4.0 发布
- THEN 课时 (6) + 课件 (11) + 定价 (5) + 批量 (2) MUST 全部登记

### Requirement: Owner 校验下沉约定文档化

权限矩阵 MUST 显式记录 Controller @PreAuthorize 与 Service isOwnerOrAdmin 的分层约定, 避免 reviewer 误判。

#### Scenario: 权限矩阵 §5 实施建议补全
- WHEN 权限矩阵 v4.0 发布
- THEN §5 MUST 明确记录:
  - @PreAuthorize 仅做"角色门禁"粗粒度
  - 对象级 Owner 校验下沉到 Service 层 (SecurityUtil.isOwnerOrAdmin)
  - 任何"创建者本人"约束都意味着 Service 层有 isOwnerOrAdmin 校验