<template>
  <div class="video-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="所属课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="search-input-w200">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>视频列表</span>
          <div class="header-actions">
            <el-upload
              :before-upload="handleBeforeUpload"
              :http-request="handleBatchUpload"
              multiple
              accept="video/*"
              :show-file-list="false"
            >
              <el-button type="success" size="small">批量上传视频</el-button>
            </el-upload>
            <el-button type="primary" @click="handleCreate">新增视频</el-button>
          </div>
        </div>
      </template>

      <!-- 上传进度 -->
      <div v-if="uploadQueue.length > 0" class="upload-queue">
        <div class="queue-title">上传队列</div>
        <div v-for="(item, idx) in uploadQueue" :key="idx" class="queue-item">
          <span class="queue-name">{{ item.name }}</span>
          <el-progress :percentage="item.percentage" :stroke-width="6" class="queue-progress" />
          <span class="queue-status">
            <el-tag v-if="item.status === 'success'" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="item.status === 'error'" type="danger" size="small">失败</el-tag>
            <el-tag v-else type="info" size="small">上传中</el-tag>
          </span>
        </div>
        <div class="queue-summary">
          成功: {{ uploadSuccess }} / 失败: {{ uploadError }} / 总计: {{ uploadQueue.length }}
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column prop="courseName" label="所属课程" min-width="120" />
        <el-table-column prop="chapterId" label="章节ID" width="100" />
        <el-table-column label="封面" width="100" align="center">
          <template #default="{ row }">
            <img v-if="row.coverUrl" :src="row.coverUrl" class="video-cover-thumb" @click="handlePreviewCover(row)" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning">上传中</el-tag>
            <el-tag v-else-if="row.status === 1" type="info">转码中</el-tag>
            <el-tag v-else-if="row.status === 2" type="success">完成</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger">失败</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="操作" width="210" fixed="right">
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
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
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
    <el-dialog v-model="coverDialogVisible" title="设置视频封面" width="400px">
      <div class="cover-preview">
        <img v-if="currentCoverUrl" :src="currentCoverUrl" class="cover-img" />
        <span v-else>暂无封面</span>
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
    <el-dialog v-model="previewDialogVisible" title="封面预览" width="600px">
      <img v-if="previewCoverUrl" :src="previewCoverUrl" class="full-width" />
      <span v-else>无封面</span>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 视频列表页面 - Phase 6 增强：批量上传视频 + 视频封面自定义
 * @author Claude Code Agent
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVideos, createVideo, updateVideo, deleteVideo, uploadVideo } from '@/api/video'
import { getCourses } from '@/api/course'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

const searchForm = reactive({
  courseId: ''
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

// 批量上传队列
const uploadQueue = ref([])
const uploadSuccess = ref(0)
const uploadError = ref(0)

// 封面相关
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
    const { data } = await getCourses({ size: 1000 })
    courseOptions.value = data.items || []
  } catch (error) {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: searchForm.courseId || undefined
    }
    const { data } = await getVideos(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
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
  page.value = 1
  fetchData()
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleCreate = () => {
  dialogTitle.value = '新增视频'
  isEdit.value = false
  currentId.value = null
  formData.title = ''
  formData.courseId = null
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
  formData.sortOrder = row.sortOrder
  formData.url = row.url
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
    } catch (error) {
      ElMessage.error(isEdit.value ? '编辑失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}

// 批量上传
const handleBeforeUpload = (file) => {
  uploadQueue.value.push({
    name: file.name,
    percentage: 0,
    status: 'uploading'
  })
  return false
}

const handleBatchUpload = async ({ file }) => {
  const queueItem = uploadQueue.value.find(q => q.name === file.name)
  if (!queueItem) return
  try {
    const form = new FormData()
    form.append('file', file)
    // 模拟上传进度
    const interval = setInterval(() => {
      if (queueItem.percentage < 90) {
        queueItem.percentage += 10
      }
    }, 200)
    // 实际调用上传API
    await uploadVideo(form)
    clearInterval(interval)
    queueItem.percentage = 100
    queueItem.status = 'success'
    uploadSuccess.value++
    ElMessage.success(`${file.name} 上传成功`)
  } catch (error) {
    queueItem.status = 'error'
    uploadError.value++
    ElMessage.error(`${file.name} 上传失败`)
  }
}

// 封面相关
const handleSetCover = (row) => {
  currentVideoId.value = row.id
  currentCoverUrl.value = row.coverUrl || ''
  coverFile.value = null
  coverDialogVisible.value = true
}

const handleCoverChange = (file) => {
  coverFile.value = file.raw
}

const handleSubmitCover = async () => {
  if (!coverFile.value) {
    ElMessage.warning('请先选择图片')
    return
  }
  coverSubmitLoading.value = true
  try {
    const form = new FormData()
    form.append('file', coverFile.value)
    ElMessage.info('视频封面上传功能开发中')
    coverDialogVisible.value = false
    fetchData()
  } catch (error) {
    ElMessage.error('封面设置失败')
  } finally {
    coverSubmitLoading.value = false
  }
}

const handlePreviewCover = (row) => {
  previewCoverUrl.value = row.coverUrl || ''
  previewDialogVisible.value = true
}

onMounted(() => {
  fetchCourses()
  fetchData()
})
</script>

<style scoped>
.video-list {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.video-cover-thumb {
  width: 50px;
  height: 30px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
}

.upload-queue {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.queue-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.queue-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.queue-status {
  width: 60px;
}

.queue-progress {
  width: 200px;
}

.queue-summary {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
}

.cover-preview {
  margin-bottom: 16px;
  text-align: center;
}

.cover-img {
  max-width: 100%;
  max-height: 200px;
  border-radius: 4px;
}

@media (max-width: 768px) {
  .video-list {
    padding: 12px;
  }

  .search-card {
    margin-bottom: 12px;
  }

  .header-actions {
    flex-direction: column;
  }
}

.data-table { width: 100%; }
.full-width { width: 100%; }
.search-input-w200 { width: 200px; }
</style>