import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/design-tokens.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { usePluginStore } from './store/plugins'

const app = createApp(App)

app.config.errorHandler = (err, instance, info) => {
  console.error('[Global Error]', info, err)
}

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
