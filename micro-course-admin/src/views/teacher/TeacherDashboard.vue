<template>
  <div class="teacher-dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon student-icon">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ overview.studentCount || 0 }}</div>
            <div class="stat-label">选课学生数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon completion-icon">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ overview.completionRate || 0 }}%</div>
            <div class="stat-label">平均完成率</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon score-icon">
            <el-icon><Medal /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ overview.avgScore || 0 }}</div>
            <div class="stat-label">平均成绩</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-icon duration-icon">
            <el-icon><VideoPlay /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ formatDuration(overview.totalWatchTime) }}</div>
            <div class="stat-label">视频学习总时长</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 中部图表区 -->
    <el-card class="chart-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>近 7 天学习活跃度</span>
        </div>
      </template>
      <div ref="chartRef" class="chart-container"></div>
    </el-card>

    <!-- 底部课程列表 -->
    <el-card class="course-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的课程</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">草稿</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger" size="small">驳回</el-tag>
            <el-tag v-else-if="row.status === 4" type="primary" size="small">已发布</el-tag>
            <el-tag v-else-if="row.status === 5" size="small">下架</el-tag>
            <el-tag v-else type="info" size="small">归档</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="studentCount" label="学生数" width="90" align="center" />
        <el-table-column prop="rating" label="评分" width="80" align="center">
          <template #default="{ row }">
            {{ row.rating ? row.rating.toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
           <el-button type="primary" link size="small" @click="handleStudents(row)">学员</el-button>
          </template>
        </el-table-column>
      </el-table>
     <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 教师数据看板
 * @author Claude Code Agent
 */
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, CircleCheck, Medal, VideoPlay } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getCourses } from '@/api/course'
import { getOverview, getUserTrend } from '@/api/admin-stats'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const overview = ref({
  studentCount: 0,
  completionRate: 0,
  avgScore: 0,
  totalWatchTime: 0
})

const chartRef = ref(null)
let chartInstance = null

const formatDuration = (minutes) => {
  if (!minutes) return '0分钟'
  if (minutes < 60) return `${minutes}分钟`
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}小时${mins}分钟` : `${hours}小时`
}

const fetchOverview = async () => {
  try {
    const res = await getOverview()
    overview.value = res.data || {}
  } catch (error) {
    //静默失败，使用默认值
  }
}

const fetchTrend = async () => {
  try {
    const res = await getUserTrend(7)
    const trendData = res.data || []
    renderChart(trendData)
  } catch (error) {
    // 静默失败，渲染空图表
    renderChart([])
  }
}

const renderChart = (data) => {
  if (!chartRef.value) return
  if (chartInstance) {
    chartInstance.dispose()
  }
  chartInstance = echarts.init(chartRef.value)
  const dates = data.map(item => item.date || item.dateStr || '')
  const values = data.map(item => item.count || item.value || 0)
  const option = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '学习人数',
      type: 'line',
      smooth: true,
      data: values,
      areaStyle: { opacity: 0.2 },
      lineStyle: { width: 2 },
      itemStyle: { color: '#409eff' }
    }],
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true }
  }
  chartInstance.setOption(option)
}

const fetchData = async () => {
  loading.value = true
  try {
    const teacherId = userStore.userInfo?.id
    const params = {
      page: page.value - 1,
      size: size.value,
      teacherId: teacherId || undefined
    }
    const { data } = await getCourses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
    ElMessage.error('获取课程列表失败')
  } finally {
    loading.value = false
  }
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleView = (row) => {
  router.push(`/courses/${row.id}`)
}

const handleStudents = (row) => {
  router.push(`/teacher/students?courseId=${row.id}`)
}

const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  fetchOverview()
  fetchTrend()
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.teacher-dashboard {
  padding: 20px;
}

.stats-row {
  margin-bottom: 16px;
}

.data-table {
  width: 100%;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 16px;
}

.student-icon {
  background: #e6f4ff;
  color: #1677ff;
}

.completion-icon {
  background: #f6ffed;
  color: #52c41a;
}

.score-icon {
  background: #fff7e6;
  color: #fa8c16;
}

.duration-icon {
  background: #f0f5ff;
  color: #7c6cff;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.chart-card {
  margin-bottom: 16px;
}

.chart-container {
  height: 300px;
  width: 100%;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
}

.course-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .stats-row {
    margin-bottom: 12px;
  }

  .stat-card {
    padding: 12px;
    margin-bottom: 12px;
  }

  .stat-icon {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }

  .stat-value {
    font-size: 20px;
  }

  .chart-container {
    height: 220px;
  }
}
</style>