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
          <span class="card-count">共 {{ totalElements }} 条记录</span>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="handleCreate" aria-label="新增用户">
            <el-icon><Plus /></el-icon>新增用户
          </el-button>
          <el-button type="success" @click="handleImport" aria-label="确认">
            <el-icon><Upload /></el-icon>Excel 导入
          </el-button>
          <el-button type="primary" @click="handleExport" aria-label="下载">
            <el-icon><Download /></el-icon>导出
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
    <el-dialog v-model="resetVisible" title="重置密码" width="420px" destroy-on-close>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="用户">
          <el-input :model-value="resetTarget ? (resetTarget.realName || resetTarget.username) : ''" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="至少 8 位且包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="confirmResetPassword">确定</el-button>
      </template>
    </el-dialog>

    <!-- Excel 导入弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      title="Excel 批量导入用户"
      width="520px"
      destroy-on-close
      :close-on-press-escape="true"
    >
      <div class="import-guide">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            请上传 .xlsx/.xls 格式的 Excel 文件，每次最多导入 500 条记录。
          </template>
        </el-alert>
        <div class="import-template">
          <p class="template-title">Excel 模板格式：</p>
          <el-table :data="[]" size="small" border class="template-table">
            <el-table-column prop="username" label="username (必填)" />
            <el-table-column prop="realName" label="realName (必填)" />
            <el-table-column prop="password" label="password (可选，留空自动生成)" />
            <el-table-column prop="role" label="role (STUDENT/TEACHER)" />
            <el-table-column prop="departmentName" label="departmentName" />
            <el-table-column prop="majorName" label="majorName" />
            <el-table-column prop="className" label="className" />
          </el-table>
          <el-button type="primary" text @click="handleDownloadTemplate" aria-label="编辑">
            <el-icon><Download /></el-icon>下载模板文件
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
          <div class="upload-text">将 Excel 文件拖到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="upload-tip">只能上传 xlsx/xls 文件，单个文件不超过 5MB</div>
          </template>
        </el-upload>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!uploadFile" @click="handleConfirmImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果弹窗 -->
    <el-dialog
      v-model="resultDialogVisible"
      title="导入结果"
      width="600px"
      destroy-on-close
      :close-on-press-escape="true"
    >
      <div class="result-content">
        <el-result
          :icon="importResult.successCount > 0 ? 'success' : 'warning'"
          :title="importResult.successCount > 0 ? `成功导入 ${importResult.successCount} 条` : '导入失败'"
          :sub-title="importResult.failCount > 0 ? `失败 ${importResult.failCount} 条，以下为失败条目` : ''"
        />
        <el-table
          v-if="importResult.errors && importResult.errors.length > 0"
          :data="importResult.errors"
          stripe
          border
          size="small"
          max-height="300"
        >
          <el-table-column prop="row" label="行号" width="80" align="center" />
          <el-table-column prop="username" label="账号" width="140" />
          <el-table-column prop="reason" label="失败原因" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">确定</el-button>
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
import * as XLSX from 'xlsx'
import UserSearchBar from '@/components/users/UserSearchBar.vue'
import UserTable from '@/components/users/UserTable.vue'
import UserDetailCard from '@/components/users/UserDetailCard.vue'

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
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,}$/, message: '密码需至少 8 位且包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => (value === resetForm.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))),
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
    ElMessage.success('密码重置成功，请通知用户使用新密码登录')
    resetVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '重置失败')
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
    ElMessage.error('获取用户列表失败')
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
    ElMessage.warning('请先选择 Excel 文件')
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
      ElMessage.success(`成功导入 ${result.successCount} 条用户记录`)
    }
    fetchData()
  } catch (err) {
    ElMessage.error('导入失败，请检查文件格式')
    importResult.value = {
      successCount: 0,
      failCount: 0,
      errors: [{ row: 0, username: '-', reason: err.message || '导入失败' }]
    }
    importDialogVisible.value = false
    resultDialogVisible.value = true
  } finally {
    importing.value = false
  }
}

function handleDownloadTemplate() {
  const template = [
    ['username', 'realName', 'password', 'role', 'departmentName', 'majorName', 'className'],
    ['zhangsan', '张三', '', 'STUDENT', '计算机学院', '软件工程', '软工 2023-1 班'],
    ['lisi', '李四', '', 'STUDENT', '计算机学院', '软件工程', '软工 2023-2 班']
  ]
  const wb = XLSX.utils.book_new()
  const ws = XLSX.utils.aoa_to_sheet(template)
  XLSX.utils.book_append_sheet(wb, ws, '用户导入模板')
  const wbout = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
  const blob = new Blob([wbout], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'user_import_template.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

function handleExport() {
  if (!tableData.value.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  ElMessage.info(`即将导出当前页 ${tableData.value.length} 条数据，如需全部导出请联系管理员`)
  const exportData = tableData.value.map((item, index) => ({
    序号: index + 1,
    ID: item.id,
    账号: item.username || '',
    姓名: item.realName || '',
    角色: getRoleLabel(item.role),
    院系: item.departmentName || '',
    专业: item.majorName || '',
    班级: item.className || '',
    状态: getStatusLabel(item.status),
    注册时间: item.createdAt ? new Date(item.createdAt).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '-'
  }))
  const ws = XLSX.utils.json_to_sheet(exportData)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '用户列表')
  const date = new Date().toISOString().split('T')[0]
  XLSX.writeFile(wb, `users-${date}.xlsx`)
  ElMessage.success('导出成功')
}

function getRoleLabel(role) {
  const map = { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员', ACADEMIC: '教务' }
  return map[role] || role || '-'
}

function getStatusLabel(status) {
  const map = { 0: '未激活', 1: '启用', 2: '禁用', 3: '已删除' }
  return map[status] || '未知'
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
      `确定删除用户「${row.realName || row.username}」吗？此操作将注销该用户。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await updateUserStatus(row.id, { status: 3 })
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || '删除失败')
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
