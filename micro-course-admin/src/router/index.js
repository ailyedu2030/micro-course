import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '../utils/auth'
import { useUserStore } from '../store/user'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/auth/Login.vue'), meta: { requiresAuth: false } },
  { path: '/', name: 'Home', redirect: '/admin/dashboard' },
  // P0-2: 从 userStore 读取角色（beforeEach 中已填充），避免与 sessionStorage 双源不一致
  { path: '/profile', name: 'Profile', component: () => import('../views/student/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/departments', name: 'DepartmentList', component: () => import('../views/departments/DepartmentList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/majors', name: 'MajorList', component: () => import('../views/majors/MajorList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/classes', name: 'ClassList', component: () => import('../views/classes/ClassList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/users', name: 'UserList', component: () => import('../views/users/UserList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/users/create', name: 'UserCreate', component: () => import('../views/users/UserForm.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/users/:id/edit', name: 'UserEdit', component: () => import('../views/users/UserForm.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/courses', name: 'CourseList', component: () => import('../views/courses/CourseList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/courses/create', name: 'CourseCreate', component: () => import('../views/courses/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/courses/:id', name: 'CourseView', component: () => import('../views/courses/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/courses/:id/edit', name: 'CourseEdit', component: () => import('../views/courses/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/course-categories', name: 'CourseCategoryList', component: () => import('../views/courses/CourseCategoryList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/tags', name: 'TagList', component: () => import('../views/courses/TagList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/chapters', name: 'ChapterList', component: () => import('../views/courses/ChapterList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC', 'TEACHER'] } },
  { path: '/videos', name: 'VideoList', component: () => import('../views/courses/VideoList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC', 'TEACHER'] } },
  { path: '/enrollments', name: 'EnrollmentList', component: () => import('../views/courses/EnrollmentList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/favorites', name: 'FavoriteList', component: () => import('../views/courses/FavoriteList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/questions', name: 'QuestionList', component: () => import('../views/courses/QuestionList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC', 'TEACHER'] } },
  { path: '/exercises', name: 'ExerciseList', component: () => import('../views/courses/ExerciseList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC', 'TEACHER'] } },
  { path: '/courses/:courseId/exercises/form', name: 'ExerciseForm', component: () => import('../views/courses/ExerciseForm.vue'), meta: { requiresAuth: true } },
  { path: '/discussions', name: 'DiscussionList', component: () => import('../views/courses/DiscussionList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC', 'TEACHER'] } },
  { path: '/reviews', name: 'ReviewManagement', component: () => import('../views/admin/ReviewsManagement.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/discussions/:id', name: 'DiscussionDetail', component: () => import('../views/courses/DiscussionDetail.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC', 'TEACHER'] } },
  { path: '/notifications', name: 'NotificationList', component: () => import('../views/notifications/NotificationList.vue'), meta: { requiresAuth: true } },
  { path: '/courses/review', name: 'CourseApproval', component: () => import('../views/courses/CourseApproval.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/bundles', name: 'BundleList', component: () => import('../views/courses/BundleList.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN', 'ACADEMIC'] } },

  { path: '/admin', redirect: '/admin/dashboard' },
  // 管理后台路由
  { path: '/admin/dashboard', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/users', name: 'AdminUserList', component: () => import('../views/admin/UserList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/logs', name: 'OperationLogs', component: () => import('../views/admin/OperationLogs.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/operation-logs', redirect: '/admin/logs' },
  { path: '/admin/roles', redirect: '/admin/users' },
  { path: '/admin/settings', name: 'AdminSettings', component: () => import('../views/admin/AdminSettings.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/banners', name: 'BannerList', component: () => import('../views/admin/BannerList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },
  { path: '/admin/teaching-classes', name: 'TeachingClassList', component: () => import('../views/admin/TeachingClassList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },

  // 教务处路由
  { path: '/academic/dashboard', name: 'AcademicDashboard', component: () => import('../views/academic/Dashboard.vue'), meta: { requiresAuth: true, roles: ['ACADEMIC', 'ADMIN'] } },
  { path: '/academic/stats', name: 'AcademicStats', component: () => import('../views/academic/LearningAnalytics.vue'), meta: { requiresAuth: true, roles: ['ACADEMIC', 'ADMIN'] } },

  // 教师端路由
  { path: '/teacher/dashboard', name: 'TeacherDashboard', component: () => import('../views/teacher/TeacherDashboard.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/courses', name: 'TeacherCourseList', component: () => import('../views/courses/CourseList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/courses/:id/workspace', name: 'TeacherWorkspace', component: () => import('../views/teacher/workspace/TeacherWorkspace.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/videos', name: 'TeacherVideoList', component: () => import('../views/courses/VideoList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/exercises', name: 'TeacherExerciseList', component: () => import('../views/courses/ExerciseList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/discussions', name: 'TeacherDiscussions', component: () => import('../views/student/DiscussionView.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/favorites', name: 'TeacherFavorites', component: () => import('../views/courses/FavoriteList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/questions', name: 'TeacherQuestions', component: () => import('../views/courses/QuestionList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/students', name: 'studentList', component: () => import('../views/teacher/StudentList.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/grades', name: 'studentGrades', component: () => import('../views/teacher/StudentGrades.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN', 'ACADEMIC'] } },
  { path: '/teacher/teaching-classes', name: 'teacherTeachingClasses', component: () => import('../views/teacher/TeacherTeachingClasses.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },

  { path: '/student', redirect: '/student/courses' },
  // 学生端路由
  { path: '/student/courses', name: 'StudentCourseSquare', component: () => import('../views/student/CourseSquare.vue'), meta: { requiresAuth: true, roles: ['STUDENT'], menuTab: true, menuLabel: '广场', menuIcon: 'Grid', menuOrder: 1 } },
  { path: '/student/courses/:id', name: 'StudentCourseDetail', component: () => import('../views/student/CourseDetail.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/courses/:id/play/:videoId?', name: 'StudentVideoPlay', component: () => import('../views/student/VideoPlayer.vue'), meta: { requiresAuth: true, roles: ['STUDENT'], layout: 'video' } },
  { path: '/student/my-courses', name: 'StudentMyCourses', component: () => import('../views/student/MyCourses.vue'), meta: { requiresAuth: true, roles: ['STUDENT'], menuTab: true, menuLabel: '我的课程', menuIcon: 'Reading', menuOrder: 2 } },
  // /student/training 保留为隐藏路由（训练中心），不显示在导航标签页
  { path: '/student/training', name: 'StudentTraining', component: () => import('../views/student/TrainingCenter.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  // Fix P1: /student/learning 路由 - 无 courseId 时显示学习中心，使用查询参数 ?courseId= 传递
  { path: '/student/learning', name: 'StudentLearning', component: () => import('../views/student/LearningView.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/learning-stats', name: 'StudentLearningStats', component: () => import('../views/student/LearningCenter.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/notifications', name: 'StudentNotifications', component: () => import('../views/notifications/NotificationList.vue'), meta: { requiresAuth: true, roles: ['STUDENT'], menuTab: true, menuLabel: '消息', menuIcon: 'Bell', menuOrder: 3 } },
  { path: '/student/announcements', redirect: '/student/notifications' },
  { path: '/student/exams', name: 'StudentExams', component: () => import('../views/student/Exams.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/profile', name: 'StudentProfile', component: () => import('../views/student/Profile.vue'), meta: { requiresAuth: true, roles: ['STUDENT'], menuTab: true, menuLabel: '我的', menuIcon: 'User', menuOrder: 4 } },
  { path: '/student/report', name: 'StudentWeeklyReport', component: () => import('../views/student/WeeklyReport.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/orders', name: 'StudentOrders', component: () => import('../views/student/MyOrders.vue'), meta: { requiresAuth: true, roles: ['STUDENT'], menuTab: true, menuLabel: '订单', menuIcon: 'Wallet', menuOrder: 5 } },
  { path: '/student/checkout', name: 'StudentCheckout', component: () => import('../views/student/Checkout.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/bundles', name: 'StudentBundles', component: () => import('../views/student/BundleSquare.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/bundles/:id', name: 'StudentBundleDetail', component: () => import('../views/student/BundleDetail.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/redirect', redirect: '/student/courses', meta: { requiresAuth: false } },
  { path: '/student/chapters/:chapterId/exercises', name: 'StudentExerciseTake', component: () => import('../views/student/ExerciseTake.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/discussions', name: 'StudentDiscussion', component: () => import('../views/student/DiscussionView.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  // Fix P3: /student/discussion/:chapterId -> /student/discussions?chapterId=:chapterId
  { path: '/student/discussion/:chapterId', redirect: (to) => `/student/discussions?chapterId=${to.params.chapterId}` },
  { path: '/student/reviews', name: 'StudentMyReviews', component: () => import('../views/student/MyReviews.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/settings', name: 'StudentSettings', component: () => import('../views/student/Settings.vue'), meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/student/achievements', name: 'StudentAchievements', component: () => import('../views/student/AchievementWall.vue'), meta: { requiresAuth: true, roles: ['STUDENT', 'ADMIN'] } },
  // P1-4: 404 通配路由 — 根据角色重定向到对应首页
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue'), beforeEnter: (to, from, next) => {
    try {
      const userStore = useUserStore()
      if (userStore.role) return next(getRoleHomePage(userStore.role))
    } catch { /* store 未初始化，显示 404 页面 */ }
    next()
  } },
]

function getRoleHomePage(role) {
  if (role === 'STUDENT') return '/student/courses'
  if (role === 'TEACHER') return '/teacher/dashboard'
  if (role === 'ACADEMIC') return '/academic/dashboard'
  return '/admin/dashboard'
}

// P1-3: STAFF_ONLY_PATHS — 仅限管理/教学角色访问的路径前缀列表
// 学生端（STUDENT）命中这些路径时，beforeEach 会强制重定向到 /student/courses
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

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  if (to.meta.requiresAuth !== false && !isAuthenticated()) return next({ path: '/login', query: { redirect: to.fullPath } })

  // 优先从 store 获取角色，store 为空则调用 /api/auth/me
  const userStore = useUserStore()
  let userRole = userStore.role || ''
  if (!userRole && isAuthenticated()) {
    try {
      await userStore.getInfo()
      userRole = userStore.role || ''
    } catch (e) {
      console.warn('[router] 获取用户信息失败, 清除登录态', e)
      removeToken()
      sessionStorage.removeItem('userRole')
      sessionStorage.removeItem('micro_course_refresh_token')
      userStore.token = ''
      userStore.userInfo = null
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }
  }

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

router.afterEach(() => {
  NProgress.done()
})

// P1-5: 路由加载异常时给用户可感知的反馈
router.onError((error) => {
  NProgress.done()
  console.error('[router] 路由加载失败:', error)
  ElMessage.error('页面加载失败，请刷新重试')
})

export default router