---
name: microcourse-frontend
description: |
  微课平台前端 Vue 3 开发实施手册。
  当用户在本项目 micro-course-admin/ 下编写 Vue/JavaScript 代码时加载本 skill。

  前置技能：
  - microcourse（项目宪法，自动加载）
  - ui-ux-pro-max（UI/UX 设计体系，强制前置加载）
  用途：提供 API 封装模式、Store 组织模式、权限前端实现、页面模板、Element Plus 使用规范

  与 microcourse/SKILL.md 的关系：
  - microcourse 定义"不能做什么"（25 条禁止项）
  - 本 skill 定义"应该怎么做"（5 大模块）
  - 本 skill 不重复 microcourse 的禁止项，二者互补
---

# 微课平台 · 前端开发技能

## 1. 何时加载本 skill

在 `/Users/jackie/微课平台/micro-course-admin/` 下创建/修改 `.vue` / `.js` 文件时加载。

不加载场景：只读查阅、后端 Java 编写、文档编写。

## 2. 前置技能声明

本 skill **强制加载** [ui-ux-pro-max]（UI/UX 设计体系），确保所有页面组件遵循专业 UI/UX 标准：

- **色彩系统**：基于管理后台场景，使用 Element Plus 默认主题色 #409eff，状态色 success/warning/danger/info 四色
- **间距系统**：4px 基础单位（4/8/12/16/20/24/32），表单间距至少 16px
- **字体/排版**：标题 18-24px，正文 14px，辅助文字 12px
- **圆角/阴影**：卡片 border-radius 8px，按钮 4px，弹窗 8px
- **过渡动画**：折叠/展开 0.3s ease，hover 0.2s
- **可访问性**：对比度 ≥ 4.5:1，焦点环可见，aria-label 必填
- **响应式**：管理后台最小宽度 1280px，表格/表单自适应

## 3. 5 大模块开发规范

### 模块 1：API 封装模式

```js
// 文件：micro-course-admin/src/api/department.js
import request from '../utils/request'

// GET 分页/列表
export function getDepartments(params) {
  return request({ method: 'GET', url: '/departments', params })
}

// GET 详情
export function getDepartmentById(id) {
  return request({ method: 'GET', url: `/departments/${id}` })
}

// POST 创建
export function createDepartment(data) {
  return request({ method: 'POST', url: '/departments', data })
}

// PUT 更新
export function updateDepartment(id, data) {
  return request({ method: 'PUT', url: `/departments/${id}`, data })
}

// DELETE 删除
export function deleteDepartment(id) {
  return request({ method: 'DELETE', url: `/departments/${id}` })
}
```

**铁律**：
- 方法名 = `动词 + 资源`（getDepartments, createDepartment）
- URL 前缀 `/api` 由 `request.js` 的 baseURL 统一处理，这里只写相对路径
- 响应拦截器统一处理 `code !== 200` + `401 Token` 刷新

### 模块 2：Store 组织模式

```js
// 文件：micro-course-admin/src/store/user.js
import { defineStore } from 'pinia'
import { getCurrentUser, logout as logoutApi } from '../api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('micro_course_token') || '',
    userInfo: null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.userInfo?.role || '',
    realName: (state) => state.userInfo?.realName || '',
  },
  actions: {
    async login(loginData) { /* ... */ },
    async getInfo() { /* ... */ },
    async logout() { /* ... */ },
  },
})
```

**铁律**：
- 每个域一个 store（user / department / major / class）
- State 只存 token + 用户信息 + 列表数据
- Actions 调 api/ → 更新 state，不直接在组件内调 axios
- 持久化用 localStorage（token） + sessionStorage（临时搜索条件）

### 模块 3：权限前端实现

```js
// 路由守卫（router/index.js）
router.beforeEach((to, from, next) => {
  const requiresAuth = to.meta.requiresAuth !== false
  if (requiresAuth && !getToken()) {
    next('/login')
  } else if (to.path === '/login' && getToken()) {
    next('/users')
  } else {
    next()
  }
})

// 路由 meta 定义
{ path: '/users', meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } }
{ path: '/departments', meta: { requiresAuth: true, roles: ['ADMIN'] } }
```

**铁律**：
- `meta.requiresAuth` 控制登录态
- `meta.roles` 控制角色级权限
- **不需要** `<v-permission>` 指令（Phase 1 不需要按钮级权限）

### 模块 4：页面模板

#### 4.1 列表页模板（UserList / DepartmentList / MajorList / ClassList）

```
<template>
  <div class="xxx-list-container">
    <!-- 搜索区 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" clearable @clear="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="handleCreate">新增</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" stripe border>
        <!-- 列定义 -->
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          :page-size="pagination.size"
          :total="pagination.totalElements"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </el-card>

    <!-- 弹窗（创建/编辑） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" :rules="rules">...</el-form>
      <template #footer>...</template>
    </el-dialog>
  </div>
</template>
```

#### 4.2 表单双模式（UserForm.vue）

```js
// create 模式 vs edit 模式
const isEdit = computed(() => !!route.params.id)

// 初始化
if (isEdit.value) {
  const res = await getUserById(route.params.id)
  Object.assign(form, res.data)
}
```

### 模块 5：Element Plus 使用规范

| 场景 | 组件 | 用法 |
|------|------|------|
| 搜索区 | `el-card` + `el-form :inline="true"` | 标准间距 16px |
| 表格 | `el-table` + `el-table-column` | stripe + border，操作列 fixed="right" |
| 分页 | `el-pagination` | `:total="totalElements"`，size 默认 20 |
| 弹窗 | `el-dialog` | width=500px，footer 含"取消"+"保存" |
| 表单 | `el-form` + `el-input/el-select/el-cascader` | label-width="100px"，rules 含 required |
| 消息 | `ElMessage.success/error` | 简洁，≤ 15 字 |
| 确认 | `ElMessageBox.confirm` | 危险操作（删除）必须走确认弹窗 |

**关键字段映射**（前端 → 后端）：

| 前端字段 | 后端 PageResult | 说明 |
|---------|---------------|------|
| `res.data.items` | `items` | 列表数据 |
| `res.data.totalElements` | `totalElements` | 总记录数 ← **注意不是 total** |
| `res.data.totalPages` | `totalPages` | 总页数 |
| `res.data.page` | `page` | 当前页（0-based） |
| `res.data.size` | `size` | 每页条数 |

---

## 4. 详细参考

- **API 封装模式** → `references/frontend-api-usage.md`
- **TS 类型定义** → `references/frontend-data-types.md`
- **权限前端实现** → `references/frontend-permission.md`
- **UI/UX 规则** → `references/ui-ux-rules.md`（摘自 ui-ux-pro-max）
- **交付门禁** → `references/frontend-checklist.md`

## 5. 与 microcourse skill 共享的资源

- `microcourse/references/api-contract.md` → 18 个 API 端点清单 + 响应格式
- `microcourse/references/data-contract.md` → 字段类型映射
- `microcourse/references/permission-matrix.md` → 角色权限

---

*skill 版本：v1.0*
*最后更新：2026-06-11*
*维护者：总工程师*
