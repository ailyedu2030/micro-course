<!--
  根组件
  路由路径: (root)
  Phase 1
  Author: jackie
-->
<template>
  <div id="app" :class="appClass">
    <router-view v-if="isLoginPage" />
    <StudentLayout v-else-if="isStudent" />
    <Layout v-else />
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from './store/user'
import Layout from './components/Layout.vue'
import StudentLayout from './components/StudentLayout.vue'

const route = useRoute()
const userStore = useUserStore()

const isLoginPage = computed(() => route.path === '/login')
const isStudent = computed(() => userStore.role === 'STUDENT')

const appClass = computed(() => ({
  'role-student': isStudent.value,
  'role-staff': !isStudent.value
}))

// 响应式主题切换：role 变化时 Element Plus 变量自动跟随 .role-* 变化
watch(() => userStore.role, (role) => {
  // design-tokens.css 已通过 .role-* 类切换变量，此处仅作安全兜案
}, { immediate: true })

onMounted(() => {
  if (!userStore.userInfo) {
    userStore.getInfo()
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
