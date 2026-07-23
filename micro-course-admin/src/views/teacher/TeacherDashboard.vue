<!--
  教师端仪表盘
  路由路径: /teacher/dashboard
  Phase 9
  Author: Phase9-Development-Team
-->
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

    <!-- 统计加载失败提示 -->
    <div v-if="statsError" class="stats-error-tip">
      <el-icon><WarningFilled /></el-icon>
      <span>统计数据加载失败，请刷新重试</span>
    </div>

    <!-- 教师收益卡片 -->
    <div class="revenue-card" v-loading="revenueLoading">
      <div class="revenue-card-header">
        <span class="revenue-card-title">
          <el-icon><TrendCharts /></el-icon> 收益概况
        </span>
        <span class="revenue-subtitle">共 {{ revenueData.orderCount ?? 0 }} 笔付费订单</span>
      </div>
      <div class="revenue-card-body">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <div class="revenue-stat">
              <span class="revenue-label">总收入</span>
              <span class="revenue-value primary">¥{{ formatRevenue(revenueData.totalRevenue) }}</span>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="revenue-stat">
              <span class="revenue-label">平台分成 ({{ platformSharePercent }}%)</span>
              <span class="revenue-value muted">-¥{{ formatRevenue(revenueData.platformShare) }}</span>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="revenue-stat">
              <span class="revenue-label">教师净收入</span>
              <span class="revenue-value success">¥{{ formatRevenue(revenueData.netEarnings) }}</span>
            </div>
          </el-col>
        </el-row>

        <!-- 按课程分解 -->
        <template v-if="revenueData.courseBreakdown && revenueData.courseBreakdown.length > 0">
          <el-divider />
          <div class="revenue-subtitle">按课程</div>
          <div class="revenue-course-list">
            <div v-for="item in revenueData.courseBreakdown.slice(0, 5)" :key="item.courseId" class="revenue-course-item">
              <span class="rc-title" :title="item.courseTitle">{{ item.courseTitle }}</span>
              <span class="rc-orders">{{ item.orderCount }} 单</span>
              <span class="rc-revenue">¥{{ formatRevenue(item.revenue) }}</span>
              <span class="rc-earnings">净 ¥{{ formatRevenue(item.netEarnings) }}</span>
            </div>
          </div>
        </template>

        <!-- 最近交易 -->
        <template v-if="revenueData.recentTransactions && revenueData.recentTransactions.length > 0">
          <el-divider />
          <div class="revenue-subtitle">最近交易</div>
          <div class="revenue-tx-list">
            <div v-for="tx in revenueData.recentTransactions.slice(0, 5)" :key="tx.orderNo" class="revenue-tx-item">
              <span class="tx-course">{{ tx.courseTitle }}</span>
              <span class="tx-amount">¥{{ formatRevenue(tx.amount) }}</span>
              <span class="tx-time">{{ formatTxTime(tx.paidAt) }}</span>
            </div>
          </div>
        </template>

        <div v-else-if="!revenueLoading" class="revenue-empty">
          暂无收益数据
        </div>
      </div>
    </div>

    <!-- 教师评级卡片 -->
    <div class="rating-card" v-loading="ratingLoading">
      <div class="rating-card-header">
        <span class="rating-card-title">
          <el-icon><Medal /></el-icon> 我的教师等级
        </span>
        <span class="rating-update" v-if="ratingData.calculatedAt">更新于 {{ formatTime(ratingData.calculatedAt) }}</span>
      </div>
      <div class="rating-card-body">
        <div class="rating-tier-section">
          <div class="rating-tier-badge" :class="'tier-badge--' + (ratingData.tier || 'NEW').toLowerCase()">
            <el-icon class="tier-icon"><component :is="tierIconComponent" /></el-icon>
            <span class="tier-name">{{ ratingData.tierLabel || '新教师' }}</span>
          </div>
          <div class="rating-score-section">
            <div class="rating-score">{{ ratingData.ratingScore ?? 0 }}</div>
            <div class="rating-score-label">综合评分</div>
            <div class="rating-next" v-if="nextTierInfo">
              距 {{ nextTierInfo.label }} 还差 {{ nextTierInfo.gap }} 分
            </div>
          </div>
          <div class="rating-share">
            <span class="share-label">当前平台分成</span>
            <span class="share-value">{{ platformSharePercent }}%</span>
            <span class="share-hint">教师占 {{ 100 - platformSharePercent }}%</span>
          </div>
        </div>
        <el-divider />
        <div class="rating-metrics">
          <div class="metric-item">
            <span class="metric-value">{{ ratingData.avgStudentRating ?? '-' }}</span>
            <span class="metric-label">学生评价</span>
          </div>
          <div class="metric-item">
            <span class="metric-value">{{ ratingData.completionRate ?? 0 }}%</span>
            <span class="metric-label">完成率</span>
          </div>
          <div class="metric-item">
            <span class="metric-value">{{ ratingData.totalStudents ?? 0 }}</span>
            <span class="metric-label">学员数</span>
          </div>
          <div class="metric-item">
            <span class="metric-value">{{ ratingData.totalCourses ?? 0 }}</span>
            <span class="metric-label">课程数</span>
          </div>
        </div>
        <!-- 提升指南 -->
        <el-collapse v-if="ratingData.tier" class="rating-guide">
          <el-collapse-item name="guide">
            <template #title>
              <span class="collapse-title">
                <el-icon><InfoFilled /></el-icon>
                <span>如何提升等级？</span>
              </span>
            </template>
            <div class="guide-list">
              <div class="guide-item" :class="{ 'guide-done': (ratingData.avgStudentRating || 0) >= 4.5 }">
                <el-icon class="guide-icon" :class="{ 'guide-icon--done': (ratingData.avgStudentRating || 0) >= 4.5 }">
                  <CircleCheckFilled v-if="(ratingData.avgStudentRating || 0) >= 4.5" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <span class="guide-text">学生评价 ≥ 4.5 分（当前 {{ (ratingData.avgStudentRating || 0).toFixed(1) }}）</span>
              </div>
              <div class="guide-item" :class="{ 'guide-done': (ratingData.completionRate || 0) >= 80 }">
                <el-icon class="guide-icon" :class="{ 'guide-icon--done': (ratingData.completionRate || 0) >= 80 }">
                  <CircleCheckFilled v-if="(ratingData.completionRate || 0) >= 80" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <span class="guide-text">课程完成率 ≥ 80%（当前 {{ (ratingData.completionRate || 0).toFixed(1) }}%）</span>
              </div>
              <div class="guide-item" :class="{ 'guide-done': (ratingData.totalStudents || 0) >= 200 }">
                <el-icon class="guide-icon" :class="{ 'guide-icon--done': (ratingData.totalStudents || 0) >= 200 }">
                  <CircleCheckFilled v-if="(ratingData.totalStudents || 0) >= 200" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <span class="guide-text">累计学员 ≥ 200 人（当前 {{ ratingData.totalStudents || 0 }}）</span>
              </div>
              <div class="guide-item" :class="{ 'guide-done': (ratingData.totalCourses || 0) >= 5 }">
                <el-icon class="guide-icon" :class="{ 'guide-icon--done': (ratingData.totalCourses || 0) >= 5 }">
                  <CircleCheckFilled v-if="(ratingData.totalCourses || 0) >= 5" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <span class="guide-text">课程数 ≥ 5 门（当前 {{ ratingData.totalCourses || 0 }}）</span>
              </div>
            </div>
          </el-collapse-item>
          <el-collapse-item v-if="tierHistory.length > 0" name="history">
            <template #title>
              <span class="collapse-title">
                <el-icon><Tickets /></el-icon>
                <span>等级变更记录</span>
              </span>
            </template>
            <div class="history-list">
              <div v-for="h in tierHistory" :key="h.createdAt" class="history-item">
                <span class="history-arrow">{{ h.fromTierLabel }} → {{ h.toTierLabel }}</span>
                <span class="history-reason">{{ h.reason || '-' }}</span>
                <span class="history-time">{{ formatTxTime(h.createdAt) }}</span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <!-- 客户体验修复 v1.7.0: 教师快捷操作区,让"创建课程"等核心入口一目了然 -->
    <div class="quick-actions">
      <router-link to="/teacher/courses" class="quick-action-card" aria-label="创建新课程">
        <div class="qa-icon qa-icon-primary"><el-icon :size="22"><Plus /></el-icon></div>
        <div class="qa-info">
          <div class="qa-title">创建课程</div>
          <div class="qa-desc">新建课程、编辑章节、添加视频</div>
        </div>
      </router-link>
      <router-link to="/teacher/teaching-classes" class="quick-action-card" aria-label="管理教学班">
        <div class="qa-icon qa-icon-success"><el-icon :size="22"><OfficeBuilding /></el-icon></div>
        <div class="qa-info">
          <div class="qa-title">教学班</div>
          <div class="qa-desc">开班、结课、查看学员</div>
        </div>
      </router-link>
      <router-link to="/teacher/students" class="quick-action-card" aria-label="查看学员列表">
        <div class="qa-icon qa-icon-warning"><el-icon :size="22"><User /></el-icon></div>
        <div class="qa-info">
          <div class="qa-title">学员管理</div>
          <div class="qa-desc">学员成绩、批改作业</div>
        </div>
      </router-link>
      <router-link to="/teacher/questions" class="quick-action-card" aria-label="回复学员提问">
        <div class="qa-icon qa-icon-info"><el-icon :size="22"><ChatDotRound /></el-icon></div>
        <div class="qa-info">
          <div class="qa-title">学员提问</div>
          <div class="qa-desc">答疑、互动讨论</div>
        </div>
      </router-link>
    </div>

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
              <div v-else ref="studyChartRef" class="chart-container" role="img" aria-label="最近7天学情趋势图"></div>
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
              <div v-else ref="activeChartRef" class="chart-container" role="img" aria-label="学员活跃度分布图"></div>
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
            <div v-for="course in courses" :key="course.id" class="course-card-item" @click="$router.push(`/teacher/courses/${course.id}`)" style="cursor:pointer">
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
                  <el-rate v-if="course.rating != null" :model-value="course.rating" disabled show-score size="small" />
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
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Reading, User, Document, QuestionFilled, VideoPlay, WarningFilled, Finished, Star, Plus, OfficeBuilding, ChatDotRound, Medal, TrendCharts, CircleCheckFilled, CircleCloseFilled, InfoFilled, Tickets } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useUserStore } from '@/store/user'
import { getStats, getStudentActivity, getPendingTasks, getNotifications, getMyCourses } from '@/api/teacher'
import { getMyRating, getMyTierHistory } from '@/api/teacher-rating'
import { getTeacherRevenue } from '@/api/teacher'

const userStore = useUserStore()

// 欢迎信息
const now = new Date()
const welcomeDate = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 ${['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()]}`
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
const statsError = ref(false)
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

// 收益
const revenueLoading = ref(false)
const revenueData = ref({})
// 修复 P0-2: 优先用后端返回的 tierRate(从 platform_share_config 实时读)
// 后端没返回时用兜底映射(GOLD=25,与后端 V111 默认值一致)
// P1-1 修复: 删除重复的 platformShareRate，统一用 platformSharePercent
const platformSharePercent = computed(() => {
  if (ratingData.value.tierRate != null) {
    return Number(ratingData.value.tierRate)
  }
  const fallback = { PLATINUM: 20, GOLD: 25, SILVER: 28, BRONZE: 32, NEW: 35 }
  return fallback[ratingData.value.tier] || 30
})

function formatRevenue(val) {
  if (!val && val !== 0) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTxTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  if (date.getFullYear() === now.getFullYear()) {
    return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`
  }
  return `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`
}

// 评级
const ratingLoading = ref(false)
const ratingData = ref({})
const tierHistory = ref([])
const tierIconComponent = computed(() => {
  const tier = ratingData.value.tier
  if (tier === 'GOLD') return Star
  if (tier === 'PLATINUM') return Medal
  if (tier === 'SILVER') return Medal
  if (tier === 'BRONZE') return Medal
  return TrendCharts
})
const nextTierInfo = computed(() => {
  const score = Number(ratingData.value.ratingScore) || 0
  const thresholds = [
    { tier: 'BRONZE', label: '青铜', min: 0 },
    { tier: 'SILVER', label: '白银', min: 40 },
    { tier: 'GOLD', label: '黄金', min: 60 },
    { tier: 'PLATINUM', label: '铂金', min: 80 }
  ]
  const current = ratingData.value.tier
  const idx = thresholds.findIndex(t => t.tier === current)
  if (idx >= 0 && idx < thresholds.length - 1) {
    const next = thresholds[idx + 1]
    return { label: next.label, gap: (next.min - score).toFixed(1) }
  }
  if (idx === thresholds.length - 1) return null // 已达最高
  // NEW 等级
  return { label: '青铜', gap: (40 - score).toFixed(1) }
})
// P1-1 已移除重复的 platformShareRate，统一用 platformSharePercent (第 514 行)
// 定时刷新
const refreshInterval = ref(60000)
let refreshTimer = null

async function refreshAll() {
  await Promise.all([
    loadStats(),
    loadActivity(),
    loadTasks(),
    loadNotifications(),
    loadCourses(),
    loadRating(),
    loadRevenue()
  ])
}

// 加载统计数据
async function loadStats() {
  statsLoading.value = true
  statsError.value = false
  try {
    const res = await getStats()
    stats.value = res.data || {}
  } catch {
    statsError.value = true
    ElMessage.error('统计数据加载失败')
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
    // BUG-009 修复: nextTick + 关 loading 之后再 render
    activityLoading.value = false
    await nextTick()
    renderStudyChart(data)
    renderActiveChart(data)
  } catch {
    activityError.value = true
    ElMessage.error('学情数据加载失败')
    activityLoading.value = false
    await nextTick()
    renderStudyChart([])
    renderActiveChart([])
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
    color: ['#409eff', '#67c23a'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderRadius: 8,
      boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
      padding: [10, 14]
    },
    legend: { data: ['学习时长(分钟)', '完成率(%)'], bottom: 0, textStyle: { color: '#909399', fontSize: 12 } },
    grid: { left: '3%', right: '4%', bottom: '18%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#ebeef5' } }, axisLabel: { color: '#909399' } },
    yAxis: [
      { type: 'value', name: '分钟', minInterval: 1, axisLine: { show: false }, splitLine: { lineStyle: { color: '#ebeef5' } }, axisLabel: { color: '#909399' } },
      { type: 'value', name: '%', minInterval: 1, max: 100, axisLine: { show: false }, splitLine: { lineStyle: { color: '#ebeef5' } }, axisLabel: { color: '#909399' } }
    ],
    series: [
      {
        name: '学习时长(分钟)',
        type: 'line',
        smooth: true,
        lineStyle: { width: 3 },
        data: studyMinutes,
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '完成率(%)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: completionRate,
        itemStyle: { color: '#67c23a' },
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
    color: ['#409eff'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderRadius: 8,
      boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
      padding: [10, 14]
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#ebeef5' } }, axisLabel: { color: '#909399' } },
    yAxis: { type: 'value', name: '活跃学员', minInterval: 1, axisLine: { show: false }, splitLine: { lineStyle: { color: '#ebeef5' } }, axisLabel: { color: '#909399' } },
    series: [{
      name: '活跃学员',
      type: 'bar',
      data: activeUsers,
      itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
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
    ElMessage.error('待办数据加载失败')
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
    ElMessage.error('通知数据加载失败')
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
    ElMessage.error('课程数据加载失败')
  } finally {
    coursesLoading.value = false
  }
}

async function loadRating() {
  ratingLoading.value = true
  try {
    const [ratingRes, historyRes] = await Promise.all([
      getMyRating(),
      getMyTierHistory()
    ])
    ratingData.value = ratingRes.data || {}
    tierHistory.value = historyRes.data || []
  } catch {
    ratingData.value = {}
    tierHistory.value = []
  } finally {
    ratingLoading.value = false
  }
}

async function loadRevenue() {
  revenueLoading.value = true
  try {
    const res = await getTeacherRevenue()
    revenueData.value = res.data || {}
  } catch {
    revenueData.value = {}
  } finally {
    revenueLoading.value = false
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

let resizeTimer = null
function debouncedResizeCharts() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(resizeCharts, 200)
}

async function startRefresh() {
  await refreshAll()
  refreshTimer = setTimeout(startRefresh, refreshInterval.value)
}

onMounted(async () => {
  document.title = '教师工作台 - 微课平台'
  await refreshAll()
  window.addEventListener('resize', debouncedResizeCharts)
  refreshTimer = setTimeout(startRefresh, refreshInterval.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', debouncedResizeCharts)
  studyChart?.dispose()
  activeChart?.dispose()
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
  if (resizeTimer) {
    clearTimeout(resizeTimer)
    resizeTimer = null
  }
})
</script>

<style scoped>
.teacher-dashboard {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

/* 欢迎条 — 玻璃态 + 着色阴影 */
.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  padding: var(--space-6) var(--space-7);
  background: linear-gradient(135deg, var(--role-primary-darkest) 0%, var(--role-primary) 100%);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-tinted-lg), 0 12px 40px rgba(64, 158, 255, 0.2);
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
  color: rgba(255, 255, 255, 0.75);
  letter-spacing: var(--tracking-wide);
}

.welcome-greeting {
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: white;
  letter-spacing: var(--tracking-tight);
}

.greeting-name {
  color: white;
}

.greeting-suffix {
  color: rgba(255, 255, 255, 0.85);
}

.welcome-right {
  position: relative;
  z-index: 1;
}

.welcome-right .welcome-title {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  color: white;
  letter-spacing: var(--tracking-wider);
}

/* stat-row */
.stats-row {
  margin-bottom: var(--space-6);
}

.stats-error-tip {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-4);
  background: rgba(239, 68, 68, 0.08);
  color: var(--el-color-danger);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
}

/* 客户体验修复 v1.7.0: 教师快捷操作区,把"创建课程"等核心入口放在第一屏 */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}
.quick-action-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-tinted-sm);
  text-decoration: none;
  color: inherit;
  transition: all var(--duration-base) var(--ease-out);
  cursor: pointer;
  outline: none;
}
.quick-action-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-tinted-md);
  color: var(--el-color-primary);
}
.quick-action-card:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.qa-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.qa-icon-primary { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.qa-icon-success { background: var(--el-color-success-light-9); color: var(--el-color-success); }
.qa-icon-warning { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.qa-icon-info { background: var(--el-color-info-light-9); color: var(--el-color-info); }
.qa-info { min-width: 0; }
.qa-title { font-size: var(--text-base); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); margin-bottom: 2px; }
.qa-desc { font-size: var(--text-xs); color: var(--el-text-color-secondary); }
@media (max-width: 768px) {
  .quick-actions { grid-template-columns: 1fr 1fr; gap: var(--space-2); }
  .qa-title { font-size: var(--text-sm); }
}
@media (max-width: 480px) {
  .quick-actions { grid-template-columns: 1fr; }
}

/* stat-card — 着色阴影 + 更精细的悬停 */
.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  cursor: default;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md), var(--shadow-lg);
  border-color: var(--role-primary-light-7);
}

.stat-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.stat-card:hover .stat-icon-wrap {
  transform: scale(1.08);
  box-shadow: var(--shadow-tinted-sm);
}

.stat-icon-course { background: var(--role-primary-light-9); }
.stat-icon-course .stat-icon { color: var(--role-primary); }

.stat-icon-student { background: rgba(16, 185, 129, 0.08); }
.stat-icon-student .stat-icon { color: var(--el-color-success); }

.stat-icon-homework { background: rgba(245, 158, 11, 0.08); }
.stat-icon-homework .stat-icon { color: var(--el-color-warning); }

.stat-icon-question { background: rgba(239, 68, 68, 0.08); }
.stat-icon-question .stat-icon { color: var(--el-color-danger); }

.stat-icon-completion { background: rgba(16, 185, 129, 0.08); }
.stat-icon-completion .stat-icon { color: var(--el-color-success); }

.stat-icon-score { background: rgba(245, 158, 11, 0.08); }
.stat-icon-score .stat-icon { color: var(--el-color-warning); }

.stat-icon {
  font-size: 24px;
}

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
}

/* 主区域 */
.main-row {
  margin-bottom: var(--space-6);
}

/* 图表卡片 */
.chart-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  margin-bottom: var(--space-6);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.chart-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.card-header {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
  letter-spacing: var(--tracking-wide);
}

.chart-container {
  height: 300px;
  width: 100%;
  padding: var(--space-4);
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
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  margin-bottom: var(--space-6);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.list-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.list-ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.list-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: var(--text-base);
  transition: background var(--duration-fast) var(--ease-out);
}

.list-item:hover {
  background: var(--role-primary-light-9);
}

.list-item:last-child {
  border-bottom: none;
}

.item-type {
  font-size: var(--text-xs);
  padding: 2px var(--space-2);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  font-weight: var(--weight-medium);
  letter-spacing: var(--tracking-wide);
}

.item-type-homework { background: rgba(245, 158, 11, 0.1); color: var(--el-color-warning); }
.item-type-question { background: rgba(239, 68, 68, 0.1); color: var(--el-color-danger); }
.item-type-course   { background: var(--role-primary-light-9); color: var(--role-primary); }
.item-type-notice  { background: rgba(16, 185, 129, 0.1); color: var(--el-color-success); }
.item-type-default { background: var(--el-fill-color-light); color: var(--el-text-color-secondary); }

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
  font-variant-numeric: tabular-nums;
}

/* 课程网格 */
.course-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.course-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-5);
  padding: var(--space-5);
}

.course-card-item {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--el-fill-color-light);
  transition: box-shadow var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
  cursor: default;
  border: none;
}

.course-card-item:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
  border-color: var(--role-primary-light-7);
}

.course-cover {
  height: 120px;
  background: linear-gradient(135deg, var(--role-primary-light-9) 0%, var(--role-primary-light-7) 100%);
  overflow: hidden;
  position: relative;
}

.course-cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 60%, rgba(0, 0, 0, 0.05) 100%);
  pointer-events: none;
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
  color: var(--role-primary-light-3);
  font-size: 36px;
}

.course-info {
  padding: var(--space-3) var(--space-4);
}

.course-title {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: var(--space-2);
  letter-spacing: var(--tracking-tight);
}

.course-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.course-student {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

/* Skeleton */
.skeleton-value {
  width: 60px;
  height: 32px;
  border-radius: var(--radius-sm);
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

.skeleton-course {
  height: 170px;
  border-radius: var(--radius-lg);
}

@media (max-width: 1024px) {
  .course-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .teacher-dashboard {
    padding: var(--space-4);
  }

  .welcome-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
    padding: var(--space-5);
  }

  .welcome-right .welcome-title {
    font-size: var(--text-2xl);
  }

  .course-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--space-3);
  }

  .stats-row :deep(.el-col) {
    margin-bottom: var(--space-3);
  }

  .stat-card {
    padding: var(--space-4);
  }

  .stat-value {
    font-size: var(--text-2xl);
  }
}

/* 教师收益卡片 */
.revenue-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  margin-bottom: var(--space-6);
  overflow: hidden;
}
.revenue-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.revenue-card-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}
.revenue-subtitle {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}
.revenue-card-body {
  padding: var(--space-5);
}
.revenue-stat {
  text-align: center;
  padding: var(--space-4);
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-md);
}
.revenue-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-1);
}
.revenue-value {
  display: block;
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  font-variant-numeric: tabular-nums;
}
.revenue-value.primary { color: var(--role-primary); }
.revenue-value.muted { color: var(--el-text-color-secondary); }
.revenue-value.success { color: var(--el-color-success); }

.revenue-course-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.revenue-course-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
}
.rc-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rc-orders { color: var(--el-text-color-secondary); min-width: 40px; text-align: right; }
.rc-revenue { min-width: 70px; text-align: right; font-weight: var(--weight-medium); }
.rc-earnings { min-width: 70px; text-align: right; color: var(--el-color-success); }

.revenue-tx-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-top: var(--space-2);
}
.revenue-tx-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-1) var(--space-2);
  font-size: var(--text-sm);
}
.tx-course { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tx-amount { min-width: 70px; text-align: right; font-weight: var(--weight-medium); }
.tx-time { min-width: 50px; text-align: right; color: var(--el-text-color-secondary); font-size: 11px; }

.revenue-empty {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: var(--space-6);
}

/* 教师评级卡片 */
.rating-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  margin-bottom: var(--space-6);
  overflow: hidden;
}

.rating-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.rating-card-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.rating-update {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.rating-card-body {
  padding: var(--space-5);
}

.rating-tier-section {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.rating-tier-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-5);
  border-radius: var(--radius-lg);
  min-width: 100px;
}

.tier-icon { font-size: 32px; }
.tier-name { font-size: var(--text-sm); font-weight: var(--weight-semibold); }

.tier-badge--new { background: linear-gradient(135deg, #e8f5e9, #c8e6c9); color: #2e7d32; }
.tier-badge--bronze { background: linear-gradient(135deg, #fff3e0, #ffe0b2); color: #e65100; }
.tier-badge--silver { background: linear-gradient(135deg, #f5f5f5, #e0e0e0); color: #616161; }
.tier-badge--gold { background: linear-gradient(135deg, #fff8e1, #ffecb3); color: #f57f17; }
.tier-badge--platinum { background: linear-gradient(135deg, #e8eaf6, #c5cae9); color: #283593; }

.rating-score-section {
  text-align: center;
}

.rating-score {
  font-size: 36px;
  font-weight: var(--weight-bold);
  color: var(--role-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.rating-score-label {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
}

.rating-next {
  font-size: var(--text-xs);
  color: var(--el-color-warning);
  margin-top: var(--space-1);
}

.rating-share {
  text-align: center;
  padding: var(--space-2) var(--space-4);
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-md);
}

.share-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.share-value {
  display: block;
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  color: var(--el-color-danger);
  font-variant-numeric: tabular-nums;
}

.share-hint {
  display: block;
  font-size: 11px;
  color: var(--el-color-success);
}

.rating-metrics {
  display: flex;
  justify-content: space-around;
  gap: var(--space-4);
}

.metric-item {
  text-align: center;
}

.metric-value {
  display: block;
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.metric-label {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

/* 提升指南 */
.rating-guide { margin-top: var(--space-4); }
.rating-guide :deep(.el-collapse-item__header) { font-size: var(--text-sm); font-weight: var(--weight-medium); }
.collapse-title { display: inline-flex; align-items: center; gap: var(--space-2); }
.guide-list { display: flex; flex-direction: column; gap: var(--space-2); }
.guide-item { display: flex; align-items: center; gap: var(--space-2); font-size: var(--text-sm); padding: var(--space-1) 0; }
.guide-done { opacity: 0.6; }
.guide-icon { font-size: 14px; flex-shrink: 0; }
.guide-text { color: var(--el-text-color-regular); }
.guide-done .guide-text { color: var(--el-text-color-disabled); text-decoration: line-through; }

/* 等级历史 */
.history-list { display: flex; flex-direction: column; gap: var(--space-1); }
.history-item { display: flex; align-items: center; gap: var(--space-3); font-size: var(--text-sm); padding: var(--space-1) 0; }
.history-arrow { min-width: 120px; font-weight: var(--weight-medium); }
.history-reason { flex: 1; color: var(--el-text-color-secondary); }
.history-time { color: var(--el-text-color-disabled); font-size: 11px; }

@media (max-width: 768px) {
  .rating-tier-section {
    flex-direction: column;
    gap: var(--space-4);
  }
  .rating-metrics {
    flex-wrap: wrap;
  }
  .metric-item {
    flex: 1 1 45%;
  }
}
</style>
