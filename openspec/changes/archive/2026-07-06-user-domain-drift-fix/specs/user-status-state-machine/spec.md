# 用户管理域 · UserStatusStateMachine 统一入口 (user-status-state-machine)

## Purpose

消除用户管理域状态机守卫碎片化 (模式 2), 引入 `UserStatusStateMachine` 作为用户状态变更的**唯二入口**, 修复 12 项 P1-C 必修 (含 INACTIVE→DELETED 超额转换 + INACTIVE→ACTIVE 缺激活守卫) + 14 处状态字段硬编码。

## ADDED Requirements

### Requirement: UserStatusStateMachine 单一入口

系统 MUST 提供 `UserStatusStateMachine` 接口作为用户状态变更的**唯二入口**。所有用户状态变更 MUST 通过 `UserStatusStateMachine.transition(userId, targetStatus, actor, context)` 调用。直接调用 `userRepository.update(... WHERE status = X)` MUST 删除或仅限状态机内部使用。

#### Scenario: 状态变更调用统一入口
- WHEN UserStatusServiceImpl.updateStatus() / UserServiceImpl.updateUser() 涉及状态变更被调用
- THEN 必须委托给 UserStatusStateMachine.transition()
- AND 不得直接使用 userRepository.update() 修改 status 字段

#### Scenario: UserStatus.canTransitionTo 白名单收紧
- WHEN 任何角色调用 INACTIVE(0) → DELETED(3) 状态变更
- THEN MUST 抛出 BusinessException(USER_STATUS_TRANSITION_NOT_ALLOWED, "INACTIVE 不可直接 DELETED")
- AND 数据库 status 字段保持 INACTIVE

#### Scenario: UserStatus 转换白名单完整 (与设计一致)
- WHEN canTransitionTo() 被调用
- THEN 4 状态 × 4 目标 = 16 转换穷举
- AND 允许: INACTIVE→ACTIVE, ACTIVE→DISABLED/DELETED, DISABLED→ACTIVE/DELETED, DELETED→ACTIVE (180天守卫)
- AND 禁止: 上述之外的所有转换

### Requirement: INACTIVE→ACTIVE 激活守卫

UserStatusStateMachine MUST 在 INACTIVE→ACTIVE 转换前执行激活验证守卫。

#### Scenario: 普通注册用户首次激活
- WHEN 用户通过邮箱验证链接点击激活 (context.emailVerified=true)
- THEN 状态变更 INACTIVE→ACTIVE 成功
- AND 写入 lastLoginAt 字段

#### Scenario: CAS 首次登录自动激活
- WHEN 用户通过 CAS 登录且 casBound=true (context.casBound=true)
- THEN 状态变更 INACTIVE→ACTIVE 成功
- AND 绑定 CAS 用户名

#### Scenario: 未验证用户尝试激活
- WHEN 管理员调用 PUT /api/users/{id}/status 但未传 adminForceActivate=true
- AND context.emailVerified=false && context.casBound=false
- THEN MUST 抛出 BusinessException(USER_NOT_ACTIVE_VERIFIED, "INACTIVE 用户需邮箱验证或 CAS 绑定后才能激活")

#### Scenario: 管理员强制激活
- WHEN 管理员调用 PUT /api/users/{id}/status 且 context.adminForceActivate=true
- THEN 状态变更 INACTIVE→ACTIVE 成功 (跳过激活守卫)
- AND 写 OperationLog (含操作人)

### Requirement: UserStatusStateMachineExhaustiveTest 16 转换穷举

测试套件 MUST 覆盖所有 4×4=16 个用户状态转换。

#### Scenario: 16 转换穷举测试
- WHEN UserStatusStateMachineExhaustiveTest 跑测试
- THEN 覆盖所有 4 状态 × 4 目标 = 16 个转换
- AND 每个转换验证: canTransitionTo / 业务守卫 (激活/180天) / 乐观锁

### Requirement: UserStatus 枚举迁移 14 处硬编码

系统 MUST 将所有 Service 文件中 UserStatus 状态字段硬编码 (Integer 字面量) 替换为 UserStatus 枚举引用。

#### Scenario: UserServiceImpl 状态字段改枚举
- WHEN UserServiceImpl.updateUser() / createUser() 设置用户状态
- THEN MUST 使用 `UserStatus.ACTIVE.getCode()` 而非 `1`
- AND MUST 使用 `UserStatus.fromCode(request.getStatus())` 转换请求字段

#### Scenario: AuthServiceImpl 状态字段改枚举
- WHEN AuthServiceImpl.register() / login() / refresh() / casLogin() 设置或校验状态
- THEN MUST 使用 `UserStatus.ACTIVE.getCode()` / `UserStatus.DISABLED.getCode()` / `UserStatus.DELETED.getCode()` 而非数字字面量
- AND MUST 使用 `UserStatus.fromCode()` 转换 User.status 字段

#### Scenario: UserBatchImportServiceImpl / UserQueryServiceImpl 状态字段改枚举
- WHEN 批量导入设置状态 或 查询状态转 statusText
- THEN MUST 使用 UserStatus 枚举

## MODIFIED Requirements

### Requirement: 状态机设计 v1.2→v1.3

`docs/状态机设计.md` MUST 更新至 v1.3:
- §1.3 删除 INACTIVE→DELETED 转换 (标记为禁止)
- §1.3 T1 新增 INACTIVE→ACTIVE 激活守卫详细定义
- §1.3 T7 新增 DELETED→[*] 180天自动物理删除约定 (UserRetentionCleanupJob @Scheduled)

#### Scenario: 状态机设计文档同步
- WHEN 本变更的阶段 7 完成时
- THEN docs/状态机设计.md v1.2→v1.3
- AND 包含 INACTIVE→DELETED 禁止标记
- AND 包含激活守卫详细定义
- AND 包含 180天自动清理约定