<!--
  学习中心
  路由路径: /student/learning
  Phase 5
  Author: jackie
-->
<template>
  <div class="learning-center">
    <!-- ========== PC 布局 (≥769px) ========== -->
    <div v-if="!isMobile" class="pc-layout">
      <!-- 欢迎栏 -->
      <div class="welcome-bar">
        <div class="welcome-left">
          <span class="welcome-text">你好，{{ username }}</span>
        </div>
        <div class="welcome-right">
          <span class="badge-date">
            <el-icon><Calendar /></el-icon>
            {{ currentDate }}
          </span>
          <span class="badge-weather">
            <el-icon><Sunny /></el-icon>
            晴 26°C
          </span>
          <span class="badge-motto">
            <el-icon><Star /></el-icon>
            学无止境
          </span>
        </div>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loading" class="stats-row">
        <el-card v-for="i in 4" :key="i" class="stat-card" shadow="hover">
          <el-skeleton :rows="1" animated />
        </el-card>
      </div>

      <!-- 4 统计卡片 -->
      <div v-else class="stats-row">
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.totalHours }}</div>
            <div class="stat-card-label">累计学习时长</div>
          </el-card>
        </div>
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.completedCourses }}</div>
            <div class="stat-card-label">已完成课程</div>
          </el-card>
        </div>
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.certificates }}</div>
            <div class="stat-card-label">获得证书</div>
          </el-card>
        </div>
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.points }}</div>
            <div class="stat-card-label">积分</div>
          </el-card>
        </div>
      </div>

      <!-- 继续学习 -->
      <div class="continue-learning">
        <el-card shadow="hover" class="continue-card">
          <div class="continue-card-inner">
            <div class="continue-info">
              <div class="continue-label">继续学习</div>
              <div class="continue-title">{{ recentCourse.title }}</div>
              <div class="continue-meta">
                <span class="continue-chapter">第{{ recentCourse.currentChapter }}章</span>
                <span class="continue-progress-label">学习进度</span>
              </div>
              <el-progress
                :percentage="recentCourse.progress"
                :stroke-width="8"
                :show-text="true"
                :format="(val) => val + '%'"
              />
            </div>
            <div class="continue-cover">
              <img :src="recentCourse.cover" :alt="recentCourse.title" class="cover-img" />
            </div>
          </div>
        </el-card>
      </div>

      <!-- 两栏：本周学习 + 学习日历 -->
      <div class="chart-calendar-row">
        <!-- 本周学习 -->
        <div class="chart-section">
          <el-card shadow="hover" class="chart-card">
            <template #header>
              <div class="card-header-title">本周学习时长</div>
            </template>
            <div v-loading="chartLoading" class="chart-container">
              <div v-if="chartData.length === 0" class="empty-wrap">
                <el-empty description="暂无学习数据" :image-size="80" />
              </div>
              <div v-else ref="chartRef" class="echarts-container"></div>
            </div>
          </el-card>
        </div>

        <!-- 学习日历 -->
        <div class="calendar-section">
          <el-card shadow="hover" class="calendar-card">
            <template #header>
              <div class="card-header-title">学习日历（近30天）</div>
            </template>
            <div class="heatmap-wrapper">
              <div class="heatmap-grid">
                <div class="heatmap-row" v-for="(week, wi) in heatmapData" :key="wi">
                  <div
                    v-for="(day, di) in week"
                    :key="di"
                    class="heatmap-cell"
                    :class="getHeatmapCellClass(day.level)"
                    :title="day.date + '：' + day.minutes + '分钟'"
                  >
                    <span class="cell-day">{{ day.day }}</span>
                  </div>
                </div>
              </div>
              <div class="heatmap-legend">
                <span class="legend-label">少</span>
                <div class="legend-cell level-0"></div>
                <div class="legend-cell level-1"></div>
                <div class="legend-cell level-2"></div>
                <div class="legend-cell level-3"></div>
                <span class="legend-label">多</span>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 推荐课程 -->
      <div class="recommendations-section">
        <div class="section-title">推荐课程</div>
        <div class="recommendations-grid">
          <el-card
            v-for="course in recommendations"
            :key="course.id"
            shadow="hover"
            class="recommend-card"
          >
            <div class="recommend-cover-wrap">
              <img :src="course.cover" :alt="course.title" class="recommend-cover" />
              <el-tag class="recommend-tag" type="primary" size="small">{{ course.tag }}</el-tag>
            </div>
            <div class="recommend-info">
              <div class="recommend-title">{{ course.title }}</div>
              <div class="recommend-meta">
                <span class="recommend-author">{{ course.author }}</span>
                <span class="recommend-students">{{ course.students }}人在学</span>
              </div>
              <div class="recommend-footer">
                <span class="recommend-rating">
                  <el-icon><Star /></el-icon>
                  {{ course.rating }}
                </span>
                <el-button type="primary" size="small" plain>开始学习</el-button>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 我的徽章 -->
      <div class="badges-section">
        <div class="section-title">我的徽章</div>
        <div class="badges-row">
          <div
            v-for="badge in badges"
            :key="badge.id"
            class="badge-item"
            :class="{ 'badge-locked': !badge.earned }"
          >
            <el-tooltip :content="badge.name" placement="top">
              <div class="badge-circle">
                <el-icon class="badge-icon" :size="24"><Medal /></el-icon>
              </div>
            </el-tooltip>
            <span class="badge-name">{{ badge.name }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== H5 布局 (≤768px) ========== -->
    <div v-else class="h5-layout">
      <!-- 欢迎栏 -->
      <div class="welcome-bar h5-welcome">
        <span class="welcome-text">你好，{{ username }}</span>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loading" class="stats-row h5-stats">
        <el-card v-for="i in 4" :key="i" class="stat-card" shadow="hover">
          <el-skeleton :rows="1" animated />
        </el-card>
      </div>

      <!-- 4 统计卡片 (2×2) -->
      <div v-else class="stats-row h5-stats">
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.totalHours }}</div>
            <div class="stat-card-label">累计学习时长</div>
          </el-card>
        </div>
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.completedCourses }}</div>
            <div class="stat-card-label">已完成课程</div>
          </el-card>
        </div>
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.certificates }}</div>
            <div class="stat-card-label">获得证书</div>
          </el-card>
        </div>
        <div class="stat-card">
          <el-card shadow="hover">
            <div class="stat-card-value">{{ stats.points }}</div>
            <div class="stat-card-label">积分</div>
          </el-card>
        </div>
      </div>

      <!-- 继续学习 -->
      <div class="continue-learning h5-continue">
        <el-card shadow="hover" class="continue-card">
          <div class="continue-card-inner h5-continue-inner">
            <div class="continue-info">
              <div class="continue-label">继续学习</div>
              <div class="continue-title">{{ recentCourse.title }}</div>
              <el-progress
                :percentage="recentCourse.progress"
                :stroke-width="6"
                :show-text="true"
                :format="(val) => val + '%'"
              />
            </div>
          </div>
        </el-card>
      </div>

      <!-- 本周学习 -->
      <div class="chart-section h5-chart">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header-title">本周学习时长</div>
          </template>
          <div v-loading="chartLoading" class="chart-container h5-chart-container">
            <div v-if="chartData.length === 0" class="empty-wrap">
              <el-empty description="暂无学习数据" :image-size="60" />
            </div>
            <div v-else ref="chartRefH5" class="echarts-container"></div>
          </div>
        </el-card>
      </div>

      <!-- 推荐课程 (单列) -->
      <div class="recommendations-section h5-recommend">
        <div class="section-title">推荐课程</div>
        <div class="recommendations-list">
          <el-card
            v-for="course in recommendations"
            :key="course.id"
            shadow="hover"
            class="recommend-card recommend-card-list"
          >
            <div class="recommend-list-inner">
              <img :src="course.cover" :alt="course.title" class="recommend-cover-small" />
              <div class="recommend-info">
                <div class="recommend-title">{{ course.title }}</div>
                <div class="recommend-meta">
                  <span class="recommend-author">{{ course.author }}</span>
                  <span class="recommend-students">{{ course.students }}人在学</span>
                </div>
                <div class="recommend-footer">
                  <span class="recommend-rating">
                    <el-icon><Star /></el-icon>
                    {{ course.rating }}
                  </span>
                  <el-button type="primary" size="small" plain>开始学习</el-button>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 我的徽章 (横向滚动) -->
      <div class="badges-section h5-badges">
        <div class="section-title">我的徽章</div>
        <div class="badges-scroll">
          <div
            v-for="badge in badges"
            :key="badge.id"
            class="badge-item"
            :class="{ 'badge-locked': !badge.earned }"
          >
            <el-tooltip :content="badge.name" placement="top">
              <div class="badge-circle">
                <el-icon class="badge-icon" :size="24"><Medal /></el-icon>
              </div>
            </el-tooltip>
            <span class="badge-name">{{ badge.name }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Calendar, Sunny, Star, Medal } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getCompletion } from '@/api/learning-progress'
import { getMyEnrollments } from '@/api/enrollment'
import { getMyBadges } from '@/api/badge'
import { getMyCheckIns } from '@/api/checkin'
import { getAccuracyTrend } from '@/api/exercise-record'

// ---------------------------------------------------------------------------
// Store & Router
// ---------------------------------------------------------------------------
const userStore = useUserStore()

// ---------------------------------------------------------------------------
// 响应式
// ---------------------------------------------------------------------------
const isMobile = ref(window.innerWidth <= 768)

function onResize() {
  isMobile.value = window.innerWidth <= 768
}

onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

// ---------------------------------------------------------------------------
// 用户信息
// ---------------------------------------------------------------------------
const username = computed(() => userStore.userInfo?.realName || userStore.userInfo?.username || '同学')
const currentDate = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
})

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------
const loading = ref(true)
const chartLoading = ref(true)

// ---------------------------------------------------------------------------
// 统计数据
// ---------------------------------------------------------------------------
const stats = ref({
  totalHours: '0小时',
  completedCourses: 0,
  certificates: 0,
  points: 0
})

// ---------------------------------------------------------------------------
// 最近课程
// ---------------------------------------------------------------------------
const recentCourse = ref({
  title: 'Java 程序设计基础',
  currentChapter: 3,
  progress: 0,
  cover: 'https://picsum.photos/seed/java/300/180'
})

// ---------------------------------------------------------------------------
// 图表数据
// ---------------------------------------------------------------------------
const chartData = ref([])
const chartRef = ref(null)
const chartRefH5 = ref(null)
const accuracyMode = ref(false)  // 正确率趋势模式标志
let chartInstance = null

// ---------------------------------------------------------------------------
// 热力图数据 (30天) — 从真实打卡 API 获取
// ---------------------------------------------------------------------------
const heatmapData = ref([])

async function loadHeatmap() {
  try {
    const { data } = await getMyCheckIns({ days: 30 })
    const checkIns = Array.isArray(data) ? data : []

    // 建立 date → minutes 映射
    const minutesMap = {}
    checkIns.forEach(record => {
      if (record.checkInAt) {
        const d = new Date(record.checkInAt)
        const dateKey = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
        minutesMap[dateKey] = (minutesMap[dateKey] || 0) + (record.duration || record.minutes || 0)
      }
    })

    const today = new Date()
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1)
    const startDayOfWeek = firstDay.getDay() || 7
    const daysInMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate()

    const weeks = []
    let dayIndex = 1

    for (let w = 0; w < 6; w++) {
      const week = []
      for (let d = 0; d < 7; d++) {
        if (w === 0 && d < startDayOfWeek - 1) {
          week.push({ day: '', date: '', minutes: 0, level: 0 })
        } else if (dayIndex > daysInMonth) {
          week.push({ day: '', date: '', minutes: 0, level: 0 })
        } else {
          const date = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(dayIndex).padStart(2, '0')}`
          const minutes = minutesMap[date] || 0
          const level = minutes === 0 ? 0 : minutes < 30 ? 1 : minutes < 60 ? 2 : 3
          week.push({ day: dayIndex, date, minutes, level })
          dayIndex++
        }
      }
      weeks.push(week)
      if (dayIndex > daysInMonth) break
    }

    heatmapData.value = weeks
  } catch {
    heatmapData.value = []
  }
}

function getHeatmapCellClass(level) {
  return `level-${level}`
}

// ---------------------------------------------------------------------------
// 推荐课程
// ---------------------------------------------------------------------------
const recommendations = ref([
  {
    id: 1,
    title: 'Python 数据分析实战',
    cover: 'https://picsum.photos/seed/python/300/180',
    tag: '数据分析',
    author: '李明教授',
    students: 1234,
    rating: 4.8
  },
  {
    id: 2,
    title: '机器学习入门与实践',
    cover: 'https://picsum.photos/seed/ml/300/180',
    tag: '人工智能',
    author: '王强博士',
    students: 2345,
    rating: 4.9
  },
  {
    id: 3,
    title: 'Web 前端开发进阶',
    cover: 'https://picsum.photos/seed/web/300/180',
    tag: '前端开发',
    author: '张老师',
    students: 3456,
    rating: 4.7
  }
])

// ---------------------------------------------------------------------------
// 徽章
// ---------------------------------------------------------------------------
const badges = ref([
  { id: 1, name: '初露锋芒', earned: true },
  { id: 2, name: '学习达人', earned: true },
  { id: 3, name: '全勤徽章', earned: true },
  { id: 4, name: '知识探索者', earned: false },
  { id: 5, name: '优秀学员', earned: false }
])

// ---------------------------------------------------------------------------
// API 获取函数
// ---------------------------------------------------------------------------
async function getStats() {
  try {
    const userId = userStore.userInfo?.id
    const [completionData, enrollmentData] = await Promise.all([
      getCompletion({ userId }),
      getMyEnrollments(userId)
    ])

    const enrollments = Array.isArray(enrollmentData?.data) ? enrollmentData.data : []
    const completedCourses = enrollments.filter(e => e.completed).length

    // 从 completionData 中汇总总学习时长（分钟转小时）
    const completionMap = completionData?.data || {}
    let totalMinutes = 0
    Object.values(completionMap).forEach(v => {
      if (v && typeof v === 'object' && v.totalWatchTime) {
        totalMinutes += v.totalWatchTime || 0
      }
    })
    const totalHours = totalMinutes > 0 ? `${Math.round(totalMinutes / 60)}小时` : '0小时'

    stats.value = {
      totalHours,
      completedCourses,
      certificates: 0,   // 后端暂无证书 API
      points: 0          // 后端暂无积分 API
    }
  } catch {
    stats.value = {
      totalHours: '0小时',
      completedCourses: 0,
      certificates: 0,
      points: 0
    }
  }
}

async function getRecent() {
  try {
    const userId = userStore.userInfo?.id
    const { data } = await getMyEnrollments(userId)
    const enrollments = Array.isArray(data) ? data : []

    // 取第一个进行中的课程作为"继续学习"
    const inProgress = enrollments.find(e => !e.completed && e.progress > 0)
    if (inProgress) {
      recentCourse.value = {
        title: inProgress.courseTitle || inProgress.title || '课程',
        currentChapter: 1,
        progress: inProgress.progress || 0,
        cover: inProgress.courseCover || inProgress.coverUrl || 'https://picsum.photos/seed/course/300/180'
      }
    }
  } catch {
    // 保持默认 mock
  }
}

async function getChart() {
  try {
    // 优先尝试正确率趋势 API（后端 Agent 1 在实现中）
    // 如果 API 不存在则 fallback 到本周打卡数据
    accuracyMode.value = false
    try {
      const trendRes = await getAccuracyTrend({ days: 7 })
      const trendData = trendRes?.data
      if (Array.isArray(trendData) && trendData.length > 0) {
        const dayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
        chartData.value = dayLabels.map((day, idx) => {
          const found = trendData.find(t => {
            const tDay = t.day ?? t.date
            return typeof tDay === 'number' ? tDay === idx + 1 : tDay === day
          })
          return {
            day,
            hours: found ? (found.accuracy ?? found.correctRate ?? 0) : 0
          }
        })
        accuracyMode.value = true
      }
    } catch {
      // API 不存在，继续用打卡数据
    }

    if (!accuracyMode.value) {
      const { data } = await getMyCheckIns({ days: 7 })
      const checkIns = Array.isArray(data) ? data : []

      const dayMap = {}
      const dayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
      dayLabels.forEach((label, idx) => {
        dayMap[idx] = { day: label, hours: 0 }
      })

      checkIns.forEach(record => {
        if (record.checkInAt) {
          const d = new Date(record.checkInAt)
          const dayOfWeek = d.getDay()
          const idx = dayOfWeek === 0 ? 6 : dayOfWeek - 1
          if (dayMap[idx]) {
            dayMap[idx].hours += (record.duration || record.minutes || 0) / 60
          }
        }
      })

      chartData.value = Object.values(dayMap)
    }
  } catch {
    chartData.value = []
  }
}

async function getRecommendations() {
  try {
    const userId = userStore.userInfo?.id
    const { data } = await getMyEnrollments(userId)
    const enrollments = Array.isArray(data) ? data : []

    // 取进行中的课程作为推荐
    const inProgress = enrollments
      .filter(e => !e.completed)
      .slice(0, 3)
      .map(e => ({
        id: e.courseId,
        title: e.courseTitle || e.title,
        cover: e.courseCover || e.coverUrl,
        tag: '学习中',
        author: e.teacherName || '',
        students: e.studentCount || 0,
        rating: e.avgRating || 0
      }))

    if (inProgress.length > 0) {
      recommendations.value = inProgress
    }
  } catch {
    // 保持默认 mock
  }
}

async function getBadges() {
  try {
    const { data } = await getMyBadges()
    const badgeList = Array.isArray(data) ? data : []

    badges.value = badgeList.map(b => ({
      id: b.id,
      name: b.name || b.badgeName || '徽章',
      earned: b.earned !== false
    }))
  } catch {
    // 保持默认 mock
  }
}

// ---------------------------------------------------------------------------
// 初始化图表
// ---------------------------------------------------------------------------
function initChart(containerRef) {
  if (!containerRef || chartData.value.length === 0) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(containerRef)

  const option = {
    title: {
      text: accuracyMode.value ? '正确率趋势' : '本周学习时长',
      textStyle: {
        fontSize: 14,
        fontWeight: 600,
        color: 'var(--el-text-color-primary)'
      },
      left: 0,
      top: 0
    },
    tooltip: {
      trigger: 'axis',
      formatter: accuracyMode.value ? '{b}: {c}%' : '{b}: {c} 小时'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: chartData.value.map((d) => d.day),
      axisLine: { lineStyle: { color: 'var(--el-border-color)' } },
      axisLabel: { color: 'var(--el-text-color-secondary)', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      name: '小时',
      nameTextStyle: { color: 'var(--el-text-color-secondary)', fontSize: 12 },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'var(--el-border-color-lighter)', type: 'dashed' } },
      axisLabel: { color: 'var(--el-text-color-secondary)' }
    },
    series: [
      {
        data: chartData.value.map((d) => d.hours),
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: 'var(--role-primary)', width: 2 },
        itemStyle: { color: 'var(--role-primary)', borderColor: 'var(--el-color-white)', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(99, 102, 241, 0.3)' },
              { offset: 1, color: 'rgba(99, 102, 241, 0.02)' }
            ]
          }
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

// ---------------------------------------------------------------------------
// 加载数据
// ---------------------------------------------------------------------------
async function loadData() {
  loading.value = true
  chartLoading.value = true
  try {
    await Promise.all([getStats(), getRecent(), getChart(), getRecommendations(), getBadges(), loadHeatmap()])
  } finally {
    loading.value = false
    chartLoading.value = false
  }
}

// ---------------------------------------------------------------------------
// 监听 chartData 变化后初始化图表
// ---------------------------------------------------------------------------
watch(chartData, async () => {
  await nextTick()
  if (!isMobile.value && chartRef.value) {
    initChart(chartRef.value)
  } else if (isMobile.value && chartRefH5.value) {
    initChart(chartRefH5.value)
  }
})

// ---------------------------------------------------------------------------
// 监听 isMobile 变化，重新初始化图表
// ---------------------------------------------------------------------------
watch(isMobile, async (mobile) => {
  await nextTick()
  if (!mobile && chartRef.value) {
    initChart(chartRef.value)
  } else if (mobile && chartRefH5.value) {
    initChart(chartRefH5.value)
  }
})

// ---------------------------------------------------------------------------
// 窗口 resize 时重绘图表
// ---------------------------------------------------------------------------
function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(async () => {
  await loadData()
  await nextTick()
  if (!isMobile.value && chartRef.value) {
    initChart(chartRef.value)
  } else if (isMobile.value && chartRefH5.value) {
    initChart(chartRefH5.value)
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
/* ---------------------------------------------------------------------------
   基础容器
   --------------------------------------------------------------------------- */
.learning-center {
  padding: var(--space-5);
  animation: fadeIn var(--duration-slow) var(--ease-out);
}

/* ---------------------------------------------------------------------------
   欢迎栏
   --------------------------------------------------------------------------- */
.welcome-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
  padding: var(--space-4) var(--space-5);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.welcome-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.welcome-text {
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.welcome-right {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.badge-date,
.badge-weather,
.badge-motto {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

/* ---------------------------------------------------------------------------
   统计卡片
   --------------------------------------------------------------------------- */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.stat-card .el-card {
  border-radius: var(--radius-lg);
}

/* ---------------------------------------------------------------------------
   继续学习卡片
   --------------------------------------------------------------------------- */
.continue-card {
  border-radius: var(--radius-lg);
}

.continue-card-inner {
  display: flex;
  gap: var(--space-5);
}

.continue-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.continue-label {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.continue-title {
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.continue-meta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.continue-chapter {
  color: var(--role-primary);
}

.continue-progress-label {
  color: var(--el-text-color-secondary);
}

.continue-cover {
  width: 240px;
  height: 140px;
  border-radius: var(--radius-md);
  overflow: hidden;
  flex-shrink: 0;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ---------------------------------------------------------------------------
   图表 + 日历行
   --------------------------------------------------------------------------- */
.chart-calendar-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.chart-section,
.calendar-section {
  display: flex;
  flex-direction: column;
}

.chart-card,
.calendar-card {
  border-radius: var(--radius-lg);
  flex: 1;
}

.card-header-title {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.chart-container {
  height: 260px;
}

.echarts-container {
  width: 100%;
  height: 100%;
}

.empty-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

/* ---------------------------------------------------------------------------
   热力图
   --------------------------------------------------------------------------- */
.heatmap-wrapper {
  padding: var(--space-3) 0;
}

.heatmap-grid {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.heatmap-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 3px;
}

.heatmap-cell {
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  cursor: default;
  transition: transform var(--duration-fast) var(--ease-out);
}

.heatmap-cell:hover {
  transform: scale(1.1);
}

.heatmap-cell.level-0 {
  background: var(--role-primary-light-9);
}

.heatmap-cell.level-1 {
  background: var(--role-primary-light-7);
}

.heatmap-cell.level-2 {
  background: var(--role-primary-light-5);
}

.heatmap-cell.level-3 {
  background: var(--role-primary);
  color: var(--el-color-white);
}

.cell-day {
  line-height: 1;
}

.heatmap-legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-1);
  margin-top: var(--space-3);
}

.legend-label {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.legend-cell {
  width: 14px;
  height: 14px;
  border-radius: var(--radius-sm);
}

.legend-cell.level-0 {
  background: var(--role-primary-light-9);
}

.legend-cell.level-1 {
  background: var(--role-primary-light-7);
}

.legend-cell.level-2 {
  background: var(--role-primary-light-5);
}

.legend-cell.level-3 {
  background: var(--role-primary);
}

/* ---------------------------------------------------------------------------
   推荐课程
   --------------------------------------------------------------------------- */
.section-title {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-4);
}

.recommendations-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.recommend-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.recommend-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg) !important;
}

.recommend-cover-wrap {
  position: relative;
  height: 160px;
  overflow: hidden;
}

.recommend-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recommend-tag {
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
}

.recommend-info {
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.recommend-title {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-meta {
  display: flex;
  justify-content: space-between;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.recommend-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-2);
}

.recommend-rating {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  color: var(--role-primary);
  font-weight: var(--weight-medium);
}

/* ---------------------------------------------------------------------------
   徽章
   --------------------------------------------------------------------------- */
.badges-section {
  margin-bottom: var(--space-5);
}

.badges-row {
  display: flex;
  gap: var(--space-5);
  flex-wrap: wrap;
}

.badge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
}

.badge-circle {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-circle);
  background: var(--role-primary-light-9);
  border: 2px solid var(--role-primary-light-5);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.badge-circle:hover {
  transform: scale(1.08);
  box-shadow: var(--shadow-md);
}

.badge-icon {
  color: var(--role-primary);
}

.badge-locked .badge-circle {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color);
  opacity: 0.5;
}

.badge-locked .badge-icon {
  color: var(--el-text-color-disabled);
}

.badge-name {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  text-align: center;
}

/* ---------------------------------------------------------------------------
   H5 布局
   --------------------------------------------------------------------------- */
.h5-layout {
  padding: var(--space-3);
  animation: fadeIn var(--duration-slow) var(--ease-out);
}

.h5-welcome {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
}

.h5-stats {
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.h5-continue {
  margin-bottom: var(--space-4);
}

.h5-continue-inner {
  flex-direction: column;
}

.h5-continue .continue-cover {
  display: none;
}

.h5-chart {
  margin-bottom: var(--space-4);
}

.h5-chart-container {
  height: 200px;
}

.h5-recommend {
  margin-bottom: var(--space-4);
}

.recommendations-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.recommend-card-list {
  overflow: visible;
}

.recommend-list-inner {
  display: flex;
  gap: var(--space-3);
}

.recommend-cover-small {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-md);
  object-fit: cover;
  flex-shrink: 0;
}

.recommend-info {
  flex: 1;
}

.h5-badges {
  margin-bottom: var(--space-4);
}

.badges-scroll {
  display: flex;
  gap: var(--space-4);
  overflow-x: auto;
  padding-bottom: var(--space-2);
  -webkit-overflow-scrolling: touch;
}

.badges-scroll::-webkit-scrollbar {
  display: none;
}

/* ---------------------------------------------------------------------------
   响应式
   --------------------------------------------------------------------------- */
@media (max-width: 768px) {
  .learning-center {
    padding: var(--space-3);
  }

  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-calendar-row {
    grid-template-columns: 1fr;
  }

  .recommendations-grid {
    grid-template-columns: 1fr;
  }

  .continue-cover {
    width: 100%;
    height: 160px;
  }

  .continue-card-inner {
    flex-direction: column;
  }
}
</style>