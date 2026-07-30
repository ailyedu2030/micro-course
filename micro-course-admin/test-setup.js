/**
 * R4 修复: vitest setup — 通过 @vue/test-utils 2.x 的 setGlobalConfig 全局 install vue-i18n。
 *
 * <h3>背景</h3>
 * <p>2026-07-30 发现 15 个组件 mount 测试因 "Need to install with `app.use` function" 失败：
 * Profile/LearningCenter/CourseSquare/TeacherDashboard/VideoPlayer/LayoutMenu 等。
 * 测试用 mount() 直接挂载 Vue 组件，但未在测试 app 实例上 install vue-i18n，
 * 导致 useI18n() 在 SFC setup() 编译/执行时抛 SyntaxError。</p>
 *
 * <p>该问题在 R1 之前已存在（约 6 个月），但 CI 之前用 jest + vue-i18n 旧版，
 * 迁移到 vitest 后未在 setup 文件中 install vue-i18n，导致 R1/R2/R3 期间所有 vue-i18n
 * 相关测试都失败，被记录为"pre-existing i18n setup 问题"（R2 报告 Phase 6 跟进项）。</p>
 *
 * <h3>修复方案</h3>
 * <p>vue-test-utils 2.x 的 setGlobalConfig({ global: { plugins: [i18n] } }) 会在所有
 * mount() 时调用 app.use(plugin)，避免 "Need to install" 报错。vue-i18n 9+
 * 检查 provide('i18n') 存在性，我们的 plugin 通过 app.provide('i18n', ...) 满足。</p>
 */
import { config, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import zhCN from './src/i18n/zh-CN.js'
import enUS from './src/i18n/en-US.js'

// 创建一个轻量级 i18n 实例（不依赖完整 vue-i18n features，仅满足 useI18n() 需要）
const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: 'zh-CN',
  fallbackLocale: 'en-US',
  messages: { 'zh-CN': zhCN, 'en-US': enUS }
})

// vue-test-utils 2.x: config.global.plugins 在每次 mount() 时被 app.use(plugin)
config.global = config.global || {}
config.global.plugins = config.global.plugins || []
if (!config.global.plugins.includes(i18n)) {
  config.global.plugins.push(i18n)
}
