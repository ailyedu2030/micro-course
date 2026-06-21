/**
 * 通用格式化工具
 * Round 9-4 新增：集中时间格式化，避免各页面重复实现
 */

/**
 * 格式化 ISO 时间戳为友好显示：YYYY-MM-DD HH:mm
 * @param {string|number|Date} isoString ISO 时间字符串 / 时间戳 / Date
 * @returns {string} 格式化后的时间，无效输入返回空字符串
 */
export function formatDateTime(isoString) {
  if (!isoString) return ''
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) return ''
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

/**
 * 格式化 ISO 时间戳为日期：YYYY-MM-DD
 * @param {string|number|Date} isoString
 * @returns {string} 无效输入返回空字符串
 */
export function formatDate(isoString) {
  if (!isoString) return ''
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) return ''
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}
