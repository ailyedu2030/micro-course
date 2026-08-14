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
          <span class="card-title">{{ $t('bannerList.title') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-if="userRole === 'ADMIN'" type="primary" @click="handleAdd" :aria-label="$t('bannerList.addBanner')">
            <el-icon><Plus /></el-icon>{{ $t('bannerList.addBanner') }}
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
        :title="$t('bannerList.loadFailed')"
        :sub-title="$t('bannerList.loadFailedSubtitle')"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        :description="$t('bannerList.empty')"
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
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column :label="$t('bannerList.image')" width="200" align="center">
          <template #default="{ row }">
            <el-image
              :src="row.imageUrl"
              fit="cover"
              class="banner-image"
              :alt="$t('bannerList.bannerAlt')"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
            >
              <template #error>
                <div class="image-error">{{ $t('bannerList.imageLoadFailed') }}</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="linkUrl" :label="$t('bannerList.linkUrl')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <a v-if="row.linkUrl" :href="row.linkUrl" target="_blank" rel="noopener noreferrer" class="banner-link">
              {{ row.linkUrl }}
            </a>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" :label="$t('bannerList.sortOrder')" width="80" align="center" sortable />
        <el-table-column prop="enabled" :label="$t('bannerList.status')" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :aria-label="$t('bannerList.enabledAria')"
              :active-text="$t('bannerList.enabled')"
              :inactive-text="$t('bannerList.disabled')"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('bannerList.createdAt')" width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="userRole === 'ADMIN'" type="primary" link @click="handleEdit(row)" :aria-label="$t('app.edit')">
            <el-icon><Edit /></el-icon>{{ $t('app.edit') }}
            </el-button>
            <el-button v-if="userRole === 'ADMIN'" type="danger" link @click="handleDelete(row)" :aria-label="$t('app.delete')">
            <el-icon><Delete /></el-icon>{{ $t('app.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="tableData.length > 0" class="count-wrap">
        <span class="text-secondary">{{ $t('bannerList.totalCount', { count: tableData.length }) }}</span>
      </div>
    </el-card>

    <!-- 添加/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? $t('bannerList.editTitle') : $t('bannerList.addTitle')"
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
        <el-form-item :label="$t('bannerList.image')" prop="imageUrl">
          <div class="image-upload-wrap">
            <el-upload
              ref="imageUploadRef"
              class="image-uploader"
              :show-file-list="false"
              :auto-upload="false"
              :limit="1"
              accept="image/jpeg,image/jpg,image/png,image/webp"
              :before-upload="handleBeforeUpload"
              :on-change="handleImageChange"
            >
              <el-image
                v-if="form.imageUrl"
                :src="form.imageUrl"
                fit="contain"
                class="preview-image"
              >
                <template #error>
                  <div class="image-error">{{ $t('bannerList.imageLoadFailed') }}</div>
                </template>
              </el-image>
              <div v-else class="upload-placeholder">
                <el-icon class="upload-icon"><Plus /></el-icon>
                <span class="upload-text">{{ $t('bannerList.clickUpload') }}</span>
              </div>
            </el-upload>
            <div v-if="form.imageUrl" class="image-actions">
              <el-button type="danger" size="small" @click="handleRemoveImage">
<el-icon><Delete /></el-icon>{{ $t('bannerList.remove') }}
              </el-button>
            </div>
          </div>
          <div class="form-tip">{{ $t('bannerList.formTip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('bannerList.linkUrl')" prop="linkUrl">
          <el-input
            v-model="form.linkUrl"
            :placeholder="$t('bannerList.linkPlaceholder')"
            type="url"
          />
        </el-form-item>
        <el-form-item :label="$t('bannerList.sortOrder')" prop="sortOrder">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
          <span class="form-tip-inline">{{ $t('bannerList.sortTip') }}</span>
        </el-form-item>
        <el-form-item :label="$t('bannerList.status')">
          <el-switch v-model="form.enabled" :aria-label="$t('bannerList.enabled')" />
          <span class="form-hint">{{ form.enabled ? $t('bannerList.enabled') : $t('bannerList.disabled') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleFormCancel">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleConfirmSave">
          {{ isEdit ? $t('bannerList.saveEdit') : $t('bannerList.confirmAdd') }}
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
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getBanners,
  createBanner,
  updateBanner,
  deleteBanner,
  toggleBannerStatus
} from '@/api/admin-banner'

const { t } = useI18n()

// 加载状态
const loading = ref(false)
// P1-C 修复 (2026-08-04): userRole 未定义导致新增/编辑/删除按钮全部隐藏，
// 管理员无法管理轮播图（功能不可用）
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
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
  imageUrl: [{ required: true, message: t('bannerList.imageRequired'), trigger: ['blur', 'change'] }],
  linkUrl: [{
    pattern: /^$|^https?:\/\/.+/i,
    message: t('bannerList.linkFormat'),
    trigger: 'blur'
  }]
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
    ElMessage.error(t('bannerList.fetchFailed'))
  } finally {
    loading.value = false
  }
}

// 新增
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
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (saving.value) return
  if (!formRef.value) return
  saving.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { saving.value = false; return }
  } catch { saving.value = false; return }

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
      ElMessage.success(t('common.success'))
    } else {
      await createBanner(fd)
      ElMessage.success(t('common.success'))
    }
    formVisible.value = false
    fetchData()
  } catch (err) {
    ElMessage.error(err.message || t(isEdit.value ? 'bannerList.updateFailedRetry' : 'bannerList.createFailedRetry'))
  } finally {
    saving.value = false
  }
}

// 删除
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('bannerList.confirmDelete', { id: row.id }),
      t('bannerList.deleteConfirmTitle'),
      { type: 'warning', confirmButtonText: t('app.delete'), cancelButtonText: t('common.cancel') }
    )
    await deleteBanner(row.id)
    ElMessage.success(t('bannerList.deleteSuccess'))
    fetchData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || t('bannerList.deleteFailed'))
    }
  }
}

// 切换状态（带二次确认）
async function handleToggleStatus(row) {
  const newVal = row.enabled
  const bannerTitle = row.title || row.id || t('bannerList.unnamed')
  const confirmMsg = newVal
    ? t('bannerList.confirmOnline', { title: bannerTitle })
    : t('bannerList.confirmOffline', { title: bannerTitle })
  const confirmTitle = newVal ? t('bannerList.onlineConfirmTitle') : t('bannerList.offlineConfirmTitle')
  try {
    await ElMessageBox.confirm(
      confirmMsg,
      confirmTitle,
      { confirmButtonText: t('app.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
    await toggleBannerStatus(row.id, newVal)
    ElMessage.success(t(newVal ? 'bannerList.onlineSuccess' : 'bannerList.offlineSuccess'))
    fetchData()
  } catch (e) {
// eslint-disable-next-line no-console
    if (e !== 'cancel') console.debug(e)
    row.enabled = !newVal
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

@media (max-width: 768px) {
  .banner-list-container {
    padding: var(--space-3);
  }
  .toolbar {
    flex-wrap: wrap;
    gap: var(--space-2);
  }
  .data-table {
    font-size: var(--text-sm);
  }
}
</style>
