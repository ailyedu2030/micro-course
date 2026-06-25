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
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="handleAdd" aria-label="添加轮播图">
<el-icon><Plus /></el-icon>添加轮播图
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
        v-loading="loading" :aria-busy="loading"
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
              alt="轮播图"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
            >
              <template #error>
                <div class="image-error">加载失败</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
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
            <el-button type="primary" link @click="handleEdit(row)" aria-label="编辑">
<el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)" aria-label="删除">
<el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="tableData.length > 0" class="count-wrap">
        <span class="text-secondary">共 {{ tableData.length }} 张轮播图</span>
      </div>
    </el-card>

    <!-- 添加/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑轮播图' : '添加轮播图'"
      width="600px"
      destroy-on-close
     :close-on-press-escape="true"
>
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        class="banner-form"
      >
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
                fit="contain"
                class="preview-image"
              >
                <template #error>
                  <div class="image-error">加载失败</div>
                </template>
              </el-image>
              <div v-else class="upload-placeholder">
                <el-icon class="upload-icon"><Plus /></el-icon>
                <span class="upload-text">点击上传图片</span>
              </div>
            </el-upload>
            <div v-if="form.imageUrl" class="image-actions">
              <el-button type="danger" size="small" @click="handleRemoveImage">
<el-icon><Delete /></el-icon>移除
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
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

// 弹窗状态
const formVisible = ref(false)
const isEdit = ref(false)
const currentBannerId = ref(null)
const formRef = ref(null)
const imageUploadRef = ref(null)

// 表单数据
const form = reactive({
  imageUrl: '',
  linkUrl: '',
  sortOrder: 0,
  enabled: true
})

// 表单验证
const formRules = {
  imageUrl: [{ required: true, message: '请上传轮播图图片', trigger: ['blur', 'change'] }]
}

// 获取数据
async function fetchData() {
  loading.value = true
  error.value = false
  try {
    const res = await getBanners()
    tableData.value = Array.isArray(res.data) ? res.data : []
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
  form.imageUrl = row.imageUrl || ''
  form.linkUrl = row.linkUrl || ''
  form.sortOrder = row.sortOrder || 0
  form.enabled = row.enabled
  formVisible.value = true
}

// 重置表单
function resetForm() {
  form.imageUrl = ''
  form.linkUrl = ''
  form.sortOrder = 0
  form.enabled = true
  form._rawFile = null
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
    saving.value = false
  }
}

// 删除
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除轮播图「${row.id}」吗？`,
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

onUnmounted(() => {
  // 清理未释放的 blob URL
  if (form.imageUrl && form.imageUrl.startsWith('blob:')) {
    URL.revokeObjectURL(form.imageUrl)
  }
})
</script>

<style scoped>
.banner-list-container {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.toolbar-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.card-count {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
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

.banner-image {
  width: 160px;
  height: 80px;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.banner-link {
  color: var(--role-primary);
  text-decoration: none;
  font-size: var(--text-sm);
}

.banner-link:hover {
  text-decoration: underline;
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light) !important;
  color: var(--el-text-color-primary);
  font-weight: var(--weight-semibold);
  font-size: var(--text-base);
  letter-spacing: var(--tracking-wide);
}

.data-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.pagination-wrap {
  margin-top: var(--space-6);
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.text-secondary {
  color: var(--el-text-color-secondary);
  font-size: var(--text-base);
}

/* 表单 */
.banner-form :deep(.el-input),
.banner-form :deep(.el-input-number) {
  width: 320px;
}

.image-upload-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.image-uploader {
  width: 240px;
  height: 120px;
  border: 2px dashed var(--el-border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: border-color var(--duration-base) var(--ease-out);
}

.image-uploader:hover {
  border-color: var(--role-primary);
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
  gap: var(--space-2);
  background: var(--el-fill-color-light);
}

.upload-icon {
  font-size: 28px;
  color: var(--el-text-color-placeholder);
}

.upload-text {
  font-size: var(--text-sm);
  color: var(--el-text-color-placeholder);
}

.image-actions {
  display: flex;
  gap: var(--space-2);
}

.form-tip {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  margin-top: var(--space-1);
  line-height: 1.4;
}

.form-tip-inline {
  margin-left: var(--space-3);
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
}

.form-hint {
  margin-left: var(--space-3);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

/* 弹窗 border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}
:deep(.el-dialog__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:deep(.el-dialog__body) {
  padding: var(--space-5);
}
:deep(.el-dialog__footer) {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>