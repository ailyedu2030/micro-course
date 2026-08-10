/**
 * 课程类型配置（F-2026-08-10-05）
 *
 * 5 种课件/课程类型独立管理：HTML 课件 / PPT 课件 / 视频课件 / 练习课件 / 线下课程。
 *
 * 后端 CourseType 枚举保留 V333 4 值（HTML_COURSEWARE / PPT_COURSEWARE / VIDEO / OFFLINE）；
 * "练习课件"是章节维度（CourseSection.sectionType=EXERCISE）的课程聚合视图，
 * 不作为 CourseType 课程维度的新枚举值，避免破坏已部署 V333 兼容性。
 *
 * 前端按 5 维度展示，统一用户用语（"X 课件"），与后端枚举值解耦。
 */

export const COURSE_TYPE_CONFIG = {
  HTML_COURSEWARE: { label: 'HTML 课件', shortLabel: 'HTML 课件', tagType: 'success' },
  PPT_COURSEWARE:  { label: 'PPT 课件',  shortLabel: 'PPT 课件',  tagType: 'primary' },
  VIDEO:           { label: '视频课件',  shortLabel: '视频课件',  tagType: 'primary' },
  OFFLINE:         { label: '线下课程',  shortLabel: '线下课程',  tagType: 'info' },
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
