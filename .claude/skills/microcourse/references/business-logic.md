# 业务逻辑引用视图

> **源文档**：
> - [`docs/状态机设计.md`](../../状态机设计.md) v1.0（状态机）
> - [`docs/开发规范.md`](../../开发规范.md) v1.3 §3.3.1（响应/分页格式）
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
| DELETED → ACTIVE | 管理员 | `status=1, deleted_at=NULL`（180 天内可恢复） | 不可超过 180 天 |

**端点映射**：`PUT /api/users/{id}/status` 接受 `status=0`（软删/禁用）或 `status=1`（启用/恢复）。

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
1. 校验登录失败次数（Redis key: login:lock:{username}）
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
