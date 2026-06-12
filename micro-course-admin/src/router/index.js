import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '../utils/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/auth/Login.vue'), meta: { requiresAuth: false } },
  { path: '/', name: 'Home', redirect: '/admin/dashboard' },
  { path: '/profile', redirect: to => { const role = localStorage.getItem('userRole') || ''; if (role === 'STUDENT') return '/student/profile'; return getRoleHomePage(role); } },
  { path: '/departments', name: 'DepartmentList', component: () => import('../views/departments/DepartmentList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/majors', name: 'MajorList', component: () => import('../views/majors/MajorList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/classes', name: 'ClassList', component: () => import('../views/classes/ClassList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/users', name: 'UserList', component: () => import('../views/users/UserList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
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
  { path: '/courses/review', name: 'CourseReview', component: () => import('../views/courses/CourseReviewList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },

  // 管理后台路由
  { path: '/admin/dashboard', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/logs', name: 'OperationLogs', component: () => import('../views/admin/OperationLogs.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/settings', name: 'AdminSettings', component: () => import('../views/admin/AdminSettings.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/teaching-classes', name: 'TeachingClassList', component: () => import('../views/admin/TeachingClassList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },

  // 教务处路由
  { path: '/academic/dashboard', name: 'AcademicDashboard', component: () => import('../views/academic/Dashboard.vue'), meta: { requiresAuth: true, roles: ['ACADEMIC', 'ADMIN'] } },

  // 教师端路由
  { path: '/teacher/dashboard', name: 'TeacherDashboard', component: () => import('../views/teacher/TeacherDashboard.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/students', name: 'StudentList', component: () => import('../views/teacher/StudentList.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/grades', name: 'StudentGrades', component: () => import('../views/teacher/StudentGrades.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/teaching-classes', name: 'TeacherTeachingClasses', component: () => import('../views/teacher/TeacherTeachingClasses.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },

  // 学生端路由
  { path: '/student/courses', name: 'StudentCourseSquare', component: () => import('../views/student/CourseSquare.vue'), meta: { requiresAuth: true } },
  { path: '/student/courses/:id', name: 'StudentCourseDetail', component: () => import('../views/student/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/student/courses/:id/play/:videoId?', name: 'StudentVideoPlay', component: () => import('../views/student/VideoPlayer.vue'), meta: { requiresAuth: true, layout: 'video' } },
  { path: '/student/my-courses', name: 'StudentMyCourses', component: () => import('../views/student/MyCourses.vue'), meta: { requiresAuth: true } },
  { path: '/student/learning', name: 'StudentLearning', component: () => import('../views/student/LearningCenter.vue'), meta: { requiresAuth: true } },
  { path: '/student/notifications', name: 'StudentNotifications', component: () => import('../views/notifications/NotificationList.vue'), meta: { requiresAuth: true } },
  { path: '/student/profile', name: 'StudentProfile', component: () => import('../views/student/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/student/report', name: 'StudentWeeklyReport', component: () => import('../views/student/WeeklyReport.vue'), meta: { requiresAuth: true } },
  { path: '/student/redirect', redirect: '/student/courses' },
  { path: '/student/chapters/:chapterId/exercises', name: 'StudentExerciseTake', component: () => import('../views/student/ExerciseTake.vue'), meta: { requiresAuth: true } },
  { path: '/student/discussions', name: 'StudentDiscussion', component: () => import('../views/student/DiscussionView.vue'), meta: { requiresAuth: true } },
  { path: '/student/reviews', name: 'StudentMyReviews', component: () => import('../views/student/MyReviews.vue'), meta: { requiresAuth: true } },
  { path: '/student/settings', name: 'StudentSettings', component: () => import('../views/student/Settings.vue'), meta: { requiresAuth: true } },
  { path: '/student/achievements', name: 'StudentAchievements', component: () => import('../views/student/AchievementWall.vue'), meta: { requiresAuth: true, roles: ['STUDENT', 'ADMIN'] } },
]

function getRoleHomePage(role) {
  if (role === 'STUDENT') return '/student/courses'
  if (role === 'TEACHER') return '/teacher/dashboard'
  if (role === 'ACADEMIC') return '/academic/dashboard'
  return '/admin/dashboard'
}

const STAFF_ONLY_PATHS = [
  '/departments', '/majors', '/classes',
  '/courses', '/course-categories', '/tags', '/chapters', '/videos',
  '/enrollments', '/favorites', '/questions', '/exercises',
  '/discussions', '/notifications', '/courses/review'
]

function isStaffOnlyPath(path) {
  return STAFF_ONLY_PATHS.some(p => path === p || path.startsWith(p + '/'))
}

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth !== false && !isAuthenticated()) return next('/login')
  const userRole = localStorage.getItem('userRole') || ''
  if (to.path === '/login' && isAuthenticated()) {
    return next(getRoleHomePage(userRole))
  }
  if (to.path === '/' && isAuthenticated()) {
    return next(getRoleHomePage(userRole))
  }
  if (userRole === 'STUDENT' && isStaffOnlyPath(to.path)) {
    return next('/student/courses')
  }
  if (to.meta.roles && to.meta.roles.length > 0) {
    if (!to.meta.roles.includes(userRole)) {
      return next(getRoleHomePage(userRole))
    }
  }
  next()
})

export default router