<!--
  管理端数据看板
  /admin/dashboard
  Phase 7 - 管理后台补齐
-->
<template>
  <div class="dashboard-container">
    <!-- 统计卡片区 -->
    <div class="stat-cards">
      <el-card class="stat-card" shadow="never">
        <div class="stat-icon user-icon"><el-icon :size="28"><User /></el-icon></div>
        <div class="stat-content">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ overview.userTotal ?? 0 }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-icon active-icon"><el-icon :size="28"><UserFilled /></el-icon></div>
        <div class="stat-content">
          <div class="stat-label">活跃用户(7d)</div>
          <div class="stat-value">{{ overview.activeUsers7d ?? 0 }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-icon course-icon"><el-icon :size="28"><Reading /></el-icon></div>
        <div class="stat-content">
          <div class="stat-label">课程总数</div>
          <div class="stat-value">{{ overview.courseTotal ?? 0 }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-icon enroll-icon"><el-icon :size="28"><Tickets /></el-icon></div>
        <div class="stat-content">
          <div class="stat-label">报名总数</div>
          <div class="stat-value">{{ overview.enrollmentTotal ?? 0 }}</div>
        </div>
      </el-card>
    </div>

    <!-- 图表区 -->
    <div class="charts-grid">
      <el-card class="chart-card" shadow="never">
        <template #header><span>用户增长趋势</span></template>
        <div ref="userTrendRef" class="chart-container"></div>
      </el-card>
      <el-card class="chart-card" shadow="never">
        <template #header><span>课程增长趋势</span></template>
        <div ref="courseTrendRef" class="chart-container"></div>
      </el-card>
      <el-card class="chart-card" shadow="never">
        <template #header><span>课程状态分布</span></template>
        <div ref="courseDistRef" class="chart-container"></div>
      </el-card>
      <el-card class="chart-card" shadow="never">
        <template #header><span>学习行为</span></template>
        <div ref="learningRef" class="chart-container"></div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
/**
 * 管理端数据看板
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { User, UserFilled, Reading, Tickets } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getOverview, getUserTrend, getCourseTrend, getCourseDistribution, getLearningBehavior } from '@/api/admin-stats'

// 概览数据
const overview = reactive({
  userTotal: 0,
  activeUsers7d: 0,
  courseTotal: 0,
  enrollmentTotal: 0
})

// 图表 DOM ref
const userTrendRef = ref(null)
const courseTrendRef = ref(null)
const courseDistRef = ref(null)
const learningRef = ref(null)

// 图表实例
let userTrendChart = null
let courseTrendChart = null
let courseDistChart = null
let learningChart = null

// 初始化用户增长趋势图
function initUserTrendChart(data) {
  if (!userTrendRef.value) return
  userTrendChart = echarts.init(userTrendRef.value)
  const dates = data.map(item => item.date)
  const counts = data.map(item => item.count)
  userTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '新增用户',
      type: 'line',
      data: counts,
      smooth: true,
      areaStyle: { color: 'rgba(64,158,255,0.15)' },
      lineStyle: { color: '#409eff', width: 2 },
      itemStyle: { color: '#409eff' }
    }]
  })
}

// 初始化课程增长趋势图
function initCourseTrendChart(data) {
  if (!courseTrendRef.value) return
  courseTrendChart = echarts.init(courseTrendRef.value)
  const dates = data.map(item => item.date)
  const counts = data.map(item => item.count)
  courseTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '新增课程',
      type: 'line',
      data: counts,
      smooth: true,
      areaStyle: { color: 'rgba(103,194,58,0.15)' },
      lineStyle: { color: '#67c23a', width: 2 },
      itemStyle: { color: '#67c23a' }
    }]
  })
}

// 初始化课程状态分布饼图
function initCourseDistChart(data) {
  if (!courseDistRef.value) return
  courseDistChart = echarts.init(courseDistRef.value)
  const legendMap = { draft: '草稿', underReview: '审核中', published: '已发布', offline: '已下线' }
  const pieData = Object.entries(data).map(([key, value]) => ({
    name: legendMap[key] || key,
    value
  }))
  courseDistChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}' },
      data: pieData,
      color: ['#909399', '#e6a23c', '#67c23a', '#f56c6c']
    }]
  })
}

// 初始化学习行为柱状图
function initLearningChart(data) {
  if (!learningRef.value) return
  learningChart = echarts.init(learningRef.value)
  learningChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: ['视频学习', '练习提交'], axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '视频学习次数',
        type: 'bar',
        data: [data.videoPlayCount ?? 0, 0],
        itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '练习提交次数',
        type: 'bar',
        data: [0, data.exerciseSubmitCount ?? 0],
        itemStyle: { color: '#e6a23c', borderRadius: [4, 4, 0, 0] }
      }
    ]
  })
}

// 窗口 resize 时重绘图表
function resizeCharts() {
  userTrendChart?.resize()
  courseTrendChart?.resize()
  courseDistChart?.resize()
  learningChart?.resize()
}

// 加载所有数据
async function loadData() {
  try {
    const [overviewRes, userTrendRes, courseTrendRes, courseDistRes, learningRes] = await Promise.all([
      getOverview(),
      getUserTrend(30),
      getCourseTrend(30),
      getCourseDistribution(),
      getLearningBehavior(30)
    ])

    const d = overviewRes.data
    overview.userTotal = d.userTotal ?? 0
    overview.activeUsers7d = d.activeUsers7d ?? 0
    overview.courseTotal = d.courseTotal ?? 0
    overview.enrollmentTotal = d.enrollmentTotal ?? 0

    initUserTrendChart(userTrendRes.data || [])
    initCourseTrendChart(courseTrendRes.data || [])
    initCourseDistChart(courseDistRes.data || { draft: 0, underReview: 0, published: 0, offline: 0 })
    initLearningChart(learningRes.data || { videoPlayCount: 0, exerciseSubmitCount: 0 })
  } catch (err) {
    // 静默失败，图表保持空白
  }
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  userTrendChart?.dispose()
  courseTrendChart?.dispose()
  courseDistChart?.dispose()
  learningChart?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-icon { background: rgba(64,158,255,0.1); color: #409eff; }
.active-icon { background: rgba(103,194,58,0.1); color: #67c23a; }
.course-icon { background: rgba(230,162,60,0.1); color: #e6a23c; }
.enroll-icon { background: rgba(245,108,108,0.1); color: #f56c6c; }

.stat-content { flex: 1; min-width: 0; }

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.chart-card {
  /* default */
}

.chart-card :deep(.el-card__header) {
  font-size: 15px;
  font-weight: 600;
  padding: 12px 20px;
}

.chart-container {
  width: 100%;
  height: 280px;
}

@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }

  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>