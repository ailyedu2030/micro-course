<!--
  教师端 - 学生成绩
  /teacher/grades
  Author: jackie
-->
<template>
  <div class="student-grades-container">
    <!-- 搜索筛选区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="选择课程">
          <el-select
            v-model="searchForm.courseId"
            placeholder="请选择课程"
            clearable
            class="course-select"
            @change="handleCourseChange"
          >
            <el-option
              v-for="item in courseOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <div v-if="searchForm.courseId" class="stats-grid">
      <el-card class="stat-card shadow-hover" shadow="never">
        <div class="stat-item">
          <div class="stat-value text-primary-color">{{ stats.averageScore ?? '-' }}</div>
          <div class="stat-label">平均分</div>
        </div>
      </el-card>
      <el-card class="stat-card shadow-hover" shadow="never">
        <div class="stat-item">
          <div class="stat-value" :class="stats.passRate >= 60 ? 'text-success' : 'text-danger'">
            {{ stats.passRate != null ? stats.passRate + '%' : '-' }}
          </div>
          <div class="stat-label">及格率</div>
        </div>
      </el-card>
      <el-card class="stat-card shadow-hover" shadow="never">
        <div class="stat-item">
          <div class="stat-value text-primary-color">{{ stats.maxScore ?? '-' }}</div>
          <div class="stat-label">最高分</div>
        </div>
      </el-card>
      <el-card class="stat-card shadow-hover" shadow="never">
        <div class="stat-item">
          <div class="stat-value text-secondary">{{ stats.gradedCount ?? 0 }} / {{ stats.totalCount ?? 0 }}</div>
          <div class="stat-label">已批改 / 总人数</div>
        </div>
      </el-card>
    </div>

    <!-- 图表 + 表格 -->
    <div v-if="searchForm.courseId" class="content-grid">
      <!-- 成绩分布图 -->
      <el-card class="chart-card shadow-hover" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">成绩分布</span>
          </div>
        </template>
        <div ref="chartRef" class="chart-container"></div>
      </el-card>

      <!-- 成绩表格 -->
      <el-card class="table-card shadow-hover" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">成绩明细</span>
          </div>
        </template>

        <!-- 加载中 -->
        <el-skeleton v-if="loading" :rows="6" animated />

        <!-- 空状态 -->
        <el-empty
          v-else-if="!loading && tableData.length === 0"
          description="暂无成绩数据"
          :image-size="120"
        />

        <!-- 数据表格 -->
        <el-table
          v-else
          v-loading="loading"
          :data="tableData"
          stripe
          border
          class="data-table"
        >
          <el-table-column type="index" label="序号" width="70" align="center" />
          <el-table-column prop="realName" label="学生" min-width="120" show-overflow-tooltip />
          <el-table-column prop="courseName" label="课程" min-width="160" show-overflow-tooltip />
          <el-table-column prop="score" label="分数" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.score != null" :class="getScoreClass(row.score)">
                {{ row.score.toFixed(1) }}
              </span>
              <el-tag v-else type="info" size="small">待批改</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="评语" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-secondary">{{ row.comment || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="gradedAt" label="批改时间" width="170">
            <template #default="{ row }">
              <span class="text-secondary">{{ formatDate(row.gradedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                :disabled="!searchForm.courseId"
                @click="handleGrade(row)"
              >
                <el-icon><Edit /></el-icon>{{ row.score != null ? '查看' : '批改' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div v-if="tableData.length > 0" class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="totalElements"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 空状态提示（未选课程） -->
    <el-empty
      v-if="!searchForm.courseId"
      description="请先选择课程查看成绩"
      :image-size="120"
      class="empty-hint"
    />

    <!-- 批改/查看弹窗 -->
    <el-dialog
      v-model="gradeVisible"
      :title="isGraded ? '查看成绩' : '批改成绩'"
      width="500px"
      destroy-on-close
    >
      <el-form :model="gradeForm" label-width="80px">
        <el-form-item label="学生">
          <el-input :model-value="currentStudent?.realName || ''" disabled />
        </el-form-item>
        <el-form-item label="课程">
          <el-input :model-value="currentStudent?.courseName || ''" disabled />
        </el-form-item>
        <el-form-item label="分数" required>
          <el-input-number
            v-model="gradeForm.score"
            :min="0"
            :max="100"
            :step="0.1"
            :disabled="isGraded"
            controls-position="right"
            class="score-input"
          />
        </el-form-item>
        <el-form-item label="评语">
          <el-input
            v-model="gradeForm.comment"
            type="textarea"
            :rows="3"
            :disabled="isGraded"
            placeholder="请输入评语（选填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeVisible = false">关闭</el-button>
        <el-button v-if="!isGraded" type="primary" :loading="savingGrade" @click="confirmGrade">
          提交成绩
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 教师端 - 学员成绩
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getCourses } from '@/api/course'
import { getCourseEnrollments } from '@/api/enrollment'
import { submitGrade } from '@/api/grade'
import { useUserStore } from '@/store/user'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const route = useRoute()
const userStore = useUserStore()

// 加载状态
const loading = ref(false)

// 表格数据
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

// 搜索表单
const searchForm = reactive({
  courseId: ''
})

// 统计数据
const stats = reactive({
  averageScore: null,
  passRate: null,
  maxScore: null,
  gradedCount: 0,
  totalCount: 0
})

// 图表
const chartRef = ref(null)
let chartInstance = null

// 批改弹窗
const gradeVisible = ref(false)
const currentStudent = ref(null)
const savingGrade = ref(false)
const gradeForm = reactive({
  score: null,
  comment: ''
})

// 是否已批改
const isGraded = computed(() => currentStudent.value?.score != null)

// 获取课程列表
async function fetchCourses() {
  try {
    const teacherId = userStore.userInfo?.id
    const { data } = await getCourses({ size: 1000, teacherId })
    courseOptions.value = data.items || []
    if (route.query.courseId) {
      searchForm.courseId = Number(route.query.courseId)
    }
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

// 获取成绩数据
async function fetchData() {
  if (!searchForm.courseId) {
    tableData.value = []
    totalElements.value = 0
    resetStats()
    return
  }
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: searchForm.courseId
    }
    const { data } = await getCourseEnrollments(params)
    const result = data
    let items = []
    if (Array.isArray(result)) {
      items = result
      totalElements.value = result.length
    } else {
      items = result.items || []
      totalElements.value = result.totalElements || items.length
    }
    // 模拟成绩数据（实际项目中从成绩接口获取）
    tableData.value = items.map((item, index) => ({
      ...item,
      courseName: courseOptions.value.find(c => c.id === searchForm.courseId)?.title || '',
      score: item.exerciseAvgScore != null ? Math.round(item.exerciseAvgScore * 10) / 10 : null,
      comment: '',
      gradedAt: item.exerciseAvgScore != null ? item.lastWatchAt : null
    }))
    updateStats(tableData.value)
    renderChart(tableData.value)
  } catch {
    ElMessage.error('获取成绩数据失败')
  } finally {
    loading.value = false
  }
}

// 更新统计数据
function updateStats(items) {
  const scored = items.filter(i => i.score != null)
  if (scored.length === 0) {
    resetStats()
    return
  }
  const scores = scored.map(i => i.score)
  stats.averageScore = (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1)
  stats.maxScore = Math.max(...scores).toFixed(1)
  stats.passRate = Math.round((scores.filter(s => s >= 60).length / scores.length) * 100)
  stats.gradedCount = scored.length
  stats.totalCount = items.length
}

function resetStats() {
  stats.averageScore = null
  stats.passRate = null
  stats.maxScore = null
  stats.gradedCount = 0
  stats.totalCount = 0
}

// 渲染图表
function renderChart(items) {
  if (!chartRef.value) return
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  chartInstance = echarts.init(chartRef.value)

  // 成绩区间统计
  const ranges = [
    { label: '0-59', min: 0, max: 59, count: 0 },
    { label: '60-69', min: 60, max: 69, count: 0 },
    { label: '70-79', min: 70, max: 79, count: 0 },
    { label: '80-89', min: 80, max: 89, count: 0 },
    { label: '90-100', min: 90, max: 100, count: 0 }
  ]
  items.forEach(item => {
    if (item.score == null) return
    const range = ranges.find(r => item.score >= r.min && item.score <= r.max)
    if (range) range.count++
  })

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: '{b}: {c}人'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: ranges.map(r => r.label),
      axisLabel: { color: 'var(--el-text-color-regular)' },
      axisLine: { lineStyle: { color: 'var(--el-border-color)' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: 'var(--el-text-color-regular)' },
      axisLine: { lineStyle: { color: 'var(--el-border-color)' } },
      splitLine: { lineStyle: { color: 'var(--el-border-color-lighter)' } }
    },
    series: [{
      type: 'bar',
      data: ranges.map(r => ({
        value: r.count,
        itemStyle: {
          color: r.min < 60 ? 'var(--el-color-danger)' : 'var(--role-primary)',
          borderRadius: [4, 4, 0, 0]
        }
      })),
      barWidth: '50%',
      label: {
        show: true,
        position: 'top',
        color: 'var(--el-text-color-regular)',
        fontSize: 12
      }
    }]
  }

  chartInstance.setOption(option)
}

// 课程变化
function handleCourseChange() {
  page.value = 1
  if (searchForm.courseId) {
    fetchData()
  } else {
    tableData.value = []
    totalElements.value = 0
    resetStats()
    if (chartInstance) {
      chartInstance.dispose()
      chartInstance = null
    }
  }
}

// 翻页
function handleSizeChange() {
  page.value = 1
  fetchData()
}

function handlePageChange() {
  fetchData()
}

// 批改/查看
function handleGrade(row) {
  currentStudent.value = row
  gradeForm.score = row.score ?? null
  gradeForm.comment = row.comment || ''
  gradeVisible.value = true
}

// 确认提交成绩
async function confirmGrade() {
  if (gradeForm.score == null) {
    ElMessage.warning('请输入分数')
    return
  }
  savingGrade.value = true
  try {
    await submitGrade({
      enrollmentId: currentStudent.value.id,
      score: gradeForm.score,
      comment: gradeForm.comment
    })
    ElMessage.success('成绩提交成功')
    gradeVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('提交失败，请稍后重试')
  } finally {
    savingGrade.value = false
  }
}

// 工具方法
function formatDate(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function getScoreClass(score) {
  if (score >= 90) return 'score-excellent'
  if (score >= 80) return 'score-good'
  if (score >= 60) return 'score-pass'
  return 'score-fail'
}

// 监听窗口变化，重绘图表
function handleResize() {
  chartInstance?.resize()
}

onMounted(() => {
  fetchCourses()
  if (searchForm.courseId) {
    fetchData()
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.student-grades-container {
  padding: var(--space-4);
  background: var(--el-bg-color-page);
}

/* 搜索区 */
.search-card {
  margin-bottom: var(--space-4);
}

.course-select {
  width: 220px;
}

/* 统计卡片网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.stat-card {
  text-align: center;
}

.stat-item {
  padding: var(--space-2) 0;
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-tight);
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-2);
}

/* 图表 + 表格网格 */
.content-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--space-4);
  align-items: start;
}

/* 图表卡片 */
.chart-card {
  position: sticky;
  top: var(--space-4);
}

.chart-container {
  width: 100%;
  height: 280px;
}

/* 表格卡片 */
.table-card {
  margin-bottom: var(--space-4);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

/* 表格 */
.data-table {
  width: 100%;
}

/* 分数颜色 */
.score-excellent { color: var(--el-color-success); font-weight: var(--weight-semibold); }
.score-good { color: var(--role-primary); font-weight: var(--weight-semibold); }
.score-pass { color: var(--el-color-warning); font-weight: var(--weight-semibold); }
.score-fail { color: var(--el-color-danger); font-weight: var(--weight-semibold); }

/* 文字辅助 */
.text-secondary { color: var(--el-text-color-secondary); font-size: var(--text-sm); }
.text-primary-color { color: var(--role-primary); }
.text-success { color: var(--el-color-success); }
.text-danger { color: var(--el-color-danger); }

/* 分页 */
.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}

/* 批改弹窗 */
.score-input {
  width: 160px;
}

/* 空提示 */
.empty-hint {
  margin-top: var(--space-8);
}

@media (max-width: 1279px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .chart-card {
    position: static;
  }
}
</style>