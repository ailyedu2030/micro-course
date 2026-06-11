# 前端 API 使用参考

> 源文档：`microcourse/references/api-contract.md` v1.2

## 1. Auth 域

```js
// 登录 — POST /api/auth/login
export function login(data) {
  return request({ method: 'POST', url: '/auth/login', data })
}
// 响应：data = { accessToken, refreshToken, expiresIn, tokenType }

// 获取当前用户 — GET /api/auth/me
export function getCurrentUser() {
  return request({ method: 'GET', url: '/auth/me' })
}

// 登出 — POST /api/auth/logout
export function logout() {
  return request({ method: 'POST', url: '/auth/logout' })
}

// 刷新 Token — POST /api/auth/refresh
export function refreshToken(data) {
  return request({ method: 'POST', url: '/auth/refresh', data })
}

// 更新当前用户 — PUT /api/auth/me
export function updateCurrentUser(data) {
  return request({ method: 'PUT', url: '/auth/me', data })
}

// 修改密码 — PUT /api/auth/me/password
export function updatePassword(data) {
  return request({ method: 'PUT', url: '/auth/me/password', data })
}

// 上传头像 — POST /api/auth/me/avatar（multipart）
export function uploadAvatar(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request({ method: 'POST', url: '/auth/me/avatar', data: fd,
    headers: { 'Content-Type': 'multipart/form-data' } })
}
```

## 2. CRUD 域（Department / Major / Class）

```js
// 列表（分页）
export function getDepartments(params) {
  return request({ method: 'GET', url: '/departments', params })
}
// params: { page, size, keyword, code, departmentId/majorId/grade 等 }

// 详情
export function getDepartmentById(id) {
  return request({ method: 'GET', url: `/departments/${id}` })
}

// 创建
export function createDepartment(data) {
  return request({ method: 'POST', url: '/departments', data })
}

// 更新
export function updateDepartment(id, data) {
  return request({ method: 'PUT', url: `/departments/${id}`, data })
}

// 删除
export function deleteDepartment(id) {
  return request({ method: 'DELETE', url: `/departments/${id}` })
}
```

## 3. User 域

```js
// 同 CRUD 域 + 额外一个：
export function updateUserStatus(id, data) {
  return request({ method: 'PUT', url: `/users/${id}/status`, data })
}
// data = { status: 0 }  或  { status: 1 }
```

## 4. 响应处理

```js
// request.js 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res  // 返回 { code, message, data, timestamp }
  },
  (error) => {
    if (error.response?.status === 401) {
      // Token 过期 → 尝试 refreshToken
    }
    ElMessage.error(error.response?.data?.message || '网络错误')
    return Promise.reject(error)
  }
)
```

## 5. 错误码前端处理

| code | 处理 |
|------|------|
| 1001 | ElMessage.error('用户名或密码错误') |
| 1002/1003 | 跳转登录页 + 提示 |
| 1004 | 静默刷新 Token，失败再跳登录 |
| 1006 | ElMessage.warning('账号已锁定，请30分钟后重试') |
| 2002/3002 | ElMessage.warning('该记录下存在关联数据，无法删除') |
| 409 | ElMessage.warning(res.message) |
| 5002-5004 | 表单字段级错误提示 |
