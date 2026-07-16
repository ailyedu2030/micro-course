# 微课管理平台 · API 契约文档 · Phase 1

> 版本：v2.2（2026-07-07 用户管理域 P1-C 修复同步）
> 日期：2026-06-11  
> 状态：正式发布  
> 范围：用户认证、院系管理、专业管理、班级管理、用户管理（共 27 个 API）

## 跨域引用

- **课程管理域契约**: 见 `docs/API契约-课程管理.md` (课程/章节/视频/分类/标签/套件/课时/课件/评价/收藏, 85+ 端点)
- **选课管理域契约**: 见 `docs/API契约-选课管理.md` (选课/购物车/订单, 23 端点)
- **微专业域契约**: 见 `docs/API契约-微专业.md` (51 个微专业相关 API)
- **权限矩阵**: 见 `docs/权限矩阵.md` v4.0

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-11 | 初稿 |
| v1.1 | 2026-06-11 | 修复分页格式（统一为 items）、完善 JWT Payload 结构、预留讨论区 API |
| v1.2 | 2026-06-11 | 补 3 个 /me 端点（PUT /me, PUT /me/password, POST /me/avatar），与权限矩阵 v2.0 §1.1 对齐。依据：穷举审查 E4 P1-3 |
| v2.0 | 2026-06-11 | Phase 14 新增微专业 51 个 API |
| v2.1 | 2026-07-07 | 增加跨域引用 (课程管理域/微专业域/权限矩阵) |
| v2.2 | 2026-07-07 | 用户管理域 P1-C 修复同步：ACADEMIC 越权收窄、2 个缺失端点补全、错误码 1007/4002/1011 修正、UpdateProfileRequest 加 avatar 字段、分页 size 最大 100 统一、R.java timestamp 已删除响应示例同步 |

---

## 目录

1. [全局约定](#全局约定)
2. [错误码规范](#错误码规范)
3. [用户认证（5 个）](#用户认证5个)
4. [院系管理（5 个）](#院系管理5个)
5. [专业管理（5 个）](#专业管理5个)
6. [班级管理（5 个）](#班级管理5个)
7. [用户管理（4 个）](#用户管理4个)

---

## 全局约定

### 基础路径
```
/api
```

### 统一响应格式
```json
{
  "code": 200,
  "message": "ok",
  "data": { ... },
  "timestamp": 1749620400000
}
```

### 分页格式
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  },
  "timestamp": 1749620400000
}
```

### 认证方式
- Header：`Authorization: Bearer <JWT>`
- JWT Payload：
```json
{
  "sub": "用户ID",
  "username": "登录名",
  "role": "STUDENT|TEACHER|ADMIN|ACADEMIC",
  "departmentId": "所属院系ID（可选）",
  "iat": 1700000000,
  "exp": 1700007200
}
```

### 时间格式
- ISO 8601，UTC+8（北京时间）
- 示例：`2026-06-11T10:00:00+08:00`

### 角色说明
| 角色 | 说明 |
|------|------|
| STUDENT | 学生 |
| TEACHER | 教师 |
| ADMIN | 管理员 |
| ACADEMIC | 教务处 |

### Content-Type
- 请求：`application/json`
- 响应：`application/json`

---

## 错误码规范

| HTTP 状态码 | code | 说明 |
|-------------|------|------|
| 200 | 200 | 成功 |
| 400 | 400 | 请求参数错误 |
| 401 | 401 | 未认证 / Token 无效 |
| 403 | 403 | 无权限 |
| 404 | 404 | 资源不存在 |
| 409 | 409 | 资源冲突（如重复创建） |
| 423 | 423 | 账号锁定 |
| 429 | 429 | 请求过于频繁 |
| 500 | 500 | 服务器内部错误 |

### Phase 1 业务错误码

| code | 说明 |
|------|------|
| 1001 | 用户名或密码错误 |
| 1002 | 账号已被禁用 |
| 1003 | 账号已被删除 |
| 1004 | Token 已过期 |
| 1005 | Token 格式错误 |
| 1006 | 登录失败次数过多，账号已锁定 |
| 2001 | 院系不存在 |
| 2002 | 院系下存在专业，无法删除 |
| 3001 | 专业不存在 |
| 3002 | 专业下存在班级，无法删除 |
| 4001 | 班级不存在 |
| 5001 | 用户不存在 |
| 5002 | 用户名已存在 |
| 5003 | 学号/工号已存在 |
| 5004 | 邮箱已存在 |

---

## 用户认证（5个）

### 1.1 POST /api/auth/login — 登录

**权限要求**：公开（无需认证）

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| username | body | 是 | String | 用户名 |
| password | body | 是 | String | 密码（明文传输，生产环境必须 HTTPS） |

**请求体示例**
```json
{
  "username": "zhangsan",
  "password": "password123"
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200,
    "tokenType": "Bearer"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 401 | 1001 | 用户名或密码错误 |
| 401 | 1002 | 账号已被禁用 |
| 401 | 1003 | 账号已被删除 |
| 423 | 1006 | 登录失败次数过多，账号已锁定 30 分钟 |

**业务规则**
- 登录失败 5 次后锁定账号 30 分钟
- 锁定信息存入 Redis，Key：`login:lock:{username}`
- 每次失败记录到 `operation_logs`

---

### 1.2 POST /api/auth/refresh — 刷新 Token

**权限要求**：已认证用户

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| refreshToken | body | 是 | String | 刷新令牌 |

**请求体示例**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200,
    "tokenType": "Bearer"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 401 | 1004 | Token 已过期 |
| 401 | 1005 | Token 格式错误 |

---

### 1.3 POST /api/auth/logout — 登出

**权限要求**：已认证用户

**请求参数**：无

**请求体示例**：无

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": null,
  "timestamp": 1749620400000
}
```

**错误响应**：无

**业务规则**
- 清除 Redis 中的 Token 黑名单
- 记录登出日志到 `operation_logs`

---

### 1.4 GET /api/auth/cas — CAS 回调

**权限要求**：公开（无需认证）

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| ticket | query | 是 | String | CAS ticket |
| state | query | 否 | String | 状态参数（防 CSRF） |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200,
    "tokenType": "Bearer",
    "isNewUser": false
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 401 | 1005 | CAS ticket 无效 |

**业务规则**
- 首次登录时自动创建用户，`cas_bound = true`
- 新用户默认角色为 STUDENT
- 记录 CAS 绑定信息到 `operation_logs`

---

### 1.5 GET /api/auth/me — 当前用户

**权限要求**：已认证用户

**请求参数**：无

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "realName": "张*",
    "email": "j***@example.com",
    "phone": "138****1234",
    "gender": "MALE",
    "avatar": "https://cdn.example.com/avatars/1.png",
    "role": "STUDENT",
    "departmentId": 1,
    "departmentName": "计算机学院",
    "majorId": 1,
    "majorName": "计算机科学与技术",
    "classId": 1,
    "className": "计科2301班",
    "grade": "2023",
    "enrollmentYear": "2023",
    "status": 1,
    "lastLoginAt": "2026-06-11T10:00:00+08:00",
    "createdAt": "2023-09-01T08:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 401 | 1004 | Token 已过期 |

**数据脱敏规则**（依据《个人信息保护法》）
- `realName`：姓保留，名掩码，如"张**"
- `email`：@前保留首字，如"j***@example.com"
- `phone`：前3后4掩码，如"138****1234"

---

### 1.6 PUT /api/auth/me — 更新当前用户基本信息

**权限要求**：已认证用户（仅本人）

**请求参数**：
```json
{
  "realName": "String (可选)",
  "email": "String (可选)",
  "phone": "String (可选)",
  "gender": "MALE|FEMALE (可选)",
  "avatar": "String (可选)"
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "userId": 1,
    "realName": "张*",
    "email": "j***@example.com",
    "phone": "138****1234"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 400 | 参数错误 |
| 401 | 1004 | Token 已过期 |
| 409 | 5004 | 邮箱已被其他用户使用 |

**特别注意**：
- 用户**只能修改自己的**基本信息（realName/email/phone/gender/avatar）
- **不能**修改：role / status / departmentId / majorId / classId（仅 ADMIN 可改）
- 邮箱若被其他用户占用 → code 5004

---

### 1.7 PUT /api/auth/me/password — 修改密码

**权限要求**：已认证用户（仅本人）

**请求参数**：
```json
{
  "oldPassword": "String (必填, 当前密码)",
  "newPassword": "String (必填, 最小8字符, 需包含字母和数字)"
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "ok",
  "data": null,
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 400 | 参数错误（newPassword 强度不足） |
| 401 | 1001 | 原密码错误 |
| 401 | 1004 | Token 已过期 |

**业务规则**：
- 改密成功后，旧 Token 立即失效（强制重新登录）
- 清除登录失败计数（login:lock:{username}）

---

### 1.8 POST /api/auth/me/avatar — 上传头像

**权限要求**：已认证用户（仅本人）

**请求类型**：`multipart/form-data`

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | binary | 是 | 图片文件，限制：jpg/png/webp，≤ 2MB |

**成功响应**：
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "avatar": "https://cdn.microcourse.local/avatars/1_1749620400000.png"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 400 | 文件类型/大小不符合 |
| 401 | 1004 | Token 已过期 |

**业务规则**：
- 文件存 OSS / 本地 storage（Phase 1.5 决定）
- 文件名格式：`{userId}_{timestamp}.{ext}`

---

## 院系管理（5个）

### 2.1 GET /api/departments — 获取院系列表

**权限要求**：STUDENT / TEACHER / ADMIN / ACADEMIC

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| page | query | 否 | Integer | 页码（默认 1） |
| size | query | 否 | Integer | 每页条数（默认 20，最大 100） |
| name | query | 否 | String | 院系名称（模糊匹配） |
| code | query | 否 | String | 院系代码（精确匹配） |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 1,
        "name": "计算机学院",
        "code": "CS",
        "parentId": null,
        "sortOrder": 1,
        "createdAt": "2023-01-01T00:00:00+08:00"
      },
      {
        "id": 2,
        "name": "理学院",
        "code": "SCI",
        "parentId": null,
        "sortOrder": 2,
        "createdAt": "2023-01-01T00:00:00+08:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "timestamp": 1749620400000
}
```

**错误响应**：无

---

### 2.2 POST /api/departments — 创建院系

**权限要求**：ADMIN

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| name | body | 是 | String | 院系名称（最大 100 字符） |
| code | body | 是 | String | 院系代码（最大 30 字符，唯一） |
| parentId | body | 否 | Long | 父部门 ID（学院→系） |
| sortOrder | body | 否 | Integer | 排序号（默认 0） |

**请求体示例**
```json
{
  "name": "计算机学院",
  "code": "CS",
  "parentId": null,
  "sortOrder": 1
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计算机学院",
    "code": "CS",
    "parentId": null,
    "sortOrder": 1,
    "createdAt": "2026-06-11T10:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 400 | 请求参数错误 |
| 409 | 409 | 院系代码已存在 |

---

### 2.3 GET /api/departments/{id} — 获取院系详情

**权限要求**：STUDENT / TEACHER / ADMIN / ACADEMIC

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 院系 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计算机学院",
    "code": "CS",
    "parentId": null,
    "sortOrder": 1,
    "createdAt": "2023-01-01T00:00:00+08:00",
    "children": [
      {
        "id": 3,
        "name": "计算机科学与技术系",
        "code": "CS-CS",
        "parentId": 1,
        "sortOrder": 1
      }
    ]
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 2001 | 院系不存在 |

---

### 2.4 PUT /api/departments/{id} — 更新院系

**权限要求**：ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 院系 ID |

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| name | body | 否 | String | 院系名称（最大 100 字符） |
| code | body | 否 | String | 院系代码（最大 30 字符，唯一） |
| parentId | body | 否 | Long | 父部门 ID |
| sortOrder | body | 否 | Integer | 排序号 |

**请求体示例**
```json
{
  "name": "计算机学院（更新）",
  "sortOrder": 2
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计算机学院（更新）",
    "code": "CS",
    "parentId": null,
    "sortOrder": 2,
    "createdAt": "2023-01-01T00:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 2001 | 院系不存在 |
| 409 | 409 | 院系代码已存在 |

---

### 2.5 DELETE /api/departments/{id} — 删除院系

**权限要求**：ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 院系 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": null,
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 2001 | 院系不存在 |
| 409 | 2002 | 院系下存在专业，无法删除 |

**业务规则**
- 院系下有专业（majors）时拒绝删除
- 删除操作写入 `operation_logs`

---

## 专业管理（5个）

### 3.1 GET /api/majors — 获取专业列表

**权限要求**：STUDENT / TEACHER / ADMIN / ACADEMIC

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| page | query | 否 | Integer | 页码（默认 1） |
| size | query | 否 | Integer | 每页条数（默认 20，最大 100） |
| name | query | 否 | String | 专业名称（模糊匹配） |
| code | query | 否 | String | 专业代码（精确匹配） |
| departmentId | query | 否 | Long | 所属院系 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 1,
        "name": "计算机科学与技术",
        "code": "CS-CST",
        "departmentId": 1,
        "departmentName": "计算机学院",
        "sortOrder": 1,
        "createdAt": "2023-01-01T00:00:00+08:00"
      },
      {
        "id": 2,
        "name": "软件工程",
        "code": "CS-SE",
        "departmentId": 1,
        "departmentName": "计算机学院",
        "sortOrder": 2,
        "createdAt": "2023-01-01T00:00:00+08:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "timestamp": 1749620400000
}
```

**错误响应**：无

---

### 3.2 POST /api/majors — 创建专业

**权限要求**：ADMIN

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| name | body | 是 | String | 专业名称（最大 100 字符） |
| code | body | 是 | String | 专业代码（最大 30 字符，唯一） |
| departmentId | body | 是 | Long | 所属院系 ID |
| sortOrder | body | 否 | Integer | 排序号（默认 0） |

**请求体示例**
```json
{
  "name": "计算机科学与技术",
  "code": "CS-CST",
  "departmentId": 1,
  "sortOrder": 1
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计算机科学与技术",
    "code": "CS-CST",
    "departmentId": 1,
    "sortOrder": 1,
    "createdAt": "2026-06-11T10:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 400 | 请求参数错误 |
| 404 | 2001 | 院系不存在 |
| 409 | 409 | 专业代码已存在 |

---

### 3.3 GET /api/majors/{id} — 获取专业详情

**权限要求**：STUDENT / TEACHER / ADMIN / ACADEMIC

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 专业 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计算机科学与技术",
    "code": "CS-CST",
    "departmentId": 1,
    "departmentName": "计算机学院",
    "sortOrder": 1,
    "createdAt": "2023-01-01T00:00:00+08:00",
    "classes": [
      {
        "id": 1,
        "name": "计科2301班",
        "grade": "2023"
      }
    ]
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 3001 | 专业不存在 |

---

### 3.4 PUT /api/majors/{id} — 更新专业

**权限要求**：ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 专业 ID |

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| name | body | 否 | String | 专业名称（最大 100 字符） |
| code | body | 否 | String | 专业代码（最大 30 字符，唯一） |
| departmentId | body | 否 | Long | 所属院系 ID |
| sortOrder | body | 否 | Integer | 排序号 |

**请求体示例**
```json
{
  "name": "计算机科学与技术（更新）",
  "sortOrder": 2
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计算机科学与技术（更新）",
    "code": "CS-CST",
    "departmentId": 1,
    "sortOrder": 2,
    "createdAt": "2023-01-01T00:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 3001 | 专业不存在 |
| 404 | 2001 | 院系不存在 |
| 409 | 409 | 专业代码已存在 |

---

### 3.5 DELETE /api/majors/{id} — 删除专业

**权限要求**：ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 专业 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": null,
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 3001 | 专业不存在 |
| 409 | 3002 | 专业下存在班级，无法删除 |

**业务规则**
- 专业下有班级（classes）时拒绝删除
- 删除操作写入 `operation_logs`

---

## 班级管理（5个）

### 4.1 GET /api/classes — 获取班级列表

**权限要求**：STUDENT / TEACHER / ADMIN / ACADEMIC

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| page | query | 否 | Integer | 页码（默认 1） |
| size | query | 否 | Integer | 每页条数（默认 20，最大 100） |
| name | query | 否 | String | 班级名称（模糊匹配） |
| majorId | query | 否 | Long | 所属专业 ID |
| grade | query | 否 | String | 年级（如"2023"） |
| counselorId | query | 否 | Long | 辅导员 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 1,
        "name": "计科2301班",
        "majorId": 1,
        "majorName": "计算机科学与技术",
        "departmentName": "计算机学院",
        "grade": "2023",
        "counselorId": 10,
        "counselorName": "李老师",
        "sortOrder": 1,
        "createdAt": "2023-09-01T00:00:00+08:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": 1749620400000
}
```

**错误响应**：无

---

### 4.2 POST /api/classes — 创建班级

**权限要求**：ADMIN

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| name | body | 是 | String | 班级名称（如"计科2301班"，最大 50 字符） |
| majorId | body | 是 | Long | 所属专业 ID |
| grade | body | 是 | String | 年级（如"2023"，最大 10 字符） |
| counselorId | body | 否 | Long | 辅导员 ID |
| sortOrder | body | 否 | Integer | 排序号（默认 0） |

**请求体示例**
```json
{
  "name": "计科2301班",
  "majorId": 1,
  "grade": "2023",
  "counselorId": 10,
  "sortOrder": 1
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计科2301班",
    "majorId": 1,
    "majorName": "计算机科学与技术",
    "grade": "2023",
    "counselorId": 10,
    "counselorName": "李老师",
    "sortOrder": 1,
    "createdAt": "2026-06-11T10:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 400 | 请求参数错误 |
| 404 | 3001 | 专业不存在 |
| 404 | 5001 | 辅导员不存在 |

---

### 4.3 GET /api/classes/{id} — 获取班级详情

**权限要求**：STUDENT / TEACHER / ADMIN / ACADEMIC

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 班级 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计科2301班",
    "majorId": 1,
    "majorName": "计算机科学与技术",
    "departmentName": "计算机学院",
    "grade": "2023",
    "counselorId": 10,
    "counselorName": "李老师",
    "sortOrder": 1,
    "createdAt": "2023-09-01T00:00:00+08:00",
    "studentCount": 30
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 4001 | 班级不存在 |

---

### 4.4 PUT /api/classes/{id} — 更新班级

**权限要求**：ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 班级 ID |

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| name | body | 否 | String | 班级名称（最大 50 字符） |
| majorId | body | 否 | Long | 所属专业 ID |
| grade | body | 否 | String | 年级（最大 10 字符） |
| counselorId | body | 否 | Long | 辅导员 ID |
| sortOrder | body | 否 | Integer | 排序号 |

**请求体示例**
```json
{
  "name": "计科2301班（调整）",
  "counselorId": 11
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "计科2301班（调整）",
    "majorId": 1,
    "majorName": "计算机科学与技术",
    "grade": "2023",
    "counselorId": 11,
    "counselorName": "王老师",
    "sortOrder": 1,
    "createdAt": "2023-09-01T00:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 4001 | 班级不存在 |
| 404 | 3001 | 专业不存在 |
| 404 | 5001 | 辅导员不存在 |

---

### 4.5 DELETE /api/classes/{id} — 删除班级

**权限要求**：ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 班级 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": null,
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 4001 | 班级不存在 |
| 409 | 409 | 班级下存在学生，无法删除 |

**业务规则**
- 班级下有学生（users）时拒绝删除
- 删除操作写入 `operation_logs`

---

## 用户管理（4个）

### 5.1 GET /api/users — 获取用户列表

**权限要求**：ADMIN / ACADEMIC

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| page | query | 否 | Integer | 页码（默认 1） |
| size | query | 否 | Integer | 每页条数（默认 20，最大 100） |
| username | query | 否 | String | 用户名（模糊匹配） |
| realName | query | 否 | String | 真实姓名（模糊匹配） |
| role | query | 否 | String | 角色（STUDENT / TEACHER / ADMIN / ACADEMIC） |
| departmentId | query | 否 | Long | 院系 ID |
| majorId | query | 否 | Long | 专业 ID |
| classId | query | 否 | Long | 班级 ID |
| status | query | 否 | Integer | 状态（0=禁用/删除, 1=启用） |
| includeDeleted | query | 否 | Boolean | 是否包含已删除用户（默认 false，仅 ADMIN 可用） |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 1,
        "username": "zhangsan",
        "realName": "张*",
        "email": "j***@example.com",
        "phone": "138****1234",
        "gender": "MALE",
        "avatar": "https://cdn.example.com/avatars/1.png",
        "role": "STUDENT",
        "departmentId": 1,
        "departmentName": "计算机学院",
        "majorId": 1,
        "majorName": "计算机科学与技术",
        "classId": 1,
        "className": "计科2301班",
        "grade": "2023",
        "enrollmentYear": "2023",
        "status": 1,
        "casBound": true,
        "lastLoginAt": "2026-06-11T10:00:00+08:00",
        "createdAt": "2023-09-01T00:00:00+08:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": 1749620400000
}
```

**错误响应**：无

**数据脱敏规则**
- `realName`：姓保留，名掩码
- `email`：@前保留首字
- `phone`：前3后4掩码

---

### 5.2 POST /api/users — 创建用户

**权限要求**：ADMIN

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| username | body | 是 | String | 用户名（最大 50 字符，唯一） |
| password | body | 是 | String | 密码（最小 8 字符，需包含字母和数字） |
| realName | body | 是 | String | 真实姓名（最大 50 字符） |
| email | body | 否 | String | 邮箱（最大 100 字符，唯一） |
| phone | body | 否 | String | 手机号（最大 30 字符，唯一） |
| gender | body | 否 | String | 性别（MALE / FEMALE） |
| role | body | 是 | String | 角色（STUDENT / TEACHER / ADMIN / ACADEMIC） |
| departmentId | body | 否 | Long | 所属院系 ID |
| majorId | body | 否 | Long | 所属专业 ID |
| classId | body | 否 | Long | 所属班级 ID |
| grade | body | 否 | String | 年级（如"2023"） |
| enrollmentYear | body | 否 | String | 入学年份（如"2023"） |
| studentNo | body | 否 | String | 学号（STUDENT 角色时唯一） |
| teacherNo | body | 否 | String | 工号（TEACHER 角色时唯一） |

**请求体示例**
```json
{
  "username": "zhangsan",
  "password": "Password123",
  "realName": "张三",
  "email": "zhangsan@example.com",
  "phone": "13812341234",
  "gender": "MALE",
  "role": "STUDENT",
  "departmentId": 1,
  "majorId": 1,
  "classId": 1,
  "grade": "2023",
  "enrollmentYear": "2023",
  "studentNo": "2023010101"
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "realName": "张三",
    "email": "zhangsan@example.com",
    "phone": "13812341234",
    "gender": "MALE",
    "role": "STUDENT",
    "departmentId": 1,
    "majorId": 1,
    "classId": 1,
    "grade": "2023",
    "enrollmentYear": "2023",
    "status": 1,
    "casBound": false,
    "createdAt": "2026-06-11T10:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 400 | 请求参数错误 |
| 409 | 5002 | 用户名已存在 |
| 409 | 5003 | 学号/工号已存在 |
| 409 | 5004 | 邮箱已存在 |

**业务规则**
- 密码使用 bcrypt 加密存储
- 创建操作写入 `operation_logs`

---

### 5.3 GET /api/users/{id} — 获取用户详情

**权限要求**：ADMIN / ACADEMIC（可查看任意用户）；TEACHER（仅查看本院系/本专业/本班级的学生）；用户本人

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 用户 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "realName": "张*",
    "email": "j***@example.com",
    "phone": "138****1234",
    "gender": "MALE",
    "avatar": "https://cdn.example.com/avatars/1.png",
    "role": "STUDENT",
    "departmentId": 1,
    "departmentName": "计算机学院",
    "majorId": 1,
    "majorName": "计算机科学与技术",
    "classId": 1,
    "className": "计科2301班",
    "grade": "2023",
    "enrollmentYear": "2023",
    "graduationYear": "2027",
    "status": 1,
    "casBound": true,
    "lastLoginAt": "2026-06-11T10:00:00+08:00",
    "createdAt": "2023-09-01T00:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 5001 | 用户不存在 |
| 403 | 403 | 无权限查看此用户 |

---

### 5.4 PUT /api/users/{id} — 更新用户

**权限要求**：ADMIN（可更新任意用户）；用户本人（仅可更新自己的基本信息，不包括 role/status）

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 用户 ID |

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| realName | body | 否 | String | 真实姓名（最大 50 字符） |
| email | body | 否 | String | 邮箱（最大 100 字符，唯一） |
| phone | body | 否 | String | 手机号（最大 30 字符，唯一） |
| gender | body | 否 | String | 性别（MALE / FEMALE） |
| avatar | body | 否 | String | 头像 URL（最大 500 字符） |
| departmentId | body | 否 | Long | 所属院系 ID（ADMIN 专有） |
| majorId | body | 否 | Long | 所属专业 ID（ADMIN 专有） |
| classId | body | 否 | Long | 所属班级 ID（ADMIN 专有） |
| grade | body | 否 | String | 年级（ADMIN 专有） |
| enrollmentYear | body | 否 | String | 入学年份（ADMIN 专有） |
| role | body | 否 | String | 角色（ADMIN 专有） |

**请求体示例**
```json
{
  "realName": "张三（更新）",
  "email": "zhangsan-new@example.com"
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "realName": "张三（更新）",
    "email": "zhangsan-new@example.com",
    "phone": "13812341234",
    "gender": "MALE",
    "role": "STUDENT",
    "departmentId": 1,
    "status": 1,
    "updatedAt": "2026-06-11T10:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 5001 | 用户不存在 |
| 403 | 403 | 无权限修改此用户 |
| 409 | 5004 | 邮箱已存在 |

**业务规则**
- 用户本人不能修改自己的 role 和 status
- 修改操作写入 `operation_logs`
- 敏感字段变更（如邮箱、手机号）需记录审计日志

---

### 5.5 PUT /api/users/{id}/status — 修改用户状态

**权限要求**：ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 用户 ID |

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| status | body | 是 | Integer | 状态（0=禁用/删除, 1=启用） |

**请求体示例**
```json
{
  "status": 0
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "status": 0,
    "deletedAt": "2026-06-11T10:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 5001 | 用户不存在 |
| 400 | 400 | 不能启用已删除的用户 |

**业务规则**
- `status = 0` 时设置 `deleted_at = NOW()`（软删除）
- `status = 1` 时清除 `deleted_at`（恢复）
- 软删除后用户数据保留 180 天
- 状态变更写入 `operation_logs`
- 用户被禁用后，所有 JWT Token 立即失效
- 选课记录标记为 `DROPPED`

---

## 6. 选课管理（10个）

### 6.1 POST /api/enrollments — 创建选课

**权限要求**：STUDENT

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| courseId | body | 是 | Long | 课程 ID |
| sourceChannel | body | 否 | String | 选课来源：SEARCH / RECOMMEND / QRCODE / MANUAL / PAYMENT。PAYMENT 表示订单支付后自动选课，跳过付费检查 |

**请求体示例**
```json
{
  "courseId": 1,
  "sourceChannel": "SEARCH"
}
```

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "courseId": 1,
    "courseName": "Python 入门",
    "userId": 1,
    "userName": "张三",
    "progress": 0,
    "completed": false,
    "enrollmentStatus": "ENROLLED",
    "sourceChannel": "SEARCH",
    "enrolledAt": "2026-06-11T10:00:00+08:00"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | 6007 | 课程未发布或已下架 |
| 400 | 9005 | 该课程为付费课程，请先购买 |
| 404 | 6001 | 课程不存在 |
| 503 | 1008 | 选课服务暂时不可用 |
| 409 | 8002 | 已存在选课记录（幂等返回） |

**业务规则**
- 课程满员时自动进入 WAITLIST（候补队列），按 FIFO 顺序自动录取
- 付费课程（free=false）必须 sourceChannel=PAYMENT 才能选课
- 退课后可重新选课：物理删除旧 CANCELLED 记录后走正常流程
- 行级锁（SELECT ... FOR UPDATE）+ 原子 SQL 防超卖
- 选课成功后异步写入 enrollment_histories 审计轨迹 + 发送通知

---

### 6.2 GET /api/enrollments/my — 我的选课列表

**权限要求**：已认证（本人数据）

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| completed | query | 否 | Boolean | 是否完成（不传返回全部，true=已完成，false=进行中） |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": [
    {
      "id": 1,
      "courseId": 1,
      "courseName": "Python 入门",
      "courseTitle": "Python 入门",
      "coverUrl": "https://...",
      "teacherName": "李老师",
      "progress": 65,
      "completed": false,
      "finalScore": null,
      "enrollmentStatus": "ENROLLED",
      "sourceChannel": "SEARCH",
      "enrolledAt": "2026-06-11T10:00:00+08:00",
      "completedAt": null
    }
  ],
  "timestamp": 1749620400000
}
```

**错误响应**：无

**业务规则**
- 从 JWT 中获取 userId，不传参
- 过滤 CANCELLED 状态的记录
- 按 enrolled_at DESC 排序

---

### 6.3 GET /api/enrollments — 选课分页查询

**权限要求**：TEACHER / ADMIN / ACADEMIC

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| page | query | 否 | Integer | 页码（0-based，默认 0） |
| size | query | 否 | Integer | 每页条数（默认 10，最大 10000） |
| teacherId | query | 否 | Long | 教师 ID（TEACHER 角色自动覆写为本人） |
| studentName | query | 否 | String | 学员姓名（模糊匹配） |
| courseName | query | 否 | String | 课程名称（模糊匹配） |
| status | query | 否 | String | 选课状态（PENDING / APPROVED / WAITLIST / ENROLLED / CANCELLED / REJECTED / COMPLETED / DROPPED） |
| className | query | 否 | String | 班级名称（服务端关联过滤） |
| majorName | query | 否 | String | 专业名称（服务端关联过滤） |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 1,
        "courseId": 1,
        "courseName": "Python 入门",
        "userId": 1,
        "userName": "张三",
        "className": "计科2301班",
        "majorName": "计算机科学与技术",
        "progress": 65,
        "completed": false,
        "enrollmentStatus": "ENROLLED",
        "enrolledAt": "2026-06-11T10:00:00+08:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": 1749620400000
}
```

**错误响应**：无

**安全规则**
- TEACHER 只能查自己课程的学员（teacherId 自动覆写为当前用户）
- 服务端按 className/majorName 做关联过滤（非前端过滤）

---

### 6.4 GET /api/enrollments/course/{courseId} — 课程学员列表

**权限要求**：TEACHER / ADMIN / ACADEMIC

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| courseId | path | 是 | Long | 课程 ID |
| page | query | 否 | Integer | 页码（0-based，默认 0） |
| size | query | 否 | Integer | 每页条数（默认 10，最大 10000） |

**成功响应**：同 6.3 的分页格式

**安全规则**
- TEACHER 必须为课程 owner（assertCourseOwnership），否则 403

---

### 6.5 GET /api/enrollments/{id} — 选课详情

**权限要求**：STUDENT（本人）/ TEACHER（课程创建者）/ ADMIN / ACADEMIC

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 选课记录 ID |

**成功响应**：同 6.1 的 VO 格式

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 8001 | 选课记录不存在 |
| 403 | 10003 | 无权限操作（非本人/非课主/非管理员） |

---

### 6.6 PUT /api/enrollments/{id} — 更新选课

**权限要求**：TEACHER / ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 选课记录 ID |

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| progress | body | 否 | Double | 学习进度（0-100） |
| completed | body | 否 | Boolean | 是否完成（true 时自动颁发证书） |
| finalScore | body | 否 | BigDecimal | 总评成绩 |
| finalGrade | body | 否 | String | 成绩等级（EXCELLENT / GOOD / PASS / FAIL） |
| enrollmentStatus | body | 否 | String | 选课状态变更（走状态机白名单校验） |

**成功响应**：同 6.1 的 VO 格式

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 8001 | 选课记录不存在 |
| 403 | 10003 | 无权限（非课主/非管理员） |
| 400 | 8004 | 不允许的状态转换 |

**业务规则**
- 状态变更走 EnrollmentStatus.canTransitionTo() 白名单校验
- 状态变更写入 enrollment_histories
- completed=true 时自动调用 certificateService.issueCertificate()

---

### 6.7 DELETE /api/enrollments/{id} — 取消选课

**权限要求**：STUDENT（本人）/ ADMIN

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| id | 是 | Long | 选课记录 ID |

**成功响应**
```json
{
  "code": 200,
  "message": "ok",
  "data": null,
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 8001 | 选课记录不存在 |
| 403 | 10003 | 无权限（非本人/非管理员） |
| 400 | 8004 | 不允许的状态转换 |

**业务规则**
- IDOR 校验：STUDENT 仅能取消本人选课
- 取消后同步 courses.student_count -1（原子操作）
- 取消后自动晋升候补队列第一名（WAITLIST → APPROVED）
- 若有关联 PAID 订单则触发退款
- 写入 enrollment_histories 审计轨迹

---

### 6.8 GET /api/enrollments/course/{courseId}/ranking — 课程排行

**权限要求**：已认证

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| courseId | 是 | Long | 课程 ID |

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| limit | query | 否 | Integer | 返回条数（默认 10，最大 100） |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": [
    {
      "rank": 1,
      "userId": null,
      "userName": "匿名",
      "progress": 100,
      "completed": true,
      "isCurrentUser": false
    },
    {
      "rank": 2,
      "userId": 1,
      "userName": "张三",
      "progress": 65,
      "completed": false,
      "isCurrentUser": true
    }
  ],
  "timestamp": 1749620400000
}
```

**安全规则**
- 仅本人可看到自己的真实 userId 和 userName（用于"我"的高亮）
- 其他用户显示 userId=null，userName="匿名"（数据隔离）

---

### 6.9 GET /api/enrollments/export — 导出课程学员

**权限要求**：TEACHER / ADMIN / ACADEMIC

**请求参数**

| 参数名 | 位置 | 必填 | 类型 | 说明 |
|--------|------|------|------|------|
| courseId | query | 是 | Long | 课程 ID |

**成功响应**：application/vnd.openxmlformats-officedocument.spreadsheetml.sheet Excel 文件

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 403 | 10003 | TEACHER 非课程 owner 时无权限 |

**业务规则**
- 使用 Hutool ExcelWriter 导出
- 含：选课ID/课程ID/课程名称/用户ID/学生姓名/学习进度/是否完成/总评成绩/成绩等级/选课状态/选课来源/选课时间/完成时间

---

### 6.10 GET /api/enrollments/student-detail/{userId} — 学员详情

**权限要求**：TEACHER（仅自己课程中的学生）/ ADMIN / ACADEMIC

**路径参数**

| 参数名 | 必填 | 类型 | 说明 |
|--------|------|------|------|
| userId | 是 | Long | 学生用户 ID |

**成功响应示例**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "userId": 1,
    "username": "zhangsan",
    "realName": "张三",
    "email": "zhangsan@example.com",
    "phone": "13812341234",
    "className": "计科2301班",
    "majorName": "计算机科学与技术"
  },
  "timestamp": 1749620400000
}
```

**错误响应**

| 状态码 | code | 说明 |
|--------|------|------|
| 404 | 5001 | 用户不存在 |
| 403 | 10003 | TEACHER 查非本课程学生时无权限 |

**安全规则**
- TEACHER 通过 `countByTeacherAndStudent` 检查学生是否在授课程中
- 管理员/教务处可直接查看

---

## 附录：数据结构参考

### 用户状态（users.status）

| 值 | 名称 | 说明 |
|----|------|------|
| 0 | INACTIVE | 未激活（初始状态） |
| 1 | ACTIVE | 正常 |
| 2 | DISABLED | 禁用 |
| 3 | DELETED | 已删除（软删除） |

### 性别（gender）

| 值 | 说明 |
|------|------|
| MALE | 男 |
| FEMALE | 女 |

---

## 附录 A：预留 API 接口（Phase 2）

以下接口在本 Phase 不实现，仅作框架预留：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/discussions/posts | 讨论帖列表 (Phase 2) |
| POST | /api/discussions/posts | 发帖 (Phase 2) |
| GET | /api/discussions/posts/{id} | 帖子详情 (Phase 2) |
| POST | /api/discussions/comments | 回复 (Phase 2) |

---

## 修订记录

| 版本 | 日期 | 说明 | 作者 |
|------|------|------|------|
| v1.5 | 2026-07-17 | P1 Stage 2 增量：新增 3 个 section 资源端点(POST quizzes/tasks/reflections)、V197/198/199 migration(3 张新表) | opencode |
| v1.4 | 2026-07-17 | P1 Stage 1 增量：CourseCreateRequest 新增 hid/totalHours/totalWeeks/teachingPhilosophy/learningMode/evaluationScheme(V194)；ChapterCreateRequest 新增 no/anchorPoint/coreQuestion/chapterHours(V195)；SectionCreateRequest 新增 no/learningObjectives/anchorScenarioStep/coreCompetency/coursewareType/audioStrategy(V196) | opencode |
| v1.3 | 2026-07-17 | 增量 Phase 11.6（R4 修复）：新增 POST /api/courses/{courseId}/sections/{sectionId}/audio/batch 批量上传端点（15 段）、GET /api/courses/{courseId}/slides/pages 响应增加 segmentAudios 数组 + htmlContent 占位符动态替换（`AUDIO_SEG_NN_URL` → 真实 URL）、slide_pages.segment_count 自动设置。修复 multipart 临时目录持久化（V193 migration 更新 section 573 旧版 HTML） | opencode |
| v1.2 | 2026-07-10 | 增量 Phase 11.5（HTML 互动课件扩展）：新增 /api/courses/{courseId}/slides/upload 端点的 HTML 分支、HTML 错误码 16009-16012、slide_pages.content_type/html_content 字段说明 | 架构师 |
| v1.1 | 2026-06-11 | 修复分页格式（统一为 items）、完善 JWT Payload 结构、预留讨论区 API | 架构师 |
| v1.0 | 2026-06-11 | 初始版本，覆盖 Phase 1 共 24 个 API | 架构师 |

---

> 文档状态：正式发布
> 下次评审：Phase 2 启动前

---

## Phase 11.5: 互动课件 HTML 扩展 API（V177 增量）

> 本节记录 Phase 11 互动课件插件扩展的 HTML 课件支持。详细设计见 docs/开发规划/phase11-interactive-course-spec.md §3.2.1 和 openspec/changes/html-interactive-extension/。

### POST /api/courses/{courseId}/slides/upload — 上传课件

**功能**：教师上传 PPTX 或 HTML 课件。后端按文件扩展名自动分支：
- `.pptx` → 走 Apache POI 异步渲染为 PNG（原有 phase 11 流程）
- `.html`/`.htm` → 走 HtmlSanitizer 消毒后入库，`content_type='HTML_DIRECT'`

**权限**：TEACHER（课程所有者）或 ADMIN；需 `plugin.interactive.enabled=true`

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | ✅ | .pptx（≤50MB）或 .html/.htm（≤5MB） |
| chapterId | long | ❌ | 关联章节 ID |

**响应**（200 OK）：
```json
{
  "code": 200,
  "data": {
    "slideId": 123,
    "totalPages": 0,
    "status": 0,
    "message": "上传成功，正在后台渲染..."
  }
}
```

HTML 直传响应中 `totalPages=1, status=2`（已就绪，无需渲染）。

**错误码**：

| code | http | 含义 |
|------|------|------|
| 400 | PPT_FORMAT_INVALID | .pptx 文件 ZIP 魔数校验失败 |
| 400 | HTML_INVALID (16009) | HTML 文件读取失败 |
| 400 | HTML_SANITIZE_REMOVED_ALL (16012) | HTML 全部被消毒策略移除（如仅含 `<script>`） |
| 413 | HTML_TOO_LARGE (16010) | HTML 文件超过 5MB |
| 413 | HTML_CONTENT_TOO_LARGE (16011) | HTML 文本内容超过 5MB |
| 5002 | 400 | 业务校验失败 |

### GET /api/courses/{courseId}/slides/pages/{pageNumber} — 获取单页

**功能**：返回单个 slide_page 详情。`contentType` 和 `htmlContent` 字段为 V177 新增。

**响应**（200 OK）：
```json
{
  "code": 200,
  "data": {
    "pageNumber": 1,
    "contentType": "HTML_DIRECT",
    "htmlContent": "<p>sanitized HTML</p>",
    "imageUrl": null,
    "narrationScript": "...",
    "narrationStatus": "PENDING",
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

**contentType 取值**：
- `PPT_RENDERED`（默认）：POI 渲染的 PNG 课时，`imageUrl` 有值，`htmlContent` 为 null
- `HTML_DIRECT`：教师直传 HTML 课时，`htmlContent` 有值（已消毒），`imageUrl` 为 null

### 数据结构参考

**slide_pages.contentType 字段**（V177 新增）：
- 类型：VARCHAR(20)
- 约束：NOT NULL, DEFAULT 'PPT_RENDERED', CHECK IN ('PPT_RENDERED', 'HTML_DIRECT')

**slide_pages.htmlContent 字段**（V177 新增）：
- 类型：TEXT（可空）
- 说明：仅 contentType='HTML_DIRECT' 时有值；已通过 HtmlSanitizer 消毒

---

## Phase 11.6: HTML 单页多段音频架构（R4 修复 - 2026-07-17）

> 本节记录 R3 验收 4/10 FAIL → R4 7/7 PASS 的修复内容。
> 根因：Spring Boot multipart 临时目录默认走 `${java.io.tmpdir}/tomcat.PORT.<random>/work/...`，
> 容器重启后 random 后缀变化 + `MultipartFile.transferTo()` 把相对路径目标错位拼到 multipart.location 下，
> 导致 `FileNotFoundException`，15 段批量上传全部失败。
> 修复：multipart.location 持久化 + `Files.copy(inputStream, ...)` 替代 `transferTo()` + V193 migration 更新旧 HTML。

### POST /api/courses/{courseId}/sections/{sectionId}/audio/batch — 批量上传分段音频

**功能**：教师上传 15 段 HTML 课件分段音频。后端按文件名 `file_1..file_15` 接收，原子写入磁盘，DB 自动更新 `slide_pages.narration_audio_url/segment_count=15`。

**权限**：TEACHER（课程所有者）或 ADMIN；需 `plugin.interactive.enabled=true`

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file_1..file_15 | file | ✅ | .mp3/.wav/.m4a（每段 ≤50MB，共 15 段） |

**响应**（200 OK）：
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "audioUrl": null,
    "duration": 86,
    "audioSize": 1388724,
    "audioFormat": "mp3",
    "sampleRate": 32000,
    "segmentCount": 15,
    "mergedAudioUrl": "/api/courses/52/slides/pages/1/audio?sectionId=573&v=2&merged=true&token=418e2d419d7644bda0af3268e3ac574a"
  }
}
```

**关键字段**：
- `audioUrl`：单文件上传时有值；批量上传时为 null（由 `mergedAudioUrl` 替代）
- `mergedAudioUrl`：批量上传的总入口 URL，含 `merged=true` 和 token，供前端 fallback
- `segmentCount`：固定 15，与上传文件数一致

**错误码**：

| code | http | 含义 |
|------|------|------|
| 9005 | 400 | 分段音频文件保存失败（磁盘 IO 错误 / multipart 临时目录不存在） |
| 21001 | 401 | API Key 无效（X-API-Key 头） |
| 1005 | 401 | 未登录（无 X-API-Key + 无 JWT） |

### GET /api/courses/{courseId}/slides/pages?sectionId=N — 响应增强

**R4 增量**：响应 `data[]` 中每页新增以下字段，且 `htmlContent` 自动占位符替换：

| 字段 | 类型 | 说明 |
|------|------|------|
| **segmentAudios** | `SegmentAudioVO[]` | 当 `segmentCount > 1` 时派生 15 条 URL，URL 从 `narrationAudioUrl` 提取 token 后按 `/pages/N/audio` 模板重写 |
| **segmentAudio** | `SegmentAudioVO` | 单文件上传时使用（URL + duration） |
| **htmlContent** | string | 当 `contentType='HTML_DIRECT'` 且含 `AUDIO_SEG_NN_URL` 占位符时，**自动替换为真实 URL** |

**SegmentAudioVO 结构**：
```json
{
  "pageNumber": 1,
  "url": "/api/courses/52/slides/pages/1/audio?sectionId=573&v=2&token=418e2d419d7644bda0af3268e3ac574a",
  "duration": 86
}
```

**占位符替换规则**（服务端 toPageVO 触发）：
- 模式：`AUDIO_SEG_NN_URL`（NN 为 01-15，左补零）
- 替换为：`{baseUrlPrefix}/pages/{NN}{baseUrlSuffix}`
- 例：`AUDIO_SEG_03_URL` → `/api/courses/52/slides/pages/3?sectionId=573&v=2&token=...`
- 触发条件：`contentType='HTML_DIRECT' && htmlContent.contains("AUDIO_SEG_") && narrationAudioUrl != null`

### 数据结构参考

**slide_pages.segment_count 字段**（V191 新增，R4 自动更新）：
- 类型：INTEGER（可空）
- 说明：批量上传成功后由 `AudioUploadServiceImpl.uploadBatch()` line 201 自动设置 `setSegmentCount(segCount)`（=上传文件数）
- 历史问题：R3 时 DB 存 `segmentCount=1`，导致 `toPageVO()` line 638 `segCount > 1` 不触发，`segmentAudios` 永远 null

### 部署变更（影响生产环境）

| 文件 | 变更 | 原因 |
|------|------|------|
| `application.yml` | `spring.servlet.multipart.location: /data/uploads/tmp` | 持久化 multipart 临时目录 |
| `application.yml` | `spring.flyway.placeholder-replacement: false` | V193 SQL 含 HTML 内嵌的 `${demoIdx + 1}` JS 模板字符串 |
| `Dockerfile` | `mkdir -p /data/uploads/tmp` | 配合 multipart.location |
| `docker-compose.yml` | `+slides_data:/data/slides` + `+multipart_tmp:/data/uploads/tmp` volume | 容器重启后音频文件不丢失 |
| `V193__update_section_573_html.sql` | 新建，UPDATE `slide_pages.html_content` 为完整 50920 字节 | 替换旧的 41613 字节无占位符 HTML |
| `AudioUploadServiceImpl.java` | `file.transferTo()` → `Files.copy(inputStream, ...)` | 修复 Spring Part.write() 相对路径错位 bug |

### 验收（同一脚本对比）

- **R3 基线**：4/10 FAIL — `99-元数据/baselines/R3/` 留存
- **R4 实测**：7/7 PASS（本地容器）— `_r3_verify_local.sh` 输出见部署 commit
- **R4 部署后**：Trae 端 `_r3_verify.sh`（打生产 URL）必须 10/10 PASS 才算修复闭环

### Phase 11.7: section 资源 API（P1 Stage 2 - 2026-07-17）

> 新增 3 个 section 级资源创建端点。所有端点使用统一鉴权规则：
> `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`

#### POST /api/courses/{courseId}/sections/{sectionId}/quizzes — 创建自测题

**请求**（application/json）：
```json
{
  "slide": 3,
  "prompt": "AI 提效的主要来源是什么？",
  "options": ["A. 算力", "B. 工作流重组", "C. 工具替换", "D. 团队规模"],
  "correctIndex": 1,
  "explanation": "正确解析..."
}
```

**响应**（200 OK）：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "sectionId": 573,
    "slide": 3,
    "prompt": "...",
    "correctIndex": 1,
    "explanation": "...",
    "createdAt": "..."
  }
}
```

| 字段 | 类型 | 必填 | 校验 | 说明 |
|---|---|---|---|---|
| slide | Integer | ✅ | @Min(1) | 关联 slide 序号 |
| prompt | String | ✅ | @NotBlank ≤2000 | 题目文本 |
| options | String[] | ✅ | @Size 2-10 | 选项数组，每项 ≤100 |
| correctIndex | Integer | ✅ | @Min(0) | 正确选项索引 |
| explanation | String | ❌ | ≤2000 | 答案解析 |

#### POST /api/courses/{courseId}/sections/{sectionId}/tasks — 创建截图任务

**请求**：
```json
{
  "slide": 12,
  "description": "用 AI 工具跑本周销售数据，截图上传"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| slide | Integer | ✅ | 关联 slide 序号 |
| description | String | ✅ | 任务描述 ≤2000 字 |

#### POST /api/courses/{courseId}/sections/{sectionId}/reflections — 创建反思日志

**请求**：
```json
{
  "template": "200 字反思：本周 AI 工具如何改变了你的工作？"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| template | String | ✅ | 反思日志模板 ≤2000 字 |

---

## Phase 6: 微专业 API

> 本文档记录了 Phase 14 微专业模块的 API 契约。详细定义见 docs/开发规划/phase14-micro-specialty-spec.md §7。

### GET /api/micro-specialties/square
课程广场专区数据。公开访问。

请求参数：无
响应示例：
```json
{
  "code": 200,
  "data": {
    "goldFeatured": [
      {"id": 1, "title": "AI 微专业", "coverUrl": "...", "departmentName": "计算机学院", "leadTeacherName": "张教授", "totalCredits": 12.0, "courseCount": 6, "teacherCount": 3, "isGoldFeatured": true, "qualityScore": 9.5}
    ],
    "featured": [...],
    "recruiting": [...]
  }
}
```

### GET /api/micro-specialties/{id}
微专业详情。公开访问（DRAFT/CANCELLED 对非管理员过滤）。

### POST /api/micro-specialties
创建微专业。ACADEMIC 权限。

### POST /api/micro-specialties/{id}/submit | approve | reject | open | close | cancel | archive
状态流转操作。分别对应 LEAD 提交、ACADEMIC 审批、LEAD 开课/结业、ACADEMIC 取消/归档。

### POST /api/micro-specialty-enrollments/apply
学生自主报名。STUDENT 权限。

### POST /api/micro-specialty-enrollments/class-import
班级批量导入。ACADEMIC/ADMIN 权限。100人/批事务。

### POST /api/micro-specialty-proposals
教师提交申报。TEACHER 权限。

### POST /api/micro-specialty-teachers/{inviteId}/accept
教师接受邀请。TEACHER（本人）权限。

### POST /api/micro-specialties/{id}/transfer-leadership
LEAD 继任转移。ACADEMIC 权限。

完整 API 请参考 docs/开发规划/phase14-micro-specialty-spec.md §7（51 个端点，含主表 14+、申报 7、课程编排 4、教师 9、修读 8、置顶 6、统计证书 3）。
