import { defineStore } from 'pinia'
import router from '@/router'

const pluginModules = import.meta.glob('@/plugins/*/index.js', { eager: true })

export const usePluginStore = defineStore('plugins', {
  state: () => ({
    plugins: [],
    enabledTypes: [],
    editors: {},      // { INTERACTIVE: () => import(...) }
    properties: {},   // { INTERACTIVE: () => import(...) }
  }),

  actions: {
    registerPlugins() {
      for (const path of Object.keys(pluginModules)) {
        const plugin = pluginModules[path].default
        if (!plugin.enabled) continue

        this.plugins.push(plugin)
        this.enabledTypes.push(plugin.id)

        for (const route of plugin.routes) {
          router.addRoute(route)
        }

        if (plugin.editors) {
          Object.assign(this.editors, plugin.editors)
        }
        if (plugin.properties) {
          Object.assign(this.properties, plugin.properties)
        }
      }
    },

    getCourseCardConfig(type) {
      const plugin = this.plugins.find(p => p.id.toUpperCase() === (type || '').toUpperCase())
      return plugin?.courseCardConfig || null
    },

    getEditor(lessonType) {
      return this.editors[lessonType] || null
    },
  },
})
