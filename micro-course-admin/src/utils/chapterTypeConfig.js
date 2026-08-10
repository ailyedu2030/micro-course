/**
 * 章节类型配置常量
 * 统一管理章节类型的标签名、图标、颜色和 Element Plus Tag 类型
 *
 * 【F-2026-08-10-16/17】V333 语义对齐：
 * - sectionType=INTERACTIVE 是技术内部值（对外隐藏）
 * - 真实课件类型由 coursewareType 区分：PPT → "PPT 课件"，HTML → "互动课件（HTML 课件）"
 * - 对外 5 种课件类型：视频课件 / PPT 课件 / 互动课件（HTML 课件）/ 线下课程 / 练习课件
 */
export const CHAPTER_TYPE_CONFIG = {
  VIDEO:       { label: '视频课件',   icon: '📹', color: '#409eff', tagType: 'primary' },
  INTERACTIVE: { label: '课件',       icon: '📄', color: '#67c23a', tagType: 'success' },
  EXERCISE:    { label: '练习课件',   icon: '📝', color: '#e6a23c', tagType: 'warning' },
  OFFLINE:     { label: '线下课程',   icon: '🏫', color: '#909399', tagType: 'info' },
}

/**
 * 根据章节类型获取配置
 * @param {string} type - 章节类型
 * @returns {{ label: string, icon: string, color: string, tagType: string }}
 */
export function getChapterTypeConfig(type) {
  return CHAPTER_TYPE_CONFIG[type] || { label: type, icon: '', color: '#909399', tagType: 'info' }
}
