# 用户管理域 · Spec 漂移全量修复 · 设计文档

> **OpenSpec Change**: `user-domain-drift-fix`
> **设计目标**: 治本而非治标, 修复 86 项漂移 + 消除 4 个根因模式产生条件

---

## 1. UserStatusStateMachine 统一入口 (模式 2 治本)

### 复用 CourseStateMachine 设计

复用 `openspec/changes/archive/2026-07-06-course-domain-drift-fix/design.md` §1 设计, 但针对 UserStatus:
- 接口: `UserStatusStateMachine.transition(userId, targetStatus, actor, context)`
- 守卫 hook: `registerGuard(from, to, BiFunction<User, Context, List<String>>)`
- 乐观锁: `WHERE status=current AND version=?`
- 自审批阻断 (不需要, 用户域无审批语义)

### 新增激活守卫

```java
sm.registerGuard(INACTIVE, ACTIVE, (user, ctx) -> {
    if (!ctx.getEmailVerified() && !ctx.getCasBound() && !ctx.getAdminForceActivate()) {
        return List.of("INACTIVE→ACTIVE 需要邮箱验证或 CAS 绑定或管理员强制激活");
    }
    return List.of();
});
```

## 2. 全局响应契约统一 (模式 4 治本)

### 2.1 R.java 规范 (已完成, 仅契约同步)

`R.java` 当前定义:
```java
public class R<T> {
    private int code;
    private String message;
    private T data;  // timestamp 已删除 (v1.5)
}
```

API 契约-Phase1 v2.2 需更新所有响应示例, 删除 timestamp。

### 2.2 分页参数统一

```java
// PageRequest.java (新建, 全局使用)
public class PageRequest {
    @Min(0) private int page = 0;       // 0-based
    @Min(1) @Max(100) private int size = 20;
}
```

### 2.3 全局错误码补充

```java
ErrorCode 中新增:
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
```

## 3. 权限矩阵 v4.1 同步

### 3.1 P1-C 修复
- `PUT /api/users/{id}/status`: `hasAnyRole('ADMIN','ACADEMIC')` → `hasRole('ADMIN')`
- `GET /api/departments/{id}/majors`: 新增 (返回 List<MajorVO>)
- `GET /api/majors/{id}/classes`: 新增 (返回 List<ClassVO>)
- `GET /api/users/{id}/learning-progress`: 路径别名保留

### 3.2 补充 10+ 端点
- POST /api/auth/registration-status, register, cas
- PUT /api/users/{id}/teacher-status
- POST /api/users/batch, {id}/avatar
- GET /api/users/{id}/public-profile
- GET /api/departments/{id}/stats
- GET /api/classes/{id}/students

## 4. 状态机设计 v1.3

### 4.1 P1-C 修复
- 移除 INACTIVE→DELETED 超额转换
- INACTIVE→ACTIVE 加激活守卫

### 4.2 新增约定
- DELETED→[*] 180天自动物理删除: 新增 `UserRetentionCleanupJob @Scheduled(cron="0 2 * * * ?")`

## 5. 数据字典 v1.2

### 5.1 users 表
- 补充 chk_users_status/role/gender (V153)
- 修正 uk_users_email 索引条件 (`AND email <> ''`)
- 补充 idx_users_username/student_no/email (V81)
- 补充 uk_users_teacher_no (V1)
- 登记 email 字段 updateStrategy=IGNORED
- 修正 role 字段类型为 UserRole enum

### 5.2 departments/majors/classes 表
- 补充 sortOrder NOT NULL DEFAULT 0
- 登记 FK ON DELETE 策略 (majors.departmentId RESTRICT, classes.majorId RESTRICT)
- 移除 classes.counselorId 字段登记 (V89 已删)

### 5.3 teacher_ratings/teacher_tier_log 表
- 补充 ratingScore/tier/totalStudents/totalCourses DEFAULT
- 补充 idx_teacher_ratings_tier 索引
- 修正 V130→V159 编号
- 补充 triggeredBy DEFAULT 'CRON'

## 6. API 契约-Phase1 v2.2

### 6.1 9 个新端点契约补全
- POST /api/auth/register: RegisterRequest {username*, password*, email?, phone?, realName?, studentNo?}
- PUT /api/users/{id}/teacher-status: TeacherStatusRequest {teacherStatus*, reason}
- POST /api/users/batch: MultipartFile (≤5MB, xlsx/xls)
- GET /api/users/{id}/public-profile: 公开字段 (realName, avatar, bio, role)
- 等

### 6.2 全局响应示例删除 timestamp
所有响应示例从 `{code, message, data, timestamp}` 改为 `{code, message, data}`

### 6.3 全局分页参数统一
所有分页端点统一 `page=0-based, size max=100`

### 6.4 错误码补全
8 个新错误码 + 2 个错误码修正

## 7. 开发规范 v1.6

新增 §3.4.7:
```
§3.4.7 全局响应契约不可漂移

所有 API 响应 MUST 遵守:
- 响应体: {code: int, message: String, data: T}
- 禁止: 添加额外字段 (如 timestamp) 不经评审
- 分页参数: page 从 0 开始, size 默认 20, 最大 100
- 错误码: 必须在 ErrorCode.java 定义并契约登记
```

## 8. 测试设计 (110+ TC)

### 8.1 User CRUD (30 TC)
- TC-001 ~ TC-008: 用户列表 (角色过滤/数据范围/分页)
- TC-009 ~ TC-013: 用户详情
- TC-014 ~ TC-019: 用户创建
- TC-020 ~ TC-024: 用户更新/删除
- TC-025 ~ TC-030: 状态变更

### 8.2 认证 (30 TC)
- TC-031 ~ TC-038: 登录/注册/登出
- TC-039 ~ TC-044: Token 刷新/CAS
- TC-045 ~ TC-050: 修改密码
- TC-051 ~ TC-060: 个人信息/头像

### 8.3 院系/专业/班级 (20 TC)
- TC-061 ~ TC-066: 院系 CRUD + majors 子资源
- TC-067 ~ TC-073: 专业 CRUD + classes 子资源
- TC-074 ~ TC-080:班级 CRUD + students 子资源

### 8.4 状态机 (16 TC)
- TC-081 ~ TC-085: canTransitionTo 16 转换穷举
- TC-086 ~ TC-090: UserStatus 守卫 (激活/180天)
- TC-091 ~ TC-096: 乐观锁 + 自审批阻断

### 8.5 权限 + 异常 (14 TC)
- TC-097 ~ TC-100: 4 个 P1-C 权限修复验证
- TC-101 ~ TC-110: 跨域/异常/边界

---

## 执行时间估算

| 阶段 | 内容 | 估计 |
|------|------|------|
| 1 | P1-C 必修 (12 项) | 1 天 |
| 2 | UserStatusStateMachine 重构 | 1 天 |
| 3 | 14 处状态硬编码改枚举 | 0.5 天 |
| 4 | Controller 业务逻辑下沉 | 0.5 天 |
| 5 | OpenAPI 集成 | 0.5 天 |
| 6 | 数据字典 + 权限矩阵 | 0.5 天 |
| 7 | 5 份 spec 文档同步 | 0.5 天 |
| 8 | 110+ TC 设计 + 执行 | 8 天 |
| **合计** | | **12.5 天** |

## 验收标准

- ✅ 全部 41 个任务完成
- ✅ P1-C 12 项必修全部修复 + 回归测试通过
- ✅ UserStatusStateMachineExhaustiveTest 16 转换穷举全过
- ✅ 14 处状态硬编码全部改用 UserStatus 枚举
- ✅ 全局响应契约 (R.java) 与 API 契约-Phase1 一致
- ✅ 5 份 spec 文档版本号 +1
- ✅ 110+ TC 全部 PASS

---

**设计完成**, 等用户批准进入 /opsx-apply 阶段