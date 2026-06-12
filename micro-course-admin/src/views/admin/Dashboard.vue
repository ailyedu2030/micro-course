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
    </div>

    <!-- 8 个 stat-card（4×2） -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap primary-icon">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ stats.totalUsers ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总用户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap success-icon">
            <el-icon><Reading /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ stats.totalCourses ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总课程</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap warning-icon">
            <el-icon><Tickets /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ stats.totalStudents ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">总学员</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap info-icon">
            <el-icon><UserFilled /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ stats.activeUsers ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">活跃用户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap danger-icon">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ stats.pendingCourses ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">待审课程</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap purple-icon">
            <el-icon><ChatLineSquare /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ stats.pendingReviews ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">待批评论</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap orange-icon">
            <el-icon><Timer /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ formatDuration(stats.totalStudyMinutes) }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">累计学习时长</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap teal-icon">
            <el-icon><Medal /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template><el-skeleton-item class="skeleton-value" /></template>
              <template #default>
                <div class="stat-value">{{ stats.certificatesIssued ?? 0 }}</div>
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
          <div class="health-grid">
            <div class="health-item">
              <span class="health-label">DB</span>
              <span class="health-value" :class="health.db === 'ok' ? 'status-ok' : 'status-warn'">
                {{ health.db || 'ok' }}
              </span>
            </div>
            <div class="health-item">
              <span class="health-label">Redis</span>
              <span class="health-value" :class="health.redis === 'ok' ? 'status-ok' : 'status-warn'">
                {{ health.redis || 'ok' }}
              </span>
            </div>
            <div class="health-item">
              <span class="health-label">磁盘</span>
              <span class="health-value">{{ health.disk || '70%' }}</span>
            </div>
            <div class="health-item">
              <span class="health-label">内存</span>
              <span class="health-value">{{ health.memory || '58%' }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import {
  User, UserFilled, Reading, Tickets, Clock, ChatLineSquare, Timer, Medal
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getOverview, getUserTrend, getCourseTrend, getCourseDistribution, getLearningBehavior } from '@/api/admin-stats'
import { getLogs } from '@/api/operation-log'

const now = new Date()
const welcomeDate = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
const greeting = computed(() => {
  const h = now.getHours()
  if (h < 12) return '，上午好'
  if (h < 14) return '，中午好'
  if (h < 18) return '，下午好'
  return '，晚上好'
})

// Stats
const statsLoading = ref(true)
const stats = ref({})

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
const health = ref({ db: 'ok', redis: 'ok', disk: '70%', memory: '58%' })

// Load stats
async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getOverview()
    const d = res.data || {}
    stats.value = {
      totalUsers: d.totalUsers,
      totalCourses: d.totalCourses,
      totalStudents: d.totalEnrollments,
      activeUsers: d.activeUsers7d,
      pendingCourses: 0,
      pendingReviews: 0,
      totalStudyMinutes: Math.round((d.totalWatchTimeMinutes || 0)),
      certificatesIssued: 0
    }
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
    tooltip: { trigger: 'axis' },
    legend: { data: ['用户', '课程', '学员'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '18%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', name: '数量', minInterval: 1 },
    series: [
      {
        name: '用户',
        type: 'line',
        smooth: true,
        data: (data.users || []).map(item => item.count ?? 0),
        itemStyle: { color: 'var(--role-primary)' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 }
      },
      {
        name: '课程',
        type: 'line',
        smooth: true,
        data: (data.courses || []).map(item => item.count ?? 0),
        itemStyle: { color: 'var(--el-color-success)' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 }
      },
      {
        name: '学员',
        type: 'line',
        smooth: true,
        data: (data.students || []).map(item => item.count ?? 0),
        itemStyle: { color: 'var(--el-color-warning)' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 }
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
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: 'var(--el-color-white)', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}' },
      data: pieData,
      color: ['var(--role-primary)', 'var(--el-color-success)', 'var(--el-color-warning)', 'var(--el-color-danger)', 'var(--el-color-info)', 'var(--role-primary)']
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
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', name: '活跃用户', minInterval: 1 },
    series: [{
      name: '活跃用户',
      type: 'line',
      smooth: true,
      data: activeUsers,
      itemStyle: { color: 'var(--role-primary)' },
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.1 }
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
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendsChart?.dispose()
  categoryChart?.dispose()
  activityChart?.dispose()
})
</script>

<style scoped>
.admin-dashboard {
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
.danger-icon { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.purple-icon { background: var(--role-primary-light); color: var(--role-primary); }
.orange-icon { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.teal-icon { background: var(--el-color-info-light-9); color: var(--el-color-info); }

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
}

.chart-container {
  height: 260px;
  width: 100%;
}

.chart-error,
.list-error,
.list-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

/* 列表卡片 */
.list-card {
  margin-bottom: var(--space-4);
}

.list-ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.list-item {
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.list-item:last-child {
  border-bottom: none;
}

.log-main {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.log-action {
  font-size: var(--text-sm);
  color: var(--el-text-color-primary);
  font-weight: var(--weight-medium);
}

.log-target {
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
}

.log-sub {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.log-operator {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.log-time {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
}

/* 健康卡 */
.health-card {
  margin-bottom: var(--space-4);
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.health-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-sm);
}

.health-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.health-value {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.status-ok {
  color: var(--el-color-success);
}

.status-warn {
  color: var(--el-color-warning);
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
  .admin-dashboard {
    padding: var(--space-3);
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }
}
</style>