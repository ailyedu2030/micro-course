# 用户管理域 · Spec 漂移全量修复 (User Domain Drift Full Fix)

> **OpenSpec Change**: `user-domain-drift-fix`
> **Schema**: spec-driven
> **创建日期**: 2026-07-07
> **关联扫描报告**: `memories/scratchpad/user-domain-spec-drift.md`

---

## Why

**问题陈述**: 用户管理域 (User/Auth/Department/Major/Class/Teacher 审核) 通过 6 个并列扫描任务发现 **86 项 spec 漂移**:
- 0 项 P0
- 12 项 P1-C 必修 (含权限越权 + 状态机守卫缺失 + 全局响应不一致)
- 65 项 P1-I (主要是文档缺失 + 端点缺失)
- 9 项 P2 优化项

**根因模式**: 与课程管理域高度相似, 复用 + 新增 1 个:

| 根因模式 | 覆盖项 | 含 P1-C |
|---------|--------|--------|
| **模式 1**: Spec-Code 双向熵增 | 42 (49%) | 6 |
| **模式 2**: 状态机守卫碎片化 (复用) | 17 (20%) | 2 |
| **模式 3**: 权限与校验层杂交 (复用) | 12 (14%) | 4 |
| **模式 4 (新)**: 全局响应契约漂移 | 跨所有用户域端点 | — |
| **合计** | **71/86** (83%) | **12** |

残余 15 项 (17%) 为个别问题 (例如 V130→V159 编号错误, counselorId 字段已删仍登记)。

**为什么必须现在修**:
1. **P1-C 必修**: ACADEMIC 越权 + INACTIVE 超额转换 + 路径漂移, 涉及用户管理核心安全
2. **业务路径**: 教师审核流 (teacher-status) 完全没有契约文档, 后续维护和扩展困难
3. **可执行化**: 课程管理域已建立 OpenSpec + precheck-extra + EndpointPermissionTest 工具链, 用户管理域可直接复用
4. **老板指令**: "全量修复 P0-P3, 不能留下技术债务"

---

## What Changes

### 新增能力

- **CAP-1 UserStatusStateMachine**: 复用 CourseStateMachine 模式, 新建 UserStatusStateMachine 统一入口 (含 canTransitionTo + 守卫 hook + 乐观锁)
- **CAP-2 全局响应契约统一**: 删除/补齐 R.java timestamp + 统一分页参数 (0-based, 默认 0)
- **CAP-3 用户管理域 OpenAPI 集成**: 复用 CourseController 模式, 给 UserController/AuthController/DepartmentController/MajorController/ClassController 加 @Tag/@Operation

### 修改能力

- **CAP-4 (修改) UserController.updateStatus()**: ACADEMIC 越权收窄为 ADMIN only
- **CAP-5 (修改) UserStatus.canTransitionTo()**: 移除 INACTIVE→DELETED 超额转换
- **CAP-6 (修改) UserStatusServiceImpl**: 加 INACTIVE→ACTIVE 激活守卫, 14 处状态字段硬编码改用 UserStatus 枚举
- **CAP-7 (修改) AuthServiceImpl**: 7 处状态字段硬编码改用 UserStatus 枚举
- **CAP-8 (修改) UserServiceImpl / UserBatchImportServiceImpl / UserQueryServiceImpl**: 状态字段硬编码改用枚举
- **CAP-9 (修改) 数据字典**: 同步 17 项漂移 (CHECK/索引/V159/counselorId 已删)
- **CAP-10 (修改) API 契约 - Phase1**: 同步 12 项 P1-C + 8 个新错误码 + 9 个新端点
- **CAP-11 (修改) 权限矩阵 v2.0→v4.1**: 修复 4 项 P1-C (ACADEMIC 越权/端点缺失/路径漂移) + 补充 10+ 端点
- **CAP-12 (修改) 状态机设计 v1.2→v1.3**: 补全 INACTIVE→DELETED 禁止约定 + INACTIVE→ACTIVE 激活守卫约定 + 180天自动清理实现
- **CAP-13 (修改) 开发规范 v1.5→v1.6**: 新增 1 条禁止项 (§3.4.7 全局响应契约不可漂移)

### 新增 Capabilities (OpenSpec specs)

- `user-status-state-machine`: UserStatusStateMachine 统一入口
- `user-permission-test`: 权限矩阵 v4.1 一致性测试
- `user-contract-auto-gen`: 用户管理域 OpenAPI 自动生成
- `response-contract`: 全局响应契约 (R.java + 分页)

### Modified Capabilities

- `data-contract`: 数据字典 v1.1→v1.2 (用户管理域 17 项漂移)
- `api-contract`: API 契约 v2.1→v2.2 (用户管理域 12 项 P1-C + 10 个新端点)
- `permission-matrix`: 权限矩阵 v4.0→v4.1 (4 项 P1-C + 10+ 端点)
- `business-logic`: 状态机设计 v1.2→v1.3 (2 项 P1-C + 自动清理约定)
- `dev-standards`: 开发规范 v1.5→v1.6 (§3.4.7 全局响应契约不可漂移)

---

## Impact

### 跨域影响

| 域 | 影响范围 |
|----|---------|
| 后端 Java | UserController, AuthController, DepartmentController, MajorController, ClassController, UserServiceImpl, UserStatusServiceImpl, AuthServiceImpl, UserBatchImportServiceImpl, UserQueryServiceImpl, UserStatus 枚举, R.java (全局) |
| 前端 Vue | UserList.vue, UserForm.vue, DepartmentList.vue, MajorList.vue, ClassList.vue, Login.vue (分页参数) |
| 数据库 | 无新增迁移, 同步数据字典 |
| 中间件 | 无 |
| 测试 | 新增 UserStatusStateMachineExhaustiveTest, UserPermissionTest, AuthServiceIntegrationTest, UserBatchImportTest |
| 工具链 | 复用 precheck-extra.sh + openapi-gen.sh (新增 user-domain 标记) |
| CI/CD | 复用现有 GitHub Actions |

### 数据影响

- ✅ 本机测试库 (用于回归测试)
- ❌ 生产数据库 / 生产服务器 (不动)

### 风险

| 风险 | 等级 | 缓解 |
|------|:----:|------|
| 14 处 UserStatus 硬编码改枚举, 影响范围大 | 中 | 编译时类型检查, 全量测试覆盖 |
| ACADEMIC 越权收窄可能影响现有教务流程 | 中 | 通知教务处, 配套培训 |
| 状态机 INACTIVE→DELETED 移除可能影响未激活账号清理 | 低 | 用 scheduled job 替代 |
| 全局 R 响应删 timestamp / 分页 0-based | 高 | 前端需同步适配, 灰度发布 |

---

## 执行策略 (基于 RCA 模式修复)

### 阶段 1: P1-C 必修 (12 项)

| # | 任务 | RCA 模式 | 阻塞性 |
|---|------|---------|--------|
| 1 | UserController.updateStatus() 收窄为 ADMIN only | 模式 3 | 用户管理安全 |
| 2 | UserStatus.canTransitionTo() 移除 INACTIVE→DELETED | 模式 2 | 状态机一致性 |
| 3 | UserStatusServiceImpl 加 INACTIVE→ACTIVE 激活守卫 | 模式 2 | 注册流程安全 |
| 4 | R.java 删除 timestamp (已完成, 仅契约同步) | 模式 4 | 全局一致性 |
| 5 | 分页参数统一为 0-based | 模式 4 | 全局一致性 |
| 6 | OLD_PASSWORD_INCORRECT 错误码修正 400/1007 | 模式 1 | 错误处理 |
| 7 | CLASS_HAS_STUDENTS 错误码修正 4002 | 模式 1 | 错误处理 |
| 8 | UpdateProfileRequest 加 avatar 字段 | 模式 1 | 业务完整性 |
| 9 | GET /api/departments/{id}/majors 端点补全 | 模式 3 | 数据可访问性 |
| 10 | GET /api/majors/{id}/classes 端点补全 | 模式 3 | 数据可访问性 |
| 11 | /api/users/{id}/learning-progress 路径同步 | 模式 3 | URL 一致性 |
| 12 | GET /api/users/{id} TEACHER 角色守卫收窄 | 模式 3 | 数据隔离 |

**前置**: 复用 course-domain-drift-fix 的工具链
**后置**: ExhaustiveStateMachine + UserStatusMachine 全过

### 阶段 2: UserStatusStateMachine 重构 (模式 2 治本)

| # | 任务 | 包含 |
|---|------|------|
| 13 | 创建 UserStatusStateMachine 接口 | 复用 CourseStateMachine 模式 |
| 14 | 实现 UserStatusStateMachineImpl | 含守卫 hook |
| 15 | UserStatusStateMachineConfig 注册守卫 | INACTIVE→ACTIVE 激活守卫等 |
| 16 | 重构 UserStatusServiceImpl.updateStatus() 委托状态机 | 替换硬编码 |
| 17 | 重构 UserServiceImpl 状态变更 | 替换硬编码 |
| 18 | 写 UserStatusStateMachineExhaustiveTest | 4×4=16 转换穷举 |

### 阶段 3: 14 处状态硬编码改枚举 (模式 2 治本)

| # | 任务 | 包含 |
|---|------|------|
| 19 | UserServiceImpl.java 3 处硬编码改枚举 | L149/210/368-374 |
| 20 | UserStatusServiceImpl.java 3 处硬编码改枚举 | L106/139/147 |
| 21 | AuthServiceImpl.java 7 处硬编码改枚举 | L118/190-198/294/462/477-484 |
| 22 | UserBatchImportServiceImpl.java 1 处 | L221 |
| 23 | UserQueryServiceImpl.java 2 处 | L304-309 |

### 阶段 4: Controller 业务逻辑下沉 (模式 3 治本)

| # | 任务 | 包含 |
|---|------|------|
| 24 | UserController.verifyExcelMagic 下沉到 Service | L157/189-206 |
| 25 | (其他 4 处 isAuthenticated 收窄) | UserController/Department/Major/Class 列表查询 |

### 阶段 5: OpenAPI 集成 (模式 1 治本)

| # | 任务 | 包含 |
|---|------|------|
| 26 | 5 个 Controller 加 @Tag | UserController/AuthController/Dept/Major/Class |
| 27 | 关键端点加 @Operation | 至少 25 端点 |
| 28 | 复用 openapi-gen.sh | 增量生成 docs/api/openapi.yaml |

### 阶段 6: 数据字典 + 权限矩阵同步 (模式 1 治本)

| # | 任务 | 包含 |
|---|------|------|
| 29 | 数据字典 v1.1→v1.2 | 17 项漂移修复 (CHECK/索引/V159/counselorId) |
| 30 | 权限矩阵 v4.0→v4.1 | 修复 4 项 P1-C + 补充 10+ 端点 |
| 31 | 复用 check-permission.sh | 自动验证 |

### 阶段 7: 文档同步 (模式 1 治本)

| # | 任务 | 包含 |
|---|------|------|
| 32 | 数据字典 v1.2 更新日志 | 17 项漂移登记 |
| 33 | API 契约-Phase1 v2.2 | 9 个新端点 + 8 个新错误码 + 12 项 P1-C |
| 34 | 权限矩阵 v4.1 | 4 项 P1-C + 10+ 端点 |
| 35 | 状态机设计 v1.3 | 2 项 P1-C + 自动清理约定 |
| 36 | 开发规范 v1.6 | §3.4.7 全局响应契约 |

### 阶段 8: 测试设计与执行 (110+ TC)

| # | 任务 | 包含 |
|---|------|------|
| 37 | 写 user-test-units.md | 110+ TC 设计 |
| 38 | 执行 TC-001 ~ TC-030 (User CRUD) | |
| 39 | 执行 TC-031 ~ TC-060 (认证) | |
| 40 | 执行 TC-061 ~ TC-080 (院系/专业/班级) | |
| 41 | 执行 TC-081 ~ TC-110 (状态机/权限) | |

---

## 前置条件

1. ✅ 已读 course-domain-drift-fix 全部 artifacts (复用工具链)
2. ✅ 已读项目宪法 .claude/skills/microcourse/SKILL.md
3. ✅ 已修 OpenSpec CLI (simdutf)
4. ✅ 已确认 R.java timestamp 已删除 (v1.5)

## 引用

- 扫描报告: `memories/scratchpad/user-domain-spec-drift.md` (86 项漂移)
- 上一阶段: `openspec/changes/archive/2026-07-06-course-domain-drift-fix/` (治本模式参考)
- 治本工具链:
  - `scripts/openapi-gen.sh` (OpenAPI 自动生成)
  - `scripts/db-schema-doc-gen.sh` (数据字典反向生成)
  - `scripts/check-permission.sh` (权限矩阵校验)
  - `scripts/precheck-extra.sh` (5 条防御规则)

---

**总任务数**: 41
**估计工时**: 5-6 天
**优先级**: 老板指令"全量修复 P0-P3, 不留技术债务"