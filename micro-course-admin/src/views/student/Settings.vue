<!--
  偏好设置
  路由路径: /student/settings
  Phase 9.1
  Author: Claude Code Agent
-->
<template>
  <div class="settings-container">
    <!-- PC Layout -->
    <div v-if="!isMobile" class="settings-pc">
      <!-- 面包屑导航 -->
      <el-breadcrumb class="page-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/student/courses' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>个人设置</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="page-header">
        <h2>偏好设置</h2>
      </div>

      <!-- 骨架屏加载 -->
      <div v-if="loading" class="settings-groups">
        <el-card v-for="n in 4" :key="n" class="settings-card student-card-item" shadow="never">
          <el-skeleton animated :rows="3" />
        </el-card>
      </div>

      <!-- 加载失败 -->
      <el-result v-else-if="error" icon="error" title="加载失败" sub-title="设置加载异常">
        <template #extra>
          <el-button type="primary" @click="loadSettings">重新加载</el-button>
        </template>
      </el-result>

      <div v-else class="settings-groups">
        <!-- 播放设置 -->
        <el-card class="settings-card student-card-item" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon class="card-icon"><VideoPlay /></el-icon>
              <span>播放设置</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">播放倍速</span>
              <el-select v-model="settings.playbackSpeed" @change="handleSave" class="settings-control">
                <el-option label="0.75x" value="0.75" />
                <el-option label="1.0x" value="1" />
                <el-option label="1.25x" value="1.25" />
                <el-option label="1.5x" value="1.5" />
                <el-option label="2.0x" value="2" />
              </el-select>
            </div>
            <div class="settings-item">
              <span class="settings-label">自动播放下一节</span>
              <el-switch
                v-model="settings.autoPlayNext"
                @change="handleSave"
                class="settings-control"
              />
            </div>
          </div>
        </el-card>

        <!-- 通知设置 -->
        <el-card class="settings-card student-card-item" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon class="card-icon"><Bell /></el-icon>
              <span>通知设置</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">启用通知</span>
              <el-switch
                v-model="settings.notificationEnabled"
                @change="handleSave"
                class="settings-control"
              />
            </div>
            <div class="settings-item">
              <span class="settings-label">邮件通知</span>
              <el-switch
                v-model="settings.emailNotification"
                @change="handleSave"
                class="settings-control"
              />
            </div>
          </div>
        </el-card>

        <!-- 隐私设置 -->
        <el-card class="settings-card student-card-item" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon class="card-icon"><Lock /></el-icon>
              <span>隐私设置</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">个人主页可见性</span>
              <el-select v-model="settings.profileVisibility" @change="handleSave" class="settings-control">
                <el-option label="公开" value="public" />
                <el-option label="好友可见" value="friends" />
                <el-option label="仅自己可见" value="private" />
              </el-select>
            </div>
            <div class="settings-item">
              <span class="settings-label">显示学习进度</span>
              <el-switch
                v-model="settings.showProgress"
                @change="handleSave"
                class="settings-control"
              />
            </div>
          </div>
        </el-card>

        <!-- 辅助功能 -->
        <el-card class="settings-card student-card-item" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon class="card-icon"><Setting /></el-icon>
              <span>辅助功能</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">减少动画效果</span>
              <el-switch
                v-model="settings.reducedMotion"
                @change="handleSave"
                class="settings-control"
              />
            </div>
            <div class="settings-item">
              <span class="settings-label">高对比度模式</span>
              <el-switch
                v-model="settings.highContrast"
                @change="handleSave"
                class="settings-control"
              />
            </div>
          </div>
        </el-card>
      </div>

      <div v-if="!loading && !error" class="save-button-wrap">
        <el-button type="primary" :loading="!!saveTimer" @click="handleSave" class="save-button">保存设置</el-button>
      </div>
    </div>

    <!-- H5 Layout -->
    <div v-else class="settings-h5">
      <!-- 面包屑导航 -->
      <el-breadcrumb class="h5-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/student/courses' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>个人设置</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="page-header-h5">
        <h2>偏好设置</h2>
      </div>

      <!-- 骨架屏加载 -->
      <div v-if="loading" class="settings-groups-h5">
        <div v-for="n in 3" :key="n" class="settings-group-h5">
          <el-skeleton animated :rows="2" />
        </div>
      </div>

      <!-- 加载失败 -->
      <el-result v-else-if="error" icon="error" title="加载失败" sub-title="设置加载异常">
        <template #extra>
          <el-button type="primary" size="small" :loading="loading" @click="loadSettings">重新加载</el-button>
        </template>
      </el-result>

      <div v-else class="settings-groups-h5">
        <!-- 播放设置 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><VideoPlay /></el-icon>
            <span>播放设置</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>播放倍速</span>
              <el-select v-model="settings.playbackSpeed" @change="handleSave" class="control-select-h5">
                <el-option label="0.75x" value="0.75" />
                <el-option label="1.0x" value="1" />
                <el-option label="1.25x" value="1.25" />
                <el-option label="1.5x" value="1.5" />
                <el-option label="2.0x" value="2" />
              </el-select>
            </div>
            <div class="settings-item-h5">
              <span>自动播放下一节</span>
              <el-switch v-model="settings.autoPlayNext" @change="handleSave" />
            </div>
          </div>
        </div>

        <!-- 通知设置 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><Bell /></el-icon>
            <span>通知设置</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>启用通知</span>
              <el-switch v-model="settings.notificationEnabled" @change="handleSave" />
            </div>
            <div class="settings-item-h5">
              <span>邮件通知</span>
              <el-switch v-model="settings.emailNotification" @change="handleSave" />
            </div>
          </div>
        </div>

        <!-- 隐私设置 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><Lock /></el-icon>
            <span>隐私设置</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>个人主页可见性</span>
              <el-select v-model="settings.profileVisibility" @change="handleSave" class="control-select-h5">
                <el-option label="公开" value="public" />
                <el-option label="好友可见" value="friends" />
                <el-option label="仅自己可见" value="private" />
              </el-select>
            </div>
            <div class="settings-item-h5">
              <span>显示学习进度</span>
              <el-switch v-model="settings.showProgress" @change="handleSave" />
            </div>
          </div>
        </div>

        <!-- 辅助功能 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><Setting /></el-icon>
            <span>辅助功能</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>减少动画效果</span>
              <el-switch v-model="settings.reducedMotion" @change="handleSave" />
            </div>
            <div class="settings-item-h5">
              <span>高对比度模式</span>
              <el-switch v-model="settings.highContrast" @change="handleSave" />
            </div>
          </div>
        </div>
      </div>

      <div v-if="!loading && !error" class="save-button-wrap-h5">
        <el-button type="primary" :loading="!!saveTimer" @click="handleSave" class="save-button-h5">保存设置</el-button>
      </div>

      <div class="safe-area-bottom"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, Bell, Lock, Setting } from '@element-plus/icons-vue'
import { getMyPreferences, updateMyPreferences } from '@/api/notification-preference'

const STORAGE_KEY = 'micro_course_settings'

const loading = ref(false)
const error = ref(false)

const settings = ref({
  playbackSpeed: '1',
  autoPlayNext: true,
  notificationEnabled: true,
  emailNotification: false,
  profileVisibility: 'public',
  showProgress: true,
  reducedMotion: false,
  highContrast: false
})

const isMobile = ref(window.innerWidth <= 768)
let resizeTimer = null
const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    isMobile.value = window.innerWidth <= 768
  }, 200)
}

const loadSettings = async () => {
  loading.value = true
  error.value = false
  try {
    // 优先从后端加载通知偏好设置
    const { data } = await getMyPreferences()
    if (data) {
      settings.value.notificationEnabled = data.allowSite !== false
      settings.value.emailNotification = data.allowEmail === true
    }
    // 播放/隐私/辅助功能从 localStorage 加载（或默认值）
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const parsed = JSON.parse(stored)
      settings.value = {
        playbackSpeed: parsed.playbackSpeed || settings.value.playbackSpeed,
        autoPlayNext: parsed.autoPlayNext !== false,
        notificationEnabled: settings.value.notificationEnabled,
        emailNotification: settings.value.emailNotification,
        profileVisibility: parsed.profileVisibility || settings.value.profileVisibility,
        showProgress: parsed.showProgress !== false,
        reducedMotion: parsed.reducedMotion === true,
        highContrast: parsed.highContrast === true
      }
    }
    // 加载成功后同步到 localStorage
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
  } catch {
    // 后端不可用时回退到 localStorage
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        settings.value = {
          playbackSpeed: parsed.playbackSpeed || '1',
          autoPlayNext: parsed.autoPlayNext !== false,
          notificationEnabled: parsed.notificationEnabled !== false,
          emailNotification: parsed.emailNotification === true,
          profileVisibility: parsed.profileVisibility || 'public',
          showProgress: parsed.showProgress !== false,
          reducedMotion: parsed.reducedMotion === true,
          highContrast: parsed.highContrast === true
        }
      }
    } catch {
      error.value = true
      ElMessage.error('加载设置失败')
    }
  } finally {
    loading.value = false
  }
}

let saveTimer = null
const handleSave = async () => {
  if (saveTimer) return
  saveTimer = setTimeout(() => { saveTimer = null }, 2000)
  try {
    // 保存通知偏好到后端
    await updateMyPreferences({
      allowSite: settings.value.notificationEnabled,
      allowEmail: settings.value.emailNotification
    })
  } catch {
    // 后端保存失败时静默降级
  }
  // 所有设置持久化到 localStorage 作为离线 fallback
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
  } catch {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  isMobile.value = window.innerWidth <= 768
  window.addEventListener('resize', handleResize)
  loadSettings()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) clearTimeout(resizeTimer)
})
</script>

<style scoped>
.settings-container {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 800px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
}

/* PC Layout */
.settings-pc .page-breadcrumb {
  margin-bottom: var(--space-4);
}

.settings-pc .page-header {
  margin-bottom: var(--space-6);
}

.settings-pc .page-header h2 {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.settings-pc .settings-groups {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.settings-pc .settings-card {
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-base) ease;
}

.settings-pc .settings-card:hover {
  box-shadow: var(--shadow-lg);
}

.settings-pc .card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.settings-pc .card-icon {
  color: var(--role-primary);
  font-size: var(--text-lg);
}

.settings-pc .settings-list {
  display: flex;
  flex-direction: column;
}

.settings-pc .settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--el-border-color-light);
}

.settings-pc .settings-item:last-child {
  border-bottom: none;
}

.settings-pc .settings-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-primary);
}

.settings-pc .settings-control {
  width: 140px;
}

.settings-pc .save-button-wrap {
  margin-top: var(--space-6);
  display: flex;
  justify-content: flex-end;
}

.settings-pc .save-button {
  cursor: pointer;
  background-color: var(--role-primary);
  border-color: var(--role-primary);
}

.settings-pc .save-button:hover {
  background-color: var(--role-primary-dark);
  border-color: var(--role-primary-dark);
}

/* H5 Layout */
.settings-h5 {
  padding: var(--space-3);
}

.settings-h5 .h5-breadcrumb {
  margin-bottom: var(--space-3);
}

.settings-h5 .page-header-h5 {
  margin-bottom: var(--space-4);
}

.settings-h5 .page-header-h5 h2 {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.settings-h5 .settings-groups-h5 {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.settings-h5 .settings-group-h5 {
  background: var(--el-bg-color);
  border-radius: var(--radius-lg);
  padding: var(--space-3);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.settings-h5 .group-header-h5 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.settings-h5 .settings-list-h5 {
  display: flex;
  flex-direction: column;
}

.settings-h5 .settings-item-h5 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) 0;
  font-size: var(--text-sm);
  color: var(--el-text-color-primary);
}

.settings-h5 .settings-item-h5:not(:last-child) {
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.settings-h5 .control-select-h5 {
  width: 120px;
}

.settings-h5 .save-button-wrap-h5 {
  margin-top: var(--space-5);
}

.settings-h5 .save-button-h5 {
  cursor: pointer;
  width: 100%;
  background-color: var(--role-primary);
  border-color: var(--role-primary);
}

.settings-h5 .save-button-h5:hover {
  background-color: var(--role-primary-dark);
  border-color: var(--role-primary-dark);
}

.settings-h5 .safe-area-bottom {
  height: calc(env(safe-area-inset-bottom) + var(--space-3));
}

/* Global styles for el-switch active color */
:deep(.el-switch.is-checked .el-switch__core) {
  background-color: var(--role-primary);
  border-color: var(--role-primary);
}

:deep(.el-button--primary) {
  background-color: var(--role-primary);
  border-color: var(--role-primary);
}

:deep(.el-button--primary:hover) {
  background-color: var(--role-primary-dark);
  border-color: var(--role-primary-dark);
}
</style>