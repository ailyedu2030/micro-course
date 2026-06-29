<!--
  根组件
  路由路径: (root)
  Phase 1
  Author: jackie
-->
<template>
  <div id="app" :class="appClass">
    <!-- D2: 全局上传进度浮窗 -->
    <UploadProgress />
    <div v-if="hasError" class="app-error-boundary">
      <div class="error-card">
        <el-icon :size="48" color="var(--el-color-danger)"><WarningFilled /></el-icon>
        <h2>页面出了点问题</h2>
        <p>请尝试刷新页面或返回首页</p>
        <div class="error-actions">
          <el-button type="primary" @click="recover">刷新页面</el-button>
          <el-button @click="goHome">返回首页</el-button>
        </div>
      </div>
    </div>
    <template v-else>
      <router-view v-if="isLoginPage || isVideoPage" v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
      <StudentLayout v-else-if="isStudent" />
      <Layout v-else />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onErrorCaptured, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from './store/user'
import { isAuthenticated } from './utils/auth'
import { reportError } from './utils/errorReport'
import Layout from './components/Layout.vue'
import StudentLayout from './components/StudentLayout.vue'
import UploadProgress from './components/UploadProgress.vue'
import { WarningFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const hasError = ref(false)

const isLoginPage = computed(() => route.path === '/login')
const isStudent = computed(() => userStore.role === 'STUDENT')
const isVideoPage = computed(() => route.matched.some(r => r.meta?.layout === 'video'))

const appClass = computed(() => ({
  'role-student': isStudent.value && !isVideoPage.value,
  'role-staff': !isStudent.value && !isVideoPage.value,
  'role-video': isVideoPage.value
}))

function recover() { hasError.value = false; window.location.reload() }
function goHome() { hasError.value = false; router.push('/login') }

// 根组件错误边界——捕获子组件未处理的错误，显示降级 UI
onErrorCaptured((err, instance, info) => {
  console.error('[App ErrorBoundary]', info, err)
  hasError.value = true
  reportError(err)
  return false
})

// D1: 全局网络状态检测
function setupNetworkListeners() {
  window.addEventListener('offline', () => {
    ElMessage.warning('网络已断开，部分功能暂不可用')
  })
  window.addEventListener('online', () => {
    ElMessage.success('网络已恢复')
  })
}

onMounted(async () => {
  // D1: 启动离线检测
  setupNetworkListeners()
  // 仅在 beforeEach 未填充角色时补充获取（避免冗余 API 调用）
  if (isAuthenticated() && !userStore.role) {
    try {
      await userStore.getInfo()
    } catch (err) {
      console.error('[App] 获取用户信息失败', err)
    }
  }
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app {
  height: 100%;
  display: flex;
  flex-direction: column;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, -apple-system, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

body {
  line-height: 1.6;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color-page);
}

::selection {
  background: var(--role-primary-light-7);
  color: var(--role-primary-darkest);
}

/* 滚动条美化 */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--el-border-color); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: var(--el-text-color-placeholder); }

/* 错误边界降级 UI */
.app-error-boundary { display:flex; align-items:center; justify-content:center; height:100vh; padding:24px; }
.error-card { text-align:center; max-width:400px; }
.error-card h2 { margin:16px 0 8px; font-size:20px; color:var(--el-text-color-primary); }
.error-card p { margin-bottom:24px; color:var(--el-text-color-secondary); }
.error-actions { display:flex; gap:12px; justify-content:center; }

@media (max-width: 768px) {
  /* D8: 表格响应式 — 水平滚动，不破坏布局 */
  .el-table {
    display: block;
    overflow-x: auto;
    max-width: 100%;
    -webkit-overflow-scrolling: touch;
  }
  .el-table .el-table__header,
  .el-table .el-table__body {
    min-width: 600px;
    width: max-content;
  }
  .el-table .el-table__inner-wrapper {
    overflow-x: auto;
  }
  .el-form--inline .el-form-item { display: block; margin-right: 0; }
  .el-dialog { width: 90% !important; }
  .el-card { padding: 10px; }
  .el-pagination { justify-content: center; }
}

/* P2-1: 无障碍动效减弱支持 */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
</style>
