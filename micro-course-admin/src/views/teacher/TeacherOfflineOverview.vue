<template>
  <div class="offline-overview">
    <header class="page-header">
      <button class="back-btn" @click="$router.push('/teacher/courses')" aria-label="返回">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <h1>线下课程管理</h1>
      <span class="page-subtitle" v-if="!loading">
        {{ sessionRows.length }} 个课堂,总学生 {{ totalStudents }} 人
      </span>
    </header>

    <section class="content-card">
      <div class="empty-tip" v-if="!loading && sessionRows.length === 0">
        <el-empty description="暂无线下课堂。请先在课程章节中将章节类型设置为「线下」并添加排期。">
          <el-button type="primary" @click="$router.push('/teacher/courses')">前往课程管理</el-button>
        </el-empty>
      </div>

      <el-table v-else :data="sessionRows" stripe v-loading="loading">
        <el-table-column prop="courseTitle" label="所属课程" min-width="160" show-overflow-tooltip />
        <el-table-column prop="chapterTitle" label="章节" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sessionDate" label="日期" min-width="110" />
        <el-table-column prop="timeRange" label="时间" min-width="120" />
        <el-table-column prop="location" label="地点" min-width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="goCheckin(row)">签到</el-button>
            <el-button link size="small" @click="goAttendance(row)">出勤</el-button>
            <el-button link size="small" @click="goEdit(row)">排期</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getOfflineSessions } from '@/api/offline-session'
import { getChapters } from '@/api/chapter'

const router = useRouter()
const loading = ref(false)
const sessionRows = ref([])

const totalStudents = computed(() => sessionRows.value.reduce((s, r) => s + (r.studentCount || 0), 0))

function fmtDate(d) {
  if (!d) return ''
  const x = new Date(d)
  if (isNaN(x.getTime())) return ''
  return `${x.getFullYear()}-${String(x.getMonth()+1).padStart(2,'0')}-${String(x.getDate()).padStart(2,'0')}`
}
function fmtTime(t) {
  if (!t) return ''
  const [h,m] = String(t).split(':')
  return h && m ? `${h}:${m}` : ''
}
function timeRange(s) {
  if (!s) return ''
  const start = fmtTime(s.startTime || s.sessionStartTime)
  const end = fmtTime(s.endTime || s.sessionEndTime)
  return start && end ? `${start} - ${end}` : start || end || ''
}
function statusLabel(s) {
  return { SCHEDULED: '已排期', IN_PROGRESS: '进行中', COMPLETED: '已结束', CANCELLED: '已取消' }[s] || s || ''
}
function statusType(s) {
  return { SCHEDULED: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', CANCELLED: 'info' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const { data } = await getCourses({ courseType: 'OFFLINE', size: 100 })
    const courses = data?.items || data?.content || data?.records || []

    const allRows = []
    for (const course of courses) {
      let chapters = []
      try {
        const ch = await getChapters({ courseId: course.id, page: 0, size: 100 })
        chapters = ch.data?.items || ch.data?.content || ch.data?.records || []
      } catch { /* skip */ }
      const offlineChapters = chapters.filter(c => c.chapterType === 'OFFLINE')
      for (const ch of offlineChapters) {
        try {
          const r = await getOfflineSessions(ch.id, { page: 0, size: 100 })
          const sessions = r.data?.items || r.data?.content || r.data?.records || []
          for (const s of sessions) {
            allRows.push({
              ...s,
              courseId: course.id,
              courseTitle: course.title,
              chapterId: ch.id,
              chapterTitle: ch.title || `章节 ${ch.sortOrder || ''}`,
              sessionDate: fmtDate(s.sessionDate || s.date),
              timeRange: timeRange(s),
              location: s.location || '',
            })
          }
        } catch { /* skip */ }
      }
    }
    allRows.sort((a, b) => (a.sessionDate || '').localeCompare(b.sessionDate || ''))
    sessionRows.value = allRows
  } catch (e) {
    console.warn('[OfflineOverview] load error', e)
  } finally {
    loading.value = false
  }
}

function goCheckin(row) { router.push(`/teacher/chapters/${row.chapterId}/offline-sessions?sessionId=${row.id}`) }
function goAttendance(row) { router.push(`/teacher/chapters/${row.chapterId}/offline-sessions?sessionId=${row.id}`) }
function goEdit(row) { router.push(`/teacher/chapters/${row.chapterId}/offline-sessions`) }

onMounted(loadData)
</script>

<style scoped>
.offline-overview { padding: 24px; max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 600; margin: 0; }
.page-subtitle { color: var(--el-text-color-secondary); font-size: 14px; }
.back-btn { background: transparent; border: none; cursor: pointer; padding: 8px; border-radius: 6px; }
.back-btn:hover { background: var(--el-fill-color-light); }
.content-card { background: var(--el-bg-color); border-radius: 8px; padding: 24px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.empty-tip { padding: 60px 0; }
</style>
