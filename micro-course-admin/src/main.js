import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import './styles/design-tokens.css'
/* 注：common-table.css 已合并到 design-tokens.css，不再独立引入 */
import './styles/mobile-fixes.css'
import App from './App.vue'
import router from './router'
import { usePluginStore } from './store/plugins'
import { syncEnumsFromBackend } from './utils/enums'
import i18n from './i18n'
import { initErrorReporting, reportError } from './utils/errorReport'
import { formatDateTime, formatDate } from './utils/format'

// P-001: 只注册实际使用的 Element Plus 图标（约 74 个，按扫描统计）
// 不再用 import * as ElementPlusIconsVue 全量注册（减少 bundle 体积）
import {
  Aim, ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Back, Bell, Bottom, Calendar,
  CaretRight, ChatDotRound, ChatLineRound, ChatLineSquare, Check, CircleCheck,
  CircleCheckFilled, CircleClose, CircleCloseFilled, Clock, Close, Coin,
  Collection, Connection, CopyDocument, DataAnalysis, DataBoard, DataLine,
  Delete, Document, DocumentCopy, Download, Edit, Finished, FullScreen, Grid,
  Headset, InfoFilled, Key, List, Loading, Location, Lock, MagicStick, Medal,
  Monitor, MoreFilled, Notebook, OfficeBuilding, Picture, PictureFilled, Plus,
  Present, QuestionFilled, Reading, Refresh, RefreshRight, Search, Select,
  Setting, ShoppingCart, Star, Tickets, Timer, Top, TrendCharts, Upload,
  UploadFilled, User, VideoCamera, VideoPause, VideoPlay, View, Wallet,
  Warning, WarningFilled
} from '@element-plus/icons-vue'

const app = createApp(App)

// 全局日期格式化（模板内可直接用 :formatter="$formatDateTime" / "$formatDate"，
// 或直接调用 $formatDateTime(value)）
// 统一解决表格 prop 列直接渲染原始 ISO 时间戳（2026-08-03T19:33:19.70208）的体验问题。
// Element Plus formatter 签名: (row, column, cellValue, index) —— 单元格值在第 3 参；
// 直接调用时第 1 参即时间值。此处做签名感知适配，两种用法均正确。
function elCellDateTime(row, column, cellValue) {
  const value = cellValue !== undefined && cellValue !== null ? cellValue : row
  return formatDateTime(value)
}
function elCellDate(row, column, cellValue) {
  const value = cellValue !== undefined && cellValue !== null ? cellValue : row
  return formatDate(value)
}
app.config.globalProperties.$formatDateTime = elCellDateTime
app.config.globalProperties.$formatDate = elCellDate

app.config.errorHandler = (err, instance, info) => {
  console.error('[Global Error]', info, err)
  reportError(err)
}

// 全局 JS / 未处理 Promise 异常自动上报后端（与 Vue errorHandler 互补，捕获非 Vue 运行时错误）
initErrorReporting()

// 按需注册图标组件
const icons = {
  Aim, ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Back, Bell, Bottom, Calendar,
  CaretRight, ChatDotRound, ChatLineRound, ChatLineSquare, Check, CircleCheck,
  CircleCheckFilled, CircleClose, CircleCloseFilled, Clock, Close, Coin,
  Collection, Connection, CopyDocument, DataAnalysis, DataBoard, DataLine,
  Delete, Document, DocumentCopy, Download, Edit, Finished, FullScreen, Grid,
  Headset, InfoFilled, Key, List, Loading, Location, Lock, MagicStick, Medal,
  Monitor, MoreFilled, Notebook, OfficeBuilding, Picture, PictureFilled, Plus,
  Present, QuestionFilled, Reading, Refresh, RefreshRight, Search, Select,
  Setting, ShoppingCart, Star, Tickets, Timer, Top, TrendCharts, Upload,
  UploadFilled, User, VideoCamera, VideoPause, VideoPlay, View, Wallet,
  Warning, WarningFilled
}
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.use(i18n)

const pluginStore = usePluginStore()
pluginStore.registerPlugins()

app.mount('#app')

// P3-9：启动后后台同步后端枚举（非阻塞、可选）。
// 成功 → 挂到 window.__BACKEND_ENUMS 供运行时优先读取；失败 → 静默回退 utils/enums.js 本地常量。
// 不 await、不阻塞首屏，任何异常均被 syncEnumsFromBackend 内部吞掉，保证无感升级。
syncEnumsFromBackend().then(enums => {
  if (enums) {
    window.__BACKEND_ENUMS = enums
  }
})

// a11y: 全局后置补丁 — 自动给 el-progress / 单选/复选 / icon-only button 补 aria 属性
// 依据: axe-core 报告 landmark-unique, aria-progressbar-name, label, button-name
function patchA11y(root) {
  try {
    // 1) el-progress 自动 aria-label
    root.querySelectorAll('.el-progress[role="progressbar"]').forEach(p => {
      if (p.getAttribute('aria-label')) return
      const card = p.closest('.el-card, .metric, .stat-card, .progress-wrap, td, .dept-metrics')
      const labelEl = card && card.querySelector('.metric-label, .progress-label, .el-form-item__label, .stat-label')
      const text = labelEl ? labelEl.textContent.trim() : '进度'
      const pct = p.querySelector('.el-progress__text')?.textContent?.trim() || ''
      p.setAttribute('aria-label', pct ? `${text} ${pct}` : text)
    })
    // 2) 单一选项组中无 aria-label / name 的 radio group
    root.querySelectorAll('.el-radio-group').forEach(g => {
      if (g.getAttribute('aria-label') || g.getAttribute('aria-labelledby')) return
      const formItem = g.closest('.el-form-item')
      const lbl = formItem && formItem.querySelector('.el-form-item__label')
      const text = lbl ? lbl.textContent.replace('*', '').trim() : '选项组'
      g.setAttribute('aria-label', text)
    })
    // 3) el-checkbox-group 同理
    root.querySelectorAll('.el-checkbox-group').forEach(g => {
      if (g.getAttribute('aria-label') || g.getAttribute('aria-labelledby')) return
      const formItem = g.closest('.el-form-item')
      const lbl = formItem && formItem.querySelector('.el-form-item__label')
      const text = lbl ? lbl.textContent.replace('*', '').trim() : '复选项组'
      g.setAttribute('aria-label', text)
    })
    // 4) icon-only 按钮（无 innerText, 无 aria-label, 内含 el-icon）
    root.querySelectorAll('button').forEach(b => {
      if (b.offsetParent === null) return
      if (b.getAttribute('aria-label') || b.getAttribute('aria-labelledby')) return
      if (b.innerText.trim() !== '') return
      const icon = b.querySelector('.el-icon')
      if (!icon) return
      const cls = [...icon.classList].find(c => /^el-icon-/.test(c))
      const inferred = cls ? cls.replace('el-icon-', '').replace(/-/g, ' ') : ''
      if (inferred) b.setAttribute('aria-label', inferred)
    })
    // 5) 把 .page-breadcrumb 包到 nav 里以避免 el-breadcrumb 内部的 role="navigation" 重复
//    但 nav 本身也是 navigation landmark — 唯一性靠 aria-label 区分
//    统一处理: 任何包含 el-breadcrumb 的容器, 移除内层 el-breadcrumb 的 navigation role
root.querySelectorAll('.el-breadcrumb[role="navigation"]').forEach(bc => {
  // 仅当父容器不是 nav 时移除 — 真 nav 包面包屑保留
  const parent = bc.parentElement
  if (parent && parent.tagName === 'NAV') return
  bc.removeAttribute('role')
  bc.removeAttribute('aria-label')
})
    // 6) 内部 <nav class="header-nav"> 若无 aria-label 也会重复, 已在 StudentLayout:20 加 aria-label="主导航"
    // 7) router-view 容器无 landmark role (vue-router 不会自动加), 不需处理
    // 8) el-table 空白表头补 role + aria-label
    root.querySelectorAll('th.el-table__expand-column, .el-table th.is-leaf').forEach(th => {
      if (th.getAttribute('aria-label') || th.textContent.trim()) return
      th.setAttribute('aria-label', '展开')
    })
    // 9) el-empty 默认 min-width: 320px, 移动端会撑爆 — 设 max-width + padding 0
    root.querySelectorAll('.el-empty').forEach(el => {
      el.style.maxWidth = '100%'
      el.style.minWidth = '0'
      el.style.padding = '12px'
    })
    // 10) el-step 在窄屏会撑爆 — 允许其内部滚动
    root.querySelectorAll('.el-step').forEach(el => {
      el.style.maxWidth = '100%'
      el.style.minWidth = '0'
    })
    // 11) el-tabs__item 触摸目标 ≥ 44 (a11y 触控目标)
    root.querySelectorAll('.el-tabs__item').forEach(el => {
      el.style.minHeight = '44px'
    })
    // 12) el-pagination 在窄屏会撑爆 — 允许横向滚动
    root.querySelectorAll('.el-pagination, .pagination-wrap').forEach(el => {
      el.style.maxWidth = '100%'
      el.style.minWidth = '0'
      el.style.overflowX = 'auto'
      el.style.flexWrap = 'wrap'
    })
    // 13) toolbar / toolbar-right 在窄屏 wrap (防止 flex 撑爆)
    root.querySelectorAll('.toolbar, .toolbar-left, .toolbar-right, .quick-action-inner').forEach(el => {
      el.style.maxWidth = '100%'
      el.style.minWidth = '0'
      el.style.flexWrap = 'wrap'
      el.style.overflow = 'hidden'
    })
    // 14) el-step 容器 scrollWidth > clientWidth — 强制 ms-steps 容器隐藏 overflow + 横向滚动
    //     通过给 ms-steps/parent 加 overflow-x: auto 避免撑爆外层
    root.querySelectorAll('.el-steps').forEach(el => {
      el.style.overflowX = 'auto'
      el.style.maxWidth = '100%'
      el.style.minWidth = '0'
      el.style.paddingBottom = '4px'
    })
    // 15) EP 分页"每页条数"下拉无标签（axe label critical）— 注入可访问名称
    root.querySelectorAll('.el-pagination__sizes input.el-select__input').forEach(el => {
      if (!el.getAttribute('aria-label') && !el.getAttribute('aria-labelledby')) {
        el.setAttribute('aria-label', '每页条数')
      }
    })
    // 16) el-select 内部 input 无标签时，取最近 select 的 aria-label/title/placeholder
    root.querySelectorAll('input.el-select__input').forEach(el => {
      if (el.getAttribute('aria-label') || el.getAttribute('aria-labelledby')) return
      const wrapper = el.closest('.el-select')
      const name = wrapper?.getAttribute('aria-label')
        || wrapper?.getAttribute('title')
        || el.getAttribute('placeholder')
        || ''
      if (name) el.setAttribute('aria-label', name)
    })
    // 17) el-radio-button 隐藏 radio 无标签 → 取按钮文本
    root.querySelectorAll('input.el-radio-button__original-radio').forEach(el => {
      if (el.getAttribute('aria-label') || el.getAttribute('aria-labelledby')) return
      const btn = el.closest('.el-radio-button')
      const text = btn?.innerText?.trim() || ''
      if (text) el.setAttribute('aria-label', text)
    })
    // 18) el-radio 隐藏 radio 无标签 → 取 radio 文本
    root.querySelectorAll('input.el-radio__original').forEach(el => {
      if (el.getAttribute('aria-label') || el.getAttribute('aria-labelledby')) return
      const radio = el.closest('.el-radio')
      const text = radio?.innerText?.trim() || ''
      if (text) el.setAttribute('aria-label', text)
    })
    // 19) el-switch 隐藏 checkbox 无标签 → 取 switch 的 aria-label/title
    root.querySelectorAll('.el-switch input[type="checkbox"]').forEach(el => {
      if (el.getAttribute('aria-label') || el.getAttribute('aria-labelledby')) return
      const sw = el.closest('.el-switch')
      const name = sw?.getAttribute('aria-label') || sw?.getAttribute('title') || ''
      if (name) el.setAttribute('aria-label', name)
    })
    // 20) EP 表格内部滚动容器可键盘聚焦（axe scrollable-region-focusable）。
    //     无条件设置 tabindex（加载时序下 overflow 判定不稳定）
    root.querySelectorAll('.el-scrollbar__wrap').forEach(el => {
      if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '0')
    })
    // 21) NProgress 顶部进度条为瞬态视觉指示，标记 aria-hidden 避免
    //     axe region 规则误报（内容不在 landmark 内）
    const nprogress = document.getElementById('nprogress')
    if (nprogress && !nprogress.hasAttribute('aria-hidden')) {
      nprogress.setAttribute('aria-hidden', 'true')
    }
  } catch (e) { /* swallow */ }
}
function startA11yObserver() {
  if (typeof window === 'undefined') return
  patchA11y(document)
  const obs = new MutationObserver(() => patchA11y(document))
  obs.observe(document.body, { childList: true, subtree: true })
  window.__a11yObserver = obs
}
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', startA11yObserver, { once: true })
} else {
  startA11yObserver()
}
