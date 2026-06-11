# 微课管理平台 · API 契约文档 · Phase 1

> 版本：v1.2  
> 日期：2026-06-11  
> 状态：正式发布  
> 范围：用户认证、院系管理、专业管理、班级管理、用户管理（共 27 个 API）

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-11 | 初稿 |
| v1.1 | 2026-06-11 | 修复分页格式（统一为 items）、完善 JWT Payload 结构、预留讨论区 API |
| v1.2 | 2026-06-11 | 补 3 个 /me 端点（PUT /me, PUT /me/password, POST /me/avatar），与权限矩阵 v2.0 §1.1 对齐。依据：穷举审查 E4 P1-3 |

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
    "userId": 1,
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
| v1.1 | 2026-06-11 | 修复分页格式（统一为 items）、完善 JWT Payload 结构、预留讨论区 API | 架构师 |
| v1.0 | 2026-06-11 | 初始版本，覆盖 Phase 1 共 24 个 API | 架构师 |

---

> 文档状态：正式发布
> 下次评审：Phase 2 启动前
