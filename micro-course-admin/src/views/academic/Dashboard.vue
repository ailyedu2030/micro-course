<!--
  教务处仪表盘
  路由路径: /academic/dashboard
  Phase 9
  Author: Phase9-Development-Team
-->
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

    <!-- P1C-063: 院系数据下钻提示条 -->
    <el-card v-if="departmentId" shadow="never" class="drilldown-banner">
      <div class="drilldown-inner">
        <el-button text @click="handleBackToOverview">
          <el-icon><ArrowLeft /></el-icon> 返回全校概览
        </el-button>
        <span class="drilldown-title">当前查看：<strong>{{ departmentName || '院系详情' }}</strong></span>
      </div>
    </el-card>

    <!-- 快捷入口 -->
    <el-row :gutter="16" class="quick-actions-row">
      <el-col v-for="action in quickActions" :key="action.label" :xs="12" :sm="6">
        <el-card class="quick-action-card" shadow="hover" role="button" tabindex="0" :aria-label="`快速操作 ${action.label}`" @click="handleQuickAction(action)" @keydown.enter="handleQuickAction(action)" @keydown.space.prevent="handleQuickAction(action)">
          <div class="quick-action-inner">
            <div class="quick-action-icon">
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
            <div class="card-header">
              参与率趋势
              <el-select
                v-model="selectedSemester"
                size="small"
                style="width:150px; margin-left:auto"
                placeholder="选择学期"
                clearable
                @change="handleSemesterChange"
              >
                <el-option
                  v-for="sem in semesterOptions"
                  :key="sem.value"
                  :label="sem.label"
                  :value="sem.value"
                />
              </el-select>
            </div>
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
            <div class="card-header">
 完成率预警
              <el-tag size="small" type="danger" class="header-tag">completionRate &lt; 30%</el-tag>
              <!-- P1I-061: 阈值 30% 与后端 COMPLETION_WARNING_THRESHOLD 保持一致，修改时需同步更新 -->
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
              <el-table v-else :data="warnings" class="warning-table" empty-text="暂无数据">
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
              <el-table v-else :data="hotCourses" class="hot-table" empty-text="暂无数据">
                <el-table-column type="index" label="排名" width="60" align="center" />
                <el-table-column prop="title" label="课程名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="categoryName" label="分类" width="100" align="center" />
                <el-table-column prop="teacherName" label="教师" width="100" align="center" />
                <el-table-column prop="enrollmentCount" label="选课人次" width="100" align="center" sortable />
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
import { useRouter, useRoute } from 'vue-router'
import { Reading, User, TrendCharts, Finished, Refresh, DataAnalysis, Collection, Setting, ArrowLeft } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getAcademicOverview,
  getDepartmentStats,
  getCompletionWarnings,
  getParticipationTrend,
  getCompletionTrend,
  getDepartmentDetail
} from '@/api/academic-stats'
import { getCourses } from '@/api/course'

const router = useRouter()
const route = useRoute()

// P1C-063: 数据下钻 — 从 query 参数获取 departmentId
const departmentId = computed(() => route.query.departmentId ? Number(route.query.departmentId) : null)
const departmentName = ref('')

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
  { label: '课程审核', icon: markRaw(Reading), route: '/courses' },
  { label: '选课管理', icon: markRaw(Collection), route: '/enrollments' },
  { label: '教学班管理', icon: markRaw(Setting), route: '/admin/teaching-classes' },
  { label: '统计分析', icon: markRaw(DataAnalysis), route: '/academic/dashboard' }
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

// P1-8: 学期选择器
const _now = new Date()
const currentYear = _now.getFullYear()
const currentMonth = _now.getMonth() + 1
const selectedSemester = ref('')
const semesterOptions = computed(() => {
  const semesters = []
  for (let year = currentYear - 1; year <= currentYear + 1; year++) {
    semesters.push({ label: `${year}-${year + 1} 第一学期`, value: `${year}-1` })
    semesters.push({ label: `${year}-${year + 1} 第二学期`, value: `${year}-2` })
  }
  return semesters
})
// 默认选择当前学期
selectedSemester.value = currentMonth < 8 ? `${currentYear - 1}-2` : `${currentYear}-1`

function handleSemesterChange() {
  loadTrend()
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
    // P1C-063: 数据下钻 — 如果指定了 departmentId，加载院系详情
    if (departmentId.value) {
      const res = await getDepartmentDetail(departmentId.value)
      const d = res.data || {}
      departmentName.value = d.departmentName || ''
      stats.value = {
        totalCourses: d.totalCourses ?? 0,
        totalEnrollments: d.totalEnrollments ?? 0,
        avgCompletionRate: d.avgCompletionRate ?? 0,
        avgAccuracyRate: d.avgAccuracyRate ?? 0
      }
    } else {
      const res = await getAcademicOverview()
      const d = res.data || {}
      stats.value = {
        totalCourses: d.totalCourses ?? 0,
        totalEnrollments: d.totalEnrollments ?? 0,
        avgCompletionRate: d.avgCompletionRate ?? 0,
        avgAccuracyRate: d.avgAccuracyRate ?? 0
      }
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
    // P1C-063: 数据下钻 — 只显示选中院系
    let items = []
    if (departmentId.value) {
      const res = await getDepartmentDetail(departmentId.value)
      if (res.data) {
        // 构造单条院系数据给图表
        items = [{
          id: res.data.departmentId,
          name: res.data.departmentName,
          avgCompletionRate: res.data.avgCompletionRate
        }]
      }
    } else {
      const res = await getDepartmentStats()
      items = res.data || []
    }
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
    // P0 修复：后端期望 semester 参数，days 被忽略。统一为不传 params（semester 可选）
    const [participationRes, completionRes] = await Promise.all([
      getParticipationTrend(selectedSemester.value ? { semester: selectedSemester.value } : undefined),
      getCompletionTrend(selectedSemester.value ? { semester: selectedSemester.value } : undefined)
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

  // P0 修复：后端 TrendPointVO 字段名为 month/value，非 date/participationRate/completionRate
  const dates = participationData.map(item => item.month || '')

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
        data: participationData.map(item => item.value ?? 0),
        itemStyle: { color: '#4F46E5' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '完成率',
        type: 'line',
        smooth: true,
        data: completionData.map(item => item.value ?? 0),
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

// Load hot courses (top 10 by enrollment count)
async function loadHotCourses() {
  hotCourseLoading.value = true
  hotCourseError.value = false
  try {
    // AC01 修复: 使用课程 API 按 studentCount 降序排列，替代错误的院系统计
    const res = await getCourses({
      page: 0,
      size: 10,
      sort: 'studentCount,desc',
      status: 4  // PUBLISHED
    })
    const items = res.data?.items || res.data || []
    hotCourses.value = items.map(item => ({
      title: item.title || '',
      enrollmentCount: item.studentCount ?? 0,
      categoryName: item.categoryName || '',
      teacherName: item.teacherName || ''
    }))
  } catch {
    hotCourseError.value = true
  } finally {
    hotCourseLoading.value = false
  }
}

// P1C-063: 返回全校概览
function handleBackToOverview() {
  router.push('/academic/dashboard')
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
  background: linear-gradient(135deg, var(--role-primary-darkest, #1d3557) 0%, var(--role-primary, #409eff) 100%);
  border-radius: var(--radius-xl);
  box-shadow: 0 8px 32px rgba(79, 70, 229, 0.15);
  color: white;
  position: relative;
  overflow: hidden;
}

.welcome-bar::before {
  content: '';
  position: absolute;
  right: -80px;
  top: -80px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  pointer-events: none;
}

.welcome-bar::after {
  content: '';
  position: absolute;
  right: 60px;
  bottom: -60px;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.welcome-left {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  position: relative;
  z-index: 1;
}

.welcome-date {
  font-size: var(--text-sm);
  color: rgba(255,255,255,0.75);
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
  position: relative;
  z-index: 1;
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
   1.5 Drill-down Banner（P1C-063）
   ============================================= */
.drilldown-banner {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--role-primary-light-9) 0%, #fff 100%);
  border-left: 4px solid var(--role-primary);
}

.drilldown-inner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.drilldown-title {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.drilldown-title strong {
  color: var(--role-primary);
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
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 22px;
  transition: transform var(--duration-base) var(--ease-out);
}

.stat-card:hover .stat-icon-zone {
  transform: scale(1.05);
}

.stat-icon-zone--primary,
.stat-icon-zone--success,
.stat-icon-zone--warning,
.stat-icon-zone--purple {
  background: var(--role-primary-light-9);
  color: var(--role-primary);
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
  transition: box-shadow var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
}

.chart-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
  transform: translateY(-2px);
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
  transition: box-shadow var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
  transform: translateY(-2px);
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