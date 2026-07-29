import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
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
ElMessage.config({ ariaLive: 'polite' })
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
