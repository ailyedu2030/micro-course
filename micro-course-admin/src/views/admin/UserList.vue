<!--
  管理员 - 用户管理
  /admin/users
  含 Excel 批量导入
  Author: jackie
-->
<template>
  <div class="user-list-container">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="/" class="page-breadcrumb">
      <el-breadcrumb-item>用户管理</el-breadcrumb-item>
      <el-breadcrumb-item>用户列表</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 搜索筛选区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="账号/姓名"
            clearable
            class="filter-input"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="searchForm.role"
            placeholder="请选择"
            clearable
            class="filter-select"
            @change="handleSearch"
          >
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教务" value="ACADEMIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择"
            clearable
            class="filter-select"
            @change="handleSearch"
          >
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" aria-label="重置"><el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset" aria-label="导出"><el-icon><RefreshRight /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="card-count">共 {{ totalElements }} 条记录</span>
        </div>
        <div class="toolbar-right">
          <el-button type="success" @click="handleImport" aria-label="确认"><el-icon><Upload /></el-icon>Excel 导入
          </el-button>
          <el-button type="primary" @click="handleExport" aria-label="下载"><el-icon><Download /></el-icon>导出
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-result
        v-else-if="error"
        icon="error"
        title="数据加载失败"
        sub-title="请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">重试</el-button>
        </template>
      </el-result>
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        description="暂无用户"
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
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="账号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.realName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getRoleTagType(row.role)">
              {{ getRoleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="departmentName" label="院系" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.departmentName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="majorName" label="专业" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.majorName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="className" label="班级" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.className || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusTagType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)" aria-label="编辑"><el-icon><View /></el-icon>详情
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
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" aria-label="分页导航" />
      </div>
    </el-card>

    <!-- Excel 导入弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      title="Excel 批量导入用户"
      width="520px"
      destroy-on-close
     :close-on-press-escape="true">
      <div class="import-guide">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            请上传 .xlsx/.xls 格式的 Excel 文件，每次最多导入 500 条记录。
          </template>
        </el-alert>
        <div class="import-template">
          <p class="template-title">Excel 模板格式：</p>
          <el-table :data="templateData" size="small" border class="template-table">
            <el-table-column prop="username" label="username (必填)" />
            <el-table-column prop="realName" label="realName (必填)" />
            <el-table-column prop="password" label="password (可选，留空自动生成)" />
            <el-table-column prop="role" label="role (STUDENT/TEACHER)" />
            <el-table-column prop="departmentName" label="departmentName" />
            <el-table-column prop="majorName" label="majorName" />
            <el-table-column prop="className" label="className" />
          </el-table>
          <el-button type="primary" text @click="handleDownloadTemplate" aria-label="编辑"><el-icon><Download /></el-icon>下载模板文件
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
     :close-on-press-escape="true">
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

    <!-- 用户详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="用户详情"
      width="560px"
      destroy-on-close
     :close-on-press-escape="true">
      <el-descriptions :column="2" border v-if="currentUser">
        <el-descriptions-item label="ID">{{ currentUser.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ currentUser.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ currentUser.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag size="small" :type="getRoleTagType(currentUser.role)">
            {{ getRoleLabel(currentUser.role) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="院系">{{ currentUser.departmentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="专业">{{ currentUser.majorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ currentUser.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="currentUser.status === 1 ? 'success' : 'danger'">
            {{ currentUser.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间" :span="2">
          {{ formatTime(currentUser.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="邮箱" :span="2">{{ currentUser.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机" :span="2">{{ currentUser.phone || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 管理员 - 用户管理
 * Vue 3.4 Composition API + script setup
 * 含 Excel 批量导入
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, View, Upload, Download, UploadFilled } from '@element-plus/icons-vue'
import { getUsers, batchImportUsers } from '@/api/user'
import * as XLSX from 'xlsx'

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
  status: null
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

const templateData = [
  { username: 'zhangsan', realName: '张三', password: '', role: 'STUDENT', departmentName: '计算机学院', majorName: '软件工程', className: '软工 2023-1 班' }
]

// 详情弹窗
const detailVisible = ref(false)
const currentUser = ref(null)

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
      status: searchForm.status || undefined
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

// 搜索
function handleSearch() {
  page.value = 1
  fetchData()
}

// 重置
function handleReset() {
  searchForm.keyword = ''
  searchForm.role = ''
  searchForm.status = null
  page.value = 1
  fetchData()
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
function getRoleLabel(role) {
  const map = {
    STUDENT: '学生',
    TEACHER: '教师',
    ADMIN: '管理员',
    ACADEMIC: '教务'
  }
  return map[role] || role || '-'
}

function getRoleTagType(role) {
  const map = {
    STUDENT: 'success',
    TEACHER: 'warning',
    ADMIN: 'danger',
    ACADEMIC: ''
  }
  return map[role] || 'info'
}

function getStatusLabel(status) {
  const map = { 0: '未激活', 1: '启用', 2: '禁用', 3: '已删除' }
  return map[status] || '未知'
}

function getStatusTagType(status) {
  const map = { 0: 'info', 1: 'success', 2: 'danger', 3: 'info' }
  return map[status] || 'info'
}

function formatTime(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
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
    // P1-1 修复：字段名对齐后端 successCount/failCount
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
    ElMessage.error(err.message || '导入失败，请检查文件格式')
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
  // P0-3 修复：使用 xlsx 库生成真正的 .xlsx 文件
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
  ElMessage.info('导出功能需要后端支持')
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

.search-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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

.filter-input {
  width: 160px;
}

.filter-select {
  width: 160px;
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
</style>