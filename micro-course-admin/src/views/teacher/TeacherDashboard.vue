<template>
  <div class="teacher-dashboard">
    <!-- 顶部欢迎条 -->
    <div class="welcome-bar">
      <div class="welcome-left">
        <span class="welcome-date">{{ welcomeDate }}</span>
        <span class="welcome-weather">{{ weather }}</span>
      </div>
      <div class="welcome-greeting">
        <span class="greeting-name">{{ userName }}</span>
        <span class="greeting-suffix">{{ greeting }}</span>
      </div>
    </div>

    <!-- 4 个 stat-card -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap">
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
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap">
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
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap">
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
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon-wrap">
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
        </el-card>
      </el-col>
    </el-row>

    <!-- 中部主体区：左侧图表 + 右侧待办通知 -->
    <el-row :gutter="16" class="main-row">
      <!-- 左侧 60% -->
      <el-col :xs="24" :md="14">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最近 7 天学情</span>
            </div>
          </template>
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
        </el-card>
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>学员活跃度</span>
            </div>
          </template>
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
        </el-card>
      </el-col>

      <!-- 右侧 40% -->
      <el-col :xs="24" :md="10">
        <el-card class="list-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>待办</span>
            </div>
          </template>
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
                  <span class="item-type">{{ task.type }}</span>
                  <span class="item-title">{{ task.title }}</span>
                  <span class="item-time">{{ formatTime(task.createdAt) }}</span>
                </li>
              </ul>
            </template>
          </el-skeleton>
        </el-card>
        <el-card class="list-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最新通知</span>
            </div>
          </template>
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
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部：我教的课程 -->
    <el-card class="course-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>我教的课程</span>
        </div>
      </template>
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
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Reading, User, Document, QuestionFilled, VideoPlay, WarningFilled } from '@element-plus/icons-vue'
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
const stats = ref({ courseCount: 0, studentCount: 0, pendingHomework: 0, pendingQuestions: 0 })

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
    tooltip: { trigger: 'axis' },
    legend: { data: ['学习时长(分钟)', '完成率(%)'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '18%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '分钟', minInterval: 1 },
      { type: 'value', name: '%', minInterval: 1, max: 100 }
    ],
    series: [
      {
        name: '学习时长(分钟)',
        type: 'line',
        smooth: true,
        data: studyMinutes,
        itemStyle: { color: 'var(--role-primary)' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '完成率(%)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: completionRate,
        itemStyle: { color: '#67c23a' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.1 }
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
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', name: '活跃学员', minInterval: 1 },
    series: [{
      name: '活跃学员',
      type: 'bar',
      data: activeUsers,
      itemStyle: { color: 'var(--role-primary)', borderRadius: [4, 4, 0, 0] },
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
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  studyChart?.dispose()
  activeChart?.dispose()
})
</script>

<style scoped>
.teacher-dashboard {
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
  gap: var(--space-3);
}

.welcome-date {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.welcome-weather {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  padding: 2px 8px;
  background: var(--el-fill-color-light);
  border-radius: var(--radius-pill);
}

.welcome-greeting {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.greeting-name {
  color: var(--role-primary);
}

/* stat-row */
.stats-row {
  margin-bottom: var(--space-4);
}

/* stat-card */
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
  background: var(--role-primary-light-9);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon {
  font-size: 22px;
  color: var(--role-primary);
}

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

/* 主区域 */
.main-row {
  margin-bottom: var(--space-4);
}

/* 图表卡片 */
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
.list-empty,
.course-error,
.course-empty {
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
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: var(--text-sm);
}

.list-item:last-child {
  border-bottom: none;
}

.item-type {
  font-size: var(--text-xs);
  padding: 1px 6px;
  background: var(--role-primary-light-9);
  color: var(--role-primary);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.item-title {
  flex: 1;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-time {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}

/* 课程网格 */
.course-card {
  margin-bottom: var(--space-4);
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}

.course-card-item {
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--el-fill-color-lighter);
  transition: box-shadow var(--duration-base) var(--ease-out);
  cursor: default;
}

.course-card-item:hover {
  box-shadow: var(--shadow-lg);
}

.course-cover {
  height: 100px;
  background: var(--el-fill-color);
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
  color: var(--el-text-color-placeholder);
  font-size: 32px;
}

.course-info {
  padding: var(--space-3);
}

.course-title {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: var(--space-2);
}

.course-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.course-student {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
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

.skeleton-course {
  height: 150px;
  border-radius: var(--radius-md);
}

@media (max-width: 1024px) {
  .course-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .teacher-dashboard {
    padding: var(--space-3);
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }

  .course-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats-row .el-col {
    margin-bottom: var(--space-3);
  }
}
</style>