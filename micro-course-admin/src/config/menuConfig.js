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
    icon: 'DataAnalysis',
    children: [
      { label: '数据总览', path: '/admin/dashboard', icon: 'Odometer' },
      { label: '教务驾驶舱', path: '/academic/dashboard', icon: 'DataAnalysis' },
      { label: '教师看板', path: '/teacher/dashboard', icon: 'TrendCharts' },
    ],
  },
  {
    group: '基础数据',
    icon: 'Grid',
    children: [
      { label: '院系管理', path: '/departments', icon: 'OfficeBuilding' },
      { label: '专业管理', path: '/majors', icon: 'Reading' },
      { label: '班级管理', path: '/classes', icon: 'School' },
      { label: '用户管理', path: '/users', icon: 'User' },
    ],
  },
  {
    group: '课程管理',
    icon: 'Notebook',
    children: [
      { label: '全部课程', path: '/courses', icon: 'VideoCamera' },
      { label: '课程审核', path: '/courses/review', icon: 'Film' },
      { label: '分类管理', path: '/course-categories', icon: 'FolderOpened' },
      { label: '标签管理', path: '/tags', icon: 'List' },
      { label: '课程套餐', path: '/bundles', icon: 'Tickets' },
      { label: '轮播图管理', path: '/admin/banners', icon: 'PictureFilled' },
    ],
  },
  {
    group: '内容资源',
    icon: 'VideoPlay',
    children: [
      { label: '章节管理', path: '/chapters', icon: 'List' },
      { label: '视频管理', path: '/videos', icon: 'VideoPlay' },
      { label: '题库管理', path: '/questions', icon: 'Document' },
      { label: '练习管理', path: '/exercises', icon: 'Edit' },
    ],
  },
  {
    group: '教务管理',
    icon: 'UserFilled',
    children: [
      { label: '教学班管理', path: '/admin/teaching-classes', icon: 'Reading' },
      { label: '选课管理', path: '/enrollments', icon: 'Tickets' },
      { label: '成绩汇总', path: '/teacher/grades', icon: 'Finished' },
      { label: '学员管理', path: '/teacher/students', icon: 'School' },
      { label: '评价管理', path: '/reviews', icon: 'ChatLineSquare' },
      { label: '讨论管理', path: '/discussions', icon: 'ChatLineSquare' },
    ],
  },
  {
    group: '微专业管理',
    icon: 'Medal',
    children: [
      { label: '微专业列表', path: '/teacher/micro-specialties', icon: 'Grid' },
      { label: '申报审批', path: '/academic/micro-specialties/proposals', icon: 'Document' },
      { label: '已批准微专业', path: '/academic/micro-specialties/review', icon: 'Medal' },
      { label: '班级导入', path: '/academic/micro-specialties/class-import', icon: 'UserFilled' },
    ],
  },
  {
    group: '系统管理',
    icon: 'Setting',
    children: [
      { label: '系统设置', path: '/admin/settings', icon: 'Tools' },
      { label: '平台分账', path: '/admin/platform-share-config', icon: 'TrendCharts' },
      { label: '教师评级', path: '/admin/teacher-ratings', icon: 'Medal' },
      { label: '营收看板', path: '/admin/revenue', icon: 'TrendCharts' },
      { label: '操作日志', path: '/admin/logs', icon: 'Clock' },
      { label: '通知管理', path: '/notifications', icon: 'Bell' },
      { label: '学习数据分析', path: '/academic/stats', icon: 'TrendCharts' },
    ],
  },
]

const ACADEMIC = [
  {
    group: '数据看板',
    icon: 'DataAnalysis',
    children: [
      { label: '驾驶舱总览', path: '/academic/dashboard', icon: 'Odometer' },
    ],
  },
  {
    group: '基础数据',
    icon: 'Grid',
    children: [
      { label: '院系管理', path: '/departments', icon: 'OfficeBuilding' },
      { label: '专业管理', path: '/majors', icon: 'Reading' },
      { label: '班级管理', path: '/classes', icon: 'School' },
    ],
  },
  {
    group: '课程管理',
    icon: 'Notebook',
    children: [
      { label: '全部课程', path: '/courses', icon: 'VideoCamera' },
      { label: '课程审核', path: '/courses/review', icon: 'Film' },
      { label: '课程分类', path: '/course-categories', icon: 'FolderOpened' },
      { label: '课程套餐', path: '/bundles', icon: 'Tickets' },
      { label: '轮播图管理', path: '/admin/banners', icon: 'PictureFilled' },
    ],
  },
  {
    group: '教务管理',
    icon: 'UserFilled',
    children: [
      { label: '教学班管理', path: '/admin/teaching-classes', icon: 'Reading' },
      { label: '选课管理', path: '/enrollments', icon: 'Tickets' },
      { label: '成绩汇总', path: '/teacher/grades', icon: 'Finished' },
      { label: '评价管理', path: '/reviews', icon: 'ChatLineSquare' },
      { label: '讨论管理', path: '/discussions', icon: 'ChatLineSquare' },
    ],
  },
  {
    group: '微专业管理',
    icon: 'Medal',
    children: [
      { label: '申报审批', path: '/academic/micro-specialties/proposals', icon: 'Document' },
      { label: '已批准微专业', path: '/academic/micro-specialties/review', icon: 'Medal' },
      { label: '班级导入', path: '/academic/micro-specialties/class-import', icon: 'UserFilled' },
    ],
  },
  {
    group: '系统管理',
    icon: 'Setting',
    children: [
      { label: '操作日志', path: '/admin/logs', icon: 'Clock' },
      { label: '学习数据分析', path: '/academic/stats', icon: 'TrendCharts' },
    ],
  },
]

const TEACHER = [
  {
    group: '教学看板',
    icon: 'DataAnalysis',
    children: [
      { label: '我的看板', path: '/teacher/dashboard', icon: 'Odometer' },
    ],
  },
  {
    group: '课程管理',
    icon: 'Notebook',
    children: [
      { label: '我的课程', path: '/teacher/courses', icon: 'VideoCamera' },
      { label: '章节管理', path: '/chapters', icon: 'List' },
      { label: '视频管理', path: '/teacher/videos', icon: 'VideoPlay' },
      { label: '互动课件', path: '/teacher/slides', icon: 'Present' },
      { label: '线下课堂', path: '/teacher/courses?courseType=OFFLINE', icon: 'Calendar' },
      { label: '课程套餐', path: '/bundles', icon: 'Tickets' },
    ],
  },
  {
    group: '题库管理',
    icon: 'Document',
    children: [
      { label: '题库列表', path: '/teacher/questions', icon: 'List' },
      { label: '试卷管理', path: '/teacher/exams', icon: 'Tickets' },
    ],
  },
  {
    group: '学员管理',
    icon: 'UserFilled',
    children: [
      { label: '学员列表', path: '/teacher/students', icon: 'School' },
      { label: '成绩管理', path: '/teacher/grades', icon: 'Finished' },
      { label: '我的教学班', path: '/teacher/teaching-classes', icon: 'Reading' },
      { label: '讨论区', path: '/teacher/discussions', icon: 'ChatLineSquare' },
      { label: '收藏管理', path: '/teacher/favorites', icon: 'Star' },
    ],
  },
  {
    group: '微专业管理',
    icon: 'Medal',
    children: [
      { label: '我的微专业', path: '/teacher/micro-specialties', icon: 'Grid' },
      { label: '微专业申报', path: '/teacher/micro-specialties/proposals', icon: 'Edit' },
      { label: '我的申报', path: '/teacher/micro-specialties/my-proposals', icon: 'Document' },
      { label: '邀请列表', path: '/teacher/micro-specialties/invites', icon: 'UserFilled' },
    ],
  },
]

export const menuConfig = { ADMIN, ACADEMIC, TEACHER }
