<!--
  视频列表
  路由路径: /courses/:courseId/videos
  Phase 1
  Author: jackie
-->
<template>
  <div class="video-list-page">
    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="所属课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="filter-input-w200" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节" v-if="searchForm.courseId">
          <el-select v-model="searchForm.chapterId" placeholder="请选择章节" clearable class="filter-input-w200">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">视频列表</span>
          <div class="header-actions">
            <el-upload
              :before-upload="handleBeforeUpload"
              :http-request="handleBatchUpload"
              multiple
              accept="video/*"
              :show-file-list="false"
            >
              <el-tooltip v-if="userRole !== 'ACADEMIC'" content="请先选择课程和章节" placement="top">
                <el-button type="success" size="small" :disabled="!searchForm.courseId || !searchForm.chapterId">批量上传视频</el-button>
              </el-tooltip>
            </el-upload>
            <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增视频</el-button>
          </div>
        </div>
      </template>

      <!-- 上传队列 -->
      <div v-if="uploadQueue.length > 0" class="upload-queue">
        <div class="queue-title">上传队列</div>
        <div v-for="(item, idx) in uploadQueue" :key="idx" class="queue-item">
          <span class="queue-name">{{ item.name }}</span>
          <el-progress :percentage="item.percentage" :stroke-width="6" class="queue-progress" />
          <span class="queue-status">
            <el-tag v-if="item.status === 'success'" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="item.status === 'error'" type="danger" size="small">失败</el-tag>
            <el-tag v-else-if="item.status === 'cancelled'" type="info" size="small">已取消</el-tag>
            <el-tag v-else type="info" size="small">上传中</el-tag>
          </span>
          <el-button
            v-if="item.status === 'uploading'"
            type="danger"
            size="small"
            link
            @click="handleCancelUpload(idx)"
          >
取消
</el-button>
        </div>
        <div class="queue-summary">
          成功: {{ uploadSuccess }} / 失败: {{ uploadError }} / 总计: {{ uploadQueue.length }}
        </div>
      </div>

      <el-table v-loading="loading" :aria-busy="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <el-empty description="暂无视频数据" />
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="courseName" label="所属课程" min-width="120" />
        <el-table-column label="封面" width="90" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              fit="cover"
              class="table-thumb"
              :preview-src-list="[row.coverUrl]"
              lazy
              @click="handlePreviewCover(row)"
            />
            <span v-else class="no-thumb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning" size="small">上传中</el-tag>
            <el-tag v-else-if="row.status === 1" type="info" size="small">转码中</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">完成</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger" size="small">失败</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status ?? '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100" align="center">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleSetCover(row)">设置封面</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" aria-label="分页导航"
/>
      </div>
    </el-card>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入视频标题" />
        </el-form-item>
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" class="full-width">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节ID" prop="chapterId">
          <el-input v-model="formData.chapterId" placeholder="请输入章节ID" type="number" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="视频URL" prop="url">
          <el-input v-model="formData.url" placeholder="请输入视频URL" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 封面设置弹窗 -->
    <el-dialog v-model="coverDialogVisible" title="设置视频封面" width="400px" :close-on-press-escape="true">
      <div class="cover-preview">
        <el-image v-if="currentCoverUrl" :src="currentCoverUrl" fit="contain" class="cover-img" />
        <span v-else class="no-cover">暂无封面</span>
      </div>
      <el-upload
        ref="coverUploadRef"
        :auto-upload="false"
        :limit="1"
        accept="image/*"
        :on-change="handleCoverChange"
      >
        <el-button type="primary" size="small">选择图片</el-button>
      </el-upload>
      <template #footer>
        <el-button @click="coverDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="coverSubmitLoading" @click="handleSubmitCover">确定</el-button>
      </template>
    </el-dialog>

    <!-- 封面预览弹窗 -->
    <el-dialog v-model="previewDialogVisible" title="封面预览" width="600px" :close-on-press-escape="true">
      <el-image v-if="previewCoverUrl" :src="previewCoverUrl" fit="contain" class="preview-img" />
      <span v-else class="no-cover">无封面</span>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoCamera } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getVideos, createVideo, updateVideo, deleteVideo, uploadVideoCover, uploadVideo } from '@/api/video'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getToken } from '@/utils/auth'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])
const chapterOptions = ref([])

const searchForm = reactive({
  courseId: '',
  chapterId: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增视频')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  title: '',
  courseId: null,
  chapterId: null,
  sortOrder: 0,
  url: ''
})

const formRules = {
  title: [{ required: true, message: '请输入视频标题', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
  url: [{ required: true, message: '请输入视频URL', trigger: 'blur' }]
}

const uploadQueue = ref([])
const uploadSuccess = ref(0)
const uploadError = ref(0)
const uploadXHRMap = ref({}) // store XHR objects for cancel

const coverDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const coverSubmitLoading = ref(false)
const currentVideoId = ref(null)
const currentCoverUrl = ref('')
const previewCoverUrl = ref('')
const coverFile = ref(null)
const coverUploadRef = ref(null)

const fetchCourses = async () => {
  try {
    const params = { page: 0, size: 1000 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
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
      page: page.value - 1,
      size: size.value
    }
    const { data } = await getVideos(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取视频列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = ''
  searchForm.chapterId = ''
  chapterOptions.value = []
  page.value = 1
  tableData.value = []
  totalElements.value = 0
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

const handleBeforeUpload = (file) => {
  const item = {
    name: file.name,
    percentage: 0,
    status: 'uploading'
  }
  uploadQueue.value.push(item)
  return true
}

const handleCancelUpload = (idx) => {
  const item = uploadQueue.value[idx]
  if (item && item.xhr) {
    item.xhr.abort()
    item.status = 'cancelled'
  }
}

const handleBatchUpload = async ({ file }) => {
  const queueItem = uploadQueue.value.find(i => i.name === file.name)
  if (!queueItem) return

  const courseId = searchForm.courseId
  const chapterId = searchForm.chapterId
  if (!courseId || !chapterId) {
    queueItem.status = 'error'
    uploadError.value++
    ElMessage.error('请先选择课程和章节')
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('courseId', courseId)
    formData.append('chapterId', chapterId)

    const xhr = new XMLHttpRequest()
    queueItem.xhr = xhr

    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        queueItem.percentage = Math.round((e.loaded / e.total) * 100)
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        queueItem.status = 'success'
        uploadSuccess.value++
        ElMessage.success(`${file.name} 上传成功`)
        fetchData()
      } else {
        queueItem.status = 'error'
        uploadError.value++
        try {
          const err = JSON.parse(xhr.responseText)
          ElMessage.error(`${file.name} 上传失败: ${err.message || '未知错误'}`)
        } catch {
          ElMessage.error(`${file.name} 上传失败`)
        }
      }
      delete uploadXHRMap.value[file.name]
    })

    xhr.addEventListener('error', () => {
      queueItem.status = 'error'
      uploadError.value++
      ElMessage.error(`${file.name} 上传失败`)
      delete uploadXHRMap.value[file.name]
    })

    xhr.addEventListener('abort', () => {
      queueItem.status = 'cancelled'
      delete uploadXHRMap.value[file.name]
    })

    const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
    xhr.open('POST', `${baseURL}/api/videos/upload`)
    const token = getToken()
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }
    xhr.send(formData)
  } catch {
    queueItem.status = 'error'
    uploadError.value++
    ElMessage.error(`${file.name} 上传失败`)
  }
}

const handleCourseChange = async (courseId) => {
  searchForm.chapterId = ''
  chapterOptions.value = []
  if (!courseId) return
  try {
    const { data } = await getChapters({ courseId, size: 1000 })
    chapterOptions.value = data.items || []
  } catch {
    // chapters are optional for search; silently fail
  }
}

const handleCreate = () => {
  dialogTitle.value = '新增视频'
  isEdit.value = false
  currentId.value = null
  formData.title = ''
  formData.courseId = searchForm.courseId ? Number(searchForm.courseId) : null
  formData.chapterId = null
  formData.sortOrder = 0
  formData.url = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑视频'
  isEdit.value = true
  currentId.value = row.id
  formData.title = row.title
  formData.courseId = row.courseId
  formData.chapterId = row.chapterId
  formData.sortOrder = row.sortOrder || 0
  formData.url = row.url || ''
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该视频?', '提示', { type: 'warning' })
    await deleteVideo(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateVideo(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createVideo(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? '编辑失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

const handleSetCover = (row) => {
  currentVideoId.value = row.id
  currentCoverUrl.value = row.coverUrl || ''
  coverFile.value = null
  coverDialogVisible.value = true
}

const handleCoverChange = (file) => {
  coverFile.value = file.raw
  currentCoverUrl.value = URL.createObjectURL(file.raw)
}

const handleSubmitCover = async () => {
  if (!coverFile.value) {
    ElMessage.warning('请选择封面图片')
    return
  }
  coverSubmitLoading.value = true
  try {
    await uploadVideoCover(currentVideoId.value, coverFile.value)
    ElMessage.success('封面上传成功')
    coverDialogVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('上传失败')
  } finally {
    coverSubmitLoading.value = false
  }
}

const handlePreviewCover = (row) => {
  if (!row.coverUrl) return
  previewCoverUrl.value = row.coverUrl
  previewDialogVisible.value = true
}

onMounted(() => {
  fetchCourses()
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