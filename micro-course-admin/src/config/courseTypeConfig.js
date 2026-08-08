/**
 * 课程类型配置（V333 简化方案 · 4 值）
 *
 * HTML 课件 + PPT 课件 2 种课件类型独立管理，VIDEO / OFFLINE 保留旧值。
 * 前端统一从这里取标签 / 筛选选项 / 类型徽标，禁止散落硬编码。
 */

export const COURSE_TYPE_CONFIG = {
  HTML_COURSEWARE: { label: 'HTML 课件', shortLabel: 'HTML 课件', tagType: 'success' },
  PPT_COURSEWARE:  { label: 'PPT 课件',  shortLabel: 'PPT 课件',  tagType: 'primary' },
  VIDEO:           { label: '视频课程',  shortLabel: '视频',      tagType: 'primary' },
  OFFLINE:         { label: '线下课程',  shortLabel: '线下',      tagType: 'info' },
}

/** 筛选项（4 种） */
export const COURSE_TYPE_OPTIONS = Object.entries(COURSE_TYPE_CONFIG).map(([value, c]) => ({ value, label: c.label }))

/** 类型标签 map（用于页标题等） */
export const COURSE_TYPE_LABELS = Object.fromEntries(
  Object.entries(COURSE_TYPE_CONFIG).map(([v, c]) => [v, c.label])
)

/** 取单个类型配置（未知类型返回 null） */
export function getCourseTypeConfig(type) {
  return COURSE_TYPE_CONFIG[type] || null
}

/**
 * 是否为课件类课程类型（HTML 课件 / PPT 课件）。
 * 用于：课件入口按钮、学生端跳转播放器、插件徽标等。
 */
export function isCoursewareCourseType(type) {
  return type === 'HTML_COURSEWARE' || type === 'PPT_COURSEWARE'
}
