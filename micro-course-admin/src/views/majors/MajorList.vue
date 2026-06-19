<!--
  专业列表
  路由路径: /majors
  Phase 1
  Author: jackie
-->
<template>
  <div class="major-list">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="/" class="page-breadcrumb">
      <el-breadcrumb-item>组织管理</el-breadcrumb-item>
      <el-breadcrumb-item>专业列表</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 搜索区 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="searchForm.name" placeholder="请输入专业名称" clearable class="filter-input" />
        </el-form-item>
        <el-form-item label="院系">
          <el-select v-model="searchForm.departmentId" placeholder="请选择院系" clearable class="filter-select">
            <el-option v-for="item in departmentOptions" :key="item.id" :label="item.name" :value="item.id" />
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
          <span class="card-title">专业列表</span>
          <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增专业</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无专业数据" :image-size="120" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="departmentName" label="所属院系" width="150" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该专业？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && tableData.length > 0" class="pagination-wrap">
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="580px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入专业名称" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入专业编码" />
        </el-form-item>
        <el-form-item label="所属院系" prop="departmentId">
          <el-select v-model="formData.departmentId" placeholder="请选择院系" class="full-width">
            <el-option v-for="item in departmentOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getMajors, createMajor, updateMajor, deleteMajor } from '@/api/major'
import { getDepartments } from '@/api/department'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const departmentOptions = ref([])

const searchForm = reactive({
  name: '',
  departmentId: null
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增专业')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  name: '',
  code: '',
  departmentId: null,
  sortOrder: 0
})

const formRules = {
  name: [{ required: true, message: '请输入专业名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入专业编码', trigger: 'blur' }],
  departmentId: [{ required: true, message: '请选择所属院系', trigger: 'change' }],
  sortOrder: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
}

const fetchDepartments = async () => {
  try {
    const { data } = await getDepartments({ page: 0, size: 1000 })
    departmentOptions.value = data.items || []
  } catch {
    ElMessage.error('获取院系列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      name: searchForm.name || undefined,
      departmentId: searchForm.departmentId || undefined
    }
    const { data } = await getMajors(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取专业列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.name = ''
  searchForm.departmentId = null
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
  dialogTitle.value = '新增专业'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.code = ''
  formData.departmentId = null
  formData.sortOrder = 0
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑专业'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.code = row.code
  formData.departmentId = row.departmentId
  formData.sortOrder = row.sortOrder
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await deleteMajor(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    const code = error.response?.data?.code
    if (code === 3002) {
      ElMessage.error('该专业下存在班级，无法删除')
    } else {
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
        await updateMajor(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createMajor(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? '编辑失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchDepartments()
  fetchData()
})
</script>

<style scoped>
.major-list {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100vh;
}

.page-breadcrumb {
  margin-bottom: 16px;
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

.table-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
}

.table-card :deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.filter-input {
  width: 200px;
  border-radius: 8px;
}

.filter-select {
  width: 200px;
  border-radius: 8px;
}

.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: center;
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

.full-width {
  width: 100%;
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
