<template>
  <div class="admin-dashboard">
    <!-- 顶部欢迎条 -->
    <div class="welcome-bar">
      <div class="welcome-left">
        <span class="welcome-date">{{ welcomeDate }}</span>
      </div>
      <div class="welcome-greeting">
        <span class="greeting-name">管理员</span>
        <span class="greeting-suffix">{{ greeting }}</span>
      </div>
      <div class="welcome-right">
        <span class="last-updated">最后更新: {{ lastUpdatedText }}</span>
        <el-button
          :icon="Refresh"
          circle
          :loading="isRefreshing"
          class="refresh-btn"
          @click="handleRefresh"
        />
      </div>
    </div>

    <!-- 快捷入口 -->
    <el-row :gutter="16" class="quick-actions-row">
      <el-col v-for="action in quickActions" :key="action.label" :xs="8" :sm="4">
        <el-card
          class="quick-action-card"
          shadow="hover"
          @click="handleQuickAction(action)"
        >
          <div class="quick-action-inner">
            <div class="quick-action-icon" :style="{ background: action.bg, color: action.color }">
              <el-icon :size="22"><component :is="action.icon" /></el-icon>
            </div>
            <span class="quick-action-label">{{ action.label }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 8 个 stat-card（4×2） -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-indigo">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.totalUsers }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总用户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-green">
            <el-icon><Reading /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.totalCourses }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总课程</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-amber">
            <el-icon><Tickets /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.totalStudents }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总学员</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-blue">
            <el-icon><UserFilled /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.activeUsers }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">活跃用户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-red">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.videoPlayCount }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">视频播放次数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-purple">
            <el-icon><ChatLineSquare /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.exerciseSubmitCount }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">练习提交次数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-orange">
            <el-icon><Timer /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ formatDuration(displayStats.totalStudyMinutes) }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">学习时长</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap icon-pink">
            <el-icon><Medal /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.certificatesIssued }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">证书发放</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表行 -->
    <el-row :gutter="16" class="charts-row">
      <!-- 核心指标趋势 60% -->
      <el-col :xs="24" :md="14">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">核心指标趋势</div>
          </template>
          <el-skeleton :loading="trendsLoading" animated :rows="3">
            <template #template><el-skeleton-item class="skeleton-chart" /></template>
            <template #default>
              <div v-if="trendsError" class="chart-error">
                <span>加载失败</span>
              </div>
              <div v-else ref="trendsChartRef" class="chart-container"></div>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
      <!-- 课程分类分布 40% -->
      <el-col :xs="24" :md="10">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">课程分类分布</div>
          </template>
          <el-skeleton :loading="categoryLoading" animated :rows="3">
            <template #template><el-skeleton-item class="skeleton-chart" /></template>
            <template #default>
              <div v-if="categoryError" class="chart-error">
                <span>加载失败</span>
              </div>
              <div v-else ref="categoryChartRef" class="chart-container"></div>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二图表行 -->
    <el-row :gutter="16" class="charts-row">
      <!-- 最近 30 天活跃 60% -->
      <el-col :xs="24" :md="14">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">最近 30 天活跃</div>
          </template>
          <el-skeleton :loading="activityLoading" animated :rows="3">
            <template #template><el-skeleton-item class="skeleton-chart" /></template>
            <template #default>
              <div v-if="activityError" class="chart-error">
                <span>加载失败</span>
              </div>
              <div v-else ref="activityChartRef" class="chart-container"></div>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
      <!-- 右侧：最新操作日志 + 系统健康 -->
      <el-col :xs="24" :md="10">
        <el-card class="list-card" shadow="never">
          <template #header>
            <div class="card-header">最新操作日志</div>
          </template>
          <el-skeleton :loading="logsLoading" animated :rows="3">
            <template #template>
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
            </template>
            <template #default>
              <div v-if="logsError" class="list-error">
                <span>加载失败</span>
              </div>
              <div v-else-if="logs.length === 0" class="list-empty">
                <span>暂无日志</span>
              </div>
              <ul v-else class="list-ul">
                <li v-for="log in logs" :key="log.id" class="list-item">
                  <div class="log-main">
                    <span class="log-action">{{ log.action }}</span>
                    <span class="log-target">{{ log.target }}</span>
                  </div>
                  <div class="log-sub">
                    <span class="log-operator">{{ log.operator }}</span>
                    <span class="log-time">{{ formatTime(log.createdAt) }}</span>
                  </div>
                </li>
              </ul>
            </template>
          </el-skeleton>
        </el-card>
        <el-card class="health-card" shadow="never">
          <template #header>
            <div class="card-header">系统健康</div>
          </template>
          <div v-if="healthLoading" class="health-skeleton">
            <el-skeleton :rows="1" animated />
          </div>
          <div v-else class="health-grid">
            <div class="health-item">
              <span class="health-label">DB</span>
              <span class="health-value" :class="health.db === 'ok' ? 'status-ok' : 'status-warn'">
                {{ health.db }}
              </span>
            </div>
            <div class="health-item">
              <span class="health-label">Redis</span>
              <span class="health-value" :class="health.redis === 'ok' ? 'status-ok' : 'status-warn'">
                {{ health.redis }}
              </span>
            </div>
            <div class="health-item">
              <span class="health-label">磁盘</span>
              <span class="health-value">{{ health.disk }}</span>
            </div>
            <div class="health-item">
              <span class="health-label">内存</span>
              <span class="health-value">{{ health.memory }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import {
  User, UserFilled, Reading, Tickets, Timer, Medal, Clock, ChatLineSquare,
  Plus, Setting, OfficeBuilding, Download, List, Refresh
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getOverview, getUserTrend, getCourseTrend, getCourseDistribution, getLearningBehavior, getHealth } from '@/api/admin-stats'
import { getLogs } from '@/api/operation-log'

const router = useRouter()

// ===== 时间问候 =====
const now = ref(new Date())
const welcomeDate = computed(() => {
  const d = now.value
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})
const greeting = computed(() => {
  const h = now.value.getHours()
  if (h < 6) return '，凌晨好'
  if (h < 12) return '，上午好'
  if (h < 14) return '，中午好'
  if (h < 18) return '，下午好'
  return '，晚上好'
})

// ===== 最后更新时间 =====
const lastUpdatedAt = ref(null)
const lastUpdatedText = computed(() => {
  if (!lastUpdatedAt.value) return '加载中...'
  const diff = Date.now() - lastUpdatedAt.value
  if (diff < 10000) return '刚刚'
  if (diff < 60000) return `${Math.floor(diff / 1000)}秒前`
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  return `${Math.floor(diff / 3600000)}小时前`
})
let lastUpdatedTimer = null

function startLastUpdatedTimer() {
  lastUpdatedTimer = setInterval(() => {
    // 触发 computed 重算（通过更新 now）
    now.value = new Date()
  }, 10000)
}

// ===== 手动刷新 =====
const isRefreshing = ref(false)

async function handleRefresh() {
  isRefreshing.value = true
  try {
    await refreshAll()
  } finally {
    isRefreshing.value = false
  }
}

// ===== 快捷入口 =====
const quickActions = [
  { label: '新增用户', icon: markRaw(Plus), route: '/admin/users', bg: '#EEF2FF', color: '#4F46E5' },
  { label: '课程管理', icon: markRaw(Reading), route: '/admin/courses', bg: '#ECFDF5', color: '#10B981' },
  { label: '部门管理', icon: markRaw(OfficeBuilding), route: '/admin/departments', bg: '#FEF3C7', color: '#F59E0B' },
  { label: '数据导出', icon: markRaw(Download), route: '/admin/export', bg: '#EFF6FF', color: '#3B82F6' },
  { label: '系统设置', icon: markRaw(Setting), route: '/admin/settings', bg: '#FAF5FF', color: '#8B5CF6' },
  { label: '操作日志', icon: markRaw(List), route: '/admin/logs', bg: '#FEF2F2', color: '#EF4444' }
]

function handleQuickAction(action) {
  router.push(action.route)
}

// ===== 统计卡片动画 =====
const statsLoading = ref(true)
const stats = ref({})
const displayStats = reactive({
  totalUsers: 0,
  totalCourses: 0,
  totalStudents: 0,
  activeUsers: 0,
  videoPlayCount: 0,
  exerciseSubmitCount: 0,
  totalStudyMinutes: 0,
  certificatesIssued: 0
})

const animationFrameIds = []

function animateValue(key, from, to, duration = 800) {
  if (from === to) return
  const startTime = performance.now()
  function step(currentTime) {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    // easeOutQuart for smooth deceleration
    const eased = 1 - Math.pow(1 - progress, 4)
    displayStats[key] = Math.round(from + (to - from) * eased)
    if (progress < 1) {
      const id = requestAnimationFrame(step)
      animationFrameIds.push(id)
    }
  }
  const id = requestAnimationFrame(step)
  animationFrameIds.push(id)
}

function animateAllStats(newStats) {
  const keys = [
    'totalUsers', 'totalCourses', 'totalStudents', 'activeUsers',
    'videoPlayCount', 'exerciseSubmitCount', 'totalStudyMinutes', 'certificatesIssued'
  ]
  keys.forEach(key => {
    const from = displayStats[key] || 0
    const to = newStats[key] || 0
    animateValue(key, from, to)
  })
}

// Charts
const trendsLoading = ref(true)
const trendsError = ref(false)
const trendsChartRef = ref(null)
let trendsChart = null

const categoryLoading = ref(true)
const categoryError = ref(false)
const categoryChartRef = ref(null)
let categoryChart = null

const activityLoading = ref(true)
const activityError = ref(false)
const activityChartRef = ref(null)
let activityChart = null

// Logs
const logsLoading = ref(true)
const logsError = ref(false)
const logs = ref([])

// Health
const healthLoading = ref(true)
const health = ref({ db: '-', redis: '-', disk: '-', memory: '-' })

// 定时刷新
const refreshInterval = ref(60000)
let refreshTimer = null

async function refreshAll() {
  await Promise.all([
    loadStats(),
    loadTrends(),
    loadCategoryStats(),
    loadActivity(),
    loadLogs()
  ])
  loadHealth()
  lastUpdatedAt.value = Date.now()
}

async function loadHealth() {
  try {
    const res = await getHealth()
    health.value = res.data || {}
  } catch (e) {
    console.warn('[Dashboard] loadHealth failed', e)
  } finally {
    healthLoading.value = false
  }
}

// Load stats
async function loadStats() {
  statsLoading.value = true
  try {
    const [overviewRes, behaviorRes] = await Promise.all([
      getOverview(),
      getLearningBehavior()
    ])
    const d = overviewRes.data || {}
    // 学习行为数据
    const behaviorData = behaviorRes.data || {}
    // 兼容不同的返回结构：可能是 { videoPlayCount, exerciseSubmitCount } 或 { items: [...] }
    const items = Array.isArray(behaviorData) ? behaviorData : (behaviorData.items || [])
    const videoPlayCount = items.reduce?.((sum, item) => sum + (item.videoPlayCount || 0), 0) || behaviorData.videoPlayCount || 0
    const exerciseSubmitCount = items.reduce?.((sum, item) => sum + (item.exerciseSubmitCount || 0), 0) || behaviorData.exerciseSubmitCount || 0

    const newStats = {
      totalUsers: d.totalUsers ?? 0,
      totalCourses: d.totalCourses ?? 0,
      totalStudents: d.totalEnrollments ?? 0,
      activeUsers: d.activeUsers7d ?? 0,
      videoPlayCount,
      exerciseSubmitCount,
      totalStudyMinutes: Math.round((d.totalWatchTimeMinutes || 0)),
      certificatesIssued: d.certificatesIssued ?? 0
    }
    stats.value = newStats
    animateAllStats(newStats)
  } catch {
    // silent
  } finally {
    statsLoading.value = false
  }
}

// Load trends
async function loadTrends() {
  trendsLoading.value = true
  trendsError.value = false
  try {
    const [userRes, courseRes] = await Promise.all([
      getUserTrend(7),
      getCourseTrend(7)
    ])
    const data = {
      users: userRes.data || [],
      courses: courseRes.data || [],
      students: []
    }
    renderTrendsChart(data)
  } catch {
    trendsError.value = true
    renderTrendsChart({ users: [], courses: [], students: [] })
  } finally {
    trendsLoading.value = false
  }
}

function renderTrendsChart(data) {
  if (!trendsChartRef.value) return
  if (trendsChart) trendsChart.dispose()
  trendsChart = echarts.init(trendsChartRef.value)
  const dates = (data.users || []).map(item => item.date || '')
  trendsChart.setOption({
    color: ['#4F46E5', '#10B981', '#F59E0B'],
    tooltip: {
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#E2E8F0',
      borderWidth: 1,
      textStyle: { color: '#1E293B' },
      extraCssText: 'border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.08);'
    },
    legend: { data: ['用户', '课程', '学员'], bottom: 0, textStyle: { color: '#64748B' } },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#F1F5F9' } }, splitLine: { show: false } },
    yAxis: { type: 'value', name: '数量', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8' } },
    series: [
      {
        name: '用户',
        type: 'line',
        smooth: true,
        data: (data.users || []).map(item => item.count ?? 0),
        itemStyle: { color: '#4F46E5' },
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '课程',
        type: 'line',
        smooth: true,
        data: (data.courses || []).map(item => item.count ?? 0),
        itemStyle: { color: '#10B981' },
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '学员',
        type: 'line',
        smooth: true,
        data: (data.students || []).map(item => item.count ?? 0),
        itemStyle: { color: '#F59E0B' },
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.12 }
      }
    ]
  })
}

// Load category stats
async function loadCategoryStats() {
  categoryLoading.value = true
  categoryError.value = false
  try {
    const res = await getCourseDistribution()
    const data = res.data || {}
    const items = Object.entries(data).map(([name, value]) => ({ name, value }))
    renderCategoryChart(items)
  } catch {
    categoryError.value = true
    renderCategoryChart([])
  } finally {
    categoryLoading.value = false
  }
}

function renderCategoryChart(data) {
  if (!categoryChartRef.value) return
  if (categoryChart) categoryChart.dispose()
  categoryChart = echarts.init(categoryChartRef.value)
  const pieData = data.map(item => ({ name: item.name, value: item.value }))
  categoryChart.setOption({
    color: ['#4F46E5', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#3B82F6'],
    tooltip: {
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#E2E8F0',
      borderWidth: 1,
      textStyle: { color: '#1E293B' },
      extraCssText: 'border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.08);'
    },
    legend: { bottom: 0, type: 'scroll', textStyle: { color: '#64748B' } },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}', color: '#1E293B' },
      data: pieData
    }]
  })
}

// Load activity
async function loadActivity() {
  activityLoading.value = true
  activityError.value = false
  try {
    const res = await getLearningBehavior(30)
    const data = res.data || {}
    const items = Object.entries(data).map(([key, value]) => ({ date: key, activeUsers: value }))
    renderActivityChart(items)
  } catch {
    activityError.value = true
    renderActivityChart([])
  } finally {
    activityLoading.value = false
  }
}

function renderActivityChart(data) {
  if (!activityChartRef.value) return
  if (activityChart) activityChart.dispose()
  activityChart = echarts.init(activityChartRef.value)
  const dates = data.map(item => item.date || '')
  const activeUsers = data.map(item => item.activeUsers ?? 0)
  activityChart.setOption({
    color: ['#4F46E5'],
    tooltip: {
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#E2E8F0',
      borderWidth: 1,
      textStyle: { color: '#1E293B' },
      extraCssText: 'border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.08);'
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#F1F5F9' } }, splitLine: { show: false } },
    yAxis: { type: 'value', name: '活跃用户', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8' } },
    series: [{
      name: '活跃用户',
      type: 'line',
      smooth: true,
      data: activeUsers,
      itemStyle: { color: '#4F46E5' },
      lineStyle: { width: 3 },
      areaStyle: { opacity: 0.12 }
    }]
  })
}

// Load logs
async function loadLogs() {
  logsLoading.value = true
  logsError.value = false
  try {
    const res = await getLogs({ size: 5 })
    logs.value = res.data?.items || []
  } catch {
    logsError.value = true
  } finally {
    logsLoading.value = false
  }
}

function formatDuration(minutes) {
  if (!minutes) return '0h'
  const h = Math.floor(minutes / 60)
  return h > 0 ? `${h}h` : `${minutes}m`
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function resizeCharts() {
  trendsChart?.resize()
  categoryChart?.resize()
  activityChart?.resize()
}

onMounted(async () => {
  await Promise.all([
    loadStats(),
    loadTrends(),
    loadCategoryStats(),
    loadActivity(),
    loadLogs()
  ])
  window.addEventListener('resize', resizeCharts)
  loadHealth()
  lastUpdatedAt.value = Date.now()
  startLastUpdatedTimer()
  refreshTimer = setInterval(() => {
    refreshAll()
  }, refreshInterval.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendsChart?.dispose()
  categoryChart?.dispose()
  activityChart?.dispose()
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (lastUpdatedTimer) {
    clearInterval(lastUpdatedTimer)
    lastUpdatedTimer = null
  }
  // 清理所有动画帧
  animationFrameIds.forEach(id => cancelAnimationFrame(id))
  animationFrameIds.length = 0
})
</script>

<style scoped>
.admin-dashboard {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100vh;
}

/* ===== 欢迎条 ===== */
.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 24px 32px;
  background: linear-gradient(135deg, #4F46E5 0%, #818CF8 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(79,70,229,0.15);
}

.welcome-left {
  display: flex;
  align-items: center;
}

.welcome-date {
  font-size: 14px;
  color: rgba(255,255,255,0.8);
}

.welcome-greeting {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.greeting-name {
  font-size: 24px;
  font-weight: 700;
  color: white;
}

.greeting-suffix {
  font-size: 16px;
  color: rgba(255,255,255,0.9);
}

.welcome-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-updated {
  font-size: 12px;
  color: rgba(255,255,255,0.7);
  white-space: nowrap;
}

.refresh-btn {
  --el-button-bg-color: rgba(255,255,255,0.2) !important;
  --el-button-border-color: rgba(255,255,255,0.3) !important;
  --el-button-text-color: #fff !important;
  --el-button-hover-bg-color: rgba(255,255,255,0.35) !important;
  --el-button-hover-border-color: rgba(255,255,255,0.5) !important;
  --el-button-hover-text-color: #fff !important;
  backdrop-filter: blur(4px);
}

/* ===== 快捷入口 ===== */
.quick-actions-row {
  margin-bottom: 24px;
}

.quick-action-card {
  cursor: pointer;
  border: none;
  border-radius: 12px;
  transition: all 200ms ease;
}

.quick-action-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}

.quick-action-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.quick-action-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-action-label {
  font-size: 13px;
  color: #334155;
  font-weight: 500;
  white-space: nowrap;
}

/* ===== Stat Cards ===== */
.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  cursor: default;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  border: none;
  transition: all 200ms ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}

.stat-icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 24px;
}

.icon-indigo  { background: #EEF2FF; color: #4F46E5; }
.icon-green   { background: #ECFDF5; color: #10B981; }
.icon-amber   { background: #FEF3C7; color: #F59E0B; }
.icon-blue    { background: #EFF6FF; color: #3B82F6; }
.icon-red     { background: #FEF2F2; color: #EF4444; }
.icon-purple  { background: #FAF5FF; color: #8B5CF6; }
.icon-orange  { background: #FFF7ED; color: #F97316; }
.icon-pink    { background: #FDF2F8; color: #EC4899; }

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1E293B;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 13px;
  color: #64748B;
  margin-top: 4px;
}

/* ===== Chart Cards ===== */
.charts-row {
  margin-bottom: 24px;
}

.chart-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  border: none;
  margin-bottom: 24px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
}

.chart-container {
  height: 300px;
  width: 100%;
  padding: 12px;
}

.chart-error,
.list-error,
.list-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94A3B8;
  font-size: 13px;
}

/* ===== List Card ===== */
.list-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  border: none;
  margin-bottom: 24px;
}

.list-ul {
  list-style: none;
  margin: 0;
  padding: 0 20px;
}

.list-item {
  padding: 12px 0;
  border-bottom: 1px solid #F1F5F9;
  transition: background 150ms ease;
}

.list-item:hover {
  background: #F8FAFC;
  margin: 0 -8px;
  padding: 12px 8px;
  border-radius: 8px;
}

.list-item:last-child {
  border-bottom: none;
}

.log-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.log-action {
  font-size: 13px;
  color: #1E293B;
  font-weight: 500;
}

.log-target {
  font-size: 13px;
  color: #64748B;
}

.log-sub {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-operator {
  font-size: 12px;
  color: #94A3B8;
}

.log-time {
  font-size: 12px;
  color: #94A3B8;
}

/* ===== Health Card ===== */
.health-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  border: none;
  margin-bottom: 24px;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 16px 20px;
}

.health-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #F8FAFC;
  border-radius: 8px;
}

.health-label {
  font-size: 13px;
  color: #64748B;
}

.health-value {
  font-size: 13px;
  font-weight: 600;
  color: #1E293B;
}

.status-ok  { color: #10B981; }
.status-warn { color: #F59E0B; }

.health-skeleton {
  padding: 12px;
}

/* ===== Skeleton ===== */
.skeleton-value {
  width: 60px;
  height: 32px;
}

.skeleton-chart {
  height: 300px;
  border-radius: 8px;
}

.skeleton-item {
  height: 40px;
  margin-bottom: 8px;
  border-radius: 8px;
}

/* ===== Responsive ===== */
@media (max-width: 1024px) {
  .stats-row .el-col,
  .quick-actions-row .el-col {
    margin-bottom: 16px;
  }
}

@media (max-width: 768px) {
  .admin-dashboard {
    padding: 16px;
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding: 20px 24px;
    border-radius: 12px;
  }

  .welcome-right {
    width: 100%;
    justify-content: space-between;
  }

  .quick-action-inner {
    padding: 4px 0;
  }

  .quick-action-icon {
    width: 36px;
    height: 36px;
  }

  .quick-action-label {
    font-size: 12px;
  }
}
</style>
