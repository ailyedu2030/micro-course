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
import { ref, computed, onErrorCaptured, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from './store/user'
import { isAuthenticated, removeToken, removeRefreshToken } from './utils/auth'
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

function handleOffline() {
  ElMessage.warning('网络已断开，部分功能暂不可用')
}

function handleOnline() {
  ElMessage.success('网络已恢复')
}

function handleStorageChange(e) {
  // 跨 tab 登出检测: token 被移除 → 完全清理登录态
  if (e.key === 'micro_course_token' && !e.newValue && e.oldValue) {
    userStore.token = ''
    userStore.refreshToken = ''
    userStore.userInfo = null
    removeRefreshToken()
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login')
    }
    return
  }
  // 跨 tab token 变更同步
  if (e.key === 'micro_course_token' && e.newValue !== e.oldValue) {
    if (e.newValue !== userStore.token) {
      userStore.token = e.newValue || ''
      userStore.userInfo = null
    }
  }
  // 跨 tab refreshToken 变更同步
  if (e.key === 'micro_course_refresh_token') {
    userStore.refreshToken = e.newValue || ''
  }
}

function handleTokenRefreshed(e) {
  userStore.token = e.detail.token
  if (e.detail.refreshToken) {
    userStore.refreshToken = e.detail.refreshToken
  }
}

function registerGlobalListeners() {
  window.addEventListener('offline', handleOffline)
  window.addEventListener('online', handleOnline)
  window.addEventListener('storage', handleStorageChange)
  window.addEventListener('token-refreshed', handleTokenRefreshed)
}

function unregisterGlobalListeners() {
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('token-refreshed', handleTokenRefreshed)
}

onMounted(async () => {
  registerGlobalListeners()
  // 仅在 beforeEach 未填充角色时补充获取（避免冗余 API 调用）
  if (isAuthenticated() && !userStore.role) {
    try {
      await userStore.getInfo()
    } catch (err) {
      console.error('[App] 获取用户信息失败', err)
    }
  }
  // P1-C: 修复 QuillEditor 工具栏按钮缺少 aria-label（aria-command-name）
  fixQuillToolbarAria()
})

// P1-C: 为 QuillEditor 工具栏按钮添加中文 aria-label
// axe-core 会把无文本内容的 icon-only <button> 标记为 aria-command-name 违规
function fixQuillToolbarAria() {
  const QUILL_LABELS = {
    'ql-bold': '粗体',
    'ql-italic': '斜体',
    'ql-underline': '下划线',
    'ql-strike': '删除线',
    'ql-link': '插入链接',
    'ql-clean': '清除格式',
    'ql-blockquote': '引用',
    'ql-code-block': '代码块',
    'ql-image': '插入图片',
    'ql-video': '插入视频',
    'ql-formula': '公式',
    'ql-list': '有序列表',
    'ql-bullet': '无序列表',
    'ql-indent': '增加缩进',
    'ql-outdent': '减少缩进',
    'ql-align': '对齐',
    'ql-direction': '文字方向',
    'ql-size': '字号',
    'ql-header': '标题',
    'ql-color': '文字颜色',
    'ql-background': '背景色',
    'ql-font': '字体',
    'ql-script': '上标/下标'
  }
  const applyAriaLabels = () => {
    document.querySelectorAll('.ql-toolbar button, .ql-picker-label').forEach(btn => {
      if (btn.hasAttribute('aria-label')) return
      const cls = Array.from(btn.classList).find(c => c.startsWith('ql-'))
      if (cls && QUILL_LABELS[cls]) {
        btn.setAttribute('aria-label', QUILL_LABELS[cls])
      } else if (cls) {
        btn.setAttribute('aria-label', cls.replace('ql-', '').replace('-', ' '))
      } else if (btn.classList.contains('ql-picker-label')) {
        // Picker label 本身没有 ql- class，父元素有
        const parentCls = btn.parentElement && Array.from(btn.parentElement.classList).find(c => c.startsWith('ql-'))
        if (parentCls && QUILL_LABELS[parentCls]) {
          btn.setAttribute('aria-label', QUILL_LABELS[parentCls])
        }
      }
    })
  }
  applyAriaLabels()
  const obs = new MutationObserver(applyAriaLabels)
  obs.observe(document.body, { childList: true, subtree: true })
  // 存入 window 以便调试/清理
  window.__quillObserver = obs
}

onBeforeUnmount(() => {
  unregisterGlobalListeners()
  // 断开 Quill 无障碍 MutationObserver，防止内存泄漏
  if (window.__quillObserver) {
    window.__quillObserver.disconnect()
    window.__quillObserver = null
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
