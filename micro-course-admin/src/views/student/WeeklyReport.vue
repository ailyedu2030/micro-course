<!--
  学习周报
  路由路径: /student/report
  Phase 2
  Author: jackie
-->
<template>
  <div class="weekly-report">
    <!-- PC Layout -->
    <template v-if="!isMobile">
      <div class="page-header">
        <h2 class="page-title">学习周报</h2>
        <span class="week-range">{{ weekRangeLabel }}</span>
      </div>

      <!-- Stats Row -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="6">
          <el-card class="stat-card student-stat-card" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div class="skeleton-icon-wrap">
                  <el-skeleton-item variant="circle" style="width: 48px; height: 48px;" />
                </div>
                <el-skeleton-item variant="text" style="width: 60%; margin: 12px auto 0;" />
                <el-skeleton-item variant="text" style="width: 40%; margin: 8px auto 0;" />
              </template>
              <template #default>
                <div class="stat-icon-wrap">
                  <el-icon class="stat-icon" aria-hidden="true"><Calendar /></el-icon>
                </div>
                <div class="stat-value">{{ reportData.learningDays }}</div>
                <div class="stat-label">学习天数</div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card student-stat-card" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div class="skeleton-icon-wrap">
                  <el-skeleton-item variant="circle" style="width: 48px; height: 48px;" />
                </div>
                <el-skeleton-item variant="text" style="width: 60%; margin: 12px auto 0;" />
                <el-skeleton-item variant="text" style="width: 40%; margin: 8px auto 0;" />
              </template>
              <template #default>
                <div class="stat-icon-wrap">
                  <el-icon class="stat-icon" aria-hidden="true"><Timer /></el-icon>
                </div>
                <div class="stat-value">{{ formatDuration(reportData.videoMinutes) }}</div>
                <div class="stat-label">视频学习时长</div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card student-stat-card" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div class="skeleton-icon-wrap">
                  <el-skeleton-item variant="circle" style="width: 48px; height: 48px;" />
                </div>
                <el-skeleton-item variant="text" style="width: 60%; margin: 12px auto 0;" />
                <el-skeleton-item variant="text" style="width: 40%; margin: 8px auto 0;" />
              </template>
              <template #default>
                <div class="stat-icon-wrap">
                  <el-icon class="stat-icon" aria-hidden="true"><Edit /></el-icon>
                </div>
                <div class="stat-value">{{ reportData.exerciseCount }}</div>
                <div class="stat-label">完成练习数</div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card student-stat-card" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div class="skeleton-icon-wrap">
                  <el-skeleton-item variant="circle" style="width: 48px; height: 48px;" />
                </div>
                <el-skeleton-item variant="text" style="width: 60%; margin: 12px auto 0;" />
                <el-skeleton-item variant="text" style="width: 40%; margin: 8px auto 0;" />
              </template>
              <template #default>
                <div class="stat-icon-wrap">
                  <el-icon class="stat-icon" aria-hidden="true"><Aim /></el-icon>
                </div>
                <div class="stat-value">{{ reportData.accuracyPercent }}%</div>
                <div class="stat-label">正确率</div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
      </el-row>

      <!-- Cards Row -->
      <el-row :gutter="16" class="cards-row">
        <!-- Check-in Calendar -->
        <el-col :span="12">
          <el-card class="detail-card" shadow="never">
            <template #header>
              <div class="card-header-title">本周打卡</div>
            </template>
            <el-skeleton :loading="checkinLoading" animated>
              <template #template>
                <div class="skeleton-week">
                  <el-skeleton-item variant="text" style="width: 100%; height: 60px;" />
                </div>
              </template>
              <template #default>
                <div class="checkin-calendar">
                  <div
                    v-for="day in weekDays"
                    :key="day.date"
                    class="checkin-day"
                    :class="{ 'is-checked': day.checked }"
                  >
                    <span class="day-label">{{ day.label }}</span>
                    <span class="day-date">{{ day.dateStr }}</span>
                    <span class="day-dot" :class="{ 'is-checked': day.checked }"></span>
                  </div>
                </div>
                <div v-if="weekCheckins.length === 0" class="empty-tip">
                  <el-empty description="本周暂无打卡记录" :image-size="60" />
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>

        <!-- AI Suggestions -->
        <el-col :span="12">
          <el-card class="detail-card ai-card" shadow="never">
            <template #header>
              <div class="card-header-title">
                <el-icon class="header-icon"><Key /></el-icon>
                学习建议
              </div>
            </template>
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div class="skeleton-list">
                  <el-skeleton-item variant="text" style="width: 90%; height: 20px; margin-bottom: var(--space-3);" />
                  <el-skeleton-item variant="text" style="width: 75%; height: 20px; margin-bottom: var(--space-3);" />
                  <el-skeleton-item variant="text" style="width: 85%; height: 20px;" />
                </div>
              </template>
              <template #default>
                <ul class="ai-suggestions">
                  <li v-for="(tip, idx) in aiSuggestions" :key="idx" class="ai-tip-item">
                    <el-icon class="tip-icon"><ArrowRight /></el-icon>
                    <span>{{ tip }}</span>
                  </li>
                </ul>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- H5 Layout -->
    <template v-else>
      <div class="page-header-h5">
        <h2 class="page-title-h5">学习周报</h2>
        <span class="week-range-h5">{{ weekRangeLabel }}</span>
      </div>

      <!-- Stats 2x2 Grid -->
      <el-row :gutter="12" class="stats-row-h5">
        <el-col :span="12">
          <el-card class="stat-card-h5" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div style="display: flex; align-items: center; gap: var(--space-3);">
                  <el-skeleton-item variant="circle" style="width: 40px; height: 40px;" />
                  <div>
                    <el-skeleton-item variant="text" style="width: 50px;" />
                    <el-skeleton-item variant="text" style="width: 70px; margin-top: var(--space-2);" />
                  </div>
                </div>
              </template>
              <template #default>
                <div class="stat-icon-wrap-h5">
                  <el-icon class="stat-icon-sm"><Calendar /></el-icon>
                </div>
                <div class="stat-content-h5">
                  <div class="stat-value">{{ reportData.learningDays }}</div>
                  <div class="stat-label">学习天数</div>
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="stat-card-h5" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div style="display: flex; align-items: center; gap: var(--space-3);">
                  <el-skeleton-item variant="circle" style="width: 40px; height: 40px;" />
                  <div>
                    <el-skeleton-item variant="text" style="width: 80px;" />
                    <el-skeleton-item variant="text" style="width: 70px; margin-top: var(--space-2);" />
                  </div>
                </div>
              </template>
              <template #default>
                <div class="stat-icon-wrap-h5">
                  <el-icon class="stat-icon-sm"><Timer /></el-icon>
                </div>
                <div class="stat-content-h5">
                  <div class="stat-value">{{ formatDuration(reportData.videoMinutes) }}</div>
                  <div class="stat-label">视频学习时长</div>
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="stat-card-h5" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div style="display: flex; align-items: center; gap: var(--space-3);">
                  <el-skeleton-item variant="circle" style="width: 40px; height: 40px;" />
                  <div>
                    <el-skeleton-item variant="text" style="width: 50px;" />
                    <el-skeleton-item variant="text" style="width: 70px; margin-top: var(--space-2);" />
                  </div>
                </div>
              </template>
              <template #default>
                <div class="stat-icon-wrap-h5">
                  <el-icon class="stat-icon-sm"><Edit /></el-icon>
                </div>
                <div class="stat-content-h5">
                  <div class="stat-value">{{ reportData.exerciseCount }}</div>
                  <div class="stat-label">完成练习数</div>
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="stat-card-h5" shadow="hover">
            <el-skeleton :loading="loading" animated>
              <template #template>
                <div style="display: flex; align-items: center; gap: var(--space-3);">
                  <el-skeleton-item variant="circle" style="width: 40px; height: 40px;" />
                  <div>
                    <el-skeleton-item variant="text" style="width: 50px;" />
                    <el-skeleton-item variant="text" style="width: 70px; margin-top: var(--space-2);" />
                  </div>
                </div>
              </template>
              <template #default>
                <div class="stat-icon-wrap-h5">
                  <el-icon class="stat-icon-sm"><Aim /></el-icon>
                </div>
                <div class="stat-content-h5">
                  <div class="stat-value">{{ reportData.accuracyPercent }}%</div>
                  <div class="stat-label">正确率</div>
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </el-col>
      </el-row>

      <!-- Check-in Calendar H5 -->
      <el-card class="detail-card-h5" shadow="never">
        <template #header>
          <div class="card-header-title">本周打卡</div>
        </template>
        <el-skeleton :loading="checkinLoading" animated>
          <template #template>
            <el-skeleton-item variant="text" style="width: 100%; height: 80px;" />
          </template>
          <template #default>
            <div class="checkin-calendar-h5">
              <div
                v-for="day in weekDays"
                :key="day.date"
                class="checkin-day-h5"
                :class="{ 'is-checked': day.checked }"
              >
                <span class="day-label">{{ day.label }}</span>
                <span class="day-dot" :class="{ 'is-checked': day.checked }"></span>
              </div>
            </div>
            <div v-if="weekCheckins.length === 0" class="empty-tip">
              <el-empty description="本周暂无打卡记录" :image-size="60" />
            </div>
          </template>
        </el-skeleton>
      </el-card>

      <!-- AI Suggestions H5 -->
      <el-card class="detail-card-h5 ai-card" shadow="never">
        <template #header>
          <div class="card-header-title">
            <el-icon class="header-icon"><Key /></el-icon>
            AI 学习建议
          </div>
        </template>
        <el-skeleton :loading="loading" animated>
          <template #template>
            <div class="skeleton-list">
              <el-skeleton-item variant="text" style="width: 90%; height: 18px; margin-bottom: var(--space-3);" />
              <el-skeleton-item variant="text" style="width: 75%; height: 18px; margin-bottom: var(--space-3);" />
              <el-skeleton-item variant="text" style="width: 80%; height: 18px;" />
            </div>
          </template>
          <template #default>
            <ul class="ai-suggestions">
              <li v-for="(tip, idx) in aiSuggestions" :key="idx" class="ai-tip-item">
                <el-icon class="tip-icon"><ArrowRight /></el-icon>
                <span>{{ tip }}</span>
              </li>
            </ul>
          </template>
        </el-skeleton>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, Timer, Edit, Aim, Key, ArrowRight } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import { getMyEnrollments } from '../../api/enrollment'
import { getMyCheckIns } from '../../api/checkin'

const userStore = useUserStore()

const loading = ref(false)
const checkinLoading = ref(false)
const enrollments = ref([])
const weekCheckins = ref([])
const windowWidth = ref(window.innerWidth)

const isMobile = computed(() => windowWidth.value < 768)

const reportData = ref({
  learningDays: 0,
  videoMinutes: 0,
  exerciseCount: 0,
  accuracyPercent: 0
})

const weekRangeLabel = computed(() => {
  const { start, end } = getWeekRange()
  const startDate = new Date(start)
  const endDate = new Date(end)
  const startMonth = startDate.getMonth() + 1
  const startDay = startDate.getDate()
  const endMonth = endDate.getMonth() + 1
  const endDay = endDate.getDate()
  return `${startMonth}月${startDay}日 - ${endMonth}月${endDay}日`
})

const weekDays = computed(() => {
  const { start } = getWeekRange()
  const startDate = new Date(start)
  const checkedDates = new Set(weekCheckins.value.map(r => r.checkInDate))
  const days = []
  const dayLabels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < 7; i++) {
    const d = new Date(startDate)
    d.setDate(startDate.getDate() + i)
    const dateStr = `${d.getMonth() + 1}/${d.getDate()}`
    const dateISO = d.toISOString().split('T')[0]
    days.push({
      date: dateISO,
      label: dayLabels[d.getDay()],
      dateStr,
      checked: checkedDates.has(dateISO) || checkedDates.has(d.toDateString())
    })
  }
  return days
})

const aiSuggestions = computed(() => {
  const tips = []
  if (reportData.value.learningDays < 5) {
    tips.push('本周学习天数偏少，建议每天保持至少30分钟的学习时间')
  }
  if (reportData.value.accuracyPercent < 70 && reportData.value.exerciseCount > 0) {
    tips.push('正确率有待提高，建议回顾错题，重点复习薄弱知识点')
  }
  if (reportData.value.videoMinutes < 120 && reportData.value.videoMinutes > 0) {
    tips.push('视频学习时长较短，建议多观看教学视频加深理解')
  }
  if (weekCheckins.value.length < 5) {
    tips.push('坚持每日打卡，培养良好的学习习惯')
  }
  if (tips.length === 0) {
    tips.push('本周学习状态良好，继续保持！')
    tips.push('建议定期回顾已学内容，巩固知识点')
  }
  return tips
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
    const res = await getMyEnrollments({ userId })
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

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
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

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* ===== Weekly Report ===== */
.weekly-report {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
}

/* ===== PC Layout ===== */
.page-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.page-title {
  margin: 0;
  font-size: var(--text-xl, 20px);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.week-range {
  font-size: var(--text-sm, 13px);
  color: var(--el-text-color-secondary);
}

.stats-row {
  margin-bottom: var(--space-4);
}

.cards-row {
  margin-bottom: var(--space-4);
}

/* Stat Card */
.stat-card {
  text-align: center;
  padding: var(--space-5) var(--space-4);
  border-radius: var(--radius-lg);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
  cursor: pointer;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.stat-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-circle);
  background: rgba(99, 102, 241, 0.12);
  color: var(--role-primary);
  margin-bottom: var(--space-3);
}

.stat-icon {
  font-size: var(--text-2xl);
  color: var(--el-color-white);
}

.stat-value {
  font-size: var(--text-2xl, 24px);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: var(--text-sm, 13px);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-2);
}

/* Detail Card */
.detail-card {
  border-radius: var(--radius-lg);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}

.detail-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.card-header-title {
  font-size: var(--text-md, 16px);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.header-icon {
  color: var(--role-primary);
}

/* Check-in Calendar */
.checkin-calendar {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--space-2);
  padding: var(--space-2) 0;
}

.checkin-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-2);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
  transition: background var(--duration-base) ease;
}

.checkin-day.is-checked {
  background: var(--role-primary-light);
}

.day-label {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary);
}

.day-date {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-regular);
  font-weight: var(--weight-medium);
}

.day-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-circle);
  background: var(--el-text-color-secondary);
  opacity: 0.3;
}

.day-dot.is-checked {
  background: var(--el-color-success);
  opacity: 1;
}

.empty-tip {
  padding: var(--space-4) 0;
}

/* AI Suggestions Card */
.ai-card {
  border-left: 3px solid var(--role-primary);
}

.ai-suggestions {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ai-tip-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-2) 0;
  font-size: var(--text-sm, 13px);
  color: var(--el-text-color-regular);
  line-height: var(--leading-normal);
}

.tip-icon {
  color: var(--role-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.skeleton-icon-wrap {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-3);
}

.skeleton-week {
  padding: var(--space-2) 0;
}

.skeleton-list {
  padding: var(--space-2) 0;
}

/* ===== H5 Layout ===== */
.page-header-h5 {
  margin-bottom: var(--space-4);
}

.page-title-h5 {
  margin: 0 0 var(--space-1) 0;
  font-size: var(--text-lg, 18px);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.week-range-h5 {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary);
}

.stats-row-h5 {
  margin-bottom: var(--space-3);
}

.stat-card-h5 {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-3);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
  cursor: pointer;
}

.stat-card-h5:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.stat-icon-wrap-h5 {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-circle);
  background: rgba(99, 102, 241, 0.12);
  color: var(--role-primary);
  flex-shrink: 0;
}

.stat-icon-sm {
  font-size: var(--text-lg);
  color: var(--el-color-white);
}

.stat-content-h5 {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: var(--text-lg, 18px);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.detail-card-h5 {
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-3);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}

.detail-card-h5:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

/* Check-in Calendar H5 */
.checkin-calendar-h5 {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--space-1);
  padding: var(--space-2) 0;
}

.checkin-day-h5 {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-2) 4px;
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
}

.checkin-day-h5.is-checked {
  background: var(--role-primary-light);
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .weekly-report {
    padding: var(--space-3);
  }
}

@media (max-width: 480px) {
  .stats-row-h5 .el-col {
    padding: 0 4px !important;
  }

  .stat-card-h5 {
    padding: var(--space-2);
  }
}
</style>