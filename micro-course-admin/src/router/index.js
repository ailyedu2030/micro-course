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
  { path: '/exercises', name: 'ExerciseList', component: () => import('../views/courses/ExerciseList.vue'), meta: { requiresAuth: true } },
  { path: '/discussions', name: 'DiscussionList', component: () => import('../views/courses/DiscussionList.vue'), meta: { requiresAuth: true } },
  { path: '/discussions/:id', name: 'DiscussionDetail', component: () => import('../views/courses/DiscussionDetail.vue'), meta: { requiresAuth: true } },
  { path: '/notifications', name: 'NotificationList', component: () => import('../views/notifications/NotificationList.vue'), meta: { requiresAuth: true } },
  { path: '/courses/review', name: 'CourseReview', component: () => import('../views/courses/CourseReviewList.vue'), meta: { requiresAuth: true } },

  // 管理后台路由
  { path: '/admin/dashboard', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/logs', name: 'OperationLogs', component: () => import('../views/admin/OperationLogs.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },

  // 教师端路由
  { path: '/teacher/dashboard', name: 'TeacherDashboard', component: () => import('../views/teacher/TeacherDashboard.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/students', name: 'StudentList', component: () => import('../views/teacher/StudentList.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/grades', name: 'StudentGrades', component: () => import('../views/teacher/StudentGrades.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },

  // 学生端路由
  { path: '/student/courses', name: 'StudentCourseSquare', component: () => import('../views/student/CourseSquare.vue'), meta: { requiresAuth: true } },
  { path: '/student/courses/:id', name: 'StudentCourseDetail', component: () => import('../views/student/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/student/my-courses', name: 'StudentMyCourses', component: () => import('../views/student/MyCourses.vue'), meta: { requiresAuth: true } },
  { path: '/student/learning', name: 'StudentLearning', component: () => import('../views/student/LearningCenter.vue'), meta: { requiresAuth: true } },
  { path: '/student/notifications', name: 'StudentNotifications', component: () => import('../views/notifications/NotificationList.vue'), meta: { requiresAuth: true } },
  { path: '/student/profile', name: 'StudentProfile', component: () => import('../views/student/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/student/redirect', redirect: '/student/courses' },
  { path: '/student/chapters/:chapterId/exercises', name: 'StudentExerciseTake', component: () => import('../views/student/ExerciseTake.vue'), meta: { requiresAuth: true } },
  { path: '/student/discussions', name: 'StudentDiscussion', component: () => import('../views/student/DiscussionView.vue'), meta: { requiresAuth: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth !== false && !isAuthenticated()) return next('/login')
  if (to.path === '/login' && isAuthenticated()) return next('/')
  if (to.meta.roles && to.meta.roles.length > 0) {
    const userRole = localStorage.getItem('userRole') || ''
    if (!to.meta.roles.includes(userRole)) return next('/')
  }
  next()
})

export default router