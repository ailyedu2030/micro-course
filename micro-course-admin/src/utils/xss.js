/**
 * XSS 安全工具函数
 * 提供 HTML 清洗等安全处理功能
 */

import DOMPurify from 'dompurify'

/**
 * 完整的 HTML 清洗，移除所有 XSS 攻击向量
 * 使用 DOMPurify（OWASP 推荐）替代正则实现，防止绕过
 * @param {string} html - 原始 HTML 字符串
 * @returns {string} 清洗后的字符串
 */
export function sanitizeHtml(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'u', 'p', 'br', 'span', 'div', 'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'blockquote', 'code', 'pre'],
    ALLOWED_ATTR: ['href', 'title', 'class', 'style'],
    ALLOW_DATA_ATTR: false,
    ADD_ATTR: ['target'],
  })
}

/**
 * 转义 HTML 特殊字符为实体（用于文本内容展示）
 * @param {string} str - 原始字符串
 * @returns {string} 转义后的字符串
 */
export function escapeHtml(str) {
  if (!str) return ''
  const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
  return str.replace(/[&<>"']/g, ch => map[ch])
}
