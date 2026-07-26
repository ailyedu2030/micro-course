## ADDED Requirements

### Requirement: 权限矩阵必须真实反映注解
系统 SHALL 在 `docs/权限矩阵.md` 中列出所有 Controller 方法的 `@PreAuthorize` 注解,确保与代码 100% 一致。Agent 审计新发现的权限注解缺失/错配 MUST 立即修复 + 更新权限矩阵。

#### Scenario: 权限注解缺失
- **WHEN** Controller 方法实际有权限要求但缺 `@PreAuthorize` 注解
- **THEN** Agent MUST 补齐注解 + 在权限矩阵登记

#### Scenario: 权限矩阵更新
- **WHEN** Agent 修改了任一 Controller 的权限注解
- **THEN** `docs/权限矩阵.md` MUST 同步更新,且 scope = "docs"

### Requirement: 角色权限必须最小化
系统 MUST 遵循"最小权限原则",任何角色获得的权限 MUST 是业务必需,不允许"为图方便给 ADMIN 全权限"。

#### Scenario: 越权检测
- **WHEN** 业务上教务不需要的功能被错误赋予 ACADEMIC 角色
- **THEN** Agent MUST 移除该权限并验证业务仍能跑通

#### Scenario: 学生角色范围
- **WHEN** 任一接口对 STUDENT 角色开放
- **THEN** MUST 确认接口设计是给学生用的,否则移除 STUDENT 角色
