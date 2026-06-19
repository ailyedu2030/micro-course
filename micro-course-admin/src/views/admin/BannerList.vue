<!--
  管理员 - 轮播图管理
  /admin/banners
  Author: jackie
-->
<template>
  <div class="banner-list-container">
    <!-- 顶部工具栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="card-title">轮播图管理</span>
          <span class="card-count">共 {{ totalElements }} 张</span>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="handleAdd" aria-label="编辑"><el-icon><Plus /></el-icon>添加轮播图
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-result
        v-else-if="error"
        icon="error"
        title="加载失败"
        sub-title="请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">重试</el-button>
        </template>
      </el-result>
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        description="暂无轮播图"
        :image-size="120"
      />
      <el-table
        v-else
        v-loading="loading"
        :data="tableData"
        stripe
        border
        class="data-table"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="图片" width="200" align="center">
          <template #default="{ row }">
            <el-image
              :src="row.imageUrl"
              fit="cover"
              class="banner-image"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="linkUrl" label="跳转链接" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <a v-if="row.linkUrl" :href="row.linkUrl" target="_blank" class="banner-link">
              {{ row.linkUrl }}
            </a>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" sortable />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              active-text="启用"
              inactive-text="禁用"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)" aria-label="编辑"><el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)" aria-label="删除"><el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div v-if="tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" aria-label="分页导航" />
      </div>
    </el-card>

    <!-- 添加/编辑弹窗 -->
    <el-dialog>
      v-model="formVisible"
      :title="isEdit ? '编辑轮播图' : '添加轮播图'"
      width="600px"
      destroy-on-close
     :close-on-press-escape="true"
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        class="banner-form"
      >
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入轮播图标题" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="图片" prop="imageUrl">
          <div class="image-upload-wrap">
            <el-upload
              ref="imageUploadRef"
              class="image-uploader"
              :show-file-list="false"
              :auto-upload="false"
              :limit="1"
              accept="image/*"
              :on-change="handleImageChange"
            >
              <el-image
                v-if="form.imageUrl"
                :src="form.imageUrl"
                fit="cover"
                class="preview-image"
              />
              <div v-else class="upload-placeholder">
                <el-icon class="upload-icon"><Plus /></el-icon>
                <span class="upload-text">点击上传图片</span>
              </div>
            </el-upload>
            <div v-if="form.imageUrl" class="image-actions">
              <el-button type="danger" size="small" @click="handleRemoveImage" aria-label="删除"><el-icon><Delete /></el-icon>移除
              </el-button>
            </div>
          </div>
          <div class="form-tip">支持 JPG、PNG、WebP，建议尺寸 1920×600px，单张不超过 5MB</div>
        </el-form-item>
        <el-form-item label="跳转链接" prop="link">
          <el-input
            v-model="form.linkUrl"
            placeholder="https:// 或留空"
            type="url"
          />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
          <span class="form-tip-inline">数值越小越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" />
          <span class="form-hint">{{ form.enabled ? '启用' : '禁用' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleFormCancel">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleConfirmSave">
          {{ isEdit ? '保存修改' : '确认添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 管理员 - 轮播图管理
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getBanners,
  createBanner,
  updateBanner,
  deleteBanner,
  toggleBannerStatus
} from '@/api/admin-banner'

// 加载状态
const loading = ref(false)
const error = ref(false)
const saving = ref(false)

// 表格数据
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(20)

// 弹窗状态
const formVisible = ref(false)
const isEdit = ref(false)
const currentBannerId = ref(null)
const formRef = ref(null)
const imageUploadRef = ref(null)

// 表单数据
const form = reactive({
  title: '',
  imageUrl: '',
  linkUrl: '',
  sortOrder: 0,
  enabled: true
})

// 表单验证
const formRules = {
  title: [{ required: true, message: '请输入轮播图标题', trigger: ['blur', 'change'] }],
  imageUrl: [{ required: true, message: '请上传轮播图图片', trigger: ['blur', 'change'] }]
}

// 获取数据
async function fetchData() {
  loading.value = true
  error.value = false
  try {
    const res = await getBanners()
    tableData.value = res.data?.items || res.data || []
    totalElements.value = res.data?.total || 0
  } catch {
    error.value = true
    ElMessage.error('获取轮播图列表失败')
  } finally {
    loading.value = false
  }
}

// 添加
function handleAdd() {
  isEdit.value = false
  currentBannerId.value = null
  resetForm()
  formVisible.value = true
}

// 编辑
function handleEdit(row) {
  isEdit.value = true
  currentBannerId.value = row.id
  form.title = row.title || ''
  form.imageUrl = row.imageUrl || ''
  form.linkUrl = row.linkUrl || ''
  form.sortOrder = row.sortOrder || 0
  form.enabled = row.enabled
  formVisible.value = true
}

// 重置表单
function resetForm() {
  form.title = ''
  form.imageUrl = ''
  form.linkUrl = ''
  form.sortOrder = 0
  form.enabled = true
}

// 取消并重置校验
function handleFormCancel() {
  formRef.value?.resetFields()
  resetForm()
  formVisible.value = false
}

// 图片选择
function handleImageChange(file) {
  const raw = file.raw
  if (!raw) return
  // 客户端预览
  if (form.imageUrl && form.imageUrl.startsWith('blob:')) {
    URL.revokeObjectURL(form.imageUrl)
  }
  form.imageUrl = URL.createObjectURL(raw)
  form._rawFile = raw
}

// 移除图片
function handleRemoveImage() {
  if (form.imageUrl && form.imageUrl.startsWith('blob:')) {
    URL.revokeObjectURL(form.imageUrl)
  }

  form.imageUrl = ''
  form._rawFile = null
}

// 保存
async function handleConfirmSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const fd = new FormData()
    fd.append('title', form.title)
    fd.append('imageUrl', form.imageUrl || '')
    fd.append('linkUrl', form.linkUrl || '')
    fd.append('sortOrder', String(form.sortOrder))
    fd.append('enabled', String(form.enabled))
    if (form._rawFile) {
      fd.append('image', form._rawFile)
    }

    if (isEdit.value) {
      await updateBanner(currentBannerId.value, fd)
      ElMessage.success('操作成功')
    } else {
      await createBanner(fd)
      ElMessage.success('操作成功')
    }
    formVisible.value = false
    fetchData()
  } catch (err) {
    ElMessage.error(err.message || (isEdit.value ? '修改失败，请稍后重试' : '添加失败，请稍后重试'))
  } finally {
    setTimeout(() => { saving.value = false }, 3000)
  }
}

// 删除
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除轮播图「${row.title || row.id}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteBanner(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

// 切换状态
async function handleToggleStatus(row) {
  const newEnabled = !row.enabled
  try {
    await toggleBannerStatus(row.id, newEnabled)
    ElMessage.success(newEnabled ? '已启用' : '已禁用')
    fetchData()
  } catch (err) {
    ElMessage.error(err.message || '状态切换失败')
  }
}

// 翻页
function handleSizeChange() {
  page.value = 1
  fetchData()
}

function handlePageChange() {
  fetchData()
}

// 工具方法
function formatTime(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.banner-list-container {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100vh;
}

.toolbar-card {
  margin-bottom: 16px;
  border-radius: 12px;
  background: white;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.card-count {
  font-size: 14px;
  color: #64748B;
}

.table-card {
  border-radius: 12px;
  background: white;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.banner-image {
  width: 160px;
  height: 80px;
  border-radius: 8px;
  object-fit: cover;
}

.banner-link {
  color: #4F46E5;
  text-decoration: none;
  font-size: 13px;
}

.banner-link:hover {
  text-decoration: underline;
}

.data-table {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  background: #F8FAFC !important;
  color: #1E293B;
  font-weight: 600;
  font-size: 14px;
}

.data-table :deep(.el-table__row:hover > td) {
  background: #F1F5F9 !important;
}

.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.text-secondary {
  color: #64748B;
  font-size: 14px;
}

/* 表单 */
.banner-form :deep(.el-input),
.banner-form :deep(.el-input-number) {
  width: 320px;
}

.image-upload-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-uploader {
  width: 240px;
  height: 120px;
  border: 2px dashed #E2E8F0;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 200ms;
}

.image-uploader:hover {
  border-color: #4F46E5;
}

.preview-image {
  width: 240px;
  height: 120px;
  object-fit: cover;
  display: block;
}

.upload-placeholder {
  width: 240px;
  height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #F8FAFC;
}

.upload-icon {
  font-size: 28px;
  color: #94A3B8;
}

.upload-text {
  font-size: 13px;
  color: #94A3B8;
}

.image-actions {
  display: flex;
  gap: 8px;
}

.form-tip {
  font-size: 12px;
  color: #94A3B8;
  margin-top: 4px;
  line-height: 1.4;
}

.form-tip-inline {
  margin-left: 12px;
  font-size: 12px;
  color: #94A3B8;
}

.form-hint {
  margin-left: 12px;
  font-size: 13px;
  color: #64748B;
}

/* 弹窗 border-radius 12px */
:deep(.el-dialog) {
  border-radius: 12px;
}
:deep(.el-dialog__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
}
:deep(.el-dialog__body) {
  padding: 20px;
}
:deep(.el-dialog__footer) {
  padding: 16px 20px;
  border-top: 1px solid #F1F5F9;
}
</style>