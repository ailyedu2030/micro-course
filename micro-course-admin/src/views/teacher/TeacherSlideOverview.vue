<template>
  <div class="slide-overview">
    <header class="page-header">
      <button class="back-btn" @click="$router.push('/teacher/courses')" aria-label="返回">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <h1>互动课件管理</h1>
      <span class="page-subtitle" v-if="!loading">{{ filteredSlides.length }} 份课件</span>
    </header>

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
        <el-empty v-if="courses.length === 0" description="您还没有创建互动课程。前往「我的课程」→「新增课程」→课程类型选「互动课程」,上传 PPT 即可在此管理课件。" />
        <el-empty v-else description="您的互动课程尚未上传 PPT 课件。打开任一课程,点击「课件」→「上传 PPT」即可在此管理。" />
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
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click.stop="goEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getSlides, getSlidePages } from '@/plugins/interactive/api/slide'

const router = useRouter()
const loading = ref(false)
const slides = ref([])
const courses = ref([])

const searchForm = ref({
  courseId: '',
  status: ''
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
  loading.value = true
  try {
    const params = { courseType: 'INTERACTIVE', size: 100 }
    if (searchForm.value.courseId) params.teacherId = '' // 不需要 teacherId
    const { data } = await getCourses({ courseType: 'INTERACTIVE', size: 100 })
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

onMounted(loadData)
</script>

<style scoped>
.slide-overview { padding: 24px; max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 600; margin: 0; }
.page-subtitle { color: var(--el-text-color-secondary); font-size: 14px; }
.back-btn { background: transparent; border: none; cursor: pointer; padding: 8px; border-radius: 6px; }
.back-btn:hover { background: var(--el-fill-color-light); }
.content-card { background: var(--el-bg-color); border-radius: 8px; padding: 24px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); margin-bottom: 16px; }
.filter-input-w160 { width: 160px; }
.filter-input-w200 { width: 200px; }
.empty-tip { padding: 60px 0; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
</style>
