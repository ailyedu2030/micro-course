# 状态机设计 v1.2 → v1.3 (business-logic)

## Purpose

补全 2 项 P1-C 状态机守卫缺陷 + 180 天自动清理约定。

## MODIFIED Requirements

### Requirement: 用户状态机守卫补全

`docs/状态机设计.md` §1.2-1.3 MUST 补全 2 项 P1-C 守卫。

#### Scenario: 禁用 INACTIVE→DELETED 超额转换
- WHEN 状态机设计 v1.3 发布
- THEN §1.3 MUST 标注 INACTIVE→DELETED 为禁止转换
- AND UserStatus.canTransitionTo() MUST 移除此转换 (返回 false)
- AND 测试 MUST 验证此转换为禁止

#### Scenario: INACTIVE→ACTIVE 激活守卫详细定义
- WHEN 状态机设计 v1.3 发布
- THEN §1.3 T1 MUST 定义激活守卫三选一条件:
  - emailVerified=true (邮箱验证链接)
  - casBound=true (CAS 首次登录绑定)
  - adminForceActivate=true (管理员强制激活, 需写 OperationLog)
- AND 三个条件 MUST 至少满足一个, 否则抛出 USER_NOT_ACTIVE_VERIFIED

### Requirement: DELETED→[*] 180 天自动物理删除约定

本节定义用户管理域的关键规范要求。系统 MUST 实现上述场景, 不得偏离。所有变更 MUST 同步更新对应文档与测试。

#### Scenario: 自动清理 @Scheduled Job 实现
- WHEN 状态机设计 v1.3 发布
- THEN §1.3 MUST 新增 T7: DELETED→[*] 180 天自动物理删除约定
- AND MUST 实现 UserRetentionCleanupJob @Scheduled(cron="0 2 * * * ?")
- AND 该 Job MUST 查询 deleted_at < now() - 180 天的用户, 物理删除 (DELETE FROM users WHERE ...)
- AND 物理删除 MUST 写 OperationLog (含操作人 "SYSTEM_CRON")

### Requirement: UserStatus 转换矩阵完整定义

本节定义用户管理域的关键规范要求。系统 MUST 实现上述场景, 不得偏离。所有变更 MUST 同步更新对应文档与测试。

#### Scenario: 4×4 转换穷举
- WHEN 状态机设计 v1.3 发布
- THEN §1.2 MUST 定义完整 16 转换:
  - INACTIVE→ACTIVE (激活守卫), DELETED (禁止, 整改后)
  - ACTIVE→DISABLED, DELETED
  - DISABLED→ACTIVE, DELETED
  - DELETED→ACTIVE (180天守卫)
  - 其他 9 项 MUST 标记为禁止