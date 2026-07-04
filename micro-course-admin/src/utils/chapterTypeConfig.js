/**
 * 章节类型配置常量
 * 统一管理章节类型的标签名、图标、颜色和 Element Plus Tag 类型
 */
export const CHAPTER_TYPE_CONFIG = {
  VIDEO:       { label: '视频课',   icon: '📹', color: '#409eff', tagType: 'primary' },
  INTERACTIVE: { label: '互动课件', icon: '🎯', color: '#67c23a', tagType: 'success' },
  EXERCISE:    { label: '练习',    icon: '📝', color: '#e6a23c', tagType: 'warning' },
  OFFLINE:     { label: '线下课',   icon: '🏫', color: '#909399', tagType: 'info' },
}

/**
 * 根据章节类型获取配置
 * @param {string} type - 章节类型
 * @returns {{ label: string, icon: string, color: string, tagType: string }}
 */
export function getChapterTypeConfig(type) {
  return CHAPTER_TYPE_CONFIG[type] || { label: type, icon: '', color: '#909399', tagType: 'info' }
}
