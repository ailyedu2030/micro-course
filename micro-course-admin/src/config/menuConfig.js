/**
 * 导航菜单配置
 *
 * 每角色独立菜单树。
 * 结构: { group, icon, children: [{ label, path, icon }] }
 *
 * 设计原则:
 * - ADMIN: 全功能覆盖 + 熔断入口（内容管理组）
 * - ACADEMIC: 基础数据编辑 + 课程监控 + 教务管理
 * - TEACHER: 按创作工作流分6组，解决当前11项一锅粥
 *
 * 注: 菜单配置不替代路由守卫 meta.roles，后者是第二道防线。
 *     ADMIN 可通过 URL 直访未在菜单展示的页面。
 */

const ADMIN = [
  {
    group: '数据总览',
    icon: 'DataAnalysis',
    children: [
      { label: '数据看板', path: '/admin/dashboard', icon: 'Odometer' },
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
      { label: '评价管理', path: '/reviews', icon: 'ChatLineSquare' },
    ],
  },
  {
    group: '内容管理',
    icon: 'Edit',
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
      { label: '讨论管理', path: '/discussions', icon: 'ChatLineSquare' },
    ],
  },
  {
    group: '系统管理',
    icon: 'Setting',
    children: [
      { label: '系统设置', path: '/admin/settings', icon: 'Tools' },
      { label: '操作日志', path: '/admin/logs', icon: 'Clock' },
      { label: '通知管理', path: '/notifications', icon: 'Bell' },
    ],
  },
]

const ACADEMIC = [
  {
    group: '教务驾驶舱',
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
    group: '运营监控',
    icon: 'DataAnalysis',
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
      { label: '课程套餐', path: '/bundles', icon: 'Tickets' },
      { label: '收藏管理', path: '/teacher/favorites', icon: 'Star' },
    ],
  },
  {
    group: '教学资源',
    icon: 'VideoPlay',
    children: [
      { label: '章节管理', path: '/chapters', icon: 'List' },
      { label: '视频管理', path: '/teacher/videos', icon: 'VideoPlay' },
    ],
  },
  {
    group: '学员管理',
    icon: 'UserFilled',
    children: [
      { label: '学员列表', path: '/teacher/students', icon: 'School' },
      { label: '成绩管理', path: '/teacher/grades', icon: 'Finished' },
      { label: '我的教学班', path: '/teacher/teaching-classes', icon: 'Reading' },
    ],
  },
  {
    group: '互动管理',
    icon: 'ChatLineSquare',
    children: [
      { label: '讨论区', path: '/teacher/discussions', icon: 'ChatLineSquare' },
    ],
  },
  {
    group: '题库资源',
    icon: 'Document',
    children: [
      { label: '题库管理', path: '/teacher/questions', icon: 'Document' },
      { label: '练习管理', path: '/teacher/exercises', icon: 'Edit' },
    ],
  },
]

export const menuConfig = { ADMIN, ACADEMIC, TEACHER }
