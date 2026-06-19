<!--
  学生端布局组件 - PC / H5 双端分离
  PC (> 768px): 顶部 Header + 内容区
  H5 (≤ 768px): 顶部 Header + 内容区 + 底部 Tab Bar
  Phase 2
-->
<template>
  <div class="student-layout role-student">
    <!-- ====== PC 端顶部导航 (> 768px) ====== -->
    <header class="layout-header layout-header--pc">
      <div class="header-left">
        <div class="logo">
          <el-icon class="logo-icon" :size="28"><Microphone /></el-icon>
          <span class="logo-text">微课平台</span>
        </div>
      </div>

      <nav class="header-nav" aria-label="主导航">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-tab"
          :class="{ 'is-active': isActive(item.path) }"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="header-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索课程..."
          class="header-search"
          :prefix-icon="Search"
          clearable
          @keyup.enter="handleSearch"
        />

        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notification-badge">
          <el-button :icon="Bell" circle class="icon-btn" @click="goNotifications" aria-label="通知中心" />
        </el-badge>

        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="user-avatar-wrap">
            <el-avatar v-if="avatarUrl" :src="avatarUrl" :size="36" class="user-avatar" />
            <div v-else class="user-avatar user-avatar--fallback" :style="avatarStyle">
              {{ userInitials }}
            </div>
            <el-tag size="small" type="info" effect="plain" class="role-badge">学生</el-tag>
            <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile" :icon="User">
                个人中心
              </el-dropdown-item>
              <el-dropdown-item command="reviews" :icon="Star">
                我的评价
              </el-dropdown-item>
              <el-dropdown-item command="report" :icon="DataLine">
                我的周报
              </el-dropdown-item>
              <el-dropdown-item command="settings" :icon="Setting">
                设置
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided :icon="SwitchButton" class="logout-item">
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- ====== H5 端顶部导航 (≤ 768px) ====== -->
    <header class="layout-header layout-header--h5">
      <div class="h5-header-inner">
        <button v-if="showBackBtn" class="back-btn" @click="handleBack" aria-label="返回">
          <el-icon :size="20"><ArrowLeft /></el-icon>
        </button>
        <span class="h5-title">{{ pageTitle }}</span>
        <div class="h5-actions">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notification-badge">
            <el-button :icon="Bell" circle size="small" class="icon-btn" @click="goNotifications" aria-label="通知中心" />
          </el-badge>
        </div>
      </div>
    </header>

    <!-- ====== 主体内容区 ====== -->
    <main class="layout-main" :class="{ 'has-tabbar': isMobile }">
      <div class="layout-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <!-- ====== H5 底部 Tab Bar (≤ 768px) ====== -->
    <nav v-if="isMobile" class="tabbar" aria-label="底部导航">
      <router-link
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        class="tab-item"
        :class="{ 'is-active': isActive(item.path) }"
      >
        <el-icon :size="22"><component :is="item.icon" /></el-icon>
        <span class="tab-label">{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Grid, VideoPlay, DataLine, Bell, User, Star, Setting, Reading,
  Microphone, Search, ArrowDown, ArrowLeft, SwitchButton
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import { useNotificationStore } from '../store/notification'

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------
const userStore = useUserStore()
const notificationStore = useNotificationStore()

// ---------------------------------------------------------------------------
// Router
// ---------------------------------------------------------------------------
const route = useRoute()
const router = useRouter()

// ---------------------------------------------------------------------------
// 响应式
// ---------------------------------------------------------------------------
// P2-1: resize 事件添加防抖，避免频繁触发响应式计算
const isMobile = ref(window.innerWidth <= 768)
let resizeTimer = null

function onResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    isMobile.value = window.innerWidth <= 768
  }, 150)
}

onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  clearTimeout(resizeTimer)
})

// ---------------------------------------------------------------------------
// 菜单项（从 router 动态读取，meta.menuTab = true 的学生端路由）
// ---------------------------------------------------------------------------
const ICON_MAP = {
  Grid,
  VideoPlay,
  Bell,
  User,
  DataLine,
  Reading,
}

// P1-2: 从 router 动态读取 menuTab 标记的路由，无需二次硬编码过滤
const menuItems = router.getRoutes()
  .filter(r => r.path.startsWith('/student') && r.meta?.menuTab)
  .sort((a, b) => (a.meta?.menuOrder ?? 99) - (b.meta?.menuOrder ?? 99))
  .map(r => ({
    label: r.meta?.menuLabel ?? r.name?.replace(/^Student/, '') ?? r.path,
    path: r.path,
    icon: markRaw(ICON_MAP[r.meta?.menuIcon] ?? Grid),
  }))

// ---------------------------------------------------------------------------
// 活跃路由判断
// ---------------------------------------------------------------------------
// P1-6: 精确匹配 — 避免 startsWith('/student/my-courses') 误匹配其他路由
function isActive(path) {
  if (path === '/student/courses') {
    return route.path === '/student/courses' || route.path.startsWith('/student/courses/') || route.path === '/student/redirect'
  }
  return route.path === path || route.path.startsWith(path + '/')
}

// ---------------------------------------------------------------------------
// 未读消息数
// ---------------------------------------------------------------------------
const unreadCount = computed(() => notificationStore.unreadCount)

// ---------------------------------------------------------------------------
// 用户信息
// ---------------------------------------------------------------------------
const userInfo = computed(() => userStore.userInfo || {})
const avatarUrl = computed(() => userInfo.value.avatar || '')
const userInitials = computed(() => {
  const name = userStore.realName || userStore.username || '?'
  return name.charAt(0).toUpperCase()
})
const avatarStyle = computed(() => ({
  background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
}))

// ---------------------------------------------------------------------------
// H5 头部
// ---------------------------------------------------------------------------
// P1-7: H5 标题映射 — 增加更多路径覆盖 + 兜底使用 route.meta.menuLabel / 默认值
const h5TitleMap = {
  '/student/courses': '课程广场',
  '/student/my-courses': '我的课程',
  '/student/notifications': '消息通知',
  '/student/profile': '个人中心',
  '/student/reviews': '我的评价',
  '/student/report': '我的周报',
  '/student/settings': '设置',
  '/student/learning': '学习',
  '/student/learning-stats': '学习统计',
  '/student/training': '训练中心',
  '/student/exams': '考试',
  '/student/achievements': '成就墙',
  '/student/discussions': '讨论',
}
const pageTitle = computed(() => {
  const found = Object.entries(h5TitleMap).find(([k]) => route.path.startsWith(k))
  return found ? found[1] : (route.meta?.menuLabel || '微课平台')
})
const showBackBtn = computed(() => {
  // 只有在子页面（非 4 个主 Tab）才显示返回按钮
  // 使用精确匹配，确保 /student/courses/123 等子页面能显示返回按钮
  const mainPaths = ['/student/courses', '/student/my-courses', '/student/notifications', '/student/profile']
  return !mainPaths.includes(route.path)
})

// P2-3: 返回按钮 — 无历史记录时兜底到课程广场首页
function handleBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/student/courses')
  }
}

// ---------------------------------------------------------------------------
// 搜索
// ---------------------------------------------------------------------------
const searchKeyword = ref('')
function handleSearch() {
  if (searchKeyword.value.trim()) {
    router.push({ path: '/student/courses', query: { keyword: searchKeyword.value.trim() } })
    searchKeyword.value = ''
  }
}

// ---------------------------------------------------------------------------
// 通知跳转
// ---------------------------------------------------------------------------
function goNotifications() {
  router.push('/student/notifications')
}

// ---------------------------------------------------------------------------
// 用户下拉菜单
// ---------------------------------------------------------------------------
function handleUserCommand(command) {
  const map = {
    profile: '/student/profile',
    reviews: '/student/reviews',
    report: '/student/report',
    settings: '/student/settings',
    logout: null,
  }
  if (command === 'logout') {
    handleLogout()
  } else if (map[command]) {
    router.push(map[command])
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录?', '提示', { type: 'warning' })
    await userStore.logout()
    router.push('/login')
  } catch { /* user cancel */ }
}

// ---------------------------------------------------------------------------
// 通知轮询
// ---------------------------------------------------------------------------
onMounted(() => notificationStore.startPolling(30000))
onUnmounted(() => notificationStore.stopPolling())
</script>

<style scoped>
/* ---------------------------------------------------------------------------
   2. 布局容器
   --------------------------------------------------------------------------- */
.student-layout {
  min-height: 100vh;
  background: var(--el-bg-color-page, #f0f2f5);
  display: flex;
  flex-direction: column;
}

/* ---------------------------------------------------------------------------
   3. PC 顶部导航 (≥ 769px)
   --------------------------------------------------------------------------- */
.layout-header--pc {
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 var(--space-5);
  background: var(--el-bg-color-overlay);
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  box-shadow: var(--shadow-sm);
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  gap: var(--space-5);
}

.header-left {
  flex-shrink: 0;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
}

.logo-icon {
  color: var(--role-primary, #6366f1);
}

.logo-text {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary, #303133);
  white-space: nowrap;
}

.header-nav {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-lg, 12px);
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-secondary, #909399);
  text-decoration: none;
  cursor: pointer;
  transition: color var(--duration-base) var(--ease-out),
              background var(--duration-base) var(--ease-out);
  white-space: nowrap;
}

.nav-tab:hover {
  color: var(--role-primary, #6366f1);
  background: var(--role-primary-light-9, #eef2ff);
}

.nav-tab.is-active {
  color: var(--role-primary, #6366f1);
  background: var(--role-primary-light-9, #eef2ff);
  font-weight: var(--weight-semibold);
}

.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.header-search {
  width: 200px;
}

.header-search :deep(.el-input__wrapper) {
  border-radius: var(--radius-xl, 16px);
  background: var(--el-fill-color-light, #f5f7fa);
  box-shadow: none;
}

.icon-btn {
  border: none;
  background: transparent;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  transition: color var(--duration-base) var(--ease-out),
              background var(--duration-base) var(--ease-out);
}

.icon-btn:hover {
  color: var(--role-primary, #6366f1);
  background: var(--role-primary-light-9, #eef2ff);
}

.notification-badge {
  display: flex;
  align-items: center;
}

.user-avatar-wrap {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  border-radius: var(--radius-pill);
  padding: var(--space-1) var(--space-2);
  transition: background var(--duration-base) var(--ease-out);
}

.user-avatar-wrap:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.user-avatar {
  border: 2px solid var(--role-primary-light-5, #c7d2fe);
}

.user-avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: var(--el-color-white);
  font-weight: var(--weight-semibold);
  font-size: var(--text-base);
  border-radius: var(--radius-circle);
}

.role-badge {
  font-size: var(--text-xs);
  border-color: var(--role-primary-light-5, #c7d2fe);
  color: var(--role-primary, #6366f1);
  background: var(--role-primary-light-9, #eef2ff);
}

.dropdown-arrow {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.logout-item {
  color: var(--el-color-danger, #f56c6c);
}

/* ---------------------------------------------------------------------------
   4. H5 顶部导航 (≤ 768px)
   --------------------------------------------------------------------------- */
.layout-header--h5 {
  display: none;
  height: 48px;
  padding: 0 var(--space-3);
  background: var(--el-bg-color-overlay);
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  box-shadow: var(--shadow-xs);
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
}

.h5-header-inner {
  display: flex;
  align-items: center;
  height: 100%;
  gap: var(--space-3);
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-out);
  flex-shrink: 0;
}

.back-btn:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.h5-title {
  flex: 1;
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary, #303133);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.h5-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* ---------------------------------------------------------------------------
   5. 主体内容区 (PC 端统一 1200px 居中，H5 端不限制)
   --------------------------------------------------------------------------- */
.layout-main {
  flex: 1;
  padding: 0;
}

.layout-content {
  width: 100%;
}

.layout-main.has-tabbar {
  padding-bottom: calc(56px + var(--space-4));
}

/* PC 端：内容容器限宽 1200px 居中 (学生端统一布局约束) */
@media (min-width: 768px) {
  .layout-content {
    max-width: 1200px;
    margin: 0 auto;
  }
}

/* ---------------------------------------------------------------------------
   6. H5 底部 Tab Bar (≤ 768px)
   --------------------------------------------------------------------------- */
.tabbar {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: var(--el-bg-color-overlay);
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.06);
  z-index: var(--z-fixed);
  justify-content: space-around;
  align-items: center;
  padding: 0 var(--space-1);
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex: 1;
  height: 100%;
  color: var(--el-text-color-secondary, #909399);
  text-decoration: none;
  cursor: pointer;
  transition: color var(--duration-base) var(--ease-out);
}

.tab-item .tab-label {
  font-size: 11px;
  line-height: 1;
}

.tab-item.is-active {
  color: var(--role-primary, #6366f1);
}

.tab-item:hover {
  color: var(--role-primary, #6366f1);
}

/* ---------------------------------------------------------------------------
   7. 路由过渡动画
   --------------------------------------------------------------------------- */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-base) var(--ease-out);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ---------------------------------------------------------------------------
   8. 响应式显示/隐藏
   --------------------------------------------------------------------------- */
@media (max-width: 768px) {
  .layout-header--pc {
    display: none;
  }

  .layout-header--h5 {
    display: flex;
  }

  .tabbar {
    display: flex;
  }
}

@media (min-width: 768px) {
  .layout-header--pc {
    display: flex;
  }

  .layout-header--h5 {
    display: none;
  }

  .tabbar {
    display: none;
  }
}

/* ---------------------------------------------------------------------------
   9. PC 端内容限宽 (> 768px)
   --------------------------------------------------------------------------- */
:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-base);
  padding: var(--space-2) var(--space-4);
}

:deep(.el-dropdown-menu__item .el-icon) {
  font-size: 16px;
}
</style>