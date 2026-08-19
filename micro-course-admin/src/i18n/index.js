import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

const VALID_LOCALES = ['zh-CN', 'en-US']

// 从 localStorage 恢复语言设置；防御性校验：只接受合法值
const savedRaw = localStorage.getItem('lang')
const savedLocale = VALID_LOCALES.includes(savedRaw) ? savedRaw : 'zh-CN'

export default createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})
