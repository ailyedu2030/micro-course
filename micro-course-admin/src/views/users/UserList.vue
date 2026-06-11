<template>
  <div class="user-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="用户名/姓名" clearable class="search-input" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="请选择" clearable class="search-select role-select">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教务" value="ACADEMIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable class="search-select status-select">
            <el-option label="未激活" :value="0" />
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="2" />
            <el-option label="已删除" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="院系">
          <el-select v-model="searchForm.departmentId" placeholder="请选择院系" clearable class="search-select dept-select" @change="handleDepartmentChange">
            <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <div class="header-actions">
            <el-button type="success" @click="importDialogVisible = true">批量导入</el-button>
            <el-button type="primary" @click="handleCreate">新增用户</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN'" type="danger" size="small">管理员</el-tag>
            <el-tag v-else-if="row.role === 'TEACHER'" type="warning" size="small">教师</el-tag>
            <el-tag v-else-if="row.role === 'ACADEMIC'" type="success" size="small">教务</el-tag>
            <el-tag v-else type="info" size="small">学生</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="phone" label="手机" width="130" />
        <el-table-column prop="departmentName" label="院系" min-width="120" />
        <el-table-column prop="majorName" label="专业" min-width="120" />
        <el-table-column prop="className" label="班级" min-width="120" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else-if="row.status === 0" type="info" size="small">未激活</el-tag>
            <el-tag v-else-if="row.status === 2" type="danger" size="small">禁用</el-tag>
            <el-tag v-else type="warning" size="small">已删除</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 2" type="success" link size="small" @click="handleToggleStatus(row)">启用</el-button>
            <el-button v-else-if="row.status !== 3" type="warning" link size="small" @click="handleToggleStatus(row)">禁用</el-button>
            <el-button v-if="row.status !== 3" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="批量导入用户" width="560px" @close="handleImportDialogClose">
      <div class="import-guide">
        <p>请下载模板文件，按格式填写后上传。支持 <strong>.xlsx / .xls</strong> 文件。</p>
        <a :href="templateUrl" download class="template-link" target="_blank">下载导入模板</a>
      </div>

      <el-divider />

      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">只能上传 xlsx/xls 文件</div>
        </template>
      </el-upload>

      <!-- 导入结果 -->
      <div v-if="importResult" class="import-result">
        <el-alert
          :title="`导入完成：成功 ${importResult.successCount} 条，失败 ${importResult.failCount} 条`"
          :type="importResult.failCount > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
          class="import-alert"
        />
        <div v-if="importResult.errors && importResult.errors.length > 0" class="error-table-wrap">
          <p class="error-title">失败记录：</p>
          <el-table :data="importResult.errors" stripe border size="small" max-height="200">
            <el-table-column type="index" label="行号" width="60" align="center" />
            <el-table-column prop="row" label="Excel行" width="70" align="center" />
            <el-table-column prop="message" label="错误原因" min-width="160" />
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!selectedFile" @click="handleImportSubmit">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 用户列表页（含批量导入）
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { getUsers, updateUserStatus, batchImportUsers } from '@/api/user'
import { getDepartments } from '@/api/department'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const departments = ref([])

// 批量导入
const importDialogVisible = ref(false)
const importLoading = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const importResult = ref(null)
const templateUrl = ref('/templates/user-import-template.xlsx')

const searchForm = reactive({
  keyword: '',
  role: '',
  status: '',
  departmentId: ''
})

const fetchDepartments = async () => {
  try {
    const { data } = await getDepartments({ size: 1000 })
    departments.value = data.items || []
  } catch (error) {
    // 静默失败
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      keyword: searchForm.keyword || undefined,
      role: searchForm.role || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined,
      departmentId: searchForm.departmentId || undefined
    }
    const { data } = await getUsers(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.role = ''
  searchForm.status = ''
  searchForm.departmentId = ''
  page.value = 1
  fetchData()
}

const handleDepartmentChange = () => {
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
  router.push('/users/create')
}

const handleEdit = (row) => {
  router.push(`/users/${row.id}/edit`)
}

const handleToggleStatus = async (row) => {
  const newStatus = row.status === 2 ? 1 : 2
  const actionText = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${actionText}该用户?`, '提示', { type: 'warning' })
    await updateUserStatus(row.id, { status: newStatus })
    ElMessage.success(`${actionText}成功`)
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`${actionText}失败`)
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该用户?', '提示', { type: 'warning' })
    await updateUserStatus(row.id, { status: 3 })
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 文件选择
function handleFileChange(file) {
  selectedFile.value = file.raw
  importResult.value = null
}

function handleFileRemove() {
  selectedFile.value = null
  importResult.value = null
}

// 关闭弹窗重置状态
function handleImportDialogClose() {
  importDialogVisible.value = false
  selectedFile.value = null
  importResult.value = null
  uploadRef.value?.clearFiles()
}

// 提交导入
async function handleImportSubmit() {
  if (!selectedFile.value) return
  importLoading.value = true
  importResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    const { data } = await batchImportUsers(formData)
    // data: { successCount, failCount, errors: [{row, message}] }
    importResult.value = {
      successCount: data.successCount ?? 0,
      failCount: data.failCount ?? 0,
      errors: data.errors ?? []
    }
    if (importResult.value.failCount === 0) {
      ElMessage.success('导入完成')
      fetchData()
    }
  } catch (err) {
    ElMessage.error('导入失败，请检查文件格式')
  } finally {
    importLoading.value = false
  }
}

onMounted(() => {
  fetchDepartments()
  fetchData()
})
</script>

<style scoped>
.user-list {
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
}

.search-input {
  width: 160px;
}

.search-select {
  width: 160px;
}

.role-select {
  width: 140px;
}

.status-select {
  width: 120px;
}

.dept-select {
  width: 180px;
}

.data-table {
  width: 100%;
}

.import-alert {
  margin-bottom: 12px;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.import-guide {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 8px;
}

.import-guide p {
  margin: 0 0 8px;
  font-size: 13px;
  color: #606266;
}

.template-link {
  color: #409eff;
  font-size: 13px;
  text-decoration: none;
}

.template-link:hover {
  text-decoration: underline;
}

.error-table-wrap {
  margin-top: 8px;
}

.error-title {
  font-size: 13px;
  color: #f56c6c;
  margin: 0 0 6px;
}

@media (max-width: 768px) {
  .header-actions {
    flex-direction: column;
    gap: 4px;
  }
}

.full-width { width: 100%; }
.search-input-w140 { width: 140px; }
.search-input-w200 { width: 200px; }
.search-input-w240 { width: 240px; }
.search-select-w200 { width: 200px; }
.search-select-w240 { width: 240px; }
</style>