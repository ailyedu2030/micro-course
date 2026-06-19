import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '../utils/auth'
import { useUserStore } from '../store/user'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/auth/Login.vue'), meta: { requiresAuth: false } },
  { path: '/', name: 'Home', redirect: '/admin/dashboard' },
  { path: '/profile', redirect: (to) => { const role = sessionStorage.getItem('userRole') || ''; if (role === 'STUDENT') return '/student/profile'; return getRoleHomePage(role); } },
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
  { path: '/courses/:courseId/exercises/form', name: 'ExerciseForm', component: () => import('../views/courses/ExerciseForm.vue'), meta: { requiresAuth: true } },
  { path: '/discussions', name: 'DiscussionList', component: () => import('../views/courses/DiscussionList.vue'), meta: { requiresAuth: true } },
  { path: '/discussions/:id', name: 'DiscussionDetail', component: () => import('../views/courses/DiscussionDetail.vue'), meta: { requiresAuth: true } },
  { path: '/notifications', name: 'NotificationList', component: () => import('../views/notifications/NotificationList.vue'), meta: { requiresAuth: true } },
  { path: '/courses/review', name: 'CourseReview', component: () => import('../views/courses/CourseReviewList.vue'), meta: { requiresAuth: true, roles: ['ADMIN', 'ACADEMIC'] } },

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

  // 教师端路由
  { path: '/teacher/dashboard', name: 'TeacherDashboard', component: () => import('../views/teacher/TeacherDashboard.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/courses', name: 'TeacherCourseList', component: () => import('../views/courses/CourseList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/videos', name: 'TeacherVideoList', component: () => import('../views/courses/VideoList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/exercises', name: 'TeacherExerciseList', component: () => import('../views/courses/ExerciseList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/discussions', name: 'TeacherDiscussions', component: () => import('../views/student/DiscussionView.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/favorites', name: 'TeacherFavorites', component: () => import('../views/courses/FavoriteList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/questions', name: 'TeacherQuestions', component: () => import('../views/courses/QuestionList.vue'), meta: { requiresAuth: true, roles: ['TEACHER'] } },
  { path: '/teacher/students', name: 'studentList', component: () => import('../views/teacher/StudentList.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/grades', name: 'studentGrades', component: () => import('../views/teacher/StudentGrades.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/teaching-classes', name: 'teacherTeachingClasses', component: () => import('../views/teacher/TeacherTeachingClasses.vue'), meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } },

  // 学生端路由
  { path: '/student/courses', name: 'StudentCourseSquare', component: () => import('../views/student/CourseSquare.vue'), meta: { requiresAuth: true, menuTab: true, menuLabel: '广场', menuIcon: 'Grid', menuOrder: 1 } },
  { path: '/student/courses/:id', name: 'StudentCourseDetail', component: () => import('../views/student/CourseDetail.vue'), meta: { requiresAuth: true } },
  { path: '/student/courses/:id/play/:videoId?', name: 'StudentVideoPlay', component: () => import('../views/student/VideoPlayer.vue'), meta: { requiresAuth: true, layout: 'video' } },
  { path: '/student/my-courses', name: 'StudentMyCourses', component: () => import('../views/student/MyCourses.vue'), meta: { requiresAuth: true, menuTab: true, menuLabel: '我的课程', menuIcon: 'Reading', menuOrder: 2 } },
  // /student/training 保留为隐藏路由（训练中心），不显示在导航标签页
  { path: '/student/training', name: 'StudentTraining', component: () => import('../views/student/TrainingCenter.vue'), meta: { requiresAuth: true } },
  // Fix P1: /student/learning 路由 - 无 courseId 时显示学习中心，有 courseId 时重定向到学习页面
  { path: '/student/learning', name: 'StudentLearning', component: () => import('../views/student/LearningView.vue'), meta: { requiresAuth: true } },
  { path: '/student/learning/:courseId', redirect: (to) => {
    if (!to.params.courseId) return '/student/learning'
    return `/student/learning?courseId=${to.params.courseId}`
  }, meta: { requiresAuth: true } },
  { path: '/student/learning-stats', name: 'LearningCenter', component: () => import('../views/student/LearningCenter.vue'), meta: { requiresAuth: true } },
  { path: '/student/notifications', name: 'StudentNotifications', component: () => import('../views/notifications/NotificationList.vue'), meta: { requiresAuth: true, menuTab: true, menuLabel: '消息', menuIcon: 'Bell', menuOrder: 3 } },
  { path: '/student/announcements', redirect: '/student/notifications' },
  { path: '/student/exams', name: 'StudentExams', component: () => import('../views/student/Exams.vue'), meta: { requiresAuth: true } },
  { path: '/student/profile', name: 'StudentProfile', component: () => import('../views/student/Profile.vue'), meta: { requiresAuth: true, menuTab: true, menuLabel: '我的', menuIcon: 'User', menuOrder: 4 } },
  { path: '/student/report', name: 'StudentWeeklyReport', component: () => import('../views/student/WeeklyReport.vue'), meta: { requiresAuth: true } },
  { path: '/student/redirect', redirect: '/student/courses', meta: { requiresAuth: false } },
  { path: '/student/chapters/:chapterId/exercises', name: 'StudentExerciseTake', component: () => import('../views/student/ExerciseTake.vue'), meta: { requiresAuth: true } },
  { path: '/student/discussions', name: 'StudentDiscussion', component: () => import('../views/student/DiscussionView.vue'), meta: { requiresAuth: true } },
  // Fix P3: /student/discussion/:chapterId -> /student/discussions?chapterId=:chapterId
  { path: '/student/discussion/:chapterId', redirect: (to) => `/student/discussions?chapterId=${to.params.chapterId}` },
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

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  if (to.meta.requiresAuth !== false && !isAuthenticated()) return next('/login')

  // 优先从 store 获取角色，store 为空则调用 /api/auth/me
  const userStore = useUserStore()
  let userRole = userStore.role || ''
  if (!userRole && isAuthenticated()) {
    try {
      await userStore.getInfo()
      userRole = userStore.role || ''
    } catch (e) {
      console.warn('[router] 获取用户信息失败, 跳转登录', e)
      return next('/login')
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

router.onError(() => {
  NProgress.done()
})

export default router