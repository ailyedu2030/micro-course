<!--
  班级列表
  路由路径: /classes
  Phase 1
  Author: jackie
-->
<template>
  <div class="page-container">
    <!-- 搜索区 -->
    <div class="filter-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="searchForm.name" placeholder="请输入班级名称" clearable class="search-input" />
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="searchForm.majorId" placeholder="请选择专业" clearable class="search-select">
            <el-option v-for="item in majorOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model="searchForm.grade" placeholder="请输入年级" clearable class="search-input" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="card-header">
        <span class="card-title">班级列表</span>
        <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增班级</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" stripe class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <template #empty>
          <el-empty description="暂无班级数据" />
        </template>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="majorName" label="所属专业" min-width="150" />
        <el-table-column prop="grade" label="年级" width="100" />
        <el-table-column prop="counselorId" label="辅导员" width="120" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该班级？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
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
    </div>

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="580px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入班级名称" />
        </el-form-item>
        <el-form-item label="所属专业" prop="majorId">
          <el-select v-model="formData.majorId" placeholder="请选择专业" class="full-width">
            <el-option v-for="item in majorOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="formData.grade" placeholder="请输入年级" />
        </el-form-item>
        <el-form-item label="辅导员" prop="counselorId">
          <el-input v-model="formData.counselorId" placeholder="请输入辅导员" />
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
import { getClasses, createClass, updateClass, deleteClass } from '@/api/class'
import { getMajors } from '@/api/major'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const majorOptions = ref([])

const searchForm = reactive({
  name: '',
  majorId: null,
  grade: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增班级')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  name: '',
  majorId: null,
  grade: '',
  counselorId: '',
  sortOrder: 0
})

const formRules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  majorId: [{ required: true, message: '请选择所属专业', trigger: 'change' }],
  grade: [{ required: true, message: '请输入年级', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
}

const fetchMajors = async () => {
  try {
    const { data } = await getMajors({ page: 0, size: 1000 })
    majorOptions.value = data.items || []
  } catch {
    ElMessage.error('获取专业列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      name: searchForm.name || undefined,
      majorId: searchForm.majorId || undefined,
      grade: searchForm.grade || undefined
    }
    const { data } = await getClasses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取班级列表失败')
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
  searchForm.majorId = null
  searchForm.grade = ''
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
  dialogTitle.value = '新增班级'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.majorId = null
  formData.grade = ''
  formData.counselorId = ''
  formData.sortOrder = 0
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑班级'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.majorId = row.majorId
  formData.grade = row.grade
  formData.counselorId = row.counselorId
  formData.sortOrder = row.sortOrder
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await deleteClass(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateClass(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createClass(formData)
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
  fetchMajors()
  fetchData()
})
</script>

<style scoped>
.page-container {
  padding: 24px 28px;
  background: #F8F9FB;
  min-height: 100vh;
}

.filter-card {
  background: white;
  border-radius: 14px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: none;
  border: 1px solid #EDEFF2;
}

.table-card {
  background: white;
  border-radius: 14px;
  box-shadow: none;
  border: 1px solid #EDEFF2;
}

.table-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #F0F1F3;
}

.table-card :deep(.el-table) {
  border-radius: 14px;
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

.search-input {
  width: 200px;
}

.search-select {
  width: 200px;
}

.pagination-wrap {
  padding: 16px 20px;
  border-top: 1px solid #F0F1F3;
  display: flex;
  justify-content: flex-end;
}

.data-table {
  width: 100%;
}

:deep(.el-table) {
  border: none !important;
}
:deep(.el-table th.el-table__cell) {
  background: #F8F9FB;
  color: #8A94A6;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 12px 16px;
  border-bottom: 1px solid #EDEFF2;
}
:deep(.el-table td.el-table__cell) {
  padding: 14px 16px;
  color: #2C3E50;
  border-bottom: 1px solid #F0F1F3;
}
:deep(.el-table__row:hover > td) {
  background: #F6F8FA !important;
}
:deep(.el-table .el-table__row--striped td) {
  background: transparent !important;
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
  border-bottom: 1px solid #F0F1F3;
}
:deep(.el-dialog__body) {
  padding: 20px;
}
:deep(.el-dialog__footer) {
  padding: 16px 20px;
  border-top: 1px solid #F0F1F3;
}
</style>