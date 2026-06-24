/**
 * 课程封面辅助函数
 * 数据库中的 coverUrl 通常为 null,使用此函数生成类别感知的默认封面
 * 保证每个课程都有视觉区分度,避免"千课一面"
 */

// 类别配色方案 (主色 + 渐变 + 主题色)
const CATEGORY_THEMES = {
  1: { primary: '#3b82f6', secondary: '#1d4ed8', name: '编程基础', icon: 'Code' },
  2: { primary: '#8b5cf6', secondary: '#6d28d9', name: '数据结构与算法', icon: 'Graph' },
  3: { primary: '#10b981', secondary: '#047857', name: '数据库', icon: 'Coin' },
  4: { primary: '#f59e0b', secondary: '#d97706', name: 'Web开发', icon: 'Monitor' },
  5: { primary: '#ec4899', secondary: '#be185d', name: '人工智能', icon: 'Cpu' }
}

// 兜底主题
const DEFAULT_THEME = { primary: '#6366f1', secondary: '#4338ca', name: '课程', icon: 'Book' }

function getTheme(categoryId) {
  return CATEGORY_THEMES[categoryId] || DEFAULT_THEME
}

/**
 * 将课程信息生成 SVG data URI 作为默认封面
 * @param {Object} course - { id, title, categoryId, categoryName, teacherName }
 * @returns {string} data:image/svg+xml URI
 */
export function getDefaultCover(course) {
  if (!course) {
    return generateCoverSvg(DEFAULT_THEME, '微课', '')
  }
  const theme = getTheme(course.categoryId)
  return generateCoverSvg(theme, course.title || '未命名课程', course.teacherName || '')
}

function generateCoverSvg(theme, title, teacher) {
  // 截断标题到合理长度
  const displayTitle = title.length > 12 ? title.substring(0, 11) + '…' : title

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 180">
    <defs>
      <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="${theme.primary}"/>
        <stop offset="100%" stop-color="${theme.secondary}"/>
      </linearGradient>
      <pattern id="dots" x="0" y="0" width="20" height="20" patternUnits="userSpaceOnUse">
        <circle cx="2" cy="2" r="1.2" fill="rgba(255,255,255,0.12)"/>
      </pattern>
    </defs>
    <rect width="320" height="180" fill="url(#bg)"/>
    <rect width="320" height="180" fill="url(#dots)"/>
    <text x="24" y="100" fill="white" font-family="-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif" font-size="22" font-weight="700">${escapeXml(displayTitle)}</text>
    <text x="24" y="130" fill="rgba(255,255,255,0.78)" font-family="-apple-system,sans-serif" font-size="11">${escapeXml(teacher || theme.name)}</text>
    <g transform="translate(255, 30)">
      <rect x="0" y="0" width="40" height="40" rx="8" fill="rgba(255,255,255,0.18)"/>
      <text x="20" y="27" text-anchor="middle" fill="white" font-size="18" font-weight="600">${escapeXml(theme.name.charAt(0))}</text>
    </g>
  </svg>`

  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
}

function escapeXml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}
