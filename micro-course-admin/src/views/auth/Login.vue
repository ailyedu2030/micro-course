<!--
  登录页面 · 教育专业感 + 现代学习感
  PC 端: 居中卡片 + 渐变背景
  H5 端: 全屏 + 顶部品牌区
  依据: docs/DESIGN.md v1.1
-->
<template>
  <div class="login-page" :class="{ 'is-mobile': isMobile }">
    <div class="login-decoration" aria-hidden="true">
      <div class="deco-circle deco-1"></div>
      <div class="deco-circle deco-2"></div>
      <div class="deco-circle deco-3"></div>
    </div>

    <div class="login-container">
      <div class="login-brand">
        <div class="brand-icon">
          <el-icon :size="36"><Reading /></el-icon>
        </div>
        <h1 class="brand-title">微课管理平台</h1>
        <p class="brand-subtitle">让学习更高效 · 让教学更轻松</p>
      </div>

      <el-card class="login-box" shadow="never">
        <h2 class="login-title">账号登录</h2>
        <p class="login-tip">请使用您的账号登录系统</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
          <el-form-item prop="username" label="用户名">
            <el-input id="username" v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable maxlength="50" aria-label="用户名" />
          </el-form-item>
          <el-form-item prop="password" label="密码">
            <el-input
              id="password"
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              clearable
              maxlength="32"
              aria-label="密码"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div v-if="quickAccounts.length > 0" class="login-roles" aria-label="测试账号">
          <span class="role-tip">测试账号：</span>
          <el-tag
            v-for="r in quickAccounts"
            :key="r.label"
            :type="r.type"
            class="role-tag"
            @click="fillAccount(r)"
            effect="plain"
            round
          >
            {{ r.label }}
          </el-tag>
        </div>

        <div v-if="registrationEnabled" class="register-link">
          <span>还没有账号？</span>
          <el-button type="primary" link @click="showRegisterDialog = true">立即注册</el-button>
        </div>
      </el-card>

      <!-- 注册弹窗 -->
      <el-dialog
        v-model="showRegisterDialog"
        title="学生注册"
        width="420px"
        :close-on-click-modal="false"
        center
      >
        <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" size="large" @keyup.enter="handleRegister">
          <el-form-item prop="username" label="用户名">
            <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名（2-50个字符）"
              :prefix-icon="User"
              clearable
              maxlength="50"
            />
          </el-form-item>
          <el-form-item prop="password" label="密码">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="至少8位，含字母和数字"
              :prefix-icon="Lock"
              show-password
              clearable
              maxlength="32"
            />
          </el-form-item>
          <el-form-item prop="confirmPassword" label="确认密码">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
              clearable
              maxlength="32"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showRegisterDialog = false">取消</el-button>
          <el-button type="primary" :loading="registerLoading" @click="handleRegister">
            {{ registerLoading ? '注册中...' : '注册并登录' }}
          </el-button>
        </template>
      </el-dialog>

      <p class="login-footer">
        © {{ new Date().getFullYear() }} 微课管理平台 · Powered by Vue 3 + Element Plus
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { User, Lock, Reading } from '@element-plus/icons-vue'
import { register as registerApi, getRegistrationStatus } from '@/api/auth'
import { PASSWORD_VALIDATORS, USERNAME_VALIDATORS } from '@/utils/constants'
import { setToken, setRefreshToken } from '@/utils/auth'
import { getRoleHomePage } from '@/router'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const isMobile = ref(false)

// 注册相关状态
const showRegisterDialog = ref(false)
const registerFormRef = ref(null)
const registerLoading = ref(false)
const registerForm = reactive({ username: '', password: '', confirmPassword: '' })
const registrationEnabled = ref(true)

// P1C-002: 加载时查询注册开关
const checkRegistrationStatus = async () => {
  try {
    const { data } = await getRegistrationStatus()
    registrationEnabled.value = data?.enabled !== false
  } catch {
    // 接口不可用时默认允许注册
    registrationEnabled.value = true
  }
}

const registerRules = {
  username: USERNAME_VALIDATORS,
  password: PASSWORD_VALIDATORS,
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const form = reactive({ username: '', password: '' })
const rules = {
  username: USERNAME_VALIDATORS,
  password: PASSWORD_VALIDATORS
}

const quickAccounts = import.meta.env.DEV ? [
  { label: '管理员', type: 'danger', username: import.meta.env.VITE_DEMO_ADMIN_USER || 'admin', password: import.meta.env.VITE_DEMO_ADMIN_PASS || 'admin123' },
  { label: '教务处', type: 'warning', username: import.meta.env.VITE_DEMO_ACADEMIC_USER || 'academic', password: import.meta.env.VITE_DEMO_ACADEMIC_PASS || 'password123' },
  { label: '教师', type: 'success', username: import.meta.env.VITE_DEMO_TEACHER_USER || 'p0_teacher', password: import.meta.env.VITE_DEMO_TEACHER_PASS || 'teacher123' },
  { label: '学生', type: 'primary', username: import.meta.env.VITE_DEMO_STUDENT_USER || 'student', password: import.meta.env.VITE_DEMO_STUDENT_PASS || 'student123' }
] : []

const fillAccount = (acc) => {
  form.username = acc.username
  form.password = acc.password
  ElMessage.info(`已填入 ${acc.label} 账号`)
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form)
      // 客户体验修复 v1.7.0: 短时长 1.5s,避免 toast 滞留挡住导航
      ElMessage.success({ message: '登录成功', duration: 1500 })
      const redirect = route.query.redirect
      if (redirect) {
        router.push(redirect)
      } else {
        const home = getRoleHomePage(userStore.role)
        router.push(home)
      }
    } catch (e) {
      // 拦截器已处理 401/500/423，这里兜底 + 差异化展示
      if (e.response?.status === 423) {
        ElMessage.warning('登录失败次数过多，账号已锁定，请 15 分钟后再试')
      } else if (!e.response) {
        ElMessage.error('网络连接失败，请检查后重试')
      }
    } finally {
      loading.value = false
    }
  })
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    registerLoading.value = true
    try {
      const res = await registerApi({
        username: registerForm.username,
        password: registerForm.password
      })
      // 注册成功自动登录：保存 token 并跳转
      if (res.data && res.data.accessToken) {
        setToken(res.data.accessToken)
        setRefreshToken(res.data.refreshToken || '')
        userStore.token = res.data.accessToken
        userStore.refreshToken = res.data.refreshToken || ''
        // P1I-002: getInfo 失败时降级处理 — 允许用户使用默认信息进入首页
        try {
          await userStore.getInfo()
        } catch {
          console.warn('[Login] 注册后 getInfo 失败，使用默认用户信息')
          userStore.userInfo = { realName: registerForm.username, role: 'STUDENT' }
        }
        ElMessage.success('注册成功！欢迎你，' + (userStore.realName || registerForm.username))
        showRegisterDialog.value = false
        router.push('/student/courses')
      }
    } catch (e) {
      // 拦截器已处理 401/500，这里兜底网络错误
      if (!e.response) {
        ElMessage.error('网络连接失败，请检查后重试')
      }
    } finally {
      registerLoading.value = false
    }
  })
}

const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

onMounted(() => {
  checkMobile()
  checkRegistrationStatus()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.login-page {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background:
    radial-gradient(ellipse at 20% 50%, rgba(64,158,255,0.15) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 50%, rgba(99,102,241,0.1) 0%, transparent 50%),
    linear-gradient(135deg, var(--role-primary) 0%, var(--role-primary-dark) 50%, var(--role-primary-darkest) 100%);
  overflow: hidden;
  padding: var(--space-5);
}

.login-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  animation: float 20s infinite ease-in-out;
}

.deco-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  left: -100px;
  animation-delay: 0s;
  background: radial-gradient(circle, rgba(255,255,255,0.12) 0%, transparent 70%);
}

.deco-2 {
  width: 300px;
  height: 300px;
  bottom: -80px;
  right: -80px;
  animation-delay: -7s;
  background: radial-gradient(circle, rgba(255,255,255,0.08) 0%, transparent 70%);
}

.deco-3 {
  width: 200px;
  height: 200px;
  top: 40%;
  right: 20%;
  animation-delay: -14s;
  background: radial-gradient(circle, rgba(255,255,255,0.06) 0%, transparent 70%);
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

.login-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.login-brand {
  text-align: center;
  margin-bottom: var(--space-6);
  color: var(--el-color-white);
}

.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-3);
  color: var(--el-color-white);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.brand-title {
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  margin: 0 0 var(--space-1);
  color: var(--el-color-white);
  letter-spacing: 1px;
}

.brand-subtitle {
  font-size: var(--text-sm);
  margin: 0;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: 0.5px;
}

.login-box {
  width: 100%;
  padding: var(--space-6) var(--space-5);
  background: var(--el-bg-color-overlay);
  border: none;
  border-radius: var(--radius-lg);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2) !important;
}

.login-title {
  text-align: center;
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-1);
}

.login-tip {
  text-align: center;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin: 0 0 var(--space-5);
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  letter-spacing: 3px;
  background: linear-gradient(135deg, var(--role-primary), var(--role-primary-dark));
  border: none;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all var(--duration-base) var(--ease-out);
  position: relative;
  overflow: hidden;
}

.login-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, transparent 30%, rgba(255,255,255,0.1) 50%, transparent 70%);
  opacity: 0;
  transition: opacity var(--duration-base);
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.35);
}

.login-btn:hover::after {
  opacity: 1;
}

.login-btn:active {
  transform: translateY(0) scale(0.98);
}

.login-roles {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px dashed var(--el-border-color-lighter);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  justify-content: center;
}

.role-tip {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.role-tag {
  cursor: pointer;
  font-size: var(--text-xs);
  transition: all var(--duration-base) var(--ease-out);
}

.role-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.register-link {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--el-border-color-lighter);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.register-link span {
  margin-right: var(--space-1);
}

.login-footer {
  text-align: center;
  margin-top: var(--space-5);
  font-size: var(--text-xs);
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.5px;
}

/* H5 端 */
.login-page.is-mobile .login-container {
  max-width: 100%;
}

.login-page.is-mobile .login-box {
  padding: var(--space-5) var(--space-4);
  border-radius: var(--radius-lg);
}

.login-page.is-mobile .brand-title {
  font-size: var(--text-xl);
}

.login-page.is-mobile .deco-1,
.login-page.is-mobile .deco-2,
.login-page.is-mobile .deco-3 {
  opacity: 0.6;
}
</style>
