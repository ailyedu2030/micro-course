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
          <el-button type="primary" @click="handleCreate">新增用户</el-button>
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
  </div>
</template>

<script setup>
/**
 * 用户列表页
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUsers, updateUserStatus } from '@/api/user'
import { getDepartments } from '@/api/department'
import { getMajors } from '@/api/major'
import { getClasses } from '@/api/class'

const router = useRouter()

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
    // await updateUser(formData) // TODO: implement update API
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

onMounted(() => {
  fetchDepartments()
  fetchData()
})
</script>

<style scoped>
.user-list {
  padding: var(--space-xl);
}

.filter-card {
  margin-bottom: var(--space-lg);
  border-radius: 8px;
}

.table-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 500;
}

.filter-input {
  width: 160px;
}

.filter-select {
  width: 140px;
}

.data-table {
  width: 100%;
}

.pagination-wrap {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: center;
}

.full-width {
  width: 100%;
}
</style>
