<template>
  <div class="academic-dashboard">
    <!-- 顶部欢迎条 — 玻璃态 -->
    <div class="welcome-bar">
      <div class="welcome-left">
        <div class="welcome-date">{{ welcomeDate }}</div>
        <div class="welcome-greeting">
          <span class="greeting-name">教务处</span>
          <span class="greeting-suffix">{{ greeting }}</span>
        </div>
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
      <el-col v-for="action in quickActions" :key="action.label" :xs="12" :sm="6">
        <el-card class="quick-action-card" shadow="hover" role="button" tabindex="0" :aria-label="`快速操作 ${action.label}`" @click="handleQuickAction(action)" @keydown.enter="handleQuickAction(action)" @keydown.space.prevent="handleQuickAction(action)">
          <div class="quick-action-inner">
            <div class="quick-action-icon" :style="{ background: action.bg, color: action.color }">
              <el-icon :size="22"><component :is="action.icon" /></el-icon>
            </div>
            <span class="quick-action-label">{{ action.label }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 4 个指标卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-zone stat-icon-zone--primary">
            <el-icon><Reading /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.totalCourses }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总课程数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-zone stat-icon-zone--success">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.totalEnrollments }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总选课人次</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-zone stat-icon-zone--warning">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.avgCompletionRate }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">平均完成率</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-zone stat-icon-zone--purple">
            <el-icon><Finished /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ displayStats.avgAccuracyRate }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">平均正确率</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 中部图表 -->
    <el-row :gutter="16" class="charts-row">
      <!-- 院系对比柱状图 -->
      <el-col :xs="24" :md="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">院系完成率对比</div>
          </template>
          <el-skeleton :loading="deptLoading" animated :rows="3">
            <template #template><el-skeleton-item class="skeleton-chart" /></template>
            <template #default>
              <div v-if="deptError" class="chart-error">
                <span>加载失败</span>
              </div>
              <div v-else ref="deptChartRef" class="chart-container" role="img" aria-label="院系完成率对比图"></div>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
      <!-- 参与率趋势折线图 -->
      <el-col :xs="24" :md="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">参与率趋势</div>
          </template>
          <el-skeleton :loading="trendLoading" animated :rows="3">
            <template #template><el-skeleton-item class="skeleton-chart" /></template>
            <template #default>
              <div v-if="trendError" class="chart-error">
                <span>加载失败</span>
              </div>
              <div v-else ref="trendChartRef" class="chart-container" role="img" aria-label="参与率趋势图"></div>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部模块 -->
    <el-row :gutter="16" class="bottom-row">
      <!-- 完成率预警表格 -->
      <el-col :xs="24" :md="12">
        <el-card class="table-card" shadow="never">
          <template #header>
            <div class="card-header">完成率预警
              <el-tag size="small" type="danger" class="header-tag">completionRate &lt; 30%</el-tag>
            </div>
          </template>
          <el-skeleton :loading="warningsLoading" animated :rows="3">
            <template #template>
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
            </template>
            <template #default>
              <div v-if="warningsError" class="table-error">
                <span>加载失败</span>
              </div>
              <el-table v-else :data="warnings" class="warning-table">
                <el-table-column prop="name" label="课程名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="completionRate" label="完成率" width="100" align="center">
                  <template #default="{ row }">
                    <span :class="row.completionRate < 30 ? 'rate-danger' : 'rate-success'">
                      {{ formatPercent(row.completionRate) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="enrollmentCount" label="选课人数" width="90" align="center" />
              </el-table>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
      <!-- 热门课程排行 -->
      <el-col :xs="24" :md="12">
        <el-card class="table-card" shadow="never">
          <template #header>
            <div class="card-header">热门课程（按选课人次）</div>
          </template>
          <el-skeleton :loading="hotCourseLoading" animated :rows="3">
            <template #template>
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
            </template>
            <template #default>
              <div v-if="hotCourseError" class="table-error">
                <span>加载失败</span>
              </div>
              <el-table v-else :data="hotCourses" class="hot-table">
                <el-table-column type="index" label="排名" width="60" align="center" />
                <el-table-column prop="name" label="课程名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="enrollmentCount" label="选课人次" width="100" align="center" sortable />
                <el-table-column prop="completionRate" label="完成率" width="100" align="center">
                  <template #default="{ row }">
                    {{ formatPercent(row.completionRate) }}
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </el-skeleton>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { Reading, User, TrendCharts, Finished, Refresh, DataAnalysis, Collection, Setting } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getAcademicOverview,
  getDepartmentStats,
  getCompletionWarnings,
  getParticipationTrend,
  getCompletionTrend
} from '@/api/academic-stats'

const router = useRouter()

const now = ref(new Date())
const welcomeDate = computed(() => {
  const d = now.value
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})
const greeting = computed(() => {
  const h = now.value.getHours()
  if (h < 12) return '，上午好'
  if (h < 14) return '，中午好'
  if (h < 18) return '，下午好'
  return '，晚上好'
})

// ===== 最后更新时间 =====
const lastUpdatedAt = ref(null)
const lastUpdatedText = computed(() => {
  if (!lastUpdatedAt.value) return '加载中...'
  // 依赖 now.value 以便定时重算
  const diff = now.value.getTime() - lastUpdatedAt.value
  if (diff < 10000) return '刚刚'
  if (diff < 60000) return `${Math.floor(diff / 1000)}秒前`
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  return `${Math.floor(diff / 3600000)}小时前`
})
let lastUpdatedTimer = null

function startLastUpdatedTimer() {
  lastUpdatedTimer = setInterval(() => {
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
  { label: '课程审核', icon: markRaw(Reading), route: '/courses', bg: '#EEF2FF', color: '#4F46E5' },
  { label: '选课管理', icon: markRaw(Collection), route: '/enrollments', bg: '#ECFDF5', color: '#10B981' },
  { label: '教学班管理', icon: markRaw(Setting), route: '/admin/teaching-classes', bg: '#FEF3C7', color: '#F59E0B' },
  { label: '统计分析', icon: markRaw(DataAnalysis), route: '/academic/dashboard', bg: '#FAF5FF', color: '#8B5CF6' }
]

function handleQuickAction(action) {
  router.push(action.route)
}

// Debounce utility
function debounce(fn, delay = 200) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

// Stats
const statsLoading = ref(true)
const stats = ref({})

// 统计卡片动画（仿 admin/Dashboard）
const displayStats = reactive({
  totalCourses: '0',
  totalEnrollments: '0',
  avgCompletionRate: '0%',
  avgAccuracyRate: '0%'
})

const animationFrameIds = []

function animateNumericValue(key, from, to, duration = 800, suffix = '') {
  if (from === to) return
  const startTime = performance.now()
  function step(currentTime) {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 4)
    const current = from + (to - from) * eased
    displayStats[key] = suffix ? `${current.toFixed(1)}${suffix}` : String(Math.round(current))
    if (progress < 1) {
      const id = requestAnimationFrame(step)
      animationFrameIds.push(id)
    }
  }
  const id = requestAnimationFrame(step)
  animationFrameIds.push(id)
}

function animateAllStats(newStats) {
  animateNumericValue('totalCourses', parseInt(displayStats.totalCourses) || 0, newStats.totalCourses ?? 0)
  animateNumericValue('totalEnrollments', parseInt(displayStats.totalEnrollments) || 0, newStats.totalEnrollments ?? 0)
  animateNumericValue('avgCompletionRate', parseFloat(displayStats.avgCompletionRate) || 0, newStats.avgCompletionRate ?? 0, 800, '%')
  animateNumericValue('avgAccuracyRate', parseFloat(displayStats.avgAccuracyRate) || 0, newStats.avgAccuracyRate ?? 0, 800, '%')
}

// Department chart
const deptLoading = ref(true)
const deptError = ref(false)
const deptChartRef = ref(null)
let deptChartInstance = null

// Trend chart
const trendLoading = ref(true)
const trendError = ref(false)
const trendChartRef = ref(null)
let trendChartInstance = null

// Warnings
const warningsLoading = ref(true)
const warningsError = ref(false)
const warnings = ref([])

// Hot courses
const hotCourseLoading = ref(true)
const hotCourseError = ref(false)
const hotCourses = ref([])

// 定时刷新
const refreshInterval = ref(60000)
let refreshTimer = null

async function refreshAll() {
  await Promise.all([
    loadStats(),
    loadDepartmentStats(),
    loadTrend(),
    loadWarnings(),
    loadHotCourses()
  ])
  lastUpdatedAt.value = Date.now()
}

// Load overview stats
async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getAcademicOverview()
    const d = res.data || {}
    stats.value = {
      totalCourses: d.totalCourses ?? 0,
      totalEnrollments: d.totalEnrollments ?? 0,
      avgCompletionRate: d.avgCompletionRate ?? 0,
      avgAccuracyRate: d.avgAccuracyRate ?? 0
    }
    animateAllStats(stats.value)
  } catch {
    // silent
  } finally {
    statsLoading.value = false
  }
}

// Load department stats
async function loadDepartmentStats() {
  deptLoading.value = true
  deptError.value = false
  try {
    const res = await getDepartmentStats()
    const items = res.data || []
    renderDeptChart(items)
  } catch {
    deptError.value = true
    renderDeptChart([])
  } finally {
    deptLoading.value = false
  }
}

function renderDeptChart(data) {
  if (!deptChartRef.value) return
  if (deptChartInstance) {
    deptChartInstance.dispose()
    deptChartInstance = null
  }
  deptChartInstance = echarts.init(deptChartRef.value)

  // 按 avgCompletionRate 降序排序
  const sorted = [...data].sort((a, b) => (b.avgCompletionRate ?? 0) - (a.avgCompletionRate ?? 0))
  const names = sorted.map(item => item.name || '')
  const rates = sorted.map(item => item.avgCompletionRate ?? 0)

  deptChartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      borderRadius: 8,
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#E2E8F0',
      shadowColor: 'rgba(0,0,0,0.08)',
      shadowBlur: 8,
      textStyle: { color: '#1E293B', fontSize: 13 }
    },
    grid: { left: '3%', right: '4%', bottom: '8%', top: '8%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { rotate: 15, color: '#64748B', fontSize: 12 },
      axisLine: { lineStyle: { color: '#F1F5F9' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '完成率',
      minInterval: 1,
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#64748B', fontSize: 12 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F1F5F9' } }
    },
    series: [{
      name: '完成率',
      type: 'bar',
      data: rates,
      itemStyle: {
        borderRadius: [8, 8, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#4F46E5' },
          { offset: 1, color: '#818CF8' }
        ])
      },
      barWidth: '60%'
    }]
  })
}

// Load participation trend
async function loadTrend() {
  trendLoading.value = true
  trendError.value = false
  try {
    const [participationRes, completionRes] = await Promise.all([
      getParticipationTrend({ days: 30 }),
      getCompletionTrend({ days: 30 })
    ])
    const participationData = participationRes.data || []
    const completionData = completionRes.data || []
    renderTrendChart(participationData, completionData)
  } catch {
    trendError.value = true
    renderTrendChart([], [])
  } finally {
    trendLoading.value = false
  }
}

function renderTrendChart(participationData, completionData) {
  if (!trendChartRef.value) return
  if (trendChartInstance) {
    trendChartInstance.dispose()
    trendChartInstance = null
  }
  trendChartInstance = echarts.init(trendChartRef.value)

  const dates = participationData.map(item => item.date || '')

  trendChartInstance.setOption({
    color: ['#4F46E5', '#10B981', '#F59E0B', '#EF4444'],
    tooltip: {
      trigger: 'axis',
      borderRadius: 8,
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#E2E8F0',
      shadowColor: 'rgba(0,0,0,0.08)',
      shadowBlur: 8,
      textStyle: { color: '#1E293B', fontSize: 13 }
    },
    legend: { data: ['参与率', '完成率'], bottom: 0, textStyle: { color: '#64748B', fontSize: 12 } },
    grid: { left: '3%', right: '4%', bottom: '18%', top: '8%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: { color: '#64748B', fontSize: 12 },
      axisLine: { lineStyle: { color: '#F1F5F9' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '比率',
      minInterval: 1,
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#64748B', fontSize: 12 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F1F5F9' } }
    },
    series: [
      {
        name: '参与率',
        type: 'line',
        smooth: true,
        data: participationData.map(item => item.participationRate ?? 0),
        itemStyle: { color: '#4F46E5' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '完成率',
        type: 'line',
        smooth: true,
        data: completionData.map(item => item.completionRate ?? 0),
        itemStyle: { color: '#10B981' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.12 }
      }
    ]
  })
}

// Load completion warnings
async function loadWarnings() {
  warningsLoading.value = true
  warningsError.value = false
  try {
    const res = await getCompletionWarnings()
    warnings.value = Array.isArray(res.data) ? res.data : (res.data?.items || [])
  } catch {
    warningsError.value = true
  } finally {
    warningsLoading.value = false
  }
}

// Load hot courses (mock: top 10 by enrollment from department stats)
async function loadHotCourses() {
  hotCourseLoading.value = true
  hotCourseError.value = false
  try {
    const res = await getDepartmentStats()
    const items = res.data || []
    // 按 enrollmentCount 排序取 top 10
    hotCourses.value = [...items]
      .sort((a, b) => (b.enrollmentCount ?? 0) - (a.enrollmentCount ?? 0))
      .slice(0, 10)
  } catch {
    hotCourseError.value = true
  } finally {
    hotCourseLoading.value = false
  }
}

function formatPercent(value) {
  if (value == null) return '0%'
  return `${Number(value).toFixed(1)}%`
}

function resizeCharts() {
  deptChartInstance?.resize()
  trendChartInstance?.resize()
}

const debouncedResizeCharts = debounce(resizeCharts, 200)

onMounted(async () => {
  await Promise.all([
    loadStats(),
    loadDepartmentStats(),
    loadTrend(),
    loadWarnings(),
    loadHotCourses()
  ])
  window.addEventListener('resize', debouncedResizeCharts)
  lastUpdatedAt.value = Date.now()
  startLastUpdatedTimer()
  refreshTimer = setInterval(() => {
    refreshAll()
  }, refreshInterval.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', debouncedResizeCharts)
  deptChartInstance?.dispose()
  trendChartInstance?.dispose()
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (lastUpdatedTimer) {
    clearInterval(lastUpdatedTimer)
    lastUpdatedTimer = null
  }
  animationFrameIds.forEach(id => cancelAnimationFrame(id))
  animationFrameIds.length = 0
})
</script>

<style scoped>
/* =============================================
   Academic Dashboard — Indigo Education Style
   ============================================= */

.academic-dashboard {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

/* =============================================
   1. Welcome Bar — 玻璃态
   ============================================= */
.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  padding: var(--space-6) var(--space-8);
  background: linear-gradient(135deg, #4F46E5 0%, #6366F1 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(79, 70, 229, 0.15);
  color: white;
}

.welcome-left {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.welcome-date {
  font-size: var(--text-base);
  opacity: 0.8;
}

.welcome-greeting {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  color: white;
}

.greeting-name {
  color: white;
}

.greeting-suffix {
  font-weight: var(--weight-regular);
  opacity: 0.9;
}

.welcome-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.last-updated {
  font-size: var(--text-xs);
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

/* =============================================
   Quick Actions
   ============================================= */
.quick-actions-row {
  margin-bottom: var(--space-6);
}

.quick-action-card {
  cursor: pointer;
  border: none;
  border-radius: var(--radius-lg);
  transition: all var(--duration-base) var(--ease-out);
}

.quick-action-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.quick-action-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
}

.quick-action-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-action-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-primary);
  font-weight: var(--weight-medium);
  white-space: nowrap;
}

/* =============================================
   2. 4 个 Stat Cards
   ============================================= */
.stats-row {
  margin-bottom: var(--space-4);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  cursor: default;
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

/* 左侧 52×52 圆角方形图标区 */
.stat-icon-zone {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 22px;
}

.stat-icon-zone--primary {
  background: rgba(79,70,229,0.1);
  color: #4F46E5;
}

.stat-icon-zone--success {
  background: rgba(16,185,129,0.1);
  color: #10B981;
}

.stat-icon-zone--warning {
  background: rgba(245,158,11,0.1);
  color: #F59E0B;
}

.stat-icon-zone--purple {
  background: rgba(139,92,246,0.1);
  color: #8B5CF6;
}

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
}

/* =============================================
   3. 图表面板
   ============================================= */
.charts-row {
  margin-bottom: var(--space-4);
}

.chart-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: var(--space-4);
}

.card-header {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
  letter-spacing: var(--tracking-wide);
}

.header-tag {
  transform: scale(0.85);
}

.chart-container {
  height: 300px;
  width: 100%;
}

.chart-error,
.table-error {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

/* =============================================
   5. 预警表格 + 热门表格
   ============================================= */
.bottom-row {
  margin-bottom: var(--space-4);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: var(--space-4);
}

.warning-table,
.hot-table {
  font-size: var(--text-sm);
}

.warning-table .el-table__header th,
.hot-table .el-table__header th {
  background: var(--el-fill-color-light);
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-secondary);
}

.warning-table .el-table__row:hover td,
.hot-table .el-table__row:hover td {
  background: var(--role-primary-light-9) !important;
}

.rate-danger {
  color: var(--el-color-danger);
  font-weight: var(--weight-bold);
}

.rate-success {
  color: var(--el-color-success);
}

/* =============================================
   Skeleton
   ============================================= */
.skeleton-value {
  width: 60px;
  height: 32px;
}

.skeleton-chart {
  height: 300px;
  border-radius: var(--radius-md);
}

.skeleton-item {
  height: 40px;
  margin-bottom: var(--space-2);
  border-radius: var(--radius-md);
}

/* =============================================
   Responsive
   ============================================= */
@media (max-width: 1024px) {
  .stats-row .el-col,
  .quick-actions-row .el-col {
    margin-bottom: var(--space-3);
  }
}

@media (max-width: 768px) {
  .academic-dashboard {
    padding: var(--space-4);
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
    padding: var(--space-5) var(--space-6);
  }

  .welcome-right {
    width: 100%;
    justify-content: space-between;
  }

  .quick-action-inner {
    padding: var(--space-1) 0;
  }

  .quick-action-icon {
    width: 36px;
    height: 36px;
  }

  .quick-action-label {
    font-size: var(--text-xs);
  }
}
</style>