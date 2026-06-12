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

    <!-- 标签页切换 -->
    <div class="tab-container">
      <el-tabs v-model="activeTab" class="learning-tabs">
        <el-tab-pane label="学习统计" name="stats">
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
        </el-tab-pane>

        <el-tab-pane label="练习历史" name="history">
          <div class="history-content">
            <div v-loading="historyLoading" class="history-list">
              <template v-if="exerciseHistory.length > 0">
                <div
                  v-for="record in exerciseHistory"
                  :key="record.id"
                  class="history-item"
                  @click="toggleExpand(record.id)"
                >
                  <div class="history-header">
                    <div class="history-title">
                      <span class="exercise-title">{{ record.exerciseTitle }}</span>
                      <el-tag :type="record.passed ? 'success' : 'danger'" size="small">
                        {{ record.passed ? '通过' : '未通过' }}
                      </el-tag>
                    </div>
                    <div class="history-meta">
                      <span class="history-score">得分 {{ record.score }}/{{ record.totalScore }}</span>
                      <span class="history-date">{{ formatDateTime(record.submittedAt) }}</span>
                      <span v-if="record.duration" class="history-duration">用时 {{ record.duration }}分钟</span>
                    </div>
                  </div>

                  <div v-if="expandedRecordId === record.id && record.answers" class="history-detail">
                    <div class="detail-title">答题详情</div>
                    <div class="detail-answers">
                      <template v-for="(answer, idx) in parseAnswers(record.answers)" :key="idx">
                        <div class="answer-item" :class="{ 'answer-correct': answer.isCorrect, 'answer-wrong': answer.isCorrect === false }">
                          <div class="answer-header">
                            <span class="answer-index">第{{ idx + 1 }}题</span>
                            <el-tag :type="answer.isCorrect ? 'success' : 'danger'" size="small">
                              {{ answer.isCorrect ? '正确' : '错误' }}
                            </el-tag>
                          </div>
                          <div class="answer-content">
                            <div class="answer-user">你的答案：{{ answer.userAnswer || '未答' }}</div>
                            <div v-if="answer.isCorrect === false" class="answer-correct-answer">
                              正确答案：{{ answer.correctAnswer }}
                            </div>
                          </div>
                        </div>
                      </template>
                    </div>
                  </div>
                </div>
              </template>
              <el-empty v-else description="暂无练习记录" :image-size="60" />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 打卡分享弹窗 -->
    <el-dialog v-model="shareVisible" title="分享打卡" width="360px" :close-on-click-modal="true">
      <div ref="shareCardRef" class="share-card">
        <div class="share-logo">微课平台</div>
        <div class="share-streak">
          <span class="streak-number">{{ streakDays }}</span>
          <span class="streak-unit">天</span>
        </div>
        <div class="share-label">连续学习</div>
        <div class="share-badge">今日已打卡 ✓</div>
      </div>
      <template #footer>
        <el-button @click="handleCloseShare">关闭</el-button>
        <el-button type="primary" :loading="shareLoading" @click="handleShare">分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import html2canvas from 'html2canvas'
import { useUserStore } from '../../store/user'
import { getMyEnrollments } from '../../api/enrollment'
import { getMyCheckIns, getCheckInStreak, createCheckIn } from '../../api/checkin'
import { getMyRecords as getMyExerciseRecords } from '../../api/exercise-record'

const router = useRouter()

const userStore = useUserStore()

//状态
const enrollments = ref([])
const checkinRecords = ref([])
const exerciseRecords = ref([])
const streakDays = ref(0)
const loading = ref(false)
const trendLoading = ref(false)
const checkinLoading = ref(false)
const shareVisible = ref(false)
const shareLoading = ref(false)
const shareCardRef = ref(null)

// 标签页
const activeTab = ref('stats')

// 练习历史
const exerciseHistory = ref([])
const historyLoading = ref(false)
const expandedRecordId = ref(null)

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

// 练习记录
const fetchExerciseRecords = async () => {
  exerciseRecords.value = []
}

// 获取所有练习ID并查询历史
const fetchExerciseHistory = async () => {
  if (exerciseHistory.value.length > 0) return
  historyLoading.value = true
  try {
    const userId = userStore.userInfo?.id
    if (!userId) return

    const res = await getMyExerciseRecords(userId)
    exerciseHistory.value = res.data || []
  } catch {
    ElMessage.error('获取练习历史失败')
  } finally {
    historyLoading.value = false
  }
}

const toggleExpand = (recordId) => {
  expandedRecordId.value = expandedRecordId.value === recordId ? null : recordId
}

const parseAnswers = (answersJson) => {
  if (!answersJson) return []
  try {
    return JSON.parse(answersJson)
  } catch {
    return []
  }
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

// 打卡
const handleCheckIn = async () => {
  checkinLoading.value = true
  try {
    await createCheckIn({})
    ElMessage.success('打卡成功！')
    await Promise.all([fetchCheckIns(), fetchStreak()])
    shareVisible.value = true
  } catch {
    ElMessage.error('打卡失败，请稍后重试')
  } finally {
    checkinLoading.value = false
  }
}

// 分享
const handleShare = async () => {
  if (!shareCardRef.value) return
  shareLoading.value = true
  try {
    const canvas = await html2canvas(shareCardRef.value, { scale: 2, useCORS: true })
    canvas.toBlob(async (blob) => {
      if (blob) {
        const file = new File([blob], 'checkin-share.png', { type: 'image/png' })
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({
            files: [file],
            text: `我在微课平台学习第${streakDays.value}天！`
          })
        } else {
          const url = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = url
          a.download = 'checkin-share.png'
          a.click()
          URL.revokeObjectURL(url)
        }
      }
      shareLoading.value = false
    })
  } catch {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(`我在微课平台学习第${streakDays.value}天！`)
      ElMessage.success('分享文字已复制到剪贴板')
    } else {
      ElMessage.warning('分享失败，请稍后重试')
    }
    shareLoading.value = false
  }
}

const handleCloseShare = () => {
  shareVisible.value = false
}

// 监听标签页切换
import { watch } from 'vue'
watch(activeTab, (newTab) => {
  if (newTab === 'history') {
    fetchExerciseHistory()
  }
})

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

/* 标签页容器 */
.tab-container {
  margin-top: 20px;
}

.learning-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
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
  color: #f5f5f5;
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

.share-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
  color: #f5f5f5;
}

.share-logo {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 16px;
}

.share-streak {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
  margin-bottom: 8px;
}

.streak-number {
  font-size: 64px;
  font-weight: bold;
  line-height: 1;
}

.streak-unit {
  font-size: 24px;
}

.share-label {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 16px;
}

.share-badge {
  display: inline-block;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  padding: 6px 16px;
  font-size: 13px;
}

/* 练习历史 */
.history-content {
  padding: 0 4px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  background: #f9fafb;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-item:hover {
  border-color: #409eff;
  background: #f5f7fa;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.history-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.exercise-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.history-score {
  color: #409eff;
  font-weight: 500;
}

.history-detail {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #e4e8ec;
}

.detail-title {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 12px;
}

.detail-answers {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.answer-item {
  padding: 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.answer-correct {
  border-left: 3px solid #67c23a;
}

.answer-wrong {
  border-left: 3px solid #f56c6c;
}

.answer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.answer-index {
  font-size: 13px;
  color: #606266;
}

.answer-content {
  font-size: 13px;
  color: #909399;
}

.answer-user {
  margin-bottom: 4px;
}

.answer-correct-answer {
  color: #67c23a;
}
</style>