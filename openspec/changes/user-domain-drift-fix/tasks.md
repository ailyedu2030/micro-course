# Tasks: 用户管理域 Spec 漂移全量修复

> **OpenSpec Change**: `user-domain-drift-fix`
> **Schema**: spec-driven
> **总任务数**: 41
> **优先级**: 老板指令"全量修复 P0-P3, 不留技术债务"

---

## 阶段 1: P1-C 必修 (12 项)

- [x] **1.1 UserController.updateStatus() 收窄为 ADMIN only**
  - 位置: UserController.java @PreAuthorize
  - 改为: `hasRole('ADMIN')` (移除 ACADEMIC)
  - 验证: ACADEMIC 调用 → 403

- [ ] **1.2 UserStatus.canTransitionTo() 移除 INACTIVE→DELETED**
  - 位置: UserStatus.java case INACTIVE
  - 删除: `target == DELETED` 转换
  - 验证: UserStatusTest L40 调整

- [x] **1.3 UserStatusServiceImpl 加 INACTIVE→ACTIVE 激活守卫**
  - 位置: UserStatusServiceImpl.updateStatus() ACTIVE 分支
  - 加: 检查 emailVerified || casBound || adminForceActivate

- [ ] **1.4 R.java 删除 timestamp (已存在, 验证一致性)**
  - 位置: R.java
  - 验证: API 契约-Phase1 v2.2 同步删除所有响应示例 timestamp

- [ ] **1.5 分页参数统一为 0-based**
  - 位置: 4 个 list Controller (departments/majors/classes/users)
  - 验证: page 默认 0, size 最大 100 (而非 10000)

- [ ] **1.6 OLD_PASSWORD_INCORRECT 错误码修正**
  - 位置: AuthServiceImpl.changePassword()
  - 契约: HTTP 400, code 1007
  - 验证: 契约-Phase1 v2.2 同步

- [ ] **1.7 CLASS_HAS_STUDENTS 错误码修正**
  - 位置: ClassServiceImpl.delete()
  - 契约: HTTP 409, code 4002

- [ ] **1.8 UpdateProfileRequest 加 avatar 字段**
  - 位置: dto/UpdateProfileRequest.java
  - 加: avatar 字段 (可选, base64 或 url)

- [ ] **1.9 GET /api/departments/{id}/majors 端点补全**
  - 位置: DepartmentController.java 新方法
  - 权限: hasAnyRole('STUDENT','TEACHER','ADMIN','ACADEMIC')

- [ ] **1.10 GET /api/majors/{id}/classes 端点补全**
  - 位置: MajorController.java 新方法
  - 权限: 同上

- [ ] **1.11 /api/users/{id}/learning-progress 路径同步**
  - 位置: 矩阵 v4.1 更新路径为实际 /api/learning-progress/progress

- [ ] **1.12 GET /api/users/{id} TEACHER 角色守卫收窄**
  - 位置: UserController.java @PreAuthorize
  - 改为: TEACHER 访问他人需 isCourseOwner

---

## 阶段 2: UserStatusStateMachine 重构 (模式 2 治本)

- [ ] **2.1 创建 UserStatusStateMachine 接口**
- [ ] **2.2 实现 UserStatusStateMachineImpl**
- [ ] **2.3 创建 UserStatusStateMachineConfig 注册守卫 (激活守卫等)**
- [ ] **2.4 重构 UserStatusServiceImpl.updateStatus() 委托状态机**
- [ ] **2.5 重构 UserServiceImpl 状态变更字段引用**
- [ ] **2.6 写 UserStatusStateMachineExhaustiveTest (16 转换穷举)**

---

## 阶段 3: 14 处状态硬编码改枚举 (模式 2 治本)

- [ ] **3.1 UserServiceImpl.java 3 处硬编码改枚举** (L149/210/368-374)
- [ ] **3.2 UserStatusServiceImpl.java 3 处硬编码改枚举** (L106/139/147)
- [ ] **3.3 AuthServiceImpl.java 7 处硬编码改枚举** (L118/190-198/294/462/477-484)
- [ ] **3.4 UserBatchImportServiceImpl.java 1 处** (L221)
- [ ] **3.5 UserQueryServiceImpl.java 2 处** (L304-309)

---

## 阶段 4: Controller 业务逻辑下沉 (模式 3 治本)

- [ ] **4.1 UserController.verifyExcelMagic 下沉到 Service**
- [ ] **4.2 4 处 list 端点 isAuthenticated 收窄为具体角色**

---

## 阶段 5: OpenAPI 集成 (模式 1 治本)

- [ ] **5.1 5 个 Controller 加 @Tag**
- [ ] **5.2 关键 25 端点加 @Operation**
- [ ] **5.3 复用 openapi-gen.sh 增量生成 docs/api/openapi.yaml**

---

## 阶段 6: 数据字典 + 权限矩阵同步 (模式 1 治本)

- [ ] **6.1 数据字典 v1.1→v1.2 修复 17 项漂移**
- [ ] **6.2 权限矩阵 v4.0→v4.1 修复 4 项 + 补充 10+ 端点**
- [ ] **6.3 复用 check-permission.sh 校验**

---

## 阶段 7: 5 份 spec 文档同步

- [ ] **7.1 数据字典 v1.2 更新日志**
- [ ] **7.2 API 契约-Phase1 v2.2 (9 新端点 + 8 新错误码 + 12 项 P1-C)**
- [ ] **7.3 权限矩阵 v4.1**
- [ ] **7.4 状态机设计 v1.3 (2 项 P1-C + 自动清理约定)**
- [ ] **7.5 开发规范 v1.6 (§3.4.7 全局响应契约)**

---

## 阶段 8: 测试设计与执行 (110+ TC)

- [ ] **8.1 写 user-test-units.md (110+ TC 设计)**
- [ ] **8.2 执行 TC-001 ~ TC-030 (User CRUD)**
- [ ] **8.3 执行 TC-031 ~ TC-060 (认证)**
- [ ] **8.4 执行 TC-061 ~ TC-080 (院系/专业/班级)**
- [ ] **8.5 执行 TC-081 ~ TC-110 (状态机/权限/异常)**
- [ ] **8.6 全部 PASS 后 commit, 进入下一域 (选课)**

---

## 进度追踪

```
阶段 1: 1.1⬜ 1.2⬜ 1.3⬜ 1.4⬜ 1.5⬜ 1.6⬜ 1.7⬜ 1.8⬜ 1.9⬜ 1.10⬜ 1.11⬜ 1.12⬜
阶段 2: 2.1⬜ 2.2⬜ 2.3⬜ 2.4⬜ 2.5⬜ 2.6⬜
阶段 3: 3.1⬜ 3.2⬜ 3.3⬜ 3.4⬜ 3.5⬜
阶段 4: 4.1⬜ 4.2⬜
阶段 5: 5.1⬜ 5.2⬜ 5.3⬜
阶段 6: 6.1⬜ 6.2⬜ 6.3⬜
阶段 7: 7.1⬜ 7.2⬜ 7.3⬜ 7.4⬜ 7.5⬜
阶段 8: 8.1⬜ 8.2⬜ 8.3⬜ 8.4⬜ 8.5⬜ 8.6⬜

**总任务数**: 41
**已完成**: 0
**下一步**: 用户批准后 /opsx-apply 开始执行
```