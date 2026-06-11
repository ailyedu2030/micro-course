# API 契约引用视图

> **源文档**：[`docs/API契约-Phase1.md`](../../API契约-Phase1.md) v1.1
> **视图性质**：引用视图（不复制全文，仅抓取 AI 编码时最常查的关键接口）
> **同步规则**：真文档更新后，本视图必须 24 小时内同步
> **冲突裁决**：以冲突评审决议为准

---

## 1. 全局约定

### 1.1 基础路径

```
/api          ← 注意：无 /v1 前缀
```

### 1.2 统一响应格式

```json
{
  "code": 200,                       ← 注意：成功 = 200，不是 0
  "message": "ok",                   ← 注意：不是 "success"
  "data": { ... },                   ← 成功：业务对象；失败：null
  "timestamp": 1749620400000         ← 注意：必含 Long 毫秒
}
```

### 1.3 分页格式

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [ ... ],                ← 注意：不是 content / records
    "page": 0,                       ← 0-based
    "size": 20,
    "totalElements": 100,            ← 注意：不是 total
    "totalPages": 5                  ← 注意：必含，totalElements/size 自动算
  },
  "timestamp": 1749620400000
}
```

### 1.4 鉴权

```
Header: Authorization: Bearer <JWT>
Content-Type: application/json
时间格式: ISO 8601 UTC+8（如 2026-06-11T10:00:00+08:00）
```

---

## 2. 18 个 API（Phase 1 范围）

### 2.1 用户认证（5 个）

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/api/auth/login` | 公开 |
| POST | `/api/auth/refresh` | 已认证 |
| POST | `/api/auth/logout` | 已认证 |
| GET  | `/api/auth/cas` | 公开 |
| GET  | `/api/auth/me` | 已认证 |

**特别注意**：`/me` 在 `/api/auth/me`（不是 `/api/users/me`），依据冲突评审决议 C4。

### 2.2 院系管理（5 个）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET    | `/api/departments` | 已认证 |
| POST   | `/api/departments` | ADMIN |
| GET    | `/api/departments/{id}` | 已认证 |
| PUT    | `/api/departments/{id}` | ADMIN |
| DELETE | `/api/departments/{id}` | ADMIN |

### 2.3 专业管理（5 个）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET    | `/api/majors` | 已认证 |
| POST   | `/api/majors` | ADMIN |
| GET    | `/api/majors/{id}` | 已认证 |
| PUT    | `/api/majors/{id}` | ADMIN |
| DELETE | `/api/majors/{id}` | ADMIN |

### 2.4 班级管理（5 个）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET    | `/api/classes` | 已认证 |
| POST   | `/api/classes` | ADMIN |
| GET    | `/api/classes/{id}` | 已认证 |
| PUT    | `/api/classes/{id}` | ADMIN |
| DELETE | `/api/classes/{id}` | ADMIN |

### 2.5 用户管理（5+1 = 6 个）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET    | `/api/users` | ADMIN/ACADEMIC |
| POST   | `/api/users` | ADMIN |
| GET    | `/api/users/{id}` | ADMIN/ACADEMIC/TEACHER(受限)/本人 |
| PUT    | `/api/users/{id}` | ADMIN/本人(受限) |
| **PUT** | **`/api/users/{id}/status`** | **ADMIN** ← Phase 1 软删除/启用的唯一端点 |
| ~~DELETE~~ | ~~/api/users/{id}~~ | 不存在 — 删除走 /status 端点 |

**特别注意**：`/api/users/{id}/status` 是状态机入口（status=0 软删 / status=1 启用），不要写 DELETE 端点。

---

## 3. 错误码速查（13 个业务码）

| code | 含义 | HTTP 状态 |
|------|------|----------|
| 1001 | 用户名或密码错误 | 401 |
| 1002 | 账号已被禁用 | 401 |
| 1003 | 账号已被删除 | 401 |
| 1004 | Token 已过期 | 401 |
| 1005 | Token 格式错误 | 401 |
| 1006 | 登录失败次数过多，账号已锁定 | 423 |
| 2001 | 院系不存在 | 404 |
| 2002 | 院系下存在专业，无法删除 | 409 |
| 3001 | 专业不存在 | 404 |
| 3002 | 专业下存在班级，无法删除 | 409 |
| 4001 | 班级不存在 | 404 |
| 5001 | 用户不存在 | 404 |
| 5002 | 用户名已存在 | 409 |
| 5003 | 学号/工号已存在 | 409 |
| 5004 | 邮箱已存在 | 409 |

**特别注意**：业务码 ≠ HTTP 状态码。HTTP 状态码 200（业务成功）/ 401/403/404/409/423/500；业务码 1xxx-5xxx 区分具体原因。

### 3.1 HTTP 状态码完整清单

| HTTP | 含义 | 触发场景 |
|------|------|----------|
| 200 | 业务成功 | 所有正常响应 |
| 400 | 参数错误 | 请求体校验失败、字段缺失/类型错 |
| 401 | 未认证 | Token 缺失 / 过期 / 格式错 |
| 403 | 无权限 | 已认证但角色不允许（@PreAuthorize 拒绝） |
| 404 | 资源不存在 | 院系/专业/班级/用户 ID 不存在 |
| 409 | 资源冲突 | 唯一键冲突 / 删除前置 / 用户名已存在 |
| 423 | 账号锁定 | 登录失败 ≥ 5 次（30 分钟） |
| 429 | 请求过于频繁 | 速率限制（Phase 1.5 引入） |
| 500 | 服务器内部错误 | 未捕获异常、DB 异常 |

**业务码 1xxx-5xxx** 区分具体失败原因（见 §3 表）。

---

## 4. 登录响应示例

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "tokenType": "Bearer"
  },
  "timestamp": 1749620400000
}
```

**字段名严格**：accessToken / refreshToken / expiresIn / tokenType（camelCase）。

---

## 5. /auth/me 响应示例

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "userId": 1,
    "username": "admin",
    "realName": "管理员",
    "email": "a***@x.edu.cn",
    "phone": "138****0001",
    "gender": "MALE",
    "avatar": "https://...",
    "role": "ADMIN",
    "departmentId": 1,
    "departmentName": "计算机学院",
    "majorId": null,
    "majorName": null,
    "classId": null,
    "className": null,
    "grade": null,
    "enrollmentYear": null,
    "status": 1,
    "lastLoginAt": "2026-06-11T10:00:00+08:00",
    "createdAt": "2026-06-11T09:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**脱敏字段**：realName / email / phone 在 list 端点必脱敏；本人 /me 端点可返回真实值（依据：API契约 v1.1 §2.5）。

---

## 6. AI 编码陷阱

```
❌ code: 0
❌ message: "success" / "操作成功"
❌ 缺 timestamp
❌ 分页用 total / pageSize
❌ REST 路径 /api/v1/...
❌ /me 路径 /api/users/me
❌ 删除用户走 DELETE 端点
✅ code: 200
✅ message: "ok"
✅ timestamp 必含
✅ 分页用 items/page/size/totalElements/totalPages
✅ REST 路径 /api/...
✅ /me 路径 /api/auth/me
✅ 删除用户走 PUT /users/{id}/status
```

---

*视图版本：v1.0 · 与源文档 v1.1 对齐*
*最后更新：2026-06-11*
