<template>
  <div class="slide-overview">
    <header class="page-header">
      <button class="back-btn" @click="$router.push('/teacher/courses')" aria-label="返回">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <h1>互动课件工作台</h1>
      <span class="page-subtitle" v-if="!loading">{{ filteredSlides.length }} 份课件</span>
      <div class="header-actions">
        <el-button type="primary" @click="router.push('/teacher/courses')">查看我的课程</el-button>
      </div>
    </header>

    <section class="stats-row" v-if="!loading">
      <div class="stat-card"><span class="stat-num">{{ slides.length }}</span><span class="stat-label">全部课件</span></div>
      <div class="stat-card stat-success"><span class="stat-num">{{ stats.ready }}</span><span class="stat-label">就绪</span></div>
      <div class="stat-card stat-warning"><span class="stat-num">{{ stats.rendering }}</span><span class="stat-label">渲染中</span></div>
      <div class="stat-card stat-danger"><span class="stat-num">{{ stats.failed }}</span><span class="stat-label">失败</span></div>
    </section>

    <section class="content-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="全部课程" clearable class="filter-input-w200" @change="loadData">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable class="filter-input-w160" @change="applyFilter">
            <el-option label="上传中" :value="0" />
            <el-option label="渲染中" :value="1" />
            <el-option label="就绪" :value="2" />
            <el-option label="失败" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="content-card">
      <div v-if="!loading && filteredSlides.length === 0" class="empty-tip">
        <el-empty v-if="courses.length === 0" description="您还没有互动课程，去课程列表创建。">
          <el-button type="primary" @click="router.push('/teacher/courses')">前往课程列表</el-button>
        </el-empty>
        <el-empty v-else description="已选课程尚未上传课件。从左侧课程列表选择一门课程，进入后上传 PPT 即可。">
          <el-button @click="handleReset">查看全部课程</el-button>
        </el-empty>
      </div>
      <el-table v-else :data="filteredSlides" stripe v-loading="loading" @row-click="goEdit">
        <el-table-column prop="courseTitle" label="所属课程" min-width="180" show-overflow-tooltip />
        <el-table-column prop="fileName" label="课件文件" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总页数" width="90" align="center">
          <template #default="{ row }">{{ row.totalPages || '-' }}</template>
        </el-table-column>
        <el-table-column label="AI 讲解" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.narrationReadyCount" size="small" type="success">{{ row.narrationReadyCount }}/{{ row.totalPages }}</el-tag>
            <span v-else class="muted">未生成</span>
          </template>
        </el-table-column>
        <el-table-column label="TTS 音频" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.audioReadyCount" size="small" type="success">{{ row.audioReadyCount }}/{{ row.totalPages }}</el-tag>
            <span v-else class="muted">未生成</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最后更新" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click.stop="goEdit(row)">编辑</el-button>
            <el-button link size="small" type="danger" :disabled="deleting === row.courseId" @click.stop="handleDelete(row)">{{ deleting === row.courseId ? '删除中…' : '删除' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>


  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getSlides, getSlidePages, deleteSlide } from '@/plugins/interactive/api/slide'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const slides = ref([])
const courses = ref([])
const initialized = ref(false)
const deleting = ref(null)

const searchForm = ref({
  courseId: '',
  status: ''
})



const stats = computed(() => {
  const all = slides.value
  return {
    ready: all.filter(s => s.status === 2).length,
    rendering: all.filter(s => s.status === 0 || s.status === 1).length,
    failed: all.filter(s => s.status === 3).length,
  }
})

const statusMap = {
  0: { label: '上传中', type: 'warning' },
  1: { label: '渲染中', type: 'warning' },
  2: { label: '就绪', type: 'success' },
  3: { label: '失败', type: 'danger' },
}
function statusLabel(s) { return statusMap[s]?.label || '未知' }
function statusType(s) { return statusMap[s]?.type || 'info' }
function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const filteredSlides = computed(() => {
  if (searchForm.value.status === '' || searchForm.value.status === null) return slides.value
  return slides.value.filter(s => String(s.status) === String(searchForm.value.status))
})

async function loadData() {
  if (!userStore.userInfo?.id && !initialized.value) {
    try {
      await userStore.getInfo()
    } catch { /* ignore */ }
    initialized.value = true
  }
  loading.value = true
  try {
    const { data } = await getCourses({ size: 1000, teacherId: userStore.userInfo?.id })
    const courseList = data?.items || data?.content || data?.records || []
    courses.value = courseList

    const slidePromises = courseList
      .filter(c => !searchForm.value.courseId || String(c.id) === String(searchForm.value.courseId))
      .map(async c => {
        try {
          const s = await getSlides(c.id)
          if (!s.data) return null
          let narrationReady = 0
          let audioReady = 0
          try {
            const p = await getSlidePages(c.id)
            const pages = p.data || []
            narrationReady = pages.filter(pg => pg.narrationStatus && pg.narrationStatus !== 'PENDING').length
            audioReady = pages.filter(pg => pg.narrationStatus === 'AUDIO_READY').length
          } catch { /* skip */ }
          return { ...s.data, courseTitle: c.title, narrationReadyCount: narrationReady, audioReadyCount: audioReady }
        } catch { return null }
      })
    const results = await Promise.all(slidePromises)
    slides.value = results.filter(Boolean)
  } catch (e) {
    console.warn('[SlideOverview] load error', e)
  } finally {
    loading.value = false
  }
}

function applyFilter() { /* computed 触发 */ }
function handleReset() {
  searchForm.value.courseId = ''
  searchForm.value.status = ''
  loadData()
}
function goEdit(row) {
  router.push(`/teacher/courses/${row.courseId}/slides/manage`)
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除课程「${row.courseTitle}」的课件「${row.fileName}」？此操作不可恢复。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleting.value = row.courseId
  try {
    await deleteSlide(row.courseId)
    ElMessage.success('课件已删除')
    slides.value = slides.value.filter(s => s.courseId !== row.courseId)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    deleting.value = null
  }
}



onMounted(() => loadData())
</script>

<style scoped>
.slide-overview { padding: 24px; max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 600; margin: 0; }
.page-subtitle { color: var(--el-text-color-secondary); font-size: 14px; }
.header-actions { margin-left: auto; }
.back-btn { background: transparent; border: none; cursor: pointer; padding: 8px; border-radius: 6px; }
.back-btn:hover { background: var(--el-fill-color-light); }
.content-card { background: var(--el-bg-color); border-radius: 8px; padding: 24px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); margin-bottom: 16px; }
.filter-input-w160 { width: 160px; }
.filter-input-w200 { width: 200px; }
.empty-tip { padding: 60px 0; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.stats-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; background: var(--el-bg-color); border-radius: 8px; padding: 20px 24px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); text-align: center; }
.stat-num { display: block; font-size: 28px; font-weight: 700; color: var(--el-text-color-primary); }
.stat-label { display: block; font-size: 13px; color: var(--el-text-color-secondary); margin-top: 4px; }
.stat-success .stat-num { color: var(--el-color-success); }
.stat-warning .stat-num { color: var(--el-color-warning); }
.stat-danger .stat-num { color: var(--el-color-danger); }
:deep(.full-width) { width: 100%; }
</style>
