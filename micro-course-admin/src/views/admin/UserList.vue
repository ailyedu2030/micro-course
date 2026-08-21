<!--
  管理员 - 用户管理
  /admin/users
  含 Excel 批量导入
  使用 UserSearchBar + UserTable + UserDetailCard 共享组件
  Author: jackie
-->
<template>
  <div class="user-list-container">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item>{{ $t('admin.userManagement') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('admin.userList') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 搜索筛选区（共享组件） -->
    <UserSearchBar
      v-model="searchForm"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 工具栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="card-count">{{ $t('adminUserList.totalRecords', { count: totalElements }) }}</span>
        </div>
        <div class="toolbar-right">
          <!-- P1-2026-08-21: 后端创建/导入/导出均 ADMIN-only，按钮补角色守卫（ACADEMIC 可见但点击必失败） -->
          <el-button v-if="userRole === 'ADMIN'" type="primary" @click="handleCreate" :aria-label="$t('admin.quickActions.addUser')">
            <el-icon><Plus /></el-icon>{{ $t('admin.quickActions.addUser') }}
          </el-button>
          <el-button v-if="userRole === 'ADMIN'" type="success" @click="handleImport" :aria-label="$t('app.confirm')">
            <el-icon><Upload /></el-icon>{{ $t('admin.excelImport') }}
          </el-button>
          <el-button v-if="userRole === 'ADMIN'" type="primary" @click="handleExport" :aria-label="$t('adminUserList.download')">
            <el-icon><Download /></el-icon>{{ $t('admin.export') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 用户表格（共享组件） -->
    <UserTable
      :loading="loading"
      :error="error"
      :data="tableData"
      :total="totalElements"
      :page="page"
      :size="size"
      @update:page="handlePageChange"
      @update:size="handleSizeChange"
      @retry="fetchData"
      @view-detail="handleViewDetail"
      @edit="handleEdit"
      @reset-password="handleResetPassword"
      @delete="handleSoftDelete"
    />

    <!-- 重置密码弹窗（A1.7 忘记密码兜底链路） -->
    <el-dialog v-model="resetVisible" :title="$t('adminUserList.resetPassword')" width="420px" destroy-on-close>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item :label="$t('adminUserList.user')">
          <el-input :model-value="resetTarget ? (resetTarget.realName || resetTarget.username) : ''" disabled />
        </el-form-item>
        <el-form-item :label="$t('adminUserList.newPassword')" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" show-password :placeholder="$t('adminUserList.newPasswordPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('auth.confirmPassword')" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password :placeholder="$t('adminUserList.confirmPasswordPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="resetting" @click="confirmResetPassword">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Excel 导入弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      :title="$t('adminUserList.importUsersTitle')"
      width="520px"
      destroy-on-close
      :close-on-press-escape="true"
    >
      <div class="import-guide">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            {{ $t('adminUserList.importGuide') }}
          </template>
        </el-alert>
        <div class="import-template">
          <p class="template-title">{{ $t('adminUserList.templateTitle') }}</p>
          <el-table :data="[]" size="small" border class="template-table">
            <el-table-column prop="username" :label="$t('adminUserList.usernameRequired')" />
            <el-table-column prop="realName" :label="$t('adminUserList.realNameRequired')" />
            <el-table-column prop="password" :label="$t('adminUserList.passwordOptional')" />
            <el-table-column prop="role" :label="$t('adminUserList.roleHint')" />
            <el-table-column prop="departmentName" label="departmentName" />
            <el-table-column prop="majorName" label="majorName" />
            <el-table-column prop="className" label="className" />
          </el-table>
          <el-button type="primary" text @click="handleDownloadTemplate" :aria-label="$t('app.edit')">
            <el-icon><Download /></el-icon>{{ $t('adminUserList.downloadTemplate') }}
          </el-button>
        </div>
        <el-upload
          ref="uploadRef"
          class="import-upload"
          drag
          :limit="1"
          accept=".xlsx,.xls"
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="upload-text">{{ $t('adminUserList.uploadDragText') }}<em>{{ $t('adminUserList.clickUpload') }}</em></div>
          <template #tip>
            <div class="upload-tip">{{ $t('adminUserList.uploadTip') }}</div>
          </template>
        </el-upload>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="importing" :disabled="!uploadFile" @click="handleConfirmImport">
          {{ $t('adminUserList.startImport') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果弹窗 -->
    <el-dialog
      v-model="resultDialogVisible"
      :title="$t('adminUserList.importResult')"
      width="600px"
      destroy-on-close
      :close-on-press-escape="true"
    >
      <div class="result-content">
        <el-result
          :icon="importResult.successCount > 0 ? 'success' : 'warning'"
          :title="importResult.successCount > 0 ? $t('adminUserList.importSuccessCount', { count: importResult.successCount }) : $t('adminUserList.importFailed')"
          :sub-title="importResult.failCount > 0 ? $t('adminUserList.importFailList', { count: importResult.failCount }) : ''"
        />
        <el-table
          v-if="importResult.errors && importResult.errors.length > 0"
          :data="importResult.errors"
          stripe
          border
          size="small"
          max-height="300"
        >
          <el-table-column prop="row" :label="$t('adminUserList.rowNumber')" width="80" align="center" />
          <el-table-column prop="username" :label="$t('userList.account')" width="140" />
          <el-table-column prop="reason" :label="$t('adminUserList.failReason')" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 用户详情弹窗（共享组件） -->
    <UserDetailCard
      v-model:visible="detailVisible"
      :user="currentUser"
    />
  </div>
</template>

<script setup>
/**
 * 管理员 - 用户管理
 * Vue 3.4 Composition API + script setup
 * 含 Excel 批量导入
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Download, UploadFilled } from '@element-plus/icons-vue'
import { getUsers, batchImportUsers, updateUserStatus, resetUserPassword } from '@/api/user'
import { Workbook } from 'exceljs'
import UserSearchBar from '@/components/users/UserSearchBar.vue'
import UserTable from '@/components/users/UserTable.vue'
import UserDetailCard from '@/components/users/UserDetailCard.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 加载状态
const loading = ref(false)
const error = ref(false)

// 表格数据
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(20)

// 搜索表单
const searchForm = reactive({
  keyword: '',
  role: '',
  status:  ''
})

// 导入相关
const importDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const importing = ref(false)
const uploadRef = ref(null)
const uploadFile = ref(null)

const importResult = ref({
  successCount: 0,
  failCount: 0,
  errors: []
})

// 详情弹窗
const detailVisible = ref(false)
const resetVisible = ref(false)
const resetTarget = ref(null)
const resetForm = reactive({ newPassword: '', confirmPassword: '' })
const resetFormRef = ref(null)
const resetting = ref(false)
const resetRules = {
  newPassword: [
    { required: true, message: t('adminUserList.inputNewPassword'), trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,}$/, message: t('adminUserList.passwordPolicy'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('adminUserList.inputConfirmPassword'), trigger: 'blur' },
    {
      validator: (rule, value, cb) => (value === resetForm.newPassword ? cb() : cb(new Error(t('userForm.passwordMismatch')))),
      trigger: 'blur'
    }
  ]
}

function handleResetPassword(row) {
  resetTarget.value = row
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetVisible.value = true
}

async function confirmResetPassword() {
  if (!resetFormRef.value || !resetTarget.value) return
  try {
    await resetFormRef.value.validate()
  } catch {
    return
  }
  resetting.value = true
  try {
    await resetUserPassword(resetTarget.value.id, { newPassword: resetForm.newPassword })
    ElMessage.success(t('adminUserList.passwordResetSuccess'))
    resetVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('adminUserList.resetFailed'))
  } finally {
    resetting.value = false
  }
}
const currentUser = ref(null)

const router = useRouter()

// 获取数据
async function fetchData() {
  loading.value = true
  error.value = false
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      keyword: searchForm.keyword || undefined,
      role: searchForm.role || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined
    }
    const { data } = await getUsers(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    error.value = true
    ElMessage.error(t('userList.fetchFailed'))
  } finally {
    loading.value = false
  }
}

// P1C: 300ms 防抖搜索
let searchDebounceTimer = null
function handleSearch() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    page.value = 1
    fetchData()
  }, 300)
}

// 重置
function handleReset() {
  searchForm.keyword = ''
  searchForm.role = ''
  searchForm.status = ''
  page.value = 1
  fetchData()
}

// 翻页
function handleSizeChange(val) {
  size.value = val
  page.value = 1
  fetchData()
}

function handlePageChange(val) {
  page.value = val
  fetchData()
}

// 导入
function handleImport() {
  uploadFile.value = null
  importDialogVisible.value = true
}

function handleFileChange(file) {
  uploadFile.value = file.raw
}

function handleFileRemove() {
  uploadFile.value = null
}

async function handleConfirmImport() {
  if (!uploadFile.value) {
    ElMessage.warning(t('adminUserList.selectExcelFirst'))
    return
  }
  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    const res = await batchImportUsers(formData)
    const result = res.data || {}
    importResult.value = {
      successCount: result.successCount || 0,
      failCount: result.failCount || 0,
      errors: result.errors || []
    }
    importDialogVisible.value = false
    resultDialogVisible.value = true
    if (result.failCount === 0) {
      ElMessage.success(t('adminUserList.importSuccessUsers', { count: result.successCount }))
    }
    fetchData()
  } catch (err) {
    ElMessage.error(t('adminUserList.importFailedCheck'))
    importResult.value = {
      successCount: 0,
      failCount: 0,
      errors: [{ row: 0, username: '-', reason: err.message || t('adminUserList.importFailed') }]
    }
    importDialogVisible.value = false
    resultDialogVisible.value = true
  } finally {
    importing.value = false
  }
}

async function handleDownloadTemplate() {
  const template = [
    ['username', 'realName', 'password', 'role', 'departmentName', 'majorName', 'className'],
    ['zhangsan', '张三', '', 'STUDENT', '计算机学院', '软件工程', '软工 2023-1 班'],
    ['lisi', '李四', '', 'STUDENT', '计算机学院', '软件工程', '软工 2023-2 班']
  ]
  const wb = new Workbook()
  const ws = wb.addWorksheet(t('adminUserList.templateSheetName'))
  ws.addRows(template)
  const wbout = await wb.xlsx.writeBuffer()
  const blob = new Blob([wbout], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'user_import_template.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

async function handleExport() {
  if (!tableData.value.length) {
    ElMessage.warning(t('adminUserList.noDataToExport'))
    return
  }
  ElMessage.info(t('adminUserList.exportCurrentPage', { count: tableData.value.length }))
  const exportData = tableData.value.map((item, index) => ({
    [t('course.index')]: index + 1,
    ID: item.id,
    [t('userList.account')]: item.username || '',
    [t('user.realName')]: item.realName || '',
    [t('userSearch.role')]: getRoleLabel(item.role),
    [t('userSearch.department')]: item.departmentName || '',
    [t('userSearch.major')]: item.majorName || '',
    [t('userSearch.classLabel')]: item.className || '',
    [t('userSearch.status')]: getStatusLabel(item.status),
    [t('user.registerTime')]: item.createdAt ? new Date(item.createdAt).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '-'
  }))
  const wb = new Workbook()
  const ws = wb.addWorksheet(t('adminUserList.userListSheetName'))
  ws.addRows(exportData.map(row => Object.values(row)))
  const date = new Date().toISOString().split('T')[0]
  // P1-2026-08-21: writeFile 浏览器端不可用(exceljs UMD 无 fs), 改 writeBuffer + Blob 下载
  const wbout = await wb.xlsx.writeBuffer()
  const blob = new Blob([wbout], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'users-' + date + '.xlsx'
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(t('adminUserList.exportSuccess'))
}

function getRoleLabel(role) {
  const map = { STUDENT: t('userSearch.student'), TEACHER: t('userSearch.teacher'), ADMIN: t('userSearch.admin'), ACADEMIC: t('userSearch.academic') }
  return map[role] || role || '-'
}

function getStatusLabel(status) {
  const map = { 0: t('userSearch.statusInactive'), 1: t('userSearch.statusActive'), 2: t('userSearch.statusDisabled'), 3: t('userSearch.statusDeleted') }
  return map[status] || t('course.unknown')
}

function handleCreate() {
  router.push('/users/create')
}

function handleEdit(row) {
  router.push(`/users/${row.id}/edit`)
}

async function handleSoftDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('adminUserList.deleteUserConfirm', { name: row.realName || row.username }),
      t('adminUserList.confirmDeleteTitle'),
      { confirmButtonText: t('course.dialogConfirm'), cancelButtonText: t('app.cancel'), type: 'warning' }
    )
    await updateUserStatus(row.id, { status: 3 })
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) {
      ElMessage.error(e?.response?.data?.message || t('course.deleteFailed'))
    }
  }
}

// 查看详情
function handleViewDetail(row) {
  currentUser.value = row
  detailVisible.value = true
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.user-list-container {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

.toolbar-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
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

.card-count {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
}

/* 导入弹窗 */
.import-guide {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.import-template {
  padding: var(--space-3);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
}

.template-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.template-table {
  margin-bottom: var(--space-2);
  font-size: var(--text-xs);
}

.import-upload {
  width: 100%;
}

.upload-icon {
  font-size: 40px;
  color: var(--el-text-color-placeholder);
  margin-bottom: var(--space-2);
}

.upload-text {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
}

.upload-text em {
  color: var(--role-primary);
  font-style: normal;
}

.upload-tip {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  margin-top: var(--space-2);
}

.result-content {
  max-height: 400px;
  overflow-y: auto;
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
  .user-list-container {
    padding: var(--space-3);
  }
  .toolbar {
    flex-wrap: wrap;
    gap: var(--space-2);
  }
}
</style>
