<!--
  视频列表
  路由路径: /courses/:courseId/videos
  Phase 1
  Author: jackie
-->
<template>
  <div class="video-list-page">
    <!-- 课程上下文头部 -->
    <div v-if="courseIdFromRoute && courseTitle" class="course-context">
      <el-breadcrumb separator="→">
        <el-breadcrumb-item :to="{ path: courseListPath }">{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: courseDetailPath(courseIdFromRoute) }">{{ courseTitle }}</el-breadcrumb-item>
        <el-breadcrumb-item v-if="isContextualMode">{{ chapterTitle || $t('videoList.chapterVideos') }}</el-breadcrumb-item>
        <el-breadcrumb-item v-else>{{ $t('videoList.videoManagement') }}</el-breadcrumb-item>
        <el-breadcrumb-item v-if="isContextualMode">
          <el-link type="primary" :underline="'never'" :to="{ path: courseDetailPath(courseIdFromRoute) }">
            {{ $t('videoList.backToCourse') }}
          </el-link>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('videoList.belongCourse')" v-if="!isContextualMode && !courseIdFromRoute">
          <el-select v-model="searchForm.courseId" :placeholder="$t('videoList.selectCourse')" clearable class="filter-input-w200" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.chapter')" v-if="searchForm.courseId">
          <el-select v-model="searchForm.chapterId" :placeholder="$t('course.pleaseSelectChapter')" clearable :disabled="isContextualMode" class="filter-input-w200">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('videoList.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ isContextualMode ? $t('videoList.chapterVideoList') : $t('videoList.videoList') }}</span>
          <div class="header-actions">
            <el-button type="primary" v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" @click="handleCreate">
              <el-icon><Plus /></el-icon>
              {{ isContextualMode ? $t('videoList.addVideo') : $t('videoList.createVideo') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :aria-busy="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <el-empty :description="$t('videoList.emptyData')" />
        </template>
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="courseName" :label="$t('videoList.belongCourse')" min-width="120" />
        <el-table-column prop="chapterName" :label="$t('videoList.chapterColumn')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('course.cover')" width="90" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              :alt="$t('course.coverAlt', { title: row.title || $t('course.typeVideo') })"
              fit="cover"
              class="table-thumb"
              :preview-src-list="[row.coverUrl]"
              lazy
              @click="handlePreviewCover(row)"
            />
            <span v-else class="no-thumb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('course.status')" width="180" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 3 && row.errorMessage" :content="row.errorMessage" placement="top">
              <el-tag type="danger" size="small">{{ $t('videoList.statusFailed') }}</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.status === 0" type="warning" size="small">{{ $t('videoList.statusUploading') }}</el-tag>
            <el-tag v-else-if="row.status === 1" type="info" size="small">{{ $t('videoList.statusTranscoding', { progress: row.progress || 0 }) }}</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">{{ $t('videoList.statusCompleted') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status ?? '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" :label="$t('videoList.fileSize')" width="100" align="center">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" :label="$t('course.sortOrder')" width="80" align="center" />
        <el-table-column :label="$t('app.operation')" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="success" link size="small" @click="handleSetCover(row)">{{ $t('videoList.setCover') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
            <el-button v-if="row.status === 3 && (userRole === 'TEACHER' || userRole === 'ADMIN')" type="warning" link size="small" :loading="retryingId === row.id" @click="handleRetry(row)">{{ $t('videoList.retryTranscode') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
        <div class="page-size-wrap">
          <label for="page-size-select-video" class="sr-only">{{ $t('course.perPage') }}</label>
          <el-select id="page-size-select-video" :model-value="size" class="page-size-select" @change="handleSizeChange" :aria-label="$t('course.perPage')">
            <el-option v-for="s in [10, 20, 50, 100]" :key="s" :label="$t('course.perPageOption', { count: s })" :value="s" />
          </el-select>
        </div>
      </div>
    </el-card>

    <!-- 弹窗表单（统一：新增+编辑） -->
    <el-dialog v-model="dialogVisible" :title="$t(dialogTitle)" width="600px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item v-if="!isEdit" :label="$t('videoList.videoFile')" prop="file">
          <el-upload
            :auto-upload="false"
            :limit="20"
            multiple
            accept="video/*"
            :on-change="handleDialogFileChange"
            :on-remove="handleDialogFileRemove"
            drag
          >
            <div class="upload-trigger">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">{{ $t('videoList.uploadText') }}</div>
              <div class="upload-hint">{{ $t('videoList.uploadHint') }}</div>
            </div>
            <template #tip>
              <div class="el-upload__tip">{{ $t('videoList.uploadTip') }}</div>
            </template>
          </el-upload>
        </el-form-item>
        <div v-if="!isEdit && uploadQueueItems.length > 0" class="upload-queue" aria-live="polite">
          <div class="queue-title">{{ uploadQueueSummary }}</div>
          <div v-for="item in uploadQueueItems" :key="item.id" class="queue-item">
            <span class="queue-name">{{ item.name }}</span>
            <el-progress
              class="queue-progress"
              :percentage="item.progress"
              :stroke-width="6"
              :status="item.status === 'error' ? 'exception' : item.status === 'success' ? 'success' : ''"
            />
            <span class="queue-status" :class="`queue-status-${item.status}`">{{ formatQueueStatus(item) }}</span>
          </div>
          <div class="queue-summary">
            {{ isBatchUpload ? $t('videoList.batchUploadHint') : $t('videoList.uploadDoneHint') }}
          </div>
        </div>
        <el-form-item :label="$t('course.tableTitle')" prop="title">
          <el-input
            v-model="formData.title"
            :disabled="isBatchUpload"
            :placeholder="isBatchUpload ? $t('videoList.batchTitlePlaceholder') : $t('videoList.titlePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('videoList.belongCourse')" prop="courseId" v-if="!isContextualMode || isEdit">
          <el-select v-model="formData.courseId" :placeholder="$t('videoList.selectCourse')" class="full-width" :disabled="isContextualMode || isEdit" @change="handleDialogCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('videoList.chapterColumn')" prop="chapterId">
          <el-select v-model="formData.chapterId" :placeholder="$t('course.pleaseSelectChapter')" class="full-width" :disabled="isContextualMode">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.sortOrder')" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" :disabled="isBatchUpload" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-progress
            v-if="!isEdit && submitProgress > 0"
            :percentage="submitProgress"
            :stroke-width="4"
            :status="submitProgress >= 100 ? 'success' : ''"
            class="footer-progress"
          />
          <div class="footer-buttons">
            <el-button @click="dialogVisible = false" :disabled="submitLoading">{{ $t('app.cancel') }}</el-button>
            <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 封面设置弹窗 -->
    <el-dialog v-model="coverDialogVisible" :title="$t('videoList.setCoverTitle')" width="400px" :close-on-press-escape="true" @close="handleCoverDialogClose">
      <div class="cover-preview">
        <el-image v-if="currentCoverUrl" :src="currentCoverUrl" :alt="$t('videoList.coverPreviewAlt')" fit="contain" class="cover-img" />
        <span v-else class="no-cover">{{ $t('course.noCoverText') }}</span>
      </div>
      <el-upload
        :auto-upload="false"
        :limit="1"
        accept="image/*"
        :on-change="handleCoverChange"
      >
        <el-button type="primary" size="small">{{ $t('course.selectImage') }}</el-button>
      </el-upload>
      <template #footer>
        <el-button @click="handleCoverDialogClose">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="coverSubmitLoading" :disabled="coverSubmitLoading" @click="handleSubmitCover">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 封面预览弹窗 -->
    <el-dialog v-model="previewDialogVisible" :title="$t('course.coverPreviewAlt')" width="600px" :close-on-press-escape="true">
      <el-image v-if="previewCoverUrl" :src="previewCoverUrl" :alt="$t('course.coverPreviewAlt')" fit="contain" class="preview-img" />
      <span v-else class="no-cover">{{ $t('videoList.noCover') }}</span>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, UploadFilled } from '@element-plus/icons-vue'
import { useCourseWorkspaceRoutes } from '@/composables/useCourseWorkspaceRoutes'
import { useVideoUploadQueue } from '@/composables/useVideoUploadQueue'
import { useUserStore } from '@/store/user'
import { getVideos, updateVideo, deleteVideo, uploadVideoCover, uploadVideo, retryVideoTranscode, getVideoStatus, getVideoStatusBatch } from '@/api/video'
import { getCourses, getCourseById } from '@/api/course'
import { getChapters, getChapterById } from '@/api/chapter'

const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()
const courseIdFromRoute = computed(() => route.params.courseId)
const lockedChapterId = computed(() => {
  const id = route.params.chapterId || route.query.chapterId
  if (id === undefined || id === null || id === '') return null
  const num = Number(id)
  return Number.isNaN(num) ? null : num
})
const isContextualMode = computed(() => lockedChapterId.value !== null)
const userRole = computed(() => userStore.role)
const {
  courseListPath,
  courseDetailPath
} = useCourseWorkspaceRoutes({
  userRoleRef: userRole
})

const loading = ref(false)
const submitLoading = ref(false)
const retryingId = ref(null)
const submitProgress = ref(0)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])
const chapterOptions = ref([])
const courseTitle = ref('')
const chapterTitle = ref('')

const searchForm = reactive({
  courseId: '',
  chapterId: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('videoList.createVideo')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  title: '',
  courseId: null,
  chapterId: null,
  sortOrder: 0,
  file: null
})

const formRules = computed(() => ({
  title: courseIdFromRoute.value || isBatchUpload.value ? [] : [{ required: true, message: t('videoList.titleRequired'), trigger: 'blur' }],
  courseId: courseIdFromRoute.value ? [] : [{ required: true, message: t('videoList.courseRequired'), trigger: 'change' }],
  chapterId: [{ required: true, message: t('videoList.chapterRequired'), trigger: 'change' }],
  file: isEdit.value ? [] : [{ required: true, message: t('videoList.fileRequired'), trigger: 'change' }]
}))

const videoUploadQueue = useVideoUploadQueue({
  uploader: ({ file, courseId, chapterId, onProgress }) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('courseId', courseId)
    if (chapterId !== null && chapterId !== undefined && chapterId !== '') {
      fd.append('chapterId', chapterId)
    }
    return uploadVideo(fd, onProgress)
  }
})
const uploadQueueItems = videoUploadQueue.queue
const uploadQueueSummary = videoUploadQueue.summaryText
const isBatchUpload = computed(() => videoUploadQueue.isBatchMode.value)

const coverDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const coverSubmitLoading = ref(false)
const currentVideoId = ref(null)
const currentCoverUrl = ref('')
const previewCoverUrl = ref('')
const coverFile = ref(null)

const revokeCoverPreviewUrl = () => {
  if (currentCoverUrl.value && currentCoverUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(currentCoverUrl.value)
  }
}

const resetCoverDialogState = () => {
  revokeCoverPreviewUrl()
  currentCoverUrl.value = ''
  coverFile.value = null
  currentVideoId.value = null
}

const fetchCourses = async () => {
  try {
    const params = { page: 0, size: 100 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error(t('course.fetchCoursesFailed'))
  }
}

const fetchData = async () => {
  if (!searchForm.courseId) {
    tableData.value = []
    totalElements.value = 0
    return
  }
  loading.value = true
  try {
    const params = {
      courseId: searchForm.courseId,
      chapterId: searchForm.chapterId || undefined,
      page: page.value - 1,
      size: size.value
    }
    const { data } = await getVideos(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
    startPollingIfNeeded()
  } catch {
    ElMessage.error(t('videoList.fetchFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = courseIdFromRoute.value ? Number(courseIdFromRoute.value) : ''
  const contextualChapterId = lockedChapterId.value || ''
  searchForm.chapterId = contextualChapterId
  chapterOptions.value = []
  page.value = 1
  tableData.value = []
  totalElements.value = 0
  if (searchForm.courseId) {
    handleCourseChange(searchForm.courseId).finally(() => {
      searchForm.chapterId = contextualChapterId
      fetchData()
    })
  }
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const formatFileSize = (size) => {
  if (!size) return '-'
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)}MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)}GB`
}

const handleCourseChange = async (courseId) => {
  searchForm.chapterId = ''
  chapterOptions.value = []
  if (!courseId) return
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    chapterOptions.value = data.items || []
  } catch {
    // chapters are optional for search; silently fail
  }
}

const handleCreate = () => {
  dialogTitle.value = 'videoList.addVideo'
  isEdit.value = false
  currentId.value = null
  formData.title = ''
  formData.file = null
  formData.courseId = courseIdFromRoute.value ? Number(courseIdFromRoute.value)
    : (searchForm.courseId ? Number(searchForm.courseId) : null)
  formData.chapterId = lockedChapterId.value || (searchForm.chapterId ? Number(searchForm.chapterId) : null)
  formData.sortOrder = 0
  videoUploadQueue.clearQueue()
  submitProgress.value = 0
  if (formData.courseId) handleDialogCourseChange(formData.courseId)
  dialogVisible.value = true
}

const handleDialogCourseChange = async (courseId) => {
  formData.chapterId = null
  if (!courseId) { chapterOptions.value = []; return }
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    // chapterType列已删除,显示所有章节
    chapterOptions.value = data.items || []
  } catch { chapterOptions.value = [] }
}

const handleEdit = async (row) => {
  dialogTitle.value = 'videoList.editVideo'
  isEdit.value = true
  currentId.value = row.id
  formData.title = row.title
  formData.courseId = row.courseId
  formData.chapterId = row.chapterId
  formData.sortOrder = row.sortOrder || 0
  await handleDialogCourseChange(row.courseId)
  if (row.chapterId && !chapterOptions.value.find(c => c.id === row.chapterId)) {
    formData.chapterId = null
  }
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('videoList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deleteVideo(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('course.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      if (isEdit.value) {
        await updateVideo(currentId.value, { title: formData.title, sortOrder: formData.sortOrder, chapterId: formData.chapterId })
        ElMessage.success(t('question.editSuccess'))
        dialogVisible.value = false
        fetchData()
      } else {
        const result = await videoUploadQueue.uploadAll({
          courseId: formData.courseId,
          chapterId: formData.chapterId
        })
        submitProgress.value = result.successCount > 0
          ? Math.round((result.successCount * 100) / (result.successCount + result.failureCount))
          : 0

        if (result.failureCount === 0) {
          await new Promise((resolve) => setTimeout(resolve, 500))
          ElMessage.success(result.successCount > 1 ? t('videoList.batchUploadSuccess', { count: result.successCount }) : t('course.createSuccess'))
          dialogVisible.value = false
          fetchData()
          nextTick(() => startPollingIfNeeded())
        } else if (result.successCount > 0) {
          ElMessage.warning(t('videoList.uploadPartialSuccess', { successCount: result.successCount, failureCount: result.failureCount }))
          fetchData()
          nextTick(() => startPollingIfNeeded())
        } else {
          ElMessage.error(t('videoList.uploadFailed'))
        }
      }
    } catch (e) {
      // P1-C 修复: 显示真实错误而不是通用"创建失败"
      const msg = e?.response?.data?.message || e?.message || (isEdit.value ? t('question.editFailed') : t('course.createFailed'))
      ElMessage.error(msg)
    } finally {
      submitLoading.value = false
      submitProgress.value = 0
    }
  })
}

const getFileTitle = (file) => file?.name?.replace(/\.[^.]+$/, '') || ''

const syncSelectedFiles = (selectedFiles = []) => {
  videoUploadQueue.replaceQueue(selectedFiles)
  formData.file = selectedFiles[0] || null

  if (selectedFiles.length === 0) {
    formData.title = ''
    formRef.value?.clearValidate?.(['file', 'title'])
    return
  }

  if (selectedFiles.length === 1) {
    if (!formData.title) {
      formData.title = getFileTitle(selectedFiles[0])
    }
    formRef.value?.clearValidate?.(['file', 'title'])
    return
  }

  formData.title = ''
  formData.sortOrder = 0
  formRef.value?.clearValidate?.(['file', 'title'])
}

const handleDialogFileChange = (uploadFile, uploadFiles = []) => {
  const selectedFiles = uploadFiles
    .map((item) => item?.raw)
    .filter(Boolean)

  if (selectedFiles.length === 0 && uploadFile?.raw) {
    selectedFiles.push(uploadFile.raw)
  }

  syncSelectedFiles(selectedFiles)
}

const handleDialogFileRemove = (...args) => {
  const uploadFiles = args[1] || []
  const selectedFiles = uploadFiles
    .map((item) => item?.raw)
    .filter(Boolean)

  syncSelectedFiles(selectedFiles)
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  videoUploadQueue.clearQueue()
  formData.file = null
  formData.title = ''
  formData.sortOrder = 0
}

const handleSetCover = (row) => {
  resetCoverDialogState()
  currentVideoId.value = row.id
  currentCoverUrl.value = row.coverUrl || ''
  coverFile.value = null
  coverDialogVisible.value = true
}

const handleCoverChange = (file) => {
  if (!file?.raw) return
  revokeCoverPreviewUrl()
  coverFile.value = file.raw
  currentCoverUrl.value = URL.createObjectURL(file.raw)
}

const handleCoverDialogClose = () => {
  coverDialogVisible.value = false
  resetCoverDialogState()
}

const handleSubmitCover = async () => {
  if (!coverFile.value) {
    ElMessage.warning(t('videoList.selectCoverImage'))
    return
  }
  coverSubmitLoading.value = true
  try {
    await uploadVideoCover(currentVideoId.value, coverFile.value)
    ElMessage.success(t('videoList.coverUploadSuccess'))
    await fetchData()
    handleCoverDialogClose()
  } catch {
    ElMessage.error(t('userList.uploadFailed'))
  } finally {
    coverSubmitLoading.value = false
  }
}

const handleRetry = async (row) => {
  try { await ElMessageBox.confirm(t('videoList.confirmRetryTranscode'), t('app.confirm'), { type: 'warning' }) } catch { return }
  retryingId.value = row.id
  try { await retryVideoTranscode(row.id); ElMessage.success(t('videoList.transcodeSubmitted')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('videoList.retryFailed')) }
  finally { retryingId.value = null }
}

const handlePreviewCover = (row) => {
  if (!row.coverUrl) return
  previewCoverUrl.value = row.coverUrl
  previewDialogVisible.value = true
}

const formatQueueStatus = (item) => {
  if (item.status === 'success') return t('videoList.statusCompleted')
  if (item.status === 'error') return t('operationLogs.failed')
  if (item.status === 'uploading') return `${item.progress}%`
  return t('videoList.pendingUpload')
}

/* ================================================================
   P1-C 修复: 转码进度轮询
   检测表格内未完成(0=上传中/1=转码中)的视频, 每 5s 拉取状态。
   确认全部完成或组件卸载时停止。
   ================================================================ */
let pollTimer = null

async function pollTranscodeProgress() {
  const pending = tableData.value.filter(r => r.status === 0 || r.status === 1)
  if (pending.length === 0) {
    stopPolling()
    return
  }
  try {
    const ids = pending.map(r => r.id)
    const { data } = await getVideoStatusBatch(ids)
    if (Array.isArray(data)) {
      const statusMap = {}
      data.forEach(vo => { statusMap[vo.videoId] = vo })
      pending.forEach(row => {
        const vo = statusMap[row.id]
        if (vo) {
          Object.assign(row, {
            status: vo.status,
            progress: vo.progress,
            errorMessage: vo.errorMessage
          })
        }
      })
    }
    const stillPending = tableData.value.filter(r => r.status === 0 || r.status === 1)
    if (stillPending.length === 0) {
      stopPolling()
      fetchData()
    }
  } catch (e) {
    console.warn('[VideoList] pollTranscodeProgress error', e?.message)
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(pollTranscodeProgress, 5000)
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPollingIfNeeded() {
  const hasPending = tableData.value.some(r => r.status === 0 || r.status === 1)
  if (hasPending) startPolling()
}

onMounted(() => {
  fetchCourses().then(async () => {
    const cid = courseIdFromRoute.value
    if (cid) {
      try {
        const { data } = await getCourseById(Number(cid))
        courseTitle.value = data?.title || ''
      } catch (e) {
        console.warn('[VideoList] getCourseById failed', e?.message)
      }
      searchForm.courseId = Number(cid)
      await handleCourseChange(Number(cid))
      if (isContextualMode.value) {
        try {
          const { data } = await getChapterById(lockedChapterId.value)
          chapterTitle.value = data?.title || ''
          searchForm.chapterId = lockedChapterId.value
        } catch (e) {
          console.warn('[VideoList] getChapterById failed', e?.message)
        }
      }
      fetchData()
    }
  })
})

onUnmounted(() => {
  stopPolling()
  revokeCoverPreviewUrl()
})
</script>

<style scoped>
.video-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.course-context {
  margin-bottom: var(--space-4);
}

.filter-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.table-thumb {
  width: 48px;
  height: 32px;
  border-radius: var(--radius-md);
  object-fit: cover;
  cursor: pointer;
}

.no-thumb {
  color: var(--el-text-color-placeholder);
}

.upload-queue {
  margin-bottom: var(--space-6);
  padding: var(--space-3);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
}

.queue-title {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-2);
}

.queue-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.queue-name {
  width: 200px;
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-progress {
  flex: 1;
}

.queue-status {
  width: 60px;
}

.queue-summary {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.cover-preview {
  margin-bottom: var(--space-3);
  text-align: center;
}

.cover-img {
  max-width: 100%;
  max-height: 300px;
  border-radius: var(--radius-md);
}

.preview-img {
  width: 100%;
  border-radius: var(--radius-md);
}

.no-cover {
  color: var(--el-text-color-placeholder);
  font-size: var(--text-sm);
}

.full-width {
  width: 100%;
}

.dialog-footer {
  display: flex;
  align-items: center;
  width: 100%;
  gap: var(--space-3);
}

.footer-progress {
  flex: 1;
}

.footer-buttons {
  display: flex;
  gap: var(--space-2);
}

.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-5) var(--space-6);
  border: 2px dashed var(--el-border-color);
  border-radius: var(--radius-md);
  background: var(--el-fill-color-light);
  cursor: pointer;
  transition: border-color var(--duration-base);
}

.upload-trigger:hover {
  border-color: var(--el-color-primary);
}

.upload-icon {
  font-size: 36px;
  color: var(--el-color-primary);
  margin-bottom: var(--space-2);
}

.upload-text {
  font-size: 14px;
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-1);
}

.upload-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.search-input,
.filter-input {
  width: 160px;
  border-radius: var(--radius-md);
}

.search-select,
.filter-select {
  width: 160px;
}

.filter-input-w200 {
  width: 200px;
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .video-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .header-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .pagination-wrap {
    justify-content: center;
  }

  .queue-name {
    width: 120px;
  }
}
</style>
