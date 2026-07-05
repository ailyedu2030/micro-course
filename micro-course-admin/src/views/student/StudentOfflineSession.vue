<template>
  <div class="offline-page">
    <div v-if="loading" class="page-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else>
      <div class="page-header">
        <el-button text @click="router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h1>{{ chapterTitle || '线下课' }}</h1>
        <span v-if="courseName" class="course-label">{{ courseName }}</span>
      </div>

      <div class="session-list" v-if="sessions.length > 0">
        <el-card
          v-for="session in sessions"
          :key="session.id"
          class="session-card"
          :class="{ 'session-current': isCurrentSession(session) }"
        >
          <div class="session-body">
            <div class="session-date-col">
              <div class="date-day">{{ formatDay(session.sessionDate) }}</div>
              <div class="date-weekday">{{ formatWeekday(session.sessionDate) }}</div>
            </div>
            <div class="session-info-col">
              <div class="info-row">
                <el-icon><Clock /></el-icon>
                <span>{{ formatTimeRange(session.startTime, session.endTime) }}</span>
              </div>
              <div class="info-row">
                <el-icon><Location /></el-icon>
                <span>{{ session.location || '待定' }}</span>
              </div>
              <div class="info-row info-notes" v-if="session.teacherNotes">
                <el-icon><ChatLineSquare /></el-icon>
                <span>{{ session.teacherNotes }}</span>
              </div>
            </div>
            <div class="session-action-col">
              <el-tag v-if="getAttendanceStatus(session) === 'CHECKED_IN'" type="success" effect="dark" size="large">✅ 已签到</el-tag>
              <el-button
                v-else-if="getAttendanceStatus(session) === 'CAN_CHECKIN'"
                type="primary"
                size="large"
                round
                @click="handleCheckin(session)"
                :loading="checkinLoading === session.id"
              >
                签到
              </el-button>
              <div v-else class="session-checkin-info">
                <el-tag type="info" effect="plain" size="large">不在签到时间</el-tag>
                <span class="checkin-window-hint">签到窗口：课前15分钟 ~ 课后30分钟</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <el-empty v-else description="暂无线下课程安排" :image-size="120" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Location, ChatLineSquare } from '@element-plus/icons-vue'
import { getOfflineSessions, checkin, getMyAttendance } from '@/api/offline-session'
import { getChapterById } from '@/api/chapter'

const router = useRouter()
const route = useRoute()
const chapterId = computed(() => route.params.chapterId)

const loading = ref(true)
const chapterTitle = ref('')
const courseName = ref('')
const sessions = ref([])
const attendanceMap = ref({})
const checkinLoading = ref(null)

function formatDay(dateStr) {
  if (!dateStr) return '--'
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function formatWeekday(dateStr) {
  if (!dateStr) return ''
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return days[new Date(dateStr).getDay()]
}

function formatTimeRange(start, end) {
  if (!start) return '--'
  const fmt = (t) => {
    if (!t) return ''
    if (t.length >= 5) return t.slice(0, 5)
    return t
  }
  const s = fmt(start)
  const e = fmt(end)
  return e ? `${s}-${e}` : s
}

function isCurrentSession(session) {
  if (!session.sessionDate) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const sDate = new Date(session.sessionDate)
  sDate.setHours(0, 0, 0, 0)
  return sDate.getTime() === today.getTime()
}

function getAttendanceStatus(session) {
  const record = attendanceMap.value[session.id]
  if (record) return 'CHECKED_IN'

  if (!session.sessionDate) return 'OUTSIDE'

  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const sDate = new Date(session.sessionDate)
  const sDay = new Date(sDate.getFullYear(), sDate.getMonth(), sDate.getDate())

  const diffDays = Math.floor((sDay.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))

  // 仅当天可签到；非当天显示为不可签到
  if (diffDays !== 0) return 'OUTSIDE'

  // P1C-020: 检查签到时间窗口（与后端一致：课前15分钟~课后30分钟，相对于 startTime）
  if (!session.startTime) return 'OUTSIDE'
  const CHECKIN_BEFORE = 15  // 课前分钟数
  const CHECKIN_AFTER = 30   // 课后分钟数
  const startParts = session.startTime.split(':').map(Number)
  if (startParts.length < 2) return 'OUTSIDE'
  const startMinutes = startParts[0] * 60 + startParts[1]
  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const windowStart = startMinutes - CHECKIN_BEFORE
  const windowEnd = startMinutes + CHECKIN_AFTER
  if (nowMinutes < windowStart || nowMinutes > windowEnd) return 'OUTSIDE'

  return 'CAN_CHECKIN'
}

async function handleCheckin(session) {
  checkinLoading.value = session.id
  try {
    await checkin(session.id)
    ElMessage.success('签到成功')
    await fetchAttendance()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '签到失败，请稍后重试')
  } finally {
    checkinLoading.value = null
  }
}

async function fetchChapter() {
  try {
    const { data } = await getChapterById(chapterId.value)
    if (data) {
      chapterTitle.value = data.title || ''
      courseName.value = data.courseName || data.courseTitle || ''
    }
  } catch {
    chapterTitle.value = '线下课程'
  }
}

async function fetchSessions() {
  try {
    const { data } = await getOfflineSessions(chapterId.value, { page: 0, size: 100 })
    sessions.value = (data?.items || data || [])
  } catch {
    sessions.value = []
  }
}

async function fetchAttendance() {
  try {
    const { data } = await getMyAttendance(chapterId.value)
    const map = {}
    if (Array.isArray(data)) {
      data.forEach(r => { map[r.sessionId] = r })
    } else if (data?.items) {
      data.items.forEach(r => { map[r.sessionId] = r })
    }
    attendanceMap.value = map
  } catch {
    attendanceMap.value = {}
  }
}

onMounted(async () => {
  await Promise.all([fetchChapter(), fetchSessions(), fetchAttendance()])
  loading.value = false
})
</script>

<style scoped>
.offline-page {
  max-width: 680px;
  margin: 0 auto;
  padding: var(--space-5);
  min-height: 100dvh;
  background: var(--el-bg-color-page);
}
.page-loading {
  padding: var(--space-6) 0;
}
.page-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
}
.page-header h1 {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin: 0;
  flex: 1;
}
.course-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.session-card {
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-base) var(--ease-out);
}
.session-card:hover {
  box-shadow: var(--shadow-md);
}
.session-current {
  border-left: 4px solid var(--el-color-primary);
}
.session-body {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}
.session-date-col {
  text-align: center;
  min-width: 64px;
  flex-shrink: 0;
}
.date-day {
  font-size: 28px;
  font-weight: var(--weight-bold);
  color: var(--el-color-primary);
  line-height: 1.2;
}
.date-weekday {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
}
.session-info-col {
  flex: 1;
  min-width: 0;
}
.info-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
  margin-bottom: var(--space-1);
}
.info-row:last-child {
  margin-bottom: 0;
}
.info-row .el-icon {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.info-notes {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  font-style: italic;
}
.session-action-col {
  flex-shrink: 0;
  min-width: 100px;
  text-align: center;
}
.session-action-col .el-button {
  min-height: 44px;
  min-width: 100px;
}
.session-action-col .el-tag {
  font-size: var(--text-sm);
  white-space: nowrap;
}
.session-checkin-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
}
.checkin-window-hint {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

@media (max-width: 480px) {
  .offline-page {
    padding: var(--space-3);
  }
  .session-body {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-3);
  }
  .session-date-col {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    min-width: auto;
  }
  .date-day {
    font-size: 20px;
  }
  .session-action-col {
    text-align: left;
  }
  .session-action-col .el-button {
    width: 100%;
  }
}
</style>
