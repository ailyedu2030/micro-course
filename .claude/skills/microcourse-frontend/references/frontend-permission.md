# 前端权限实现

> 源文档：`microcourse/references/permission-matrix.md` v2.0

## 1. 角色与菜单映射

```js
// 菜单项配置（Layout.vue 侧边栏）
const menuItems = [
  { path: '/users',        title: '用户管理', icon: 'User',      roles: ['ADMIN', 'ACADEMIC'] },
  { path: '/departments',  title: '院系管理', icon: 'OfficeBuilding', roles: ['ADMIN'] },
  { path: '/majors',       title: '专业管理', icon: 'Reading',   roles: ['ADMIN'] },
  { path: '/classes',      title: '班级管理', icon: 'School',    roles: ['ADMIN'] },
]

// 根据角色过滤
const visibleMenu = computed(() =>
  menuItems.filter(item => item.roles.includes(userStore.role))
)
```

## 2. 路由配置

```js
const routes = [
  // 公开路由
  { path: '/login', name: 'Login', component: () => import('../views/auth/Login.vue'),
    meta: { requiresAuth: false } },

  // 需登录路由
  { path: '/users', name: 'UserList', component: () => import('../views/users/UserList.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/users/create', name: 'UserCreate', component: () => import('../views/users/UserForm.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/users/:id/edit', name: 'UserEdit', component: () => import('../views/users/UserForm.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] } },

  // 组织架构路由（仅 ADMIN）
  { path: '/departments', meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/majors',      meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/classes',     meta: { requiresAuth: true, roles: ['ADMIN'] } },

  // 默认
  { path: '/', redirect: '/users' },
]
```

## 3. 路由守卫

```js
router.beforeEach((to, from, next) => {
  const requiresAuth = to.meta.requiresAuth !== false
  const allowedRoles = to.meta.roles || []

  // 未登录访问需登录页 → 跳 /login
  if (requiresAuth && !getToken()) {
    next('/login')
    return
  }

  // 已登录访问 /login → 跳首页
  if (to.path === '/login' && getToken()) {
    next('/')
    return
  }

  // 角色检查
  if (allowedRoles.length > 0 && !allowedRoles.includes(userStore.role)) {
    ElMessage.error('无权限访问')
    next('/')
    return
  }

  next()
})
```

## 4. 权限矩阵速查

| 页面 | STUDENT | TEACHER | ADMIN | ACADEMIC |
|------|:-------:|:-------:|:-----:|:--------:|
| /users（用户列表） | ✗ | ✗ | ✅ | ✅ |
| /users/create（创建用户） | ✗ | ✗ | ✅ | ✗ |
| /users/:id/edit（编辑用户） | ✗ | ✗ | ✅ | ✗ |
| /departments（院系管理） | ✗ | ✗ | ✅ | ✗ |
| /majors（专业管理） | ✗ | ✗ | ✅ | ✗ |
| /classes（班级管理） | ✗ | ✗ | ✅ | ✗ |

**注意**：Phase 1 学生端和教师端不涉及管理员页面，实际不会触发权限拦截。
