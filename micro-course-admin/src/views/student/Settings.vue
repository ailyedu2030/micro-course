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
      <el-breadcrumb separator="→" class="page-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/student/courses' }">{{ $t('layout.home') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ $t('studentSettings.personalSettings') }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="page-header">
        <h2>{{ $t('studentSettings.preferences') }}</h2>
      </div>

      <!-- 骨架屏加载 -->
      <div v-if="loading" class="settings-groups">
        <el-card v-for="n in 4" :key="n" class="settings-card student-card-item" shadow="never">
          <el-skeleton animated :rows="3" />
        </el-card>
      </div>

      <!-- 加载失败 -->
      <el-result v-else-if="error" icon="error" :title="$t('studentSettings.loadFailedTitle')" :sub-title="$t('studentSettings.loadFailedSubtitle')">
        <template #extra>
          <el-button type="primary" @click="loadSettings">{{ $t('studentSettings.reload') }}</el-button>
        </template>
      </el-result>

      <div v-else class="settings-groups">
        <!-- 播放设置 -->
        <el-card class="settings-card student-card-item" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon class="card-icon"><VideoPlay /></el-icon>
              <span>{{ $t('studentSettings.playbackSettings') }}</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.playbackSpeed') }}</span>
              <el-select v-model="settings.playbackSpeed" :aria-label="$t('studentSettings.playbackSpeed')" @change="handleSave" class="settings-control">
                <el-option label="0.75x" value="0.75" />
                <el-option label="1.0x" value="1" />
                <el-option label="1.25x" value="1.25" />
                <el-option label="1.5x" value="1.5" />
                <el-option label="2.0x" value="2" />
              </el-select>
            </div>
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.autoPlayNext') }}</span>
              <el-switch
                v-model="settings.autoPlayNext"
                :aria-label="$t('studentSettings.autoPlayNext')"
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
              <span>{{ $t('studentSettings.notificationSettings') }}</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.enableNotifications') }}</span>
              <el-switch
                v-model="settings.notificationEnabled"
                :aria-label="$t('studentSettings.enableNotifications')"
                @change="handleSave"
                class="settings-control"
              />
            </div>
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.emailNotification') }}</span>
              <el-switch
                v-model="settings.emailNotification"
                :aria-label="$t('studentSettings.emailNotification')"
                @change="handleSave"
                class="settings-control"
              />
            </div>
            <!-- P1I-030: 微信通知开关 -->
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.wechatNotification') }}</span>
              <el-switch
                v-model="settings.wechatNotification"
                :aria-label="$t('studentSettings.wechatNotification')"
                @change="handleSave"
                class="settings-control"
              />
            </div>
            <!-- P1I-030: 免打扰时段 -->
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.quietHours') }}</span>
              <el-switch
                v-model="settings.quietHoursEnabled"
                :aria-label="$t('studentSettings.quietHours')"
                @change="handleSave"
                class="settings-control"
              />
            </div>
            <div v-if="settings.quietHoursEnabled" class="settings-item">
              <span class="settings-label">{{ $t('course.startTime') }}</span>
              <el-time-picker
                v-model="quietHoursStartDate"
                :value="settings.quietHoursStart"
                format="HH:mm"
                value-format="HH:mm"
                :clearable="false"
                class="settings-control"
                @change="onQuietHoursStartChange"
              />
            </div>
            <div v-if="settings.quietHoursEnabled" class="settings-item">
              <span class="settings-label">{{ $t('course.endTime') }}</span>
              <el-time-picker
                v-model="quietHoursEndDate"
                :value="settings.quietHoursEnd"
                format="HH:mm"
                value-format="HH:mm"
                :clearable="false"
                class="settings-control"
                @change="onQuietHoursEndChange"
              />
            </div>
          </div>
        </el-card>

        <!-- 隐私设置 -->
        <el-card class="settings-card student-card-item" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon class="card-icon"><Lock /></el-icon>
              <span>{{ $t('studentSettings.privacySettings') }}</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.profileVisibility') }}</span>
              <el-select v-model="settings.profileVisibility" :aria-label="$t('studentSettings.profileVisibilityAria')" @change="handleSave" class="settings-control">
                <el-option :label="$t('studentSettings.visibilityPublic')" value="public" />
                <el-option :label="$t('studentSettings.visibilityFriends')" value="friends" />
                <el-option :label="$t('studentSettings.visibilityPrivate')" value="private" />
              </el-select>
            </div>
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.showProgress') }}</span>
              <el-switch
                v-model="settings.showProgress"
                :aria-label="$t('studentSettings.showProgressAria')"
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
              <span>{{ $t('studentSettings.accessibility') }}</span>
            </div>
          </template>
          <div class="settings-list">
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.reducedMotion') }}</span>
              <el-switch
                v-model="settings.reducedMotion"
                :aria-label="$t('studentSettings.reducedMotion')"
                @change="handleSave"
                class="settings-control"
              />
            </div>
            <div class="settings-item">
              <span class="settings-label">{{ $t('studentSettings.highContrast') }}</span>
              <el-switch
                v-model="settings.highContrast"
                :aria-label="$t('studentSettings.highContrast')"
                @change="handleSave"
                class="settings-control"
              />
            </div>
          </div>
        </el-card>
      </div>

      <div v-if="!loading && !error" class="save-button-wrap">
        <el-button type="primary" :loading="!!saveTimer" @click="handleSave" class="save-button">{{ $t('studentSettings.saveSettings') }}</el-button>
      </div>
    </div>

    <!-- H5 Layout -->
    <div v-else class="settings-h5">
      <!-- 面包屑导航 -->
      <el-breadcrumb separator="→" class="h5-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/student/courses' }">{{ $t('layout.home') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ $t('studentSettings.personalSettings') }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="page-header-h5">
        <h2>{{ $t('studentSettings.preferences') }}</h2>
      </div>

      <!-- 骨架屏加载 -->
      <div v-if="loading" class="settings-groups-h5">
        <div v-for="n in 3" :key="n" class="settings-group-h5">
          <el-skeleton animated :rows="2" />
        </div>
      </div>

      <!-- 加载失败 -->
      <el-result v-else-if="error" icon="error" :title="$t('studentSettings.loadFailedTitle')" :sub-title="$t('studentSettings.loadFailedSubtitle')">
        <template #extra>
          <el-button type="primary" size="small" :loading="loading" @click="loadSettings">{{ $t('studentSettings.reload') }}</el-button>
        </template>
      </el-result>

      <div v-else class="settings-groups-h5">
        <!-- 播放设置 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><VideoPlay /></el-icon>
            <span>{{ $t('studentSettings.playbackSettings') }}</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.playbackSpeed') }}</span>
              <el-select v-model="settings.playbackSpeed" :aria-label="$t('studentSettings.playbackSpeed')" @change="handleSave" class="control-select-h5">
                <el-option label="0.75x" value="0.75" />
                <el-option label="1.0x" value="1" />
                <el-option label="1.25x" value="1.25" />
                <el-option label="1.5x" value="1.5" />
                <el-option label="2.0x" value="2" />
              </el-select>
            </div>
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.autoPlayNext') }}</span>
              <el-switch v-model="settings.autoPlayNext" :aria-label="$t('studentSettings.autoPlayNext')" @change="handleSave" />
            </div>
          </div>
        </div>

        <!-- 通知设置 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><Bell /></el-icon>
            <span>{{ $t('studentSettings.notificationSettings') }}</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.enableNotifications') }}</span>
              <el-switch v-model="settings.notificationEnabled" :aria-label="$t('studentSettings.enableNotifications')" @change="handleSave" />
            </div>
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.emailNotification') }}</span>
              <el-switch v-model="settings.emailNotification" :aria-label="$t('studentSettings.emailNotification')" @change="handleSave" />
            </div>
            <!-- P1I-030: 微信通知开关 (H5) -->
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.wechatNotification') }}</span>
              <el-switch v-model="settings.wechatNotification" :aria-label="$t('studentSettings.wechatNotification')" @change="handleSave" />
            </div>
            <!-- P1I-030: 免打扰时段 (H5) -->
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.quietHours') }}</span>
              <el-switch v-model="settings.quietHoursEnabled" :aria-label="$t('studentSettings.quietHours')" @change="handleSave" />
            </div>
            <div v-if="settings.quietHoursEnabled" class="settings-item-h5">
              <span>{{ $t('studentSettings.startShort') }}</span>
              <el-time-picker
                v-model="quietHoursStartDate"
                :value="settings.quietHoursStart"
                format="HH:mm"
                value-format="HH:mm"
                :clearable="false"
                class="control-select-h5"
                @change="onQuietHoursStartChange"
              />
            </div>
            <div v-if="settings.quietHoursEnabled" class="settings-item-h5">
              <span>{{ $t('studentSettings.endShort') }}</span>
              <el-time-picker
                v-model="quietHoursEndDate"
                :value="settings.quietHoursEnd"
                format="HH:mm"
                value-format="HH:mm"
                :clearable="false"
                class="control-select-h5"
                @change="onQuietHoursEndChange"
              />
            </div>
          </div>
        </div>

        <!-- 隐私设置 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><Lock /></el-icon>
            <span>{{ $t('studentSettings.privacySettings') }}</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.profileVisibility') }}</span>
              <el-select v-model="settings.profileVisibility" :aria-label="$t('studentSettings.profileVisibilityAria')" @change="handleSave" class="control-select-h5">
                <el-option :label="$t('studentSettings.visibilityPublic')" value="public" />
                <el-option :label="$t('studentSettings.visibilityFriends')" value="friends" />
                <el-option :label="$t('studentSettings.visibilityPrivate')" value="private" />
              </el-select>
            </div>
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.showProgress') }}</span>
              <el-switch v-model="settings.showProgress" :aria-label="$t('studentSettings.showProgressAria')" @change="handleSave" />
            </div>
          </div>
        </div>

        <!-- 辅助功能 -->
        <div class="settings-group-h5">
          <div class="group-header-h5">
            <el-icon><Setting /></el-icon>
            <span>{{ $t('studentSettings.accessibility') }}</span>
          </div>
          <div class="settings-list-h5">
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.reducedMotion') }}</span>
              <el-switch v-model="settings.reducedMotion" :aria-label="$t('studentSettings.reducedMotion')" @change="handleSave" />
            </div>
            <div class="settings-item-h5">
              <span>{{ $t('studentSettings.highContrast') }}</span>
              <el-switch v-model="settings.highContrast" :aria-label="$t('studentSettings.highContrast')" @change="handleSave" />
            </div>
          </div>
        </div>
      </div>

      <div v-if="!loading && !error" class="save-button-wrap-h5">
        <el-button type="primary" :loading="!!saveTimer" @click="handleSave" class="save-button-h5">{{ $t('studentSettings.saveSettings') }}</el-button>
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const STORAGE_KEY = 'micro_course_settings'

const loading = ref(false)
const error = ref(false)

const settings = ref({
  playbackSpeed: '1',
  autoPlayNext: true,
  notificationEnabled: true,
  emailNotification: false,
  // P1I-030: 微信通知开关
  wechatNotification: false,
  profileVisibility: 'public',
  showProgress: true,
  reducedMotion: false,
  highContrast: false,
  // P1I-030: 免打扰时段
  quietHoursEnabled: false,
  quietHoursStart: '22:00',
  quietHoursEnd: '07:00'
})

const isMobile = ref(window.innerWidth <= 768)
let resizeTimer = null

// P1I-030: 免打扰时段绑定
const quietHoursStartDate = ref(new Date())
const quietHoursEndDate = ref(new Date())

const onQuietHoursStartChange = (val) => {
  if (val) {
    settings.value.quietHoursStart = val
    handleSave()
  }
}
const onQuietHoursEndChange = (val) => {
  if (val) {
    settings.value.quietHoursEnd = val
    handleSave()
  }
}
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
    // 优先从后端加载通知偏好设置（含 P1C-037 扩展偏好）
    const { data } = await getMyPreferences()
    if (data) {
      settings.value.notificationEnabled = data.allowSite !== false
      settings.value.emailNotification = data.allowEmail === true
      // 从后端加载扩展偏好设置（覆盖 localStorage 旧值）
      if (data.extraPreferences) {
        try {
          const extra = JSON.parse(data.extraPreferences)
          if (extra.playbackSpeed) settings.value.playbackSpeed = extra.playbackSpeed
          if (extra.autoPlayNext !== undefined) settings.value.autoPlayNext = extra.autoPlayNext
          if (extra.profileVisibility) settings.value.profileVisibility = extra.profileVisibility
          if (extra.showProgress !== undefined) settings.value.showProgress = extra.showProgress
          if (extra.reducedMotion !== undefined) settings.value.reducedMotion = extra.reducedMotion
          if (extra.highContrast !== undefined) settings.value.highContrast = extra.highContrast
          // P1I-030: 微信通知开关
          if (extra.wechatNotification !== undefined) settings.value.wechatNotification = extra.wechatNotification
          // P1I-030: 免打扰时段
          if (extra.quietHoursEnabled !== undefined) settings.value.quietHoursEnabled = extra.quietHoursEnabled
          if (extra.quietHoursStart) settings.value.quietHoursStart = extra.quietHoursStart
          if (extra.quietHoursEnd) settings.value.quietHoursEnd = extra.quietHoursEnd
        } catch { /* ignore JSON parse error */ }
      }
    }
    // 播放/隐私/辅助功能从 localStorage 加载（或默认值）
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const parsed = JSON.parse(stored)
      // 用 Object.assign 合并而非整体替换，避免丢失后端加载的
      // wechatNotification / quietHoursEnabled / quietHoursStart / quietHoursEnd 字段
      Object.assign(settings.value, {
        playbackSpeed: parsed.playbackSpeed || settings.value.playbackSpeed,
        autoPlayNext: parsed.autoPlayNext !== false,
        notificationEnabled: settings.value.notificationEnabled,
        emailNotification: settings.value.emailNotification,
        profileVisibility: parsed.profileVisibility || settings.value.profileVisibility,
        showProgress: parsed.showProgress !== false,
        reducedMotion: parsed.reducedMotion === true,
        highContrast: parsed.highContrast === true
      })
    }
    // 加载成功后同步到 localStorage
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
  } catch {
    // 后端不可用时回退到 localStorage
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        // 合并而非替换，保留免打扰时段/微信通知等字段的默认值
        Object.assign(settings.value, {
          playbackSpeed: parsed.playbackSpeed || '1',
          autoPlayNext: parsed.autoPlayNext !== false,
          notificationEnabled: parsed.notificationEnabled !== false,
          emailNotification: parsed.emailNotification === true,
          profileVisibility: parsed.profileVisibility || 'public',
          showProgress: parsed.showProgress !== false,
          reducedMotion: parsed.reducedMotion === true,
          highContrast: parsed.highContrast === true
        })
      }
    } catch {
      error.value = true
      ElMessage.error(t('studentSettings.loadFailed'))
    }
  } finally {
    loading.value = false
  }
}

// P1I-031: 防抖 debounce，避免频繁触发保存请求
let debounceTimer = null
const debouncedSave = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(async () => {
    debounceTimer = null
    try {
      // P1C-037: 已登录用户同步所有偏好设置到后端（含播放/隐私/辅助功能）
      const extraPrefs = {
        playbackSpeed: settings.value.playbackSpeed,
        autoPlayNext: settings.value.autoPlayNext,
        profileVisibility: settings.value.profileVisibility,
        showProgress: settings.value.showProgress,
        reducedMotion: settings.value.reducedMotion,
        highContrast: settings.value.highContrast,
        wechatNotification: settings.value.wechatNotification,
        quietHoursEnabled: settings.value.quietHoursEnabled,
        quietHoursStart: settings.value.quietHoursStart,
        quietHoursEnd: settings.value.quietHoursEnd
      }
      await updateMyPreferences({
        allowSite: settings.value.notificationEnabled,
        allowEmail: settings.value.emailNotification,
        extraPreferences: JSON.stringify(extraPrefs)
      })
      ElMessage.success(t('studentSettings.savedSuccess'))
    } catch {
      ElMessage.warning(t('studentSettings.saveFailedLocal'))
    }
    // 所有设置持久化到 localStorage 作为离线 fallback
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
    } catch {
      ElMessage.error(t('studentSettings.saveFailed'))
    }
  }, 300)
}

const handleSave = () => {
  debouncedSave()
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
