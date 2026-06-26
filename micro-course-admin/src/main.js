import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/design-tokens.css'
import './styles/common-table.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { usePluginStore } from './store/plugins'
import { syncEnumsFromBackend } from './utils/enums'
import { initErrorReporting, reportError } from './utils/errorReport'

const app = createApp(App)

app.config.errorHandler = (err, instance, info) => {
  console.error('[Global Error]', info, err)
  reportError(err)
}

// 全局 JS / 未处理 Promise 异常自动上报后端（与 Vue errorHandler 互补，捕获非 Vue 运行时错误）
initErrorReporting()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus)

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
