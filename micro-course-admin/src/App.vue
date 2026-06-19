<!--
  根组件
  路由路径: (root)
  Phase 1
  Author: jackie
-->
<template>
  <div id="app" :class="appClass">
    <router-view v-if="isLoginPage || isVideoPage" />
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
  if (isAuthenticated() && !userStore.userInfo) {
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
html, body, #app { height: 100%; font-family: 'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif; }
@media (max-width: 768px) {
  .el-table { font-size: 12px; overflow-x: auto; display: block; }
  .el-form--inline .el-form-item { display: block; margin-right: 0; }
  .el-dialog { width: 90% !important; }
  .el-card { padding: 10px; }
  .el-pagination { justify-content: center; }
}
</style>
