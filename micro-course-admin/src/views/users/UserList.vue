<!--
  用户列表
  路由路径: /users
  Phase 1
  Author: jackie
-->
<template>
  <div class="user-list">
    <!-- 搜索区 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="账号/姓名" clearable class="filter-input" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="请选择" clearable class="filter-select">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教务" value="ACADEMIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="院系">
          <el-select v-model="searchForm.departmentId" placeholder="请选择院系" clearable class="filter-select" @change="handleDepartmentChange">
            <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="searchForm.majorId" placeholder="请选择专业" clearable class="filter-select" :disabled="!searchForm.departmentId" @change="handleMajorChange">
            <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="searchForm.classId" placeholder="请选择班级" clearable class="filter-select" :disabled="!searchForm.majorId">
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable class="filter-select">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="2" />
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
          <span class="card-title">用户列表</span>
          <div class="header-actions">
            <el-button type="warning" @click="teacherApprovalVisible = true">教师审核</el-button>
            <el-button type="success" v-if="userRole !== 'ACADEMIC'" @click="batchImportVisible = true">批量导入</el-button>
            <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增用户</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="头像" width="80" align="center">
          <template #default="{ row }">
            <el-avatar v-if="row.avatar" :src="row.avatar" :size="40" />
            <el-avatar v-else :size="40">{{ row.realName?.charAt(0) || 'U' }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN'" type="danger" size="small">管理员</el-tag>
            <el-tag v-else-if="row.role === 'ACADEMIC'" type="warning" size="small">教务</el-tag>
            <el-tag v-else-if="row.role === 'TEACHER'" type="success" size="small">教师</el-tag>
            <el-tag v-else type="primary" size="small">学生</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门/专业/班级" min-width="180">
          <template #default="{ row }">
            <span>{{ row.departmentName }}</span>
            <span v-if="row.majorName"> / {{ row.majorName }}</span>
            <span v-if="row.className"> / {{ row.className }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :active-value="1"
              :inactive-value="2"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该用户？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" />
        </template>
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

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" title="编辑用户" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="formData.username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="formData.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="formData.role" placeholder="请选择角色" class="full-width">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教务" value="ACADEMIC" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleDialogSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="batchImportVisible" title="批量导入用户" width="500px">
      <div class="batch-import-tip">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            请按模板格式填写后上传。支持 .xlsx 格式文件。
          </template>
        </el-alert>
      </div>
      <div class="download-template">
        <span class="template-tip">模板文件请联系系统管理员获取</span>
      </div>
      <el-upload
        ref="uploadRef"
        class="batch-upload"
        drag
        accept=".xlsx,.xls"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">只能上传 xlsx/xls 文件，且不超过 10MB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="batchImportVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!uploadFile" @click="handleBatchImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 教师审核弹窗 -->
    <el-dialog v-model="teacherApprovalVisible" title="教师入驻审核" width="700px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--space-4)">
        <template #title>
          待审核教师列表。审核通过后，教师将获得创建课程的权限。
        </template>
      </el-alert>
      <el-table v-loading="teacherLoading" :data="pendingTeachers" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="teacherNo" label="教师编号" min-width="120" />
        <el-table-column prop="departmentName" label="院系" min-width="120" />
        <el-table-column prop="createdAt" label="申请时间" min-width="160" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="handleApproveTeacher(row)">通过</el-button>
            <el-button type="danger" size="small" @click="handleRejectTeacher(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="pendingTeachers.length === 0 && !teacherLoading" class="empty-tip">
        <el-empty description="暂无待审核教师" :image-size="80" />
      </div>
      <template #footer>
        <el-button @click="teacherApprovalVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 用户列表页
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getUsers, updateUser, updateUserStatus, batchImportUsers } from '@/api/user'
import { getDepartments } from '@/api/department'
import { getMajors } from '@/api/major'
import { getClasses } from '@/api/class'

const router = useRouter()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const departments = ref([])
const majors = ref([])
const classes = ref([])

const dialogVisible = ref(false)
const dialogLoading = ref(false)
const formRef = ref(null)

// 批量导入
const batchImportVisible = ref(false)
const uploadRef = ref(null)
const uploadFile = ref(null)
const importLoading = ref(false)

// 教师审核
const teacherApprovalVisible = ref(false)
const teacherLoading = ref(false)
const pendingTeachers = ref([])

const searchForm = reactive({
  keyword: '',
  role: '',
  departmentId: '',
  majorId: '',
  classId: '',
  status: ''
})

const formData = reactive({
  id: '',
  username: '',
  realName: '',
  role: ''
})

const formRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const fetchDepartments = async () => {
  try {
    const { data } = await getDepartments({ size: 1000 })
    departments.value = data.items || []
  } catch {
    departments.value = []
  }
}

const fetchMajors = async (departmentId) => {
  if (!departmentId) {
    majors.value = []
    return
  }
  try {
    const { data } = await getMajors({ departmentId, size: 1000 })
    majors.value = data.items || []
  } catch {
    majors.value = []
  }
}

const fetchClasses = async (majorId) => {
  if (!majorId) {
    classes.value = []
    return
  }
  try {
    const { data } = await getClasses({ majorId, size: 1000 })
    classes.value = data.items || []
  } catch {
    classes.value = []
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
      departmentId: searchForm.departmentId || undefined,
      majorId: searchForm.majorId || undefined,
      classId: searchForm.classId || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined
    }
    const { data } = await getUsers(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
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
  searchForm.departmentId = ''
  searchForm.majorId = ''
  searchForm.classId = ''
  searchForm.status = ''
  majors.value = []
  classes.value = []
  page.value = 1
  fetchData()
}

const handleDepartmentChange = () => {
  searchForm.majorId = ''
  searchForm.classId = ''
  majors.value = []
  classes.value = []
  if (searchForm.departmentId) {
    fetchMajors(searchForm.departmentId)
  }
  page.value = 1
  fetchData()
}

const handleMajorChange = () => {
  searchForm.classId = ''
  classes.value = []
  if (searchForm.majorId) {
    fetchClasses(searchForm.majorId)
  }
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
  formData.id = row.id
  formData.username = row.username
  formData.realName = row.realName
  formData.role = row.role
  dialogVisible.value = true
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

const handleDialogSave = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    dialogLoading.value = true
    await updateUser(formData.id, {
      username: formData.username,
      realName: formData.realName,
      role: formData.role
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // validation failed or API error
  } finally {
    dialogLoading.value = false
  }
}

const handleToggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 2 : 1
  const actionText = newStatus === 1 ? '启用' : '禁用'
  try {
    await updateUserStatus(row.id, { status: newStatus })
    ElMessage.success(`${actionText}成功`)
    fetchData()
  } catch {
    ElMessage.error(`${actionText}失败`)
  }
}

const handleDelete = async (row) => {
  try {
    await updateUserStatus(row.id, { status: 3 })
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

// 批量导入
const handleFileChange = (file) => {
  uploadFile.value = file.raw
}

const handleFileRemove = () => {
  uploadFile.value = null
}

const handleBatchImport = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    await batchImportUsers(formData)
    ElMessage.success('导入成功')
    batchImportVisible.value = false
    uploadFile.value = null
    uploadRef.value?.clearFiles()
    fetchData()
  } catch {
    ElMessage.error('导入失败，请检查文件格式')
  } finally {
    importLoading.value = false
  }
}

// 教师审核 - 打开弹窗时加载待审核教师
const loadPendingTeachers = async () => {
  teacherLoading.value = true
  try {
    const { data } = await getUsers({
      role: 'TEACHER',
      status: 2, // 待审核教师使用 status=2（禁用状态）
      size: 100
    })
    const items = data.items || []
    pendingTeachers.value = items
      .filter(t => t.status === 2)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      .slice(0, 20)
    if (pendingTeachers.value.length === 0) {
      ElMessage.info('暂无待审核教师')
    }
  } catch {
    pendingTeachers.value = []
    ElMessage.error('获取待审核教师列表失败')
  } finally {
    teacherLoading.value = false
  }
}

// 监听教师审核弹窗打开
import { watch } from 'vue'
watch(teacherApprovalVisible, (val) => {
  if (val) {
    loadPendingTeachers()
  }
})

// 通过教师
const handleApproveTeacher = async (row) => {
  try {
    // Mock - 后端暂无教师审核 API
    await updateUserStatus(row.id, { status: 1 })
    ElMessage.success(`教师 ${row.realName} 审核通过`)
    loadPendingTeachers()
  } catch {
    ElMessage.error('操作失败')
  }
}

// 驳回教师
const handleRejectTeacher = async (row) => {
  try {
    // Mock - 后端暂无教师审核 API
    await updateUserStatus(row.id, { status: 2 })
    ElMessage.success(`教师 ${row.realName} 已驳回`)
    loadPendingTeachers()
  } catch {
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  fetchDepartments()
  fetchData()
})
</script>

<style scoped>
.user-list {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100vh;
}

.filter-card {
  margin-bottom: 24px;
  border-radius: 12px;
  background: white;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.table-card {
  border-radius: 12px;
  background: white;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.filter-input {
  width: 160px;
  border-radius: 8px;
}

.filter-select {
  width: 140px;
  border-radius: 8px;
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

.data-table :deep(.el-table__row) {
  transition: background 150ms ease;
}

.data-table :deep(.el-table__body tr) {
  background: white;
}

.data-table :deep(.el-table__body tr:hover > td) {
  background: #F1F5F9 !important;
}

.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

.full-width {
  width: 100%;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.batch-import-tip {
  margin-bottom: var(--space-4);
}

.download-template {
  margin: var(--space-3) 0;
}

.template-tip {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

.batch-upload {
  width: 100%;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-2);
}

.upload-text {
  color: var(--el-text-color-regular);
}

.upload-text em {
  color: #4F46E5;
  font-style: normal;
}

.upload-tip {
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
  margin-top: var(--space-2);
}

.empty-tip {
  padding: var(--space-4) 0;
}

/* 角色标签颜色系统 */
:deep(.el-tag--danger) {
  --el-tag-bg-color: rgba(79, 70, 229, 0.1);
  --el-tag-text-color: #4F46E5;
  --el-tag-border-color: rgba(79, 70, 229, 0.2);
}
:deep(.el-tag--warning) {
  --el-tag-bg-color: rgba(245, 158, 11, 0.1);
  --el-tag-text-color: #F59E0B;
  --el-tag-border-color: rgba(245, 158, 11, 0.2);
}
:deep(.el-tag--success) {
  --el-tag-bg-color: rgba(16, 185, 129, 0.1);
  --el-tag-text-color: #10B981;
  --el-tag-border-color: rgba(16, 185, 129, 0.2);
}
:deep(.el-tag--primary) {
  --el-tag-bg-color: rgba(59, 130, 246, 0.1);
  --el-tag-text-color: #3B82F6;
  --el-tag-border-color: rgba(59, 130, 246, 0.2);
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
