<template>
  <router-view v-if="isLoginPage" />
  <StudentLayout v-else-if="isStudent" />
  <Layout v-else />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from './store/user'
import Layout from './components/Layout.vue'
import StudentLayout from './components/StudentLayout.vue'

const route = useRoute()
const userStore = useUserStore()

const isLoginPage = computed(() => route.path === '/login')
const isStudent = computed(() => userStore.role === 'STUDENT')
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; font-family: 'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif; }
@media (max-width: 767px) {
  .el-table { font-size: 12px; overflow-x: auto; display: block; }
  .el-form--inline .el-form-item { display: block; margin-right: 0; }
  .el-dialog { width: 90% !important; }
  .el-card { padding: 10px; }
  .el-pagination { justify-content: center; }
}
</style>
