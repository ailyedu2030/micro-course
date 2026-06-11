<!--
  学习周报
  路由路径: /student/report
  Phase 2
  Author: jackie
-->
<template>
  <div class="weekly-report">
    <h2 class="page-title">学习周报</h2>

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon study-icon">📅</div>
          <div class="stat-value">{{ reportData.learningDays }}</div>
          <div class="stat-label">学习天数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon time-icon">⏱</div>
          <div class="stat-value">{{ formatDuration(reportData.videoMinutes) }}</div>
          <div class="stat-label">视频学习时长</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon exercise-icon">✍</div>
          <div class="stat-value">{{ reportData.exerciseCount }}</div>
          <div class="stat-label">完成练习数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon accuracy-icon">🎯</div>
          <div class="stat-value">{{ reportData.accuracyPercent }}%</div>
          <div class="stat-label">正确率</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="detail-card" shadow="never">
      <template #header>
        <div class="card-header-title">本周打卡</div>
      </template>
      <div v-loading="checkinLoading" class="checkin-list">
        <template v-if="weekCheckins.length > 0">
          <div
            v-for="record in weekCheckins"
            :key="record.checkInDate"
            class="checkin-item"
          >
            <span class="checkin-date">{{ formatDate(record.checkInDate) }}</span>
            <span class="checkin-tag">已打卡</span>
          </div>
        </template>
        <el-empty v-else description="本周暂无打卡记录" :image-size="60" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { getMyEnrollments } from '../../api/enrollment'
import { getMyCheckIns } from '../../api/checkin'

const userStore = useUserStore()

const loading = ref(false)
const checkinLoading = ref(false)
const enrollments = ref([])
const weekCheckins = ref([])

const reportData = ref({
  learningDays: 0,
  videoMinutes: 0,
  exerciseCount: 0,
  accuracyPercent: 0
})

const formatDuration = (minutes) => {
  if (!minutes) return '0分钟'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0) return `${h}小时${m}分钟`
  return `${m}分钟`
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = d.getMonth() + 1
  const day = d.getDate()
  const weekDay = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${month}月${day}日 ${weekDay}`
}

const getWeekRange = () => {
  const today = new Date()
  const startOfWeek = new Date(today)
  startOfWeek.setDate(today.getDate() - today.getDay())
  const endOfWeek = new Date(startOfWeek)
  endOfWeek.setDate(startOfWeek.getDate() + 6)
  return {
    start: `${startOfWeek.getFullYear()}-${String(startOfWeek.getMonth() + 1).padStart(2, '0')}-${String(startOfWeek.getDate()).padStart(2, '0')}`,
    end: `${endOfWeek.getFullYear()}-${String(endOfWeek.getMonth() + 1).padStart(2, '0')}-${String(endOfWeek.getDate()).padStart(2, '0')}`
  }
}

const fetchEnrollments = async (userId) => {
  try {
    const res = await getMyEnrollments(userId)
    enrollments.value = res.data || []
    calculateReport()
  } catch {
    ElMessage.error('获取学习数据失败')
  }
}

const fetchWeekCheckins = async () => {
  checkinLoading.value = true
  try {
    const res = await getMyCheckIns({ days: 7 })
    const records = res.data || []
    const { start } = getWeekRange()
    const startDate = new Date(start)
    weekCheckins.value = records.filter(r => {
      if (!r.checkInDate) return false
      const d = new Date(r.checkInDate)
      return d >= startDate
    })
  } catch {
    ElMessage.error('获取打卡数据失败')
  } finally {
    checkinLoading.value = false
  }
}

const calculateReport = () => {
  const { start } = getWeekRange()
  const startDate = new Date(start)
  const today = new Date()

  // 学习天数（本周有视频进度的天数）
  const learningDaysSet = new Set()
  let videoMinutes = 0
  let exerciseCount = 0
  let correctCount = 0

  for (const e of enrollments.value) {
    if (e.lastWatchTime) {
      const d = new Date(e.lastWatchTime)
      if (d >= startDate && d <= today) {
        learningDaysSet.add(d.toDateString())
      }
    }
    videoMinutes += e.totalWatchTime || 0
    exerciseCount += e.exerciseCount || 0
    correctCount += e.correctCount || 0
  }

  reportData.value.learningDays = learningDaysSet.size
  reportData.value.videoMinutes = videoMinutes
  reportData.value.exerciseCount = exerciseCount
  reportData.value.accuracyPercent = exerciseCount > 0
    ? Math.round((correctCount / exerciseCount) * 100)
    : 0
}

onMounted(async () => {
  loading.value = true
  try {
    if (!userStore.userInfo?.id) {
      await userStore.getInfo()
    }
    const userId = userStore.userInfo?.id
    if (userId) {
      await Promise.all([
        fetchEnrollments(userId),
        fetchWeekCheckins()
      ])
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.weekly-report {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}

.page-title {
  margin: 0 0 20px 0;
  font-size: 20px;
  color: #303133;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px 0;
}

.stat-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.card-header-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.checkin-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 80px;
}

.checkin-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.checkin-date {
  font-size: 14px;
  color: #606266;
}

.checkin-tag {
  font-size: 12px;
  color: #67c23a;
  background: #f0f9eb;
  padding: 2px 8px;
  border-radius: 4px;
}

@media (max-width: 768px) {
  .weekly-report {
    padding: 12px;
  }

  .stat-card {
    padding: 16px 0;
  }

  .stat-value {
    font-size: 22px;
  }
}

@media (max-width: 480px) {
  .stats-row {
    gap: 12px;
  }

  .stat-card {
    display: flex;
    align-items: center;
    gap: 12px;
    text-align: left;
    padding: 16px;
  }

  .stat-icon {
    margin-bottom: 0;
  }

  .stat-value {
    font-size: 20px;
  }

  .stat-label {
    margin-top: 0;
  }
}
</style>