<!--
  根组件
  路由路径: (root)
  Phase 1
  Author: jackie
-->
<template>
  <div id="app" :class="appClass">
    <router-view v-if="isLoginPage || isVideoPage" v-slot="{ Component }">
      <transition name="page-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <StudentLayout v-else-if="isStudent" />
    <Layout v-else />
  </div>
</template>

<script setup>
import { computed, onErrorCaptured, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from './store/user'
import { isAuthenticated } from './utils/auth'
import Layout from './components/Layout.vue'
import StudentLayout from './components/StudentLayout.vue'

const route = useRoute()
const userStore = useUserStore()

const isLoginPage = computed(() => route.path === '/login')
const isStudent = computed(() => userStore.role === 'STUDENT')
const isVideoPage = computed(() => route.matched.some(r => r.meta?.layout === 'video'))

const appClass = computed(() => ({
  'role-student': isStudent.value && !isVideoPage.value,
  'role-staff': !isStudent.value && !isVideoPage.value,
  'role-video': isVideoPage.value
}))

// 根组件错误边界——捕获子组件未处理的错误
onErrorCaptured((err, instance, info) => {
  console.error('[App ErrorBoundary]', info, err)
  // 返回 false 继续向上传播给 app.config.errorHandler
  return false
})

onMounted(async () => {
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
  font-family: 'Outfit', 'PingFang SC', 'Microsoft YaHei', system-ui, -apple-system, sans-serif;
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

@media (max-width: 768px) {
  .el-table { font-size: 12px; overflow-x: auto; display: block; }
  .el-form--inline .el-form-item { display: block; margin-right: 0; }
  .el-dialog { width: 90% !important; }
  .el-card { padding: 10px; }
  .el-pagination { justify-content: center; }
}
</style>
