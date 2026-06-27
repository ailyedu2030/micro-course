import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        timeout: 120000,
        proxyTimeout: 120000
      }
    }
  },
  build: {
    sourcemap: false,
    // P1-3: 生产构建自动去除 console.log/debug (保留 warn/error 便于线上排查)
    // 客户体验: F12 看不到内部技术日志,减少信息泄露
    // esbuild 内置 (无需安装 terser) — drop: ['console'] 但保留 warn/error
    esbuild: {
      drop: ['debugger'],
      pure: ['console.log', 'console.debug', 'console.info']
    },
    rollupOptions: {
      output: {
        manualChunks(id) {
          // R8 修复：Element Plus 内部循环依赖，拆分多chunk导致加载顺序错误
          // Cannot access 'V' before initialization → 合并为单个vendor-el
          if (id.includes('node_modules/element-plus/es') || id.includes('node_modules/@element-plus/icons-vue')) {
            return 'vendor-el'
          }
          if (id.includes('node_modules/xlsx')) return 'vendor-xlsx'
          if (id.includes('node_modules/@vueuse')) return 'vendor-vueuse'
          if (id.includes('node_modules/axios')) return 'vendor-axios'
          // 视频播放器 lazy-load: 仅观看视频时加载
          if (id.includes('VideoPlayer.vue') || id.includes('video.js') || id.includes('hls.js')) return 'vendor-video-player'
        },
      }
    },
    chunkSizeWarningLimit: 400,
  }
})
