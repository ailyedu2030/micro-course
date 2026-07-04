/**
 * XSS 安全工具函数
 * 提供 HTML 清洗等安全处理功能
 */

/**
 * 移除 HTML 中的 <script> 标签及其内容
 * @param {string} html - 原始 HTML 字符串
 * @returns {string} 清洗后的字符串
 */
export function sanitizeHtml(html) {
  if (!html) return ''
  return html.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
}

/**
 * 转义 HTML 特殊字符为实体
 * @param {string} str - 原始字符串
 * @returns {string} 转义后的字符串
 */
export function escapeHtml(str) {
  if (!str) return ''
  const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
  return str.replace(/[&<>"']/g, ch => map[ch])
}
