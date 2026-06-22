<!--
  管理后台主布局
  PC 端布局组件 (移动端暂不动)
  Author: jackie
-->
<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside class="layout-aside" :class="{ 'is-collapsed': collapsed }">
      <!-- Logo 区域 -->
      <div class="layout-logo">
        <el-icon class="logo-icon"><Microphone /></el-icon>
        <span v-show="!collapsed" class="logo-text">微课管理平台</span>
        <span v-show="collapsed" class="logo-text-short">微课</span>
      </div>

      <!-- 菜单（配置驱动） -->
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="true"
        router
        class="layout-menu"
      >
        <template v-for="group in currentMenu" :key="group.group">
          <el-sub-menu :index="group.group">
            <template #title>
              <el-icon><component :is="iconMap[group.icon]" /></el-icon>
              <span v-show="!collapsed">{{ group.group }}</span>
            </template>
            <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
              <el-icon><component :is="iconMap[item.icon]" /></el-icon>
              <template #title>{{ item.label }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container class="layout-body">
      <!-- 顶部 Header -->
      <el-header class="layout-header">
        <!-- 左侧：折叠按钮 + 面包屑 -->
        <div class="header-left">
          <el-icon class="header-collapse-btn" @click="toggleCollapse" :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'">
            <Fold v-if="!collapsed" /><Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <!-- 右侧：主题切换 + 通知 + 用户 -->
        <div class="header-right">
          <el-tooltip :content="isDark ? '切换亮色模式' : '切换深色模式'" placement="bottom">
            <el-icon class="header-icon theme-toggle-btn" @click="toggleTheme" :aria-label="isDark ? '切换亮色模式' : '切换深色模式'">
              <Moon v-if="!isDark" />
              <Sunny v-else />
            </el-icon>
          </el-tooltip>
          <el-icon class="header-icon" @click="$router.push('/notifications')" aria-label="通知中心">
            <el-badge :value="notificationStore.unreadCount" :hidden="!notificationStore.unreadCount" :max="99">
              <Bell />
            </el-badge>
          </el-icon>

          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-avatar-wrapper">
              <el-avatar
                :size="32"
                :src="userStore.userInfo?.avatar"
                class="user-avatar"
              >
                {{ userStore.realName?.charAt(0) || userStore.username?.charAt(0) || 'U' }}
              </el-avatar>
              <span class="user-name">{{ userStore.realName || userStore.username }}</span>
              <el-tag v-if="userStore.role" size="small" type="primary" class="user-role-tag">
                {{ roleLabel }}
              </el-tag>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.role === 'ADMIN' || userStore.role === 'ACADEMIC'" command="settings">
                  <el-icon><Setting /></el-icon>系统设置
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主体内容 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
  <!-- 噪点纹理覆盖层 · 教职工端微装饰 -->
  <div class="staff-noise-overlay" aria-hidden="true" />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useNotificationStore } from '@/store/notification'
import {
  Fold, Expand, Bell, ArrowDown, Microphone, Grid, OfficeBuilding, Reading,
  School, User, Notebook, VideoCamera, Film, FolderOpened, List, VideoPlay,
  Tickets, Document, Edit, UserFilled, DataAnalysis, Finished, ChatLineSquare,
  Star, Setting, Odometer, Clock, Tools, SwitchButton, Sunny, Moon,
  TrendCharts, PictureFilled
} from '@element-plus/icons-vue'
import { menuConfig } from '@/config/menuConfig'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

// 图标名称到组件的映射（用于配置驱动菜单的动态渲染）
const iconMap = {
  Grid, OfficeBuilding, Reading, School, User, Notebook, VideoCamera, Film,
  FolderOpened, List, VideoPlay, Tickets, Document, Edit, UserFilled,
  DataAnalysis, Finished, ChatLineSquare, Star, Setting, Odometer, Clock,
  Tools, Bell, TrendCharts, PictureFilled, Microphone,
}

// 当前角色菜单（从配置中读取）
const currentMenu = computed(() => {
  return menuConfig[userStore.role] || menuConfig.ADMIN
})

// 深色模式状态
const isDark = ref(false)

function initTheme() {
  const saved = localStorage.getItem('theme')
  if (saved === 'dark') {
    isDark.value = true
    document.documentElement.setAttribute('data-theme', 'dark')
  } else {
    isDark.value = false
    document.documentElement.removeAttribute('data-theme')
  }
}

function toggleTheme() {
  isDark.value = !isDark.value
  if (isDark.value) {
    document.documentElement.setAttribute('data-theme', 'dark')
    localStorage.setItem('theme', 'dark')
  } else {
    document.documentElement.removeAttribute('data-theme')
    localStorage.setItem('theme', 'light')
  }
}

// 侧边栏折叠状态
const collapsed = ref(false)

// 响应式监听——窗口宽度 < 1200px 自动折叠
function checkResponsive() {
  if (window.innerWidth < 1200 && !collapsed.value) {
    collapsed.value = true
  }
}
let resizeTimer = null
function handleWindowResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(checkResponsive, 150)
}

// 当前激活菜单
const activeMenu = computed(() => route.path)

// 角色标签文本
const roleLabel = computed(() => {
  const roleMap = {
    ADMIN: '管理员',
    TEACHER: '教师',
    ACADEMIC: '教务处',
    STUDENT: '学生'
  }
  return roleMap[userStore.role] || userStore.role || ''
})

// 切换折叠
function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem('sidebar_collapsed', String(collapsed.value))
}

// 处理下拉命令
async function handleCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录?', '提示', { type: 'warning' })
    } catch { return }
    try {
      await userStore.logout()
      router.push('/login')
    } catch { ElMessage.error('退出失败') }
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'settings') {
    router.push('/admin/settings')
  }
}

// 监听路由变化关闭移动端抽屉
watch(() => route.path, () => {
  // 路由变化时确保移动端逻辑不干扰
})

onMounted(() => {
  // 恢复主题
  initTheme()
  // 恢复折叠状态
  const saved = localStorage.getItem('sidebar_collapsed')
  if (saved !== null) {
    collapsed.value = saved === 'true'
  }
  checkResponsive()
  window.addEventListener('resize', handleWindowResize)
  // 启动通知轮询
  notificationStore.startPolling(30000)
})

onUnmounted(() => {
  notificationStore.stopPolling()
  window.removeEventListener('resize', handleWindowResize)
  if (resizeTimer) clearTimeout(resizeTimer)
})
</script>

<style scoped>
.layout-container {
  height: 100dvh;
  overflow: hidden;
}

/* ==================== 侧边栏 ==================== */
.layout-aside {
  width: 240px;
  background: var(--sidebar-bg, #1e293b);
  background-image: linear-gradient(180deg, rgba(64,158,255,0.03) 0%, transparent 30%);
  transition: width var(--duration-slow) var(--ease-in-out);
  overflow-y: auto;
  overflow-x: hidden;
  flex-shrink: 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,0.12) transparent;
  border-right: 1px solid rgba(255,255,255,0.04);
}

.layout-aside::-webkit-scrollbar {
  width: 4px;
}

.layout-aside::-webkit-scrollbar-track {
  background: transparent;
}

.layout-aside::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.12);
  border-radius: 2px;
}

.layout-aside.is-collapsed {
  width: 64px;
}

/* Logo */
.layout-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  background: var(--sidebar-logo-bg, #0f172a);
  color: #f1f5f9;
  font-size: var(--text-md);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-wide);
  overflow: hidden;
  border-bottom: 1px solid rgba(255,255,255,0.05);
}

.logo-icon {
  font-size: 20px;
  flex-shrink: 0;
  color: var(--role-primary);
}

.logo-text {
  transition: opacity var(--duration-slow) var(--ease-in-out);
}

.logo-text-short {
  display: none;
}

/* 折叠后只显示短文字 */
.layout-aside.is-collapsed .logo-text {
  display: none;
}

.layout-aside.is-collapsed .logo-text-short {
  display: inline;
}

/* 菜单 */
.layout-menu {
  background-color: transparent;
  border-right: none;
  --el-menu-text-color: var(--sidebar-text, #bfcbd9);
  --el-menu-active-color: var(--role-primary);
  --el-menu-hover-bg-color: var(--sidebar-hover, #1f2d3d);
  --el-menu-hover-text-color: var(--role-primary);
  --el-menu-bg-color: transparent;
  --el-menu-item-height: 42px;
  --el-menu-sub-menu-title-height: 42px;
}

.layout-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 菜单项 */
:deep(.el-menu-item) {
  padding-left: 20px !important;
}

/* 菜单项 hover */
:deep(.el-menu-item:hover) {
  background-color: var(--sidebar-hover, rgba(255,255,255,0.06));
  color: #fff;
}

/* 激活项 — 毛玻璃霓虹指示条 */
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(99,102,241,0.12) 0%, transparent 100%);
  color: var(--role-primary);
  font-weight: var(--weight-medium);
  position: relative;
}

:deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 20px;
  background: var(--role-primary);
  border-radius: 0 2px 2px 0;
  box-shadow: 0 0 8px rgba(99,102,241,0.25);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: var(--role-primary);
}

/* 子菜单 */
:deep(.el-sub-menu__title) {
  padding-left: 20px !important;
}

/* 子菜单 hover */
:deep(.el-sub-menu__title:hover) {
  background-color: rgba(255,255,255,0.04) !important;
}

/* 折叠模式隐藏指示条 */
.layout-aside.is-collapsed :deep(.el-menu-item.is-active::before) {
  display: none;
}

/* 折叠时隐藏文字 */
.layout-aside.is-collapsed :deep(.el-sub-menu__title span),
.layout-aside.is-collapsed :deep(.el-menu-item span) {
  display: none;
}

/* 折叠时图标居中 */
.layout-aside.is-collapsed :deep(.el-sub-menu__title),
.layout-aside.is-collapsed :deep(.el-menu-item) {
  justify-content: center;
  padding-left: 0 !important;
  padding-right: 0 !important;
}

.layout-aside.is-collapsed :deep(.el-sub-menu__title .el-icon),
.layout-aside.is-collapsed :deep(.el-menu-item .el-icon) {
  margin-left: 0;
}

/* ==================== 主体 ==================== */
.layout-body {
  flex-direction: column;
  overflow: hidden;
}

/* ==================== 顶部 Header ==================== */
.layout-header {
  height: 60px;
  background: var(--el-bg-color-overlay);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-5);
  box-shadow: var(--shadow-xs);
  flex-shrink: 0;
  z-index: var(--z-sticky);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.header-collapse-btn {
  font-size: 18px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  transition: color var(--duration-base) var(--ease-out),
              background-color var(--duration-base) var(--ease-out);
}

.header-collapse-btn:hover {
  color: var(--role-primary);
  background-color: var(--role-primary-light-9);
}

/* ==================== 右侧用户区 ==================== */
.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.header-icon {
  font-size: 18px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  transition: color var(--duration-base) var(--ease-out),
              background-color var(--duration-base) var(--ease-out);
  display: flex;
  align-items: center;
}

.header-icon:hover {
  color: var(--role-primary);
  background-color: var(--role-primary-light-9);
}

.theme-toggle-btn {
  font-size: 20px;
}

.user-avatar-wrapper {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  transition: background-color var(--duration-base) var(--ease-out);
}

.user-avatar-wrapper:hover {
  background-color: var(--role-primary-light-9);
}

.user-avatar {
  flex-shrink: 0;
  background-color: var(--role-primary);
  color: var(--el-color-white);
  font-weight: var(--weight-medium);
}

.user-name {
  font-size: var(--text-base);
  color: var(--el-text-color-regular);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role-tag {
  --el-tag-font-size: var(--text-xs);
  transform: scale(0.85);
}

.user-arrow {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* ==================== 主体区域 ==================== */
.layout-main {
  background: var(--el-bg-color-page);
  padding: var(--space-4);
  overflow-y: auto;
  flex: 1;
}

/* ==================== 页面切换动画 (已迁移到 design-tokens.css .page-fade-*) ==================== */

/* ==================== 响应式 ==================== */
@media (max-width: 1200px) {
  .layout-header {
    padding: 0 var(--space-3);
  }

  .user-name,
  .user-role-tag {
    display: none;
  }
}

@media (max-width: 768px) {
  .layout-main {
    padding: var(--space-2);
  }

  .header-left {
    gap: var(--space-2);
  }

  .header-right {
    gap: var(--space-2);
  }

  .user-arrow {
    display: none;
  }
}
</style>