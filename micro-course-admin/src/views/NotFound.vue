<template>
  <div class="not-found-container">
    <div class="not-found-card">
      <div class="nf-icon-wrap">
        <svg viewBox="0 0 80 80" width="80" height="80" fill="none" stroke="currentColor" stroke-width="1.2" class="nf-icon">
          <circle cx="40" cy="40" r="28" stroke="var(--role-primary)" opacity="0.2"/>
          <circle cx="40" cy="40" r="28" stroke="var(--role-primary)" stroke-dasharray="176" stroke-dashoffset="44" stroke-linecap="round"/>
          <path d="M40 28v16M40 52h.01" stroke="var(--role-primary)" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <h1 class="nf-code">404</h1>
      <p class="nf-title">页面未找到</p>
      <p class="nf-desc">您访问的页面不存在，可能已被删除或地址有误</p>
      <div class="nf-actions">
        <el-button type="primary" @click="goHome" round>返回首页</el-button>
        <el-button @click="$router.back()" round>返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  // Round 8-3: 已登录用户回到角色首页，未登录用户到登录页
  if (userStore.isLoggedIn) {
    const role = userStore.role
    if (role === 'STUDENT') router.push('/student/courses')
    else if (role === 'TEACHER') router.push('/teacher/dashboard')
    else if (role === 'ACADEMIC') router.push('/academic/dashboard')
    else if (role === 'ADMIN') router.push('/admin/dashboard')
    else router.push('/login')
  } else {
    router.push('/login')
  }
}
</script>

<style scoped>
.not-found-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100dvh;
  background: var(--el-bg-color-page, #f0f2f5);
  padding: var(--space-5);
}

.not-found-card {
  text-align: center;
  background: var(--el-bg-color-overlay, #fff);
  border-radius: var(--radius-xl, 16px);
  padding: var(--space-9) var(--space-8);
  box-shadow: var(--shadow-lg);
  max-width: 420px;
  width: 100%;
}

.nf-icon-wrap {
  margin-bottom: var(--space-5);
  display: flex;
  justify-content: center;
}

.nf-icon {
  animation: nf-pulse 2s ease-in-out infinite;
}

@keyframes nf-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.8; }
}

.nf-code {
  font-size: 64px;
  font-weight: var(--weight-bold);
  color: var(--role-primary);
  line-height: 1;
  margin-bottom: var(--space-2);
  letter-spacing: -2px;
}

.nf-title {
  font-size: var(--text-xl, 20px);
  font-weight: var(--weight-semibold, 600);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-3);
}

.nf-desc {
  font-size: var(--text-sm, 13px);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-6);
  line-height: 1.6;
}

.nf-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: center;
}
</style>
