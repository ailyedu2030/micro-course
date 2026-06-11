<!--
  登录页面
  路由路径: /login
  Phase 1
  Author: jackie
-->
<template>
  <div class="login-page">
    <div class="login-box">
      <h2 class="login-title">微课管理平台</h2>
      <el-form ref="formRef" :model="form" :rules="rules">
        <el-form-item prop="username"><el-input v-model="form.username" placeholder="用户名" size="large" /></el-form-item>
        <el-form-item prop="password"><el-input v-model="form.password" type="password" placeholder="密码" size="large" @keyup.enter="handleLogin" /></el-form-item>
        <el-form-item><el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">登录</el-button></el-form-item>
      </el-form>
    </div>
  </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form)
      ElMessage.success('登录成功')
      router.push('/')
    } catch {
      // 错误已由 request.js 拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.login-title {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
  font-size: 24px;
}

.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.login-btn {
  width: 100%;
}

@media (max-width: 767px) {
  .login-box { width: 90% !important; padding: 20px !important; }
}
</style>
