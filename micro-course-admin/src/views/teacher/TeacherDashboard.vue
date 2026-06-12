<template>
  <div class="teacher-dashboard">
    <!-- 顶部欢迎条 — 玻璃态 -->
    <div class="welcome-bar">
      <div class="welcome-left">
        <div class="welcome-date">{{ welcomeDate }}</div>
        <div class="welcome-greeting">
          <span class="greeting-name">{{ userName }}</span>
          <span class="greeting-suffix">{{ greeting }}</span>
        </div>
      </div>
      <div class="welcome-right">
        <span class="welcome-title">教师工作台</span>
      </div>
    </div>

    <!-- 4 个 stat-card -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon-wrap stat-icon-course">
            <el-icon class="stat-icon"><Reading /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template>
                <el-skeleton-item class="skeleton-value" />
              </template>
              <template #default>
                <div class="stat-value">{{ stats.courseCount ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">我的课程数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon-wrap stat-icon-student">
            <el-icon class="stat-icon"><User /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template>
                <el-skeleton-item class="skeleton-value" />
              </template>
              <template #default>
                <div class="stat-value">{{ stats.studentCount ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">在学学员数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon-wrap stat-icon-homework">
            <el-icon class="stat-icon"><Document /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template>
                <el-skeleton-item class="skeleton-value" />
              </template>
              <template #default>
                <div class="stat-value">{{ stats.pendingHomework ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">待批改作业</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon-wrap stat-icon-question">
            <el-icon class="stat-icon"><QuestionFilled /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template>
                <el-skeleton-item class="skeleton-value" />
              </template>
              <template #default>
                <div class="stat-value">{{ stats.pendingQuestions ?? 0 }}</div>
              </template>
            </el-skeleton>
            <div class="stat-label">学员提问</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon-wrap stat-icon-completion">
            <el-icon class="stat-icon"><Finished /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template>
                <el-skeleton-item class="skeleton-value" />
              </template>
              <template #default>
                <div class="stat-value">{{ Number(stats.completionRate ?? 0).toFixed(1) }}%</div>
              </template>
            </el-skeleton>
            <div class="stat-label">完成率</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon-wrap stat-icon-score">
            <el-icon class="stat-icon"><Star /></el-icon>
          </div>
          <div class="stat-body">
            <el-skeleton :loading="statsLoading" animated :rows="1">
              <template #template>
                <el-skeleton-item class="skeleton-value" />
              </template>
              <template #default>
                <div class="stat-value">{{ Number(stats.avgScore ?? 0).toFixed(1) }} 分</div>
              </template>
            </el-skeleton>
            <div class="stat-label">平均分</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 中部主体区：左侧图表 + 右侧待办通知 -->
    <el-row :gutter="16" class="main-row">
      <!-- 左侧图表 -->
      <el-col :xs="24" :md="14">
        <div class="chart-card">
          <div class="card-header">
            <span>最近 7 天学情</span>
          </div>
          <el-skeleton :loading="activityLoading" animated :rows="3">
            <template #template>
              <el-skeleton-item class="skeleton-chart" />
            </template>
            <template #default>
              <div v-if="activityError" class="chart-error">
                <el-icon><WarningFilled /></el-icon>
                <span>加载失败</span>
              </div>
              <div v-else ref="studyChartRef" class="chart-container"></div>
            </template>
          </el-skeleton>
        </div>
        <div class="chart-card">
          <div class="card-header">
            <span>学员活跃度</span>
          </div>
          <el-skeleton :loading="activityLoading" animated :rows="3">
            <template #template>
              <el-skeleton-item class="skeleton-chart" />
            </template>
            <template #default>
              <div v-if="activityError" class="chart-error">
                <el-icon><WarningFilled /></el-icon>
                <span>加载失败</span>
              </div>
              <div v-else ref="activeChartRef" class="chart-container"></div>
            </template>
          </el-skeleton>
        </div>
      </el-col>

      <!-- 右侧待办通知 -->
      <el-col :xs="24" :md="10">
        <div class="list-card">
          <div class="card-header">
            <span>待办</span>
          </div>
          <el-skeleton :loading="tasksLoading" animated :rows="3">
            <template #template>
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
            </template>
            <template #default>
              <div v-if="tasksError" class="list-error">
                <span>加载失败</span>
              </div>
              <div v-else-if="tasks.length === 0" class="list-empty">
                <span>暂无待办</span>
              </div>
              <ul v-else class="list-ul">
                <li v-for="task in tasks" :key="task.id" class="list-item">
                  <span :class="['item-type', `item-type-${task.typeColor || 'default'}`]">{{ task.type }}</span>
                  <span class="item-title">{{ task.title }}</span>
                  <span class="item-time">{{ formatTime(task.createdAt) }}</span>
                </li>
              </ul>
            </template>
          </el-skeleton>
        </div>
        <div class="list-card">
          <div class="card-header">
            <span>最新通知</span>
          </div>
          <el-skeleton :loading="notifLoading" animated :rows="3">
            <template #template>
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
              <el-skeleton-item class="skeleton-item" />
            </template>
            <template #default>
              <div v-if="notifError" class="list-error">
                <span>加载失败</span>
              </div>
              <div v-else-if="notifications.length === 0" class="list-empty">
                <span>暂无通知</span>
              </div>
              <ul v-else class="list-ul">
                <li v-for="notif in notifications" :key="notif.id" class="list-item">
                  <span class="item-title">{{ notif.title }}</span>
                  <span class="item-time">{{ formatTime(notif.createdAt) }}</span>
                </li>
              </ul>
            </template>
          </el-skeleton>
        </div>
      </el-col>
    </el-row>

    <!-- 底部：我教的课程 -->
    <div class="course-card">
      <div class="card-header">
        <span>我教的课程</span>
      </div>
      <el-skeleton :loading="coursesLoading" animated :rows="2">
        <template #template>
          <div class="course-grid">
            <el-skeleton-item v-for="i in 4" :key="i" class="skeleton-course" />
          </div>
        </template>
        <template #default>
          <div v-if="coursesError" class="course-error">
            <span>加载失败</span>
          </div>
          <div v-else-if="courses.length === 0" class="course-empty">
            <span>暂无课程</span>
          </div>
          <div v-else class="course-grid">
            <div v-for="course in courses" :key="course.id" class="course-card-item">
              <div class="course-cover">
                <img v-if="course.cover" :src="course.cover" :alt="course.title" class="course-cover-img" />
                <div v-else class="course-cover-placeholder">
                  <el-icon><VideoPlay /></el-icon>
                </div>
              </div>
              <div class="course-info">
                <div class="course-title">{{ course.title }}</div>
                <div class="course-meta">
                  <span class="course-student">
                    <el-icon><User /></el-icon>
                    {{ course.studentCount ?? 0 }} 学员
                  </span>
                  <el-rate v-if="course.rating" :model-value="course.rating" disabled show-score size="small" />
                </div>
              </div>
            </div>
          </div>
        </template>
      </el-skeleton>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Reading, User, Document, QuestionFilled, VideoPlay, WarningFilled, Finished, Star } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useUserStore } from '@/store/user'
import { getStats, getStudentActivity, getPendingTasks, getNotifications, getMyCourses } from '@/api/teacher'

const userStore = useUserStore()

// 欢迎信息
const now = new Date()
const welcomeDate = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 ${['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()]}`
const weather = '晴'
const userName = computed(() => userStore.userInfo?.realName || userStore.userInfo?.username || '老师')
const greeting = computed(() => {
  const h = now.getHours()
  if (h < 12) return '，上午好'
  if (h < 14) return '，中午好'
  if (h < 18) return '，下午好'
  return '，晚上好'
})

// 统计数据
const statsLoading = ref(true)
const stats = ref({ courseCount: 0, studentCount: 0, pendingHomework: 0, pendingQuestions: 0, completionRate: 0, avgScore: 0 })

// 学情图表
const activityLoading = ref(true)
const activityError = ref(false)
const studyChartRef = ref(null)
const activeChartRef = ref(null)
let studyChart = null
let activeChart = null

// 待办/通知
const tasksLoading = ref(true)
const tasksError = ref(false)
const tasks = ref([])

const notifLoading = ref(true)
const notifError = ref(false)
const notifications = ref([])

// 课程
const coursesLoading = ref(true)
const coursesError = ref(false)
const courses = ref([])

// 定时刷新
const refreshInterval = ref(60000)
let refreshTimer = null

async function refreshAll() {
  await Promise.all([
    loadStats(),
    loadActivity(),
    loadTasks(),
    loadNotifications(),
    loadCourses()
  ])
}

// 加载统计数据
async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getStats()
    stats.value = res.data || {}
  } catch {
    // 静默失败
  } finally {
    statsLoading.value = false
  }
}

// 加载学情图表
async function loadActivity() {
  activityLoading.value = true
  activityError.value = false
  try {
    const res = await getStudentActivity(7)
    const data = res.data || []
    renderStudyChart(data)
    renderActiveChart(data)
  } catch {
    activityError.value = true
    renderStudyChart([])
    renderActiveChart([])
  } finally {
    activityLoading.value = false
  }
}

function renderStudyChart(data) {
  if (!studyChartRef.value) return
  if (studyChart) studyChart.dispose()
  studyChart = echarts.init(studyChartRef.value)
  const dates = data.map(item => item.date || '')
  const studyMinutes = data.map(item => item.studyMinutes ?? 0)
  const completionRate = data.map(item => item.completionRate ?? 0)
  studyChart.setOption({
    color: ['#4F46E5', '#10B981'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderRadius: 8,
      boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
      padding: [10, 14]
    },
    legend: { data: ['学习时长(分钟)', '完成率(%)'], bottom: 0, textStyle: { color: '#64748B', fontSize: 12 } },
    grid: { left: '3%', right: '4%', bottom: '18%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8' } },
    yAxis: [
      { type: 'value', name: '分钟', minInterval: 1, axisLine: { show: false }, splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8' } },
      { type: 'value', name: '%', minInterval: 1, max: 100, axisLine: { show: false }, splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8' } }
    ],
    series: [
      {
        name: '学习时长(分钟)',
        type: 'line',
        smooth: true,
        lineStyle: { width: 3 },
        data: studyMinutes,
        itemStyle: { color: '#4F46E5' },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '完成率(%)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: completionRate,
        itemStyle: { color: '#10B981' },
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.12 }
      }
    ]
  })
}

function renderActiveChart(data) {
  if (!activeChartRef.value) return
  if (activeChart) activeChart.dispose()
  activeChart = echarts.init(activeChartRef.value)
  const dates = data.map(item => item.date || '')
  const activeUsers = data.map(item => item.activeUsers ?? 0)
  activeChart.setOption({
    color: ['#4F46E5'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderRadius: 8,
      boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
      padding: [10, 14]
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8' } },
    yAxis: { type: 'value', name: '活跃学员', minInterval: 1, axisLine: { show: false }, splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8' } },
    series: [{
      name: '活跃学员',
      type: 'bar',
      data: activeUsers,
      itemStyle: { color: '#4F46E5', borderRadius: [4, 4, 0, 0] },
      barWidth: '50%'
    }]
  })
}

// 加载待办
async function loadTasks() {
  tasksLoading.value = true
  tasksError.value = false
  try {
    const res = await getPendingTasks(5)
    tasks.value = res.data || []
  } catch {
    tasksError.value = true
  } finally {
    tasksLoading.value = false
  }
}

// 加载通知
async function loadNotifications() {
  notifLoading.value = true
  notifError.value = false
  try {
    const res = await getNotifications(5)
    notifications.value = res.data || []
  } catch {
    notifError.value = true
  } finally {
    notifLoading.value = false
  }
}

// 加载课程
async function loadCourses() {
  coursesLoading.value = true
  coursesError.value = false
  try {
    const res = await getMyCourses()
    courses.value = res.data?.items || res.data || []
  } catch {
    coursesError.value = true
  } finally {
    coursesLoading.value = false
  }
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function resizeCharts() {
  studyChart?.resize()
  activeChart?.resize()
}

onMounted(async () => {
  await Promise.all([
    loadStats(),
    loadActivity(),
    loadTasks(),
    loadNotifications(),
    loadCourses()
  ])
  window.addEventListener('resize', resizeCharts)
  refreshTimer = setInterval(() => {
    refreshAll()
  }, refreshInterval.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  studyChart?.dispose()
  activeChart?.dispose()
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.teacher-dashboard {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100vh;
}

/* 欢迎条 — 玻璃态 */
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

.welcome-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.welcome-date {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.welcome-greeting {
  font-size: 20px;
  font-weight: 600;
  color: white;
}

.greeting-name {
  color: white;
}

.greeting-suffix {
  color: rgba(255, 255, 255, 0.85);
}

.welcome-right .welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: white;
  letter-spacing: 2px;
}

/* stat-row */
.stats-row {
  margin-bottom: 24px;
}

/* stat-card */
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  cursor: default;
  transition: transform 200ms, box-shadow 200ms;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.stat-icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-course { background: #EEF2FF; }
.stat-icon-course .stat-icon { color: #4F46E5; }

.stat-icon-student { background: #ECFDF5; }
.stat-icon-student .stat-icon { color: #10B981; }

.stat-icon-homework { background: #FEF3C7; }
.stat-icon-homework .stat-icon { color: #F59E0B; }

.stat-icon-question { background: #FEF2F2; }
.stat-icon-question .stat-icon { color: #EF4444; }

.stat-icon-completion { background: #ECFDF5; }
.stat-icon-completion .stat-icon { color: #10B981; }

.stat-icon-score { background: #FEF3C7; }
.stat-icon-score .stat-icon { color: #F59E0B; }

.stat-icon {
  font-size: 22px;
}

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1E293B;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #64748B;
  margin-top: 4px;
}

/* 主区域 */
.main-row {
  margin-bottom: 24px;
}

/* 图表卡片 */
.chart-card {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
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
}

.chart-error,
.list-error,
.list-empty,
.course-error,
.course-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748B;
  font-size: 14px;
}

/* 列表卡片 */
.list-card {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 24px;
}

.list-ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #F1F5F9;
  font-size: 14px;
  transition: background 150ms;
}

.list-item:hover {
  background: #F8FAFC;
}

.list-item:last-child {
  border-bottom: none;
}

.item-type {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
  flex-shrink: 0;
  font-weight: 500;
}

.item-type-homework { background: #FEF3C7; color: #D97706; }
.item-type-question { background: #FEF2F2; color: #DC2626; }
.item-type-course   { background: #EEF2FF; color: #4F46E5; }
.item-type-notice  { background: #ECFDF5; color: #059669; }
.item-type-default { background: #F1F5F9; color: #64748B; }

.item-title {
  flex: 1;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-time {
  font-size: 12px;
  color: #94A3B8;
  flex-shrink: 0;
}

/* 课程网格 */
.course-card {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  padding: 20px;
}

.course-card-item {
  border-radius: 12px;
  overflow: hidden;
  background: #F8FAFC;
  transition: box-shadow 200ms, transform 200ms;
  cursor: default;
}

.course-card-item:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.course-cover {
  height: 100px;
  background: #EEF2FF;
  overflow: hidden;
}

.course-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-cover-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94A3B8;
  font-size: 32px;
}

.course-info {
  padding: 12px;
}

.course-title {
  font-size: 14px;
  font-weight: 600;
  color: #1E293B;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}

.course-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.course-student {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #64748B;
}

/* Skeleton */
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

.skeleton-course {
  height: 150px;
  border-radius: 12px;
}

@media (max-width: 1024px) {
  .course-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .teacher-dashboard {
    padding: 16px;
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 20px 24px;
  }

  .welcome-right .welcome-title {
    font-size: 22px;
  }

  .course-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats-row :deep(.el-col) {
    margin-bottom: 12px;
  }
}
</style>