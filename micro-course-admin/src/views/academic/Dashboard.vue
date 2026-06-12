<template>
  <div class="academic-dashboard">
    <!-- 顶部欢迎条 — 玻璃态 -->
    <div class="welcome-bar">
      <div class="welcome-date">{{ welcomeDate }}</div>
      <div class="welcome-greeting">
        <span class="greeting-name">教务处</span>
        <span class="greeting-suffix">{{ greeting }}</span>
      </div>
    </div>

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
                <div class="stat-value">{{ stats.totalCourses ?? 0 }}</div>
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
                <div class="stat-value">{{ stats.totalEnrollments ?? 0 }}</div>
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
                <div class="stat-value">{{ formatPercent(stats.avgCompletionRate) }}</div>
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
                <div class="stat-value">{{ formatPercent(stats.avgAccuracyRate) }}</div>
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
              <div v-else ref="deptChartRef" class="chart-container"></div>
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
              <div v-else ref="trendChartRef" class="chart-container"></div>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Reading, User, TrendCharts, Finished } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getAcademicOverview,
  getDepartmentStats,
  getCompletionWarnings,
  getParticipationTrend,
  getCompletionTrend
} from '@/api/academic-stats'

const now = new Date()
const welcomeDate = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
const greeting = computed(() => {
  const h = now.getHours()
  if (h < 12) return '，上午好'
  if (h < 14) return '，中午好'
  if (h < 18) return '，下午好'
  return '，晚上好'
})

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
})
</script>

<style scoped>
/* =============================================
   Academic Dashboard — Indigo Education Style
   ============================================= */

.academic-dashboard {
  --color-primary: #4F46E5;
  --color-secondary: #818CF8;
  --color-surface: #ffffff;
  --color-bg: #F5F6FA;
  --color-text: #1E293B;
  --color-muted: #64748B;
  --color-border: #F1F5F9;

  padding: 24px;
  background: var(--color-bg);
  min-height: 100vh;
}

/* =============================================
   1. Welcome Bar — 玻璃态
   ============================================= */
.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 24px 32px;
  background: linear-gradient(135deg, #4F46E5 0%, #6366F1 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(79, 70, 229, 0.15);
  color: white;
}

.welcome-date {
  font-size: 14px;
  opacity: 0.8;
}

.welcome-greeting {
  font-size: 24px;
  font-weight: 700;
  color: white;
}

.greeting-name {
  color: white;
}

.greeting-suffix {
  font-weight: 400;
  opacity: 0.9;
}

/* =============================================
   2. 4 个 Stat Cards
   ============================================= */
.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--color-surface);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  cursor: default;
  transition: transform 200ms ease, box-shadow 200ms ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.10);
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
  background: #EEF2FF;
  color: #4F46E5;
}

.stat-icon-zone--success {
  background: #ECFDF5;
  color: #10B981;
}

.stat-icon-zone--warning {
  background: #FEF3C7;
  color: #F59E0B;
}

.stat-icon-zone--purple {
  background: #FAF5FF;
  color: #8B5CF6;
}

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--color-muted);
  margin-top: 4px;
}

/* =============================================
   3. 图表面板
   ============================================= */
.charts-row {
  margin-bottom: 16px;
}

.chart-card {
  background: var(--color-surface);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
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
  color: var(--color-muted);
  font-size: 13px;
}

/* =============================================
   5. 预警表格 + 热门表格
   ============================================= */
.bottom-row {
  margin-bottom: 16px;
}

.table-card {
  background: var(--color-surface);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;
}

.warning-table,
.hot-table {
  font-size: 13px;
}

.warning-table .el-table__header th,
.hot-table .el-table__header th {
  background: #F8FAFC;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-muted);
}

.warning-table .el-table__row:hover td,
.hot-table .el-table__row:hover td {
  background: #F1F5F9 !important;
}

.rate-danger {
  color: #EF4444;
  font-weight: 700;
}

.rate-success {
  color: #10B981;
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
  border-radius: 8px;
}

.skeleton-item {
  height: 40px;
  margin-bottom: 8px;
  border-radius: 6px;
}

/* =============================================
   Responsive
   ============================================= */
@media (max-width: 1024px) {
  .stats-row .el-col {
    margin-bottom: 12px;
  }
}

@media (max-width: 768px) {
  .academic-dashboard {
    padding: 16px;
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding: 20px 24px;
  }
}
</style>