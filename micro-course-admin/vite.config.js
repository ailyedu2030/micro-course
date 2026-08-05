import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/// <reference types="vitest/config" />
export default defineConfig({
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    globals: true,
    css: false,  // ignore CSS imports (element-plus theme-chalk etc.)
    setupFiles: ['./test-setup.js'],  // R4: 全局安装 vue-i18n，修复 15 个 mount 测试的 "$t not defined" 错误
    server: {
      deps: {
        inline: ['element-plus'],
      },
    },
  },
  plugins: [
    vue(),
    // RES-001 修复: element-plus 按需引入, 减少 vendor-el chunk 从 ~1.1MB 到 ~300KB
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:8080',
        changeOrigin: true,
        timeout: 120000,
        proxyTimeout: 120000
      }
    }
  },
  // CI e2e job 跑 `npx vite preview --port 8088`，server.proxy 不会被 preview 读取，
  // 必须显式声明 preview.proxy，否则 /api/* 请求会返回 SPA fallback HTML（vite 5 实际尝试 proxy 但 ECONNREFUSED → 500）
  preview: {
    port: 8088,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:8080',
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
        // R8 修复：Element Plus 内部循环依赖，拆分多chunk导致加载顺序错误
        // Cannot access 'V' before initialization → 合并为单个vendor-el
        manualChunks(id) {
          if (id.includes('node_modules/element-plus/es') || id.includes('node_modules/@element-plus/icons-vue')
              || id.includes('node_modules/element-plus/theme')) {
            return 'vendor-el'
          }
          if (id.includes('node_modules/xlsx')) return 'vendor-xlsx'
          if (id.includes('node_modules/@vueuse')) return 'vendor-vueuse'
          if (id.includes('node_modules/axios')) return 'vendor-axios'
          if (id.includes('node_modules/vue-router') || id.includes('node_modules/pinia') || id.includes('node_modules/vue')) return 'vendor-vue-core'
          // 视频播放器 lazy-load: 仅观看视频时加载。
          // 注意：不要用 'video.js' 子串匹配（会误命中 src/api/video.js 等模块 id），
          // 仅将 hls.js 库独立成块；hls 仅被懒加载视图引用时自然落入懒块。
          if (id.includes('node_modules/hls.js')) return 'vendor-video-player'
        },
      }
    },
    chunkSizeWarningLimit: 1000,
  }
})
