/**
 * 导航菜单配置
 *
 * 每角色独立菜单树。
 * 结构: { group, icon, children: [{ label, path, icon }] }
 *
 * 设计原则:
 * - 按业务域分组，避免单组超过 6 项
 * - 三角色分组命名对齐（如统一"数据看板"不出现"驾驶舱总览"）
 * - 评价管理归入教务（偏教学反馈），章节/视频/题库/练习归入内容资源
 * - 操作日志归入系统管理
 */

const ADMIN = [
  {
    group: '数据看板',
    groupKey: 'menu.group.数据看板',
    icon: 'DataAnalysis',
    children: [
      { labelKey: 'menu.adminDashboard', label: '数据总览', path: '/admin/dashboard', icon: 'Odometer' },
      { labelKey: 'menu.academicDashboard', label: '教务驾驶舱', path: '/academic/dashboard', icon: 'DataAnalysis' },
      { labelKey: 'menu.teacherDashboard', label: '教师看板', path: '/teacher/dashboard', icon: 'TrendCharts' },
    ],
  },
  {
    group: '基础数据',
    groupKey: 'menu.group.基础数据',
    icon: 'Grid',
    children: [
      { labelKey: 'menu.departments', label: '院系管理', path: '/departments', icon: 'OfficeBuilding' },
      { labelKey: 'menu.majors', label: '专业管理', path: '/majors', icon: 'Reading' },
      { labelKey: 'menu.classes', label: '班级管理', path: '/classes', icon: 'School' },
      { labelKey: 'menu.users', label: '用户管理', path: '/users', icon: 'User' },
    ],
  },
  {
    group: '课程管理',
    groupKey: 'menu.group.课程管理',
    icon: 'Notebook',
    children: [
      { labelKey: 'menu.courses', label: '全部课程', path: '/courses', icon: 'VideoCamera' },
      { labelKey: 'menu.courseReview', label: '课程审核', path: '/courses/review', icon: 'Film' },
      { labelKey: 'menu.courseCategories', label: '分类管理', path: '/course-categories', icon: 'FolderOpened' },
      { labelKey: 'menu.tags', label: '标签管理', path: '/tags', icon: 'List' },
      { labelKey: 'menu.bundles', label: '课程套餐', path: '/bundles', icon: 'Tickets' },
      { labelKey: 'menu.banners', label: '轮播图管理', path: '/admin/banners', icon: 'PictureFilled' },
    ],
  },
  {
    group: '内容资源',
    groupKey: 'menu.group.内容资源',
    icon: 'VideoPlay',
    children: [
      { labelKey: 'menu.chapters', label: '章节管理', path: '/chapters', icon: 'List' },
      // 【F-2026-08-10-05】5 种课件类型独立管理：HTML / PPT / 视频 / 练习 / 线下
      { labelKey: 'menu.videoCourseware', label: '视频课件', path: '/videos', icon: 'VideoPlay' },
      { labelKey: 'menu.htmlCourseware', label: 'HTML 课件', path: '/admin/courseware/html', icon: 'Document' },
      { labelKey: 'menu.pptCourseware', label: 'PPT 课件', path: '/admin/courseware/ppt', icon: 'Picture' },
      { labelKey: 'menu.exerciseCourseware', label: '练习课件', path: '/exercises', icon: 'Edit' },
      { labelKey: 'menu.offlineCourses', label: '线下课程', path: '/admin/offline-sessions', icon: 'Calendar' },
      { labelKey: 'menu.questions', label: '题库管理', path: '/questions', icon: 'Document' },
    ],
  },
  {
    group: '教务管理',
    groupKey: 'menu.group.教务管理',
    icon: 'UserFilled',
    children: [
      { labelKey: 'menu.teachingClasses', label: '教学班管理', path: '/admin/teaching-classes', icon: 'Reading' },
      { labelKey: 'menu.enrollments', label: '选课管理', path: '/enrollments', icon: 'Tickets' },
      { labelKey: 'menu.grades', label: '成绩汇总', path: '/teacher/grades', icon: 'Finished' },
      { labelKey: 'menu.students', label: '学员管理', path: '/teacher/students', icon: 'School' },
      { labelKey: 'menu.reviews', label: '评价管理', path: '/reviews', icon: 'ChatLineSquare' },
      { labelKey: 'menu.discussions', label: '讨论管理', path: '/discussions', icon: 'ChatLineSquare' },
    ],
  },
  {
    group: '微专业管理',
    groupKey: 'menu.group.微专业管理',
    icon: 'Medal',
    children: [
      { labelKey: 'menu.microSpecialtyList', label: '微专业列表', path: '/teacher/micro-specialties', icon: 'Grid' },
      { labelKey: 'menu.proposalReview', label: '申报审批', path: '/academic/micro-specialties/proposals', icon: 'Document' },
      { labelKey: 'menu.approvedMicroSpecialties', label: '已批准微专业', path: '/academic/micro-specialties/review', icon: 'Medal' },
      { labelKey: 'menu.classImport', label: '班级导入', path: '/academic/micro-specialties/class-import', icon: 'UserFilled' },
    ],
  },
  {
    group: '系统管理',
    groupKey: 'menu.group.系统管理',
    icon: 'Setting',
    children: [
      { labelKey: 'menu.systemSettings', label: '系统设置', path: '/admin/settings', icon: 'Tools' },
      { labelKey: 'menu.platformShare', label: '平台分账', path: '/admin/platform-share-config', icon: 'TrendCharts' },
      { labelKey: 'menu.teacherRating', label: '教师评级', path: '/admin/teacher-ratings', icon: 'Medal' },
      { labelKey: 'menu.revenue', label: '营收看板', path: '/admin/revenue', icon: 'TrendCharts' },
      { labelKey: 'menu.operationLogs', label: '操作日志', path: '/admin/logs', icon: 'Clock' },
      { labelKey: 'menu.notifications', label: '通知管理', path: '/notifications', icon: 'Bell' },
      { labelKey: 'menu.learningAnalytics', label: '学习数据分析', path: '/academic/stats', icon: 'TrendCharts' },
    ],
  },
]

const ACADEMIC = [
  {
    group: '数据看板',
    groupKey: 'menu.group.数据看板',
    icon: 'DataAnalysis',
    children: [
      { labelKey: 'menu.academicDashboard', label: '驾驶舱总览', path: '/academic/dashboard', icon: 'Odometer' },
    ],
  },
  {
    group: '基础数据',
    groupKey: 'menu.group.基础数据',
    icon: 'Grid',
    children: [
      { labelKey: 'menu.departments', label: '院系管理', path: '/departments', icon: 'OfficeBuilding' },
      { labelKey: 'menu.majors', label: '专业管理', path: '/majors', icon: 'Reading' },
      { labelKey: 'menu.classes', label: '班级管理', path: '/classes', icon: 'School' },
    ],
  },
  {
    group: '课程管理',
    groupKey: 'menu.group.课程管理',
    icon: 'Notebook',
    children: [
      { labelKey: 'menu.courses', label: '全部课程', path: '/courses', icon: 'VideoCamera' },
      { labelKey: 'menu.courseReview', label: '课程审核', path: '/courses/review', icon: 'Film' },
      { labelKey: 'menu.courseCategories', label: '课程分类', path: '/course-categories', icon: 'FolderOpened' },
      { labelKey: 'menu.bundles', label: '课程套餐', path: '/bundles', icon: 'Tickets' },
      { labelKey: 'menu.banners', label: '轮播图管理', path: '/admin/banners', icon: 'PictureFilled' },
    ],
  },
  {
    // 【F-2026-08-10-05】ACADEMIC 角色补齐 5 种课件独立管理入口（与 ADMIN/TEACHER 对齐）
    // 【2026-08-12 P1-C 修复】ACADEMIC 无线下课程权限（后端 OfflineSessionController 仅 TEACHER/ADMIN），
    //  移除入口防 403；线下课程运营归教师/管理员（权限矩阵 §1.27）
    group: '内容资源',
    groupKey: 'menu.group.内容资源',
    icon: 'VideoPlay',
    children: [
      { labelKey: 'menu.videoCourseware', label: '视频课件', path: '/videos', icon: 'VideoPlay' },
      { labelKey: 'menu.htmlCourseware', label: 'HTML 课件', path: '/admin/courseware/html', icon: 'Document' },
      { labelKey: 'menu.pptCourseware', label: 'PPT 课件', path: '/admin/courseware/ppt', icon: 'Picture' },
      { labelKey: 'menu.exerciseCourseware', label: '练习课件', path: '/exercises', icon: 'Edit' },
      { labelKey: 'menu.questions', label: '题库管理', path: '/questions', icon: 'Document' },
    ],
  },
  {
    group: '教务管理',
    groupKey: 'menu.group.教务管理',
    icon: 'UserFilled',
    children: [
      { labelKey: 'menu.teachingClasses', label: '教学班管理', path: '/admin/teaching-classes', icon: 'Reading' },
      { labelKey: 'menu.enrollments', label: '选课管理', path: '/enrollments', icon: 'Tickets' },
      { labelKey: 'menu.grades', label: '成绩汇总', path: '/teacher/grades', icon: 'Finished' },
      { labelKey: 'menu.reviews', label: '评价管理', path: '/reviews', icon: 'ChatLineSquare' },
      { labelKey: 'menu.discussions', label: '讨论管理', path: '/discussions', icon: 'ChatLineSquare' },
    ],
  },
  {
    group: '微专业管理',
    groupKey: 'menu.group.微专业管理',
    icon: 'Medal',
    children: [
      { labelKey: 'menu.proposalReview', label: '申报审批', path: '/academic/micro-specialties/proposals', icon: 'Document' },
      { labelKey: 'menu.approvedMicroSpecialties', label: '已批准微专业', path: '/academic/micro-specialties/review', icon: 'Medal' },
      { labelKey: 'menu.classImport', label: '班级导入', path: '/academic/micro-specialties/class-import', icon: 'UserFilled' },
    ],
  },
  {
    group: '系统管理',
    groupKey: 'menu.group.系统管理',
    icon: 'Setting',
    children: [
      { labelKey: 'menu.operationLogs', label: '操作日志', path: '/admin/logs', icon: 'Clock' },
      { labelKey: 'menu.learningAnalytics', label: '学习数据分析', path: '/academic/stats', icon: 'TrendCharts' },
    ],
  },
]

const TEACHER = [
  {
    group: '教学看板',
    groupKey: 'menu.group.教学看板',
    icon: 'DataAnalysis',
    children: [
      { labelKey: 'menu.teacherDashboard', label: '我的看板', path: '/teacher/dashboard', icon: 'Odometer' },
    ],
  },
  {
    group: '课程管理',
    groupKey: 'menu.group.课程管理',
    icon: 'Notebook',
    children: [
      { labelKey: 'menu.myCourses', label: '我的课程', path: '/teacher/courses', icon: 'VideoCamera' },
      { labelKey: 'menu.chapters', label: '章节管理', path: '/chapters', icon: 'List' },
      // 【F-2026-08-10-05】5 种课件类型独立管理：HTML / PPT / 视频 / 练习 / 线下
      { labelKey: 'menu.videoCourseware', label: '视频课件', path: '/teacher/videos', icon: 'VideoPlay' },
      { labelKey: 'menu.htmlCourseware', label: 'HTML 课件', path: '/admin/courseware/html', icon: 'Document' },
      { labelKey: 'menu.pptCourseware', label: 'PPT 课件', path: '/admin/courseware/ppt', icon: 'Picture' },
      { labelKey: 'menu.exerciseCourseware', label: '练习课件', path: '/teacher/exercises', icon: 'Edit' },
      { labelKey: 'menu.offlineCourses', label: '线下课程', path: '/teacher/offline-list', icon: 'Calendar' },
      { labelKey: 'menu.bundles', label: '课程套餐', path: '/bundles', icon: 'Tickets' },
    ],
  },
  {
    group: '题库管理',
    groupKey: 'menu.group.题库管理',
    icon: 'Document',
    children: [
      { labelKey: 'menu.questionList', label: '题库列表', path: '/teacher/questions', icon: 'List' },
      { labelKey: 'menu.exams', label: '试卷管理', path: '/teacher/exams', icon: 'Tickets' },
    ],
  },
  {
    group: '学员管理',
    groupKey: 'menu.group.学员管理',
    icon: 'UserFilled',
    children: [
      { labelKey: 'menu.studentList', label: '学员列表', path: '/teacher/students', icon: 'School' },
      { labelKey: 'menu.gradeManage', label: '成绩管理', path: '/teacher/grades', icon: 'Finished' },
      { labelKey: 'menu.myTeachingClasses', label: '我的教学班', path: '/teacher/teaching-classes', icon: 'Reading' },
      { labelKey: 'menu.discussionArea', label: '讨论区', path: '/teacher/discussions', icon: 'ChatLineSquare' },
      { labelKey: 'menu.favorites', label: '收藏管理', path: '/teacher/favorites', icon: 'Star' },
    ],
  },
  {
    group: '微专业管理',
    groupKey: 'menu.group.微专业管理',
    icon: 'Medal',
    children: [
      { labelKey: 'menu.myMicroSpecialties', label: '我的微专业', path: '/teacher/micro-specialties', icon: 'Grid' },
      { labelKey: 'menu.microSpecialtyProposal', label: '微专业申报', path: '/teacher/micro-specialties/proposals', icon: 'Edit' },
      { labelKey: 'menu.myProposals', label: '我的申报', path: '/teacher/micro-specialties/my-proposals', icon: 'Document' },
      { labelKey: 'menu.invites', label: '邀请列表', path: '/teacher/micro-specialties/invites', icon: 'UserFilled' },
    ],
  },
  {
    group: '个人设置',
    groupKey: 'menu.group.个人设置',
    icon: 'Setting',
    children: [
      { labelKey: 'menu.profile', label: '个人资料 / API Key', path: '/teacher/profile', icon: 'User' },
    ],
  },
]

export const menuConfig = { ADMIN, ACADEMIC, TEACHER }
