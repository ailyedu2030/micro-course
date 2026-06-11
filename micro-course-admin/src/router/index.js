import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '../utils/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/auth/Login.vue'), meta: { requiresAuth: false } },
  { path: '/', redirect: '/departments' },
  { path: '/departments', name: 'DepartmentList', component: () => import('../views/departments/DepartmentList.vue'), meta: { requiresAuth: true } },
  { path: '/majors', name: 'MajorList', component: () => import('../views/majors/MajorList.vue'), meta: { requiresAuth: true } },
  { path: '/classes', name: 'ClassList', component: () => import('../views/classes/ClassList.vue'), meta: { requiresAuth: true } },
  { path: '/users', name: 'UserList', component: () => import('../views/users/UserList.vue'), meta: { requiresAuth: true } },
  { path: '/users/create', name: 'UserCreate', component: () => import('../views/users/UserForm.vue'), meta: { requiresAuth: true } },
  { path: '/users/:id/edit', name: 'UserEdit', component: () => import('../views/users/UserForm.vue'), meta: { requiresAuth: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth !== false && !isAuthenticated()) next('/login')
  else if (to.path === '/login' && isAuthenticated()) next('/')
  else next()
})

export default router
