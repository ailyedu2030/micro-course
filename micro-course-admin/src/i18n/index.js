import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

const VALID_LOCALES = ['zh-CN', 'en-US']

// P1-I 修复(2026-08-19): SSR/test 环境防护
// 根因: import 时直接访问 localStorage, 在 happy-dom 未初始化或 SSR 环境下
//       localStorage 是 undefined → TypeError: Cannot read properties of undefined (reading 'getItem')
//       导致 4 个 a11y-teacher-pages.test.js 测试 + 所有 SSR 部署崩溃
// 修复: typeof 检查 + try/catch, 失败降级到 zh-CN 默认 locale
// 验证: 单测 4 failure → PASS, SSR 部署兼容
function detectInitialLocale() {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      const savedRaw = window.localStorage.getItem('lang')
      if (VALID_LOCALES.includes(savedRaw)) return savedRaw
    }
  } catch (e) {
    // localStorage 访问被浏览器拒绝 (隐私模式 / quota 超限) → 降级默认
  }
  return 'zh-CN'
}

const savedLocale = detectInitialLocale()

export default createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})
