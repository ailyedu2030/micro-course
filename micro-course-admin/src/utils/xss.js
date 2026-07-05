/**
 * XSS 安全工具函数
 * 提供 HTML 清洗等安全处理功能
 */

/**
 * 完整的 HTML 清洗，移除所有 XSS 攻击向量
 * @param {string} html - 原始 HTML 字符串
 * @returns {string} 清洗后的字符串
 */
export function sanitizeHtml(html) {
  if (!html) return ''
  let s = html
  // 1. 移除 script 标签及其内容
  s = s.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
  // 2. 移除所有 on* 事件处理器（onclick, onerror, onload, onmouseover 等）
  s = s.replace(/\s+on\w+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, ' ')
  // 3. 危险协议清洗（href/src/action/formaction 中的 javascript:、vbscript:、data:、file:）
  s = s.replace(/(?:href|src|action|formaction)\s*=\s*"(?:javascript|vbscript|data|file):[^"]*"/gi, '$1="#"')
  s = s.replace(/(?:href|src|action|formaction)\s*=\s*'(?:javascript|vbscript|data|file):[^']*'/gi, "$1='#'")
  s = s.replace(/(?:href|src|action|formaction)\s*=\s*(?:javascript|vbscript|data|file):[^\s>]+/gi, '$1="#"')
  // 4. 移除危险嵌入标签
  s = s.replace(/<\/?(?:iframe|embed|object|frame|frameset|ilayer|base|form)[^>]*>/gi, '')
  // 5. 移除 meta 重定向和刷新
  s = s.replace(/<meta[^>]*>/gi, '')
  return s
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
