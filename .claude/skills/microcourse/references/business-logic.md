# 业务逻辑引用视图

> **源文档**：
> - [`docs/状态机设计.md`](../../状态机设计.md) v1.0（状态机）
> - [`docs/开发规范.md`](../../开发规范.md) v1.4 §3.3.1（响应/分页格式）
> - [`docs/冲突评审决议.md`](../../冲突评审决议.md)（字段裁决）
> **视图性质**：引用视图
> **冲突裁决**：以冲突评审决议为准

---

## 1. 用户生命周期状态机（最关键）

### 1.1 状态定义

| 状态值 | 名称 | 业务 |
|--------|------|------|
| 0 | INACTIVE | 未激活（邮箱注册但未验证） |
| 1 | ACTIVE | 正常（可登录和操作） |
| 2 | DISABLED | 禁用（被管理员或风控禁用，登录受限） |
| 3 | DELETED | 已删除（软删除，180 天保留） |

### 1.2 状态转换图

```
                    系统创建
[*] ──────────→ INACTIVE
                  │
                  │ 邮箱验证 / CAS 首次绑定 / 管理员激活
                  ↓
                ACTIVE ←──────────┐
                ↕  ↑              │ 管理员启用
                │  │              │
   管理员禁用   │  │              │
                ↓  │              │
              DISABLED ───────────┘
                │
                │ 超时 180 天
                ↓
              DELETED ──→ 物理删除（自动）
                ↑
                │ 管理员强制删除
                │
              ACTIVE
```

### 1.3 转换规则详解

| 转换 | 触发 | 动作 | 幂等性 |
|------|------|------|--------|
| INACTIVE → ACTIVE | 用户/管理员 | `status=1, cas_bound=true, last_login_at=NOW()` | 幂等 |
| ACTIVE → DISABLED | 管理员/系统 | `status=2, JWT 立即失效` | 幂等 |
| DISABLED → ACTIVE | 管理员 | `status=1, 恢复所有权限` | 幂等 |
| ACTIVE/DISABLED → DELETED | 用户/管理员 | `status=3, deleted_at=NOW()` | 幂等 |
| DELETED → ACTIVE | 管理员 | `status=1, deleted_at=NULL`（180 天内可恢复，S-05 修复：恢复目标从 INACTIVE(0) 修正为 ACTIVE(1)） | 不可超过 180 天 |
| **T6: DISABLED → DELETED** | **定时任务（180 天后管理员强制删除）** | `status=3, deleted_at=NOW()` | 不可逆（已超时） |

**端点映射**：`PUT /api/users/{id}/status` 接受 `status=0`（禁用）或 `status=1`（启用/恢复/DELETED→ACTIVE）。DELETED 用户恢复目标为 ACTIVE(1)，非 INACTIVE(0)。

---

## 2. 删除前置检查（5 处必须实现）

```
DELETE /api/departments/{id}
  → SELECT COUNT(*) FROM majors WHERE department_id = ? AND deleted_at IS NULL
  → COUNT > 0 → return code 2002 (HTTP 409)

DELETE /api/majors/{id}
  → SELECT COUNT(*) FROM classes WHERE major_id = ? AND deleted_at IS NULL
  → COUNT > 0 → return code 3002 (HTTP 409)

DELETE /api/classes/{id}
  → SELECT COUNT(*) FROM users WHERE class_id = ? AND deleted_at IS NULL AND status != 3
  → COUNT > 0 → return code 409 (HTTP 409)
```

**强制**：3 处删除前置，**Service 层实现**，Controller 调 Service 即可，**Controller 不写业务逻辑**。

---

## 3. 登录流程（5 步）

```
1. 校验登录失败次数（Redis key: mc:login:lock:{username}）
   → 失败次数 ≥ 5 → return code 1006（HTTP 423），账号锁定 30 分钟
2. 验证密码（bcrypt）
   → 失败 → return code 1001
3. 验证用户状态
   → INACTIVE → return code 1001 + "请先激活账号"
   → DISABLED → return code 1002
   → DELETED → return code 1003
4. 验证通过 → 生成 JWT（accessToken 2h + refreshToken 7d）
5. 更新 last_login_at + 记录 operation_logs
```

**JWT claims 必含字段**（依据：状态机设计 §1.3 T1）：

```json
{
  "sub": "<userId>",
  "username": "<loginName>",
  "role": "STUDENT|TEACHER|ADMIN|ACADEMIC",
  "departmentId": <所属院系ID，可选>,
  "iat": 1700000000,
  "exp": 1700007200
}
```

**特别注意**：缺 username / departmentId 字段 = JWT 实现不完整。

---

### 2.4 JWT Token 黑名单

**用途**：用户登出后或账户被禁用时，使已签发的 JWT Token 立即失效。

**Redis key 格式**：`mc:jwt:blacklist:{jti}`

| 属性 | 值 | 说明 |
|------|----|------|
| key | `mc:jwt:blacklist:{jti}` | jti = JWT ID（JWT Claims 中唯一标识） |
| value | `"1"` | 标记位 |
| TTL | 与 accessToken 有效期间一致（7200s） | Token 过期后自动清除 |

**触发场景**：
- 用户主动登出（POST /api/auth/logout）
- 管理员禁用/删除用户（PUT /api/users/{id}/status）

**校验流程**：JWT 拦截器在验证 Token 签名后，查询 `jwt:blacklist:{jti}`，若存在 → 拒绝请求（code 1005）

### 2.5 登录锁定

**用途**：防止暴力破解，同一用户连续登录失败 ≥ 5 次 → 账号锁定 30 分钟。

**Redis key 格式**：`mc:login:lock:{username}`

| 属性 | 值 |
|------|-----|
| key | `login:lock:{username}` |
| 检查时机 | 登录请求中，在验证密码之前 |
| 阈值 | 连续失败 ≥ 5 次 |
| 锁定时间 | 30 分钟（TTL = 1800s） |
| 错误码 | 1006（HTTP 423） |
| 清除时机 | 登录成功后立刻清除 + 到期自动过期 |

---

## 4. 软删除与恢复规则

- `status=0` 时**设置** `deleted_at = NOW()`
- `status=1` 时**清除** `deleted_at`（恢复）
- 软删除后用户数据保留 180 天
- 用户被禁用后，所有 JWT Token 立即失效（通过 Redis 黑名单 + JWT 校验时查 status）

---

## 5. 数据脱敏（API 响应层）

`/api/users` 列表端点：
- `realName` → 保留（如 "张**"）
- `email` → 中间脱敏（如 "a***@x.edu.cn"）
- `phone` → 中间 4 位 `*`（如 "138****0001"）

`/api/auth/me` 端点：返回真实值（本人查自己）。

---

## 6. 审计日志

| 操作 | 必须记录 |
|------|---------|
| 登录/登出 | userId, action, ip, isSuccess |
| 账户状态变更 | userId, action, targetType, targetId, detail |
| 部门/专业/班级 CRUD | operatorId, action, targetType, targetId |

**detail 字段格式**（JSON 串）：
```json
{"field": "status", "oldValue": "1", "newValue": "0"}
```

> **注意**：Phase 1 可简化实现，operation_logs 表属于 Phase 1.5 范围。

---

## 7. AI 编码陷阱

```
❌ 状态机直接用 status=0 当"禁用"，但实际 status=0 = INACTIVE
❌ 删除院系/专业/班级时不检查关联
❌ 登录失败无锁定机制
❌ JWT claims 缺 username / departmentId
❌ 列表接口返回明文 email/phone
✅ 严格按 1.3 转换表实现
✅ Service 层做删除前置检查
✅ Redis 计数 login:lock:{username}
✅ JWT 含 sub/username/role/departmentId/iat/exp 6 字段
✅ list 端点脱敏，/me 端点不脱敏
```

---

*视图版本：v1.0 · 与源文档对齐*
*最后更新：2026-06-11*

---

## 7. 课程状态机（CourseStatus，Phase 2+）

> **源文档**：`enums/CourseStatus.java`（代码实现）
> **实现类**：`com.microcourse.enums.CourseStatus`

### 7.1 状态定义

| 状态值 | 名称 | 业务含义 |
|--------|------|---------|
| 0 | DRAFT | 草稿（教师创建后初始状态） |
| 1 | PENDING_REVIEW | 待审核（教师提交后） |
| 2 | APPROVED | 审核通过（管理员/教务处审批） |
| 3 | REJECTED | 驳回（附驳回原因） |
| 4 | PUBLISHED | 已发布（admin 上架，学生可见可选） |
| 5 | CLOSED | 已下架（admin 操作，学生不可选但已选学生可继续学） |
| 6 | ARCHIVED | 已归档（终态，不可做任何操作） |

### 7.2 状态转换图

```
DRAFT ──submit──→ PENDING_REVIEW
  ↑                   ↓ approve
  │                APPROVED ──publish──→ PUBLISHED
  │                   ↓                     ↓
  ↑                   │                  CLOSED
  │                   ↓                     ↓
  └──┐              REJECTED             ARCHIVED
     │                 │
     └──resubmit───────┘
  REJECTED ──submit(rework)──→ PENDING_REVIEW
  PUBLISHED ──unpublish──→ CLOSED
  CLOSED ──publish──→ PUBLISHED
  CLOSED ──archive──→ ARCHIVED
  APPROVED ──close──→ CLOSED
  REJECTED ──close──→ CLOSED
  REJECTED ──archive──→ ARCHIVED
```

### 7.3 转换规则

| 从状态 | 到状态 | 触发操作 | Controller 端点 |
|--------|--------|---------|----------------|
| DRAFT | PENDING_REVIEW | submitForReview() | POST /api/courses/{id}/submit |
| PENDING_REVIEW | APPROVED | approve() | POST /api/courses/{id}/approve |
| PENDING_REVIEW | REJECTED | reject(reason) | POST /api/courses/{id}/reject |
| PENDING_REVIEW | DRAFT | update() + submit | (草稿修改后重新提交) |
| APPROVED | PUBLISHED | publish() | POST /api/courses/{id}/publish |
| APPROVED | CLOSED | updateStatus(CLOSED) | PUT /api/courses/{id}/status |
| REJECTED | PENDING_REVIEW | submitForReview() | POST /api/courses/{id}/submit |
| REJECTED | CLOSED | updateStatus(CLOSED) | PUT /api/courses/{id}/status |
| REJECTED | DRAFT | update() | PUT /api/courses/{id}（保存为草稿） |
| REJECTED | ARCHIVED | updateStatus(ARCHIVED) | PUT /api/courses/{id}/status |
| PUBLISHED | CLOSED | unpublish() | POST /api/courses/{id}/unpublish |
| CLOSED | PUBLISHED | publish() | POST /api/courses/{id}/publish |
| CLOSED | ARCHIVED | updateStatus(ARCHIVED) | PUT /api/courses/{id}/status |

**关键约束**：
- ARCHIVED 是终态，不接收任何状态转换
- 所有状态变更使用 version 乐观锁（CAS）防止并发冲突
- `isSelectable()` 返回 true 当 status == APPROVED 或 PUBLISHED（学生可选该课程）
- DRAFT/PENDING_REVIEW/REJECTED → 学生选不到；PUBLISHED/CLOSED → 已选学生可继续学

---

## 8. 选课状态机（EnrollmentStatus，Phase 2+）

> **源文档**：`enums/EnrollmentStatus.java`（代码实现）
> **历史兼容**：存量数据使用 `ENROLLED` 值，新代码统一走 `APPROVED`。`fromString()` 将 `ENROLLED` 映射为 `APPROVED`。

### 8.1 状态定义

| 状态值 | 含义 | 说明 |
|--------|------|------|
| PENDING | 待审核 | 学生选课后需要教师审批 |
| APPROVED | 已录取（*含历史 ENROLLED*） | 选课成功（存量 ENROLLED 映射到此值） |
| WAITLIST | 候补 | 课程满员时自动进入候补队列 |
| CANCELLED | 已取消 | 学生主动退课（终态） |
| REJECTED | 已拒绝 | 教师拒绝选课申请（终态） |
| COMPLETED | 已完成 | 学习结束、证书可颁发（终态） |
| DROPPED | 已退出 | 教师/管理员操作退出（终态） |

### 8.2 状态转换图

```
PENDING ──approve──→ APPROVED
  │                    ↓
  ↓                 COMPLETED
REJECTED              ↓
  │                CANCELLED（退课）
  ↓                    │
WAITLIST ──promote──→ DROPPED
  │   (退课后候补晋升)
  ↓
CANCELLED
```

**历史兼容**：
- `ENROLLED`（旧值）在 `fromString()` 中映射到 `APPROVED`
- 查询 API 兼容 ENROLLED/APPROVED 双值：`in(enrollment_status, 'ENROLLED', 'APPROVED')`

### 8.3 转换矩阵

| 从状态 | 到状态 | 触发 | 说明 |
|--------|--------|------|------|
| PENDING | APPROVED | approve | 教师审批通过 |
| PENDING | REJECTED | reject | 教师拒绝 |
| PENDING | WAITLIST | (系统) | 满员时自动入候补 |
| PENDING | CANCELLED | cancel | 学生取消申请 |
| APPROVED | COMPLETED | markComplete | 学习完成（证书+徽章自动颁发） |
| APPROVED | CANCELLED | cancel | 学生主动退课（触发候补晋升） |
| APPROVED | DROPPED | drop | 管理员强制退出 |
| WAITLIST | APPROVED | promote | 课程有空位时自动晋升（行级锁+CAS） |
| WAITLIST | CANCELLED | cancel | 取消候补 |
| REJECTED | — | — | 终态 |
| CANCELLED | — | — | 终态（退课后重新选课走物理删除+重建） |
| COMPLETED | — | — | 终态 |
| DROPPED | — | — | 终态 |

**关键约束**：
- ALL 状态变更使用 version 乐观锁（防并发状态冲突）
- 退课（CANCELLED）自动触发：student_count -1 + 候补晋升
- 选课 + 容量检查通过原子 INSERT...SELECT 实现（atomicInsertIfCapacity）
- 满员时自动插入 WAITLIST 记录（atomicInsertIfEnrollable）
- CANCELLED 后重新选课：物理删除旧记录（REQUIRES_NEW 独立事务）+ 正常选课流程

---

## 9. 微专业状态机（Phase 14）

> **源文档**：[`docs/开发规划/phase14-micro-specialty-spec.md` §2 状态机全集](../../../docs/开发规划/phase14-micro-specialty-spec.md)
> **完整规则**：6 个状态机、转换矩阵、触发角色、前置条件，详见 phase14-spec §2.1-§2.5

### 8.1 微专业主表 8 状态

```
路径A（教务处直立）:                 路径B（教师申报）:
   DRAFT                              PROPOSAL_REVIEW
     ↓ submit                            ↓ approve  → 创建 DRAFT + LEAD INVITED
   PENDING_REVIEW                        ↓ reject   → REJECTED
     ↓ approve → APPROVED                ↓ withdraw → WITHDRAWN
     ↓ reject  → REJECTED ──resubmit──→ DRAFT（修改后重提）
   APPROVED →（LEAD 确认开课）→ RECRUITING
     ↓ open
   RECRUITING →（长期运行，学生报名）
     ↓ close
   COMPLETED ──archive──→ ARCHIVED
   
   ↕ 任意状态 → CANCELLED（教务处强制 ──终态──）
```

**关键不变约束**：
- LEAD 未接受邀请前，DRAFT 不可进 PENDING_REVIEW
- 课程编排 < 1 门时不可进 RECRUITING
- RECRUITING 状态前不可设置 is_featured
- CANCELLED 后不再接受任何状态转换（终态）
- `submit` API 接受 `DRAFT | REJECTED` 双 from-state
- REJECTED 后 LEAD 修改信息后可重新 submit

### 8.2 修读记录 6 状态

```
PENDING ──approve──→ APPROVED ──auto──→ IN_PROGRESS ──auto──→ COMPLETED → CERTIFIED
   │                    │                    │                    ↓
   ↓                    ↓                    ↓                 FAILED → reapply → PENDING
REJECTED              DROPPED              FAILED
   └──reapply──→ PENDING  └──reapply──→ PENDING
```

**非正常终止出口**：REJECTED/DROPPED/FAILED 都有 `reapply` 出口返回 PENDING。

### 8.3 教师邀请 5 状态

```
INVITED ──accept──→ ACTIVE（同学院）
   ├──→ PENDING_ACADEMIC（跨学院→教务处审批→ACTIVE）
   ├──→ DECLINED（手动拒绝或7天超时）
   └──→ REMOVED（LEAD移除或主动退出）

REMOVED ──reinvite──→ INVITED（复用原记录）
DECLINED ──reinvite──→ INVITED（复用原记录）
```

**部分唯一索引**：`WHERE invite_status NOT IN ('DECLINED','REMOVED')` 允许重新邀请。

### 8.4 所有状态变更必须使用 version 乐观锁

```
UPDATE ... SET status = :newStatus, version = version + 1
WHERE id = :id AND version = :oldVersion
-- rows == 0 → 抛出 BusinessException(ErrorCode.CONCURRENT_MODIFICATION)
```

**强制**：所有 UPDATE 操作必须包含 version 乐观锁（§9.1-§9.11 伪代码标记了 version 的地方必须全部实现）。
