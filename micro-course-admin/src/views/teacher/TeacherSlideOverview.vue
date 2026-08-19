<template>
  <div class="slide-overview">
    <header class="page-header">
      <button class="back-btn" @click="$router.push('/teacher/courses')" :aria-label="$t('app.back')">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <h1>{{ $t('teacherSlideOverview.workbench') }}</h1>
      <span class="page-subtitle" v-if="!loading">{{ $t('teacherSlideOverview.coursewareCount', { count: filteredSlides.length }) }}</span>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openUploadDialog">{{ $t('teacherSlideOverview.uploadCourseware') }}</el-button>
      </div>
    </header>

    <section class="stats-row" v-if="!loading">
      <div class="stat-card"><span class="stat-num">{{ slides.length }}</span><span class="stat-label">{{ $t('teacherSlideOverview.allCourseware') }}</span></div>
      <div class="stat-card stat-success"><span class="stat-num">{{ stats.ready }}</span><span class="stat-label">{{ $t('teacherSlideOverview.ready') }}</span></div>
      <div class="stat-card stat-warning"><span class="stat-num">{{ stats.rendering }}</span><span class="stat-label">{{ $t('teacherSlideOverview.rendering') }}</span></div>
      <div class="stat-card stat-danger"><span class="stat-num">{{ stats.failed }}</span><span class="stat-label">{{ $t('teacherSlideOverview.failed') }}</span></div>
    </section>

    <section class="content-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('teacherSlideOverview.course')">
          <el-select v-model="searchForm.courseId" :placeholder="$t('teacherSlideOverview.allCourses')" clearable class="filter-input-w200" @change="loadData">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.status')">
          <el-select v-model="searchForm.status" :placeholder="$t('teacherSlideOverview.allStatuses')" clearable class="filter-input-w160" @change="applyFilter">
            <el-option :label="$t('teacherSlideOverview.uploading')" :value="0" />
            <el-option :label="$t('teacherSlideOverview.rendering')" :value="1" />
            <el-option :label="$t('teacherSlideOverview.ready')" :value="2" />
            <el-option :label="$t('teacherSlideOverview.failed')" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('teacherSlideOverview.coursewareType')">
          <el-select v-model="searchForm.coursewareType" :placeholder="$t('course.allTypes')" clearable class="filter-input-w160" @change="applyFilter">
            <el-option :label="$t('course.typePptCourseware')" value="PPT" />
            <el-option :label="$t('course.typeHtmlCourseware')" value="HTML" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ $t('app.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="content-card">
      <div v-if="!loading && filteredSlides.length === 0" class="empty-tip">
        <el-empty v-if="courses.length === 0" :description="$t('teacherSlideOverview.noCoursewareCourses')">
          <el-button type="primary" @click="router.push('/teacher/courses')">{{ $t('teacherSlideOverview.goCourseList') }}</el-button>
        </el-empty>
        <el-empty v-else :description="emptyDescription">
          <el-button v-if="searchForm.courseId" @click="handleReset">{{ $t('teacherSlideOverview.viewAllCourses') }}</el-button>
          <el-button v-else type="primary" @click="openUploadDialog">{{ $t('teacherSlideOverview.uploadCourseware') }}</el-button>
        </el-empty>
      </div>
      <el-table v-else :data="displaySlides" stripe v-loading="loading">
        <el-table-column prop="courseTitle" :label="$t('teacherSlideOverview.belongCourse')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="chapterTitle" :label="$t('teacherSlideOverview.belongChapter')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.chapterTitle || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('teacherSlideOverview.coursewareType')" width="110">
          <template #default="{ row }">
            <el-tag v-if="row._coursewareType === 'PPT'" size="small" type="primary">PPT</el-tag>
            <el-tag v-else size="small" type="success">HTML</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('course.status')" width="100">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 3 && row.errorMessage" :content="row.errorMessage" placement="top">
              <el-tag size="small" type="danger">{{ $t('teacherSlideOverview.failed') }}</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('teacherSlideOverview.totalPages')" width="90" align="center">
          <template #default="{ row }">{{ row.totalPages || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('teacherSlideOverview.aiNarration')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.narrationReadyCount" size="small" type="success">{{ row.narrationReadyCount }}/{{ row.totalPages }}</el-tag>
            <span v-else class="muted">{{ $t('teacherSlideOverview.notGenerated') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('teacherSlideOverview.ttsAudio')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.audioReadyCount" size="small" type="success">{{ row.audioReadyCount }}/{{ row.totalPages }}</el-tag>
            <span v-else class="muted">{{ $t('teacherSlideOverview.notGenerated') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('teacherSlideOverview.lastUpdated')" width="170" :formatter="$formatDateTime" />
        <el-table-column :label="$t('teacherSlideOverview.fileName')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="renaming === row.id">
              <el-input v-model="renameValue" size="small" style="width:140px" @keyup.enter="confirmRename(row)" @keyup.esc="cancelRename" />
              <el-button link size="small" type="primary" @click="confirmRename(row)">{{ $t('course.dialogConfirm') }}</el-button>
              <el-button link size="small" @click="cancelRename">{{ $t('app.cancel') }}</el-button>
            </template>
            <span v-else>{{ row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" link size="small" type="primary" @click.stop="goEdit(row)">{{ $t('course.view') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" link size="small" @click.stop="startRename(row)">{{ $t('teacherSlideOverview.rename') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" link size="small" type="danger" :disabled="deleting === row.id" @click.stop="handleDelete(row)">{{ deleting === row.id ? $t('teacherSlideOverview.deleting') : $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-show="filteredSlides.length > 0" style="margin-top:16px;display:flex;justify-content:center">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredSlides.length"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="page = 1"
        />
      </div>
    </section>

    <!-- 上传课件对话框 -->
    <el-dialog v-model="uploadDialogVisible" :title="$t('teacherSlideOverview.uploadTitle')" width="500px" @close="resetUploadDialog">
      <el-form label-width="100px">
        <el-form-item :label="$t('teacherSlideOverview.belongCourse')" prop="courseId">
          <el-select v-model="uploadForm.courseId" :placeholder="$t('course.selectCourse')" class="full-width" filterable @change="onCourseChange">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('teacherSlideOverview.belongChapter')" prop="chapterId">
          <el-select v-model="uploadForm.chapterId" :placeholder="$t('course.selectChapter')" class="full-width" :disabled="!uploadForm.courseId">
            <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('teacherSlideOverview.coursewareFile')" prop="file">
          <el-upload :show-file-list="true" accept=".pptx,.html,.htm" :auto-upload="false" :on-change="onFileChange">
            <el-button type="primary" :icon="UploadFilled">{{ $t('teacherSlideOverview.chooseFile') }}</el-button>
            <template #tip>
              <div class="el-upload__tip">
                {{ $t('teacherSlideOverview.uploadTipPart1') }}
                <strong>{{ $t('teacherSlideOverview.chapterLevelUpload') }}</strong>{{ $t('teacherSlideOverview.anchorHint') }}
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" :disabled="uploading" @click="submitUpload">{{ $t('teacherSlideOverview.startUpload') }}</el-button>
      </template>
    </el-dialog>
</div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, UploadFilled } from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getSlides, listSlides, getSlidePages, deleteSlide, deleteSlideById, updateSlideName, uploadSlide } from '@/plugins/interactive/api/slide'
import { ElInput } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const router = useRouter()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const loading = ref(false)
const slides = ref([])
const courses = ref([])
const initialized = ref(false)
const page = ref(1)
const pageSize = ref(20)
const deleting = ref(null)
const renaming = ref(null)
const renameValue = ref('')
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadForm = ref({ courseId: null, chapterId: null, file: null })
const chapterOptions = ref([])

const searchForm = ref({
  courseId: '',
  status: '',
  coursewareType: ''
})

// 课件类型派生（根因修复：优先读后端 SlideVO.coursewareType 权威字段，
// fileUrl 兜底兼容历史数据/旧后端）。后端字段来自 section.courseware_type。
function deriveCoursewareType(slide) {
  if (slide?.coursewareType) return slide.coursewareType
  return slide?.fileUrl?.startsWith('html:') ? 'HTML' : 'PPT'
}



const stats = computed(() => {
  const all = slides.value
  return {
    ready: all.filter(s => s.status === 2).length,
    rendering: all.filter(s => s.status === 0 || s.status === 1).length,
    failed: all.filter(s => s.status === 3).length,
  }
})

const statusMap = {
  0: { labelKey: 'uploading', type: 'warning' },
  1: { labelKey: 'rendering', type: 'warning' },
  2: { labelKey: 'ready', type: 'success' },
  3: { labelKey: 'failed', type: 'danger' },
}
function statusLabel(s) { const m = statusMap[s]; return m ? t(`teacherSlideOverview.${m.labelKey}`) : t('course.unknown') }
function statusType(s) { return statusMap[s]?.type || 'info' }
function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const filteredSlides = computed(() => {
  let list = slides.value.map(s => ({ ...s, _coursewareType: deriveCoursewareType(s) }))
  if (searchForm.value.status !== '' && searchForm.value.status !== null) {
    list = list.filter(s => String(s.status) === String(searchForm.value.status))
  }
  if (searchForm.value.coursewareType) {
    list = list.filter(s => s._coursewareType === searchForm.value.coursewareType)
  }
  // 默认排序：同类相邻（PPT 在前、HTML 在后），同类内按更新时间倒序
  return list.slice().sort((a, b) => {
    if (a._coursewareType !== b._coursewareType) {
      return a._coursewareType === 'PPT' ? -1 : 1
    }
    return new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0)
  })
})
const displaySlides = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredSlides.value.slice(start, start + pageSize.value)
})

const emptyDescription = computed(() => {
  if (searchForm.value.courseId) {
    return t('teacherSlideOverview.emptyWithCourse')
  }
  return t('teacherSlideOverview.emptyAll')
})

async function loadData() {
  if (!userStore.userId && !initialized.value) {
    try {
      await userStore.getInfo()
    } catch { /* ignore */ }
    initialized.value = true
  }
  if (!userStore.userId) {
    courses.value = []
    slides.value = []
    ElMessage.error(t('teacherSlideOverview.cannotGetTeacher'))
    return
  }
  loading.value = true
  try {
    const { data } = await getCourses({ size: 100, teacherId: userStore.userId })
    const courseList = data?.items || data?.content || data?.records || []
    courses.value = courseList

    const slidePromises = courseList
      .filter(c => !searchForm.value.courseId || String(c.id) === String(searchForm.value.courseId))
      .map(async c => {
        try {
          const res = await listSlides(c.id)
          const slideList = res.data || []
          if (slideList.length === 0) return null
          const p = await getSlidePages(c.id)
          const allPages = p.data || []
          return slideList.map(s => {
            const slidePages = allPages.filter(pg => pg.slideId === s.id)
            return {
              ...s, courseTitle: c.title,
              narrationReadyCount: slidePages.filter(pg => pg.narrationStatus && pg.narrationStatus !== 'PENDING').length,
              audioReadyCount: slidePages.filter(pg => pg.narrationStatus === 'AUDIO_READY').length
            }
          })
        } catch { return null }
      })
    const results = await Promise.all(slidePromises)
    slides.value = results.filter(Boolean).flat()
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
  searchForm.value.coursewareType = ''
  loadData()
}
function goEdit(row) {
  const query = row.sectionId ? { sectionId: row.sectionId } : {}
  router.push({ path: `/teacher/courses/${row.courseId}/slides/manage`, query })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('teacherSlideOverview.confirmDeleteCourseware', { name: row.fileName }),
      t('teacherSlideOverview.confirmDeleteTitle'),
      { type: 'warning', confirmButtonText: t('app.delete'), cancelButtonText: t('app.cancel') }
    )
  } catch {
    return
  }
  deleting.value = row.id
  try {
    await deleteSlideById(row.courseId, row.id)
    ElMessage.success(t('teacherSlideOverview.coursewareDeleted'))
    slides.value = slides.value.filter(s => s.id !== row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('course.deleteFailed'))
  } finally {
    deleting.value = null
  }
}

function startRename(row) {
  renaming.value = row.id
  renameValue.value = row.fileName || ''
}

async function confirmRename(row) {
  if (!renameValue.value.trim()) { ElMessage.warning(t('teacherSlideOverview.fileNameRequired')); return }
  try {
    await updateSlideName(row.courseId, row.id, renameValue.value.trim())
    ElMessage.success(t('teacherSlideOverview.renameSuccess'))
    row.fileName = renameValue.value.trim()
    renaming.value = null
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('teacherSlideOverview.renameFailed'))
  }
}

function cancelRename() {
  renaming.value = null
}

// 上传课件对话框
function openUploadDialog() {
  uploadDialogVisible.value = true
}

function resetUploadDialog() {
  uploadForm.value = { courseId: null, chapterId: null, file: null }
  chapterOptions.value = []
  uploading.value = false
}

async function onCourseChange(courseId) {
  uploadForm.value.chapterId = null
  if (!courseId) { chapterOptions.value = []; return }
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    chapterOptions.value = data?.items || []
  } catch { chapterOptions.value = [] }
}

function onFileChange(uploadFile) {
  // 从 el-upload 的 change 事件中获取原始文件
  uploadForm.value.file = uploadFile.raw
  return false
}

async function submitUpload() {
  if (!uploadForm.value.courseId || !uploadForm.value.chapterId || !uploadForm.value.file) {
    ElMessage.warning(t('teacherSlideOverview.selectCourseChapterFile'))
    return
  }
  uploading.value = true
  try {
    const res = await uploadSlide(uploadForm.value.courseId, uploadForm.value.file, (e) => {
      // progress callback, can add progress bar later
    }, uploadForm.value.chapterId)
    // F-2026-08-10-08: 优先展示后端 message（包含锚点 section 提示等透明化信息）
    const backendMsg = res?.data?.message
    ElMessage.success(backendMsg || t('teacherSlideOverview.uploadSuccess'))
    uploadDialogVisible.value = false
    loadData()  // 刷新列表
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('teacherSlideOverview.uploadFailed'))
  } finally {
    uploading.value = false
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
