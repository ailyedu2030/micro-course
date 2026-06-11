<!--
  学习中心
  路由路径: /student/learning
  Phase 2
  Author: jackie
-->
<template>
  <div class="learning-center">
    <div class="header">
      <h2>学习中心</h2>
    </div>

    <!-- 顶部统计卡片 -->
    <div class="stats-row">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-value">{{ formatDuration(totalMinutes) }}</div>
        <div class="stat-label">累计学习时长</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-value">{{ completedCount }}</div>
        <div class="stat-label">完成课程数</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-value">{{ streakDays }}</div>
        <div class="stat-label">连续打卡天数</div>
      </el-card>
    </div>

    <!-- 主体两栏布局 -->
    <div class="main-content">
      <!-- 左侧：打卡日历 + 打卡按钮 -->
      <div class="left-panel">
        <el-card class="calendar-card">
          <template #header>
            <div class="card-header-title">活跃日历（近30天）</div>
          </template>
          <div class="calendar-grid">
            <div
              v-for="day in calendarDays"
              :key="day.date"
              class="calendar-cell"
              :class="day.hasCheckIn ? 'cell-active' : 'cell-inactive'"
              :title="day.date"
            >
              <span class="day-number">{{ day.day }}</span>
            </div>
          </div>
          <div class="calendar-legend">
            <span class="legend-item"><span class="legend-dot dot-active"></span>已打卡</span>
            <span class="legend-item"><span class="legend-dot dot-inactive"></span>未打卡</span>
          </div>
        </el-card>

        <el-card class="checkin-card">
          <div class="checkin-action">
            <el-button
              v-if="!todayCheckedIn"
              type="primary"
              size="large"
              circle
              class="checkin-btn"
              @click="handleCheckIn"
              :loading="checkinLoading"
            >
             <span class="checkin-icon">+</span>
            </el-button>
            <el-button
              v-else
              type="success"
              size="large"
              circle
              disabled
              class="checkin-btn"
            >
              <span class="checkin-icon">✓</span>
            </el-button>
            <div class="checkin-text">
              {{ todayCheckedIn ? '今日已打卡 ✓' : '点击打卡' }}
            </div>
          </div>
        </el-card>
      </div>

      <!-- 右侧：正确率趋势 + 知识图谱占位 -->
      <div class="right-panel">
        <el-card class="trend-card">
          <template #header>
            <div class="card-header-title">正确率趋势（近5次练习）</div>
          </template>
          <div v-loading="trendLoading" class="trend-list">
            <template v-if="exerciseTrends.length > 0">
              <div
                v-for="(item, index) in exerciseTrends"
                :key="index"
                class="trend-item"
              >
                <span class="trend-index">{{ index + 1 }}</span>
                <span class="trend-exercise">练习 #{{ item.exerciseId }}</span>
                <div class="trend-bar-wrap">
                  <div class="trend-bar" :style="{ width: item.score + '%' }"></div>
                </div>
                <span class="trend-score">{{ item.score }}%</span>
              </div>
            </template>
            <el-empty v-else description="暂无练习记录" :image-size="60" />
          </div>
        </el-card>

        <el-card class="graph-card">
          <template #header>
            <div class="card-header-title">知识图谱</div>
          </template>
          <div class="coming-soon">
            <span class="soon-icon">🔗</span>
            <span class="soon-text">即将上线</span>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { getMyEnrollments } from '../../api/enrollment'
import { getMyCheckIns, getCheckInStreak, createCheckIn } from '../../api/checkin'

const userStore = useUserStore()

//状态
const enrollments = ref([])
const checkinRecords = ref([])
const exerciseRecords = ref([])
const streakDays = ref(0)
const loading = ref(false)
const trendLoading = ref(false)
const checkinLoading = ref(false)

// 确保 userId 可用
const ensureUserId = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.getInfo()
  }
  return userStore.userInfo?.id
}

// 总学习时长（分钟）
const totalMinutes = computed(() => {
  let total = 0
  for (const e of enrollments.value) {
    total += e.totalWatchTime || 0
  }
  return total
})

// 完成课程数
const completedCount = computed(() =>
  enrollments.value.filter(e => e.completed).length
)

// 今日是否已打卡
const todayCheckedIn = computed(() => {
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return checkinRecords.value.some(r => r.checkInDate && r.checkInDate.startsWith(todayStr))
})

// 日历30天
const calendarDays = computed(() => {
  const days = []
  const today = new Date()
  for (let i = 29; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const hasCheckIn = checkinRecords.value.some(r => r.checkInDate && r.checkInDate.startsWith(dateStr))
    days.push({ date: dateStr, day: d.getDate(), hasCheckIn })
  }
  return days
})

// 最近5次练习正确率
const exerciseTrends = computed(() => {
  return exerciseRecords.value
    .slice(0, 5)
    .map(r => ({
      exerciseId: r.exerciseId,
      score: r.score || 0
    }))
})

// 格式化时长
const formatDuration = (minutes) => {
  if (!minutes) return '0小时0分钟'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${h}小时${m}分钟`
}

// 加载选课列表
const fetchEnrollments = async (userId) => {
  try {
    const res = await getMyEnrollments(userId)
    enrollments.value = res.data || []
  } catch {
    // ignore
  }
}

// 加载打卡记录
const fetchCheckIns = async () => {
  try {
    const res = await getMyCheckIns({ days: 30 })
    checkinRecords.value = res.data || []
  } catch {
    // ignore
  }
}

// 加载连续天数
const fetchStreak = async () => {
  try {
    const res = await getCheckInStreak()
    streakDays.value = res.data?.streak || 0
  } catch {
    streakDays.value = 0
  }
}

// 练习记录 - 后端暂不支持批量查询，使用空值占位
// TODO: 待后端提供 /exercise-records/my 列表接口后启用
const fetchExerciseRecords = async () => {
  exerciseRecords.value = []
}

// 打卡
const handleCheckIn = async () => {
  checkinLoading.value = true
  try {
    await createCheckIn({})
    ElMessage.success('打卡成功！')
    await Promise.all([fetchCheckIns(), fetchStreak()])
  } catch {
    ElMessage.error('打卡失败，请稍后重试')
  } finally {
    checkinLoading.value = false
  }
}

//初始化
onMounted(async () => {
  loading.value = true
  try {
    const userId = await ensureUserId()
    if (!userId) {
      ElMessage.error('无法获取用户信息')
      return
    }
    await Promise.all([
      fetchEnrollments(userId),
      fetchCheckIns(),
      fetchStreak(),
      fetchExerciseRecords()
    ])
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.learning-center {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.header {
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

/* 统计卡片行 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px 0;
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

/* 主体两栏 */
.main-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.left-panel,
.right-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 日历 */
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 36px);
  grid-template-rows: repeat(5, 36px);
  gap: 4px;
  justify-content: center;
}

.calendar-cell {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 500;
}

.cell-active {
  background-color: #67c23a;
  color: #fff;
}

.cell-inactive {
  background-color: #f0f0f0;
  color: #909399;
}

.day-number {
  line-height: 1;
}

.calendar-legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
  font-size: 12px;
  color: #606266;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.dot-active {
  background-color: #67c23a;
}

.dot-inactive {
  background-color: #f0f0f0;
}

.card-header-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* 打卡卡片 */
.checkin-card {
  text-align: center;
  padding: 24px;
}

.checkin-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.checkin-btn {
  width: 64px;
  height: 64px;
  font-size: 28px;
}

.checkin-icon {
  line-height: 1;
}

.checkin-text {
  font-size: 14px;
  color: #606266;
}

/* 正确率趋势 */
.trend-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.trend-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trend-index {
  font-size: 12px;
  color: #909399;
  width: 16px;
  flex-shrink: 0;
}

.trend-exercise {
  font-size: 13px;
  color: #606266;
  width: 80px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trend-bar-wrap {
  flex: 1;
  height: 8px;
  background-color: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.trend-bar {
  height: 100%;
  background-color: #409eff;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.trend-score {
  font-size: 13px;
  color: #409eff;
  font-weight: 600;
  width: 36px;
  text-align: right;
  flex-shrink: 0;
}

/* 知识图谱占位 */
.graph-card {
  flex: 1;
}

.coming-soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
  color: #c0c4cc;
}

.soon-icon {
  font-size: 32px;
}

.soon-text {
  font-size: 14px;
}

/* 响应式 */
@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .stat-value {
    font-size: 20px;
  }

  .stat-label {
    font-size: 12px;
  }

  .main-content {
    grid-template-columns: 1fr;
  }

  .calendar-grid {
    grid-template-columns: repeat(7, 32px);
    grid-template-rows: repeat(5, 32px);
  }

  .calendar-cell {
    width: 32px;
    height: 32px;
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  .learning-center {
    padding: 12px;
  }

  .stats-row {
    grid-template-columns: 1fr;
  }

  .stat-card {
    display: flex;
    align-items: center;
    gap: 12px;
    text-align: left;
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }

  .stat-label {
    margin-top: 0;
  }
}
</style>