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
  { path: '/users/:id/edit', name: 'UserEdit', component: () => import('../views/users/UserForm.vue'), meta: { requiresAuth: true } },
  { path: '/courses', name: 'CourseList', component: () => import('../views/courses/CourseList.vue'), meta: { requiresAuth: true } },
  { path: '/courses/create', name: 'CourseCreate', component: () => import('../views/courses/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/courses/:id', name: 'CourseView', component: () => import('../views/courses/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/courses/:id/edit', name: 'CourseEdit', component: () => import('../views/courses/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/course-categories', name: 'CourseCategoryList', component: () => import('../views/courses/CourseCategoryList.vue'), meta: { requiresAuth: true } },
  { path: '/tags', name: 'TagList', component: () => import('../views/courses/TagList.vue'), meta: { requiresAuth: true } },
  { path: '/chapters', name: 'ChapterList', component: () => import('../views/courses/ChapterList.vue'), meta: { requiresAuth: true } },
  { path: '/videos', name: 'VideoList', component: () => import('../views/courses/VideoList.vue'), meta: { requiresAuth: true } },
  { path: '/enrollments', name: 'EnrollmentList', component: () => import('../views/courses/EnrollmentList.vue'), meta: { requiresAuth: true } },
  { path: '/favorites', name: 'FavoriteList', component: () => import('../views/courses/FavoriteList.vue'), meta: { requiresAuth: true } },
  { path: '/questions', name: 'QuestionList', component: () => import('../views/courses/QuestionList.vue'), meta: { requiresAuth: true } },
  { path: '/exercises', name: 'ExerciseList', component: () => import('../views/courses/ExerciseList.vue'), meta: { requiresAuth: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth !== false && !isAuthenticated()) next('/login')
  else if (to.path === '/login' && isAuthenticated()) next('/')
  else next()
})

export default router