<template>
  <div class="academic-dashboard">
    <!-- 顶部欢迎条 -->
    <div class="welcome-bar">
      <div class="welcome-left">
        <span class="welcome-date">{{ welcomeDate }}</span>
      </div>
      <div class="welcome-greeting">
        <span class="greeting-name">教务处</span>
        <span class="greeting-suffix">{{ greeting }}</span>
      </div>
    </div>

    <!-- 4 个指标卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap primary-icon">
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
          <div class="stat-icon-wrap success-icon">
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
          <div class="stat-icon-wrap warning-icon">
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
          <div class="stat-icon-wrap purple-icon">
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
              <el-table v-else :data="warnings" stripe class="warning-table">
                <el-table-column prop="name" label="课程名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="completionRate" label="完成率" width="100" align="center">
                  <template #default="{ row }">
                    <span :class="row.completionRate < 30 ? 'rate-danger' : 'rate-normal'">
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
              <el-table v-else :data="hotCourses" stripe class="hot-table">
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
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: 15 } },
    yAxis: {
      type: 'value',
      name: '完成率',
      minInterval: 1,
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    series: [{
      name: '完成率',
      type: 'bar',
      data: rates,
      itemStyle: {
        color: function(params) {
          const rate = rates[params.dataIndex]
          if (rate < 30) return 'var(--el-color-danger)'
          if (rate < 60) return 'var(--el-color-warning)'
          return 'var(--role-primary)'
        },
        borderRadius: [4, 4, 0, 0]
      },
      barWidth: '50%'
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
    tooltip: { trigger: 'axis' },
    legend: { data: ['参与率', '完成率'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '18%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: {
      type: 'value',
      name: '比率',
      minInterval: 1,
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    series: [
      {
        name: '参与率',
        type: 'line',
        smooth: true,
        data: participationData.map(item => item.participationRate ?? 0),
        itemStyle: { color: 'var(--el-color-success)' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 }
      },
      {
        name: '完成率',
        type: 'line',
        smooth: true,
        data: completionData.map(item => item.completionRate ?? 0),
        itemStyle: { color: 'var(--el-color-warning)' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 }
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
.academic-dashboard {
  padding: var(--space-5);
  background: var(--el-bg-color);
  min-height: 100vh;
}

/* 欢迎条 */
.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-5);
  padding: var(--space-4) var(--space-5);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.welcome-left {
  display: flex;
  align-items: center;
}

.welcome-date {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.welcome-greeting {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.greeting-name {
  color: var(--role-primary);
}

/* stat-grid */
.stats-row {
  margin-bottom: var(--space-4);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  cursor: default;
}

.stat-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 22px;
}

.primary-icon { background: var(--role-primary-light-9); color: var(--role-primary); }
.success-icon { background: var(--el-color-success-light-9); color: var(--el-color-success); }
.warning-icon { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.info-icon { background: var(--el-color-info-light-9); color: var(--el-color-info); }
.purple-icon { background: var(--role-primary-light); color: var(--role-primary); }

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  color: var(--role-primary);
  line-height: var(--leading-tight);
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
}

/* 图表行 */
.charts-row {
  margin-bottom: var(--space-4);
}

.chart-card {
  margin-bottom: var(--space-4);
}

.card-header {
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.header-tag {
  transform: scale(0.85);
}

.chart-container {
  height: 260px;
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

/* 底部表格 */
.bottom-row {
  margin-bottom: var(--space-4);
}

.table-card {
  margin-bottom: var(--space-4);
}

.warning-table,
.hot-table {
  font-size: var(--text-sm);
}

.rate-danger {
  color: var(--el-color-danger);
  font-weight: var(--weight-semibold);
}

.rate-normal {
  color: var(--el-text-color-primary);
}

/* Skeleton */
.skeleton-value {
  width: 60px;
  height: 32px;
}

.skeleton-chart {
  height: 260px;
  border-radius: var(--radius-md);
}

.skeleton-item {
  height: 40px;
  margin-bottom: var(--space-2);
  border-radius: var(--radius-sm);
}

@media (max-width: 1024px) {
  .stats-row .el-col {
    margin-bottom: var(--space-3);
  }
}

@media (max-width: 768px) {
  .academic-dashboard {
    padding: var(--space-3);
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }
}
</style>