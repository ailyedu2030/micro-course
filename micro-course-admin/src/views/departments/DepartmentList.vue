<!--
  院系列表
  路由路径: /departments
  Phase 1
  Author: jackie
-->
<template>
  <div class="department-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="searchForm.name" placeholder="请输入院系名称" clearable class="search-input" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="searchForm.code" placeholder="请输入院系编码" clearable class="search-input" />
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
          <span>院系列表</span>
          <el-button type="primary" @click="handleCreate">新增院系</el-button>
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        class="data-table"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <template #empty>
          <el-empty description="暂无院系数据" />
        </template>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该院系？" @confirm="handleDelete(row)">
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
    </el-card>

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入院系名称" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入院系编码" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDepartments, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  name: '',
  code: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增院系')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  name: '',
  code: '',
  sortOrder: 0
})

const formRules = {
  name: [{ required: true, message: '请输入院系名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入院系编码', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      name: searchForm.name || undefined,
      code: searchForm.code || undefined
    }
    const { data } = await getDepartments(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
    ElMessage.error('获取院系列表失败')
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
  searchForm.code = ''
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
  dialogTitle.value = '新增院系'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.code = ''
  formData.sortOrder = 0
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑院系'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.code = row.code
  formData.sortOrder = row.sortOrder
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await deleteDepartment(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
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
        await updateDepartment(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createDepartment(formData)
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
  fetchData()
})
</script>

<style scoped>
.department-list {
  padding: var(--space-xl);
}

.search-card {
  margin-bottom: var(--space-lg);
  border-radius: var(--radius-md);
  transition: box-shadow 200ms ease;
}

.search-card:hover {
  box-shadow: var(--shadow-md);
}

.table-card {
  border-radius: var(--radius-md);
  transition: box-shadow 200ms ease;
}

.table-card:hover {
  box-shadow: var(--shadow-md);
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.table-card :deep(.el-table) {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-input {
  width: 200px;
}

.pagination-wrap {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: center;
}

.data-table {
  width: 100%;
}

.full-width {
  width: 100%;
}
</style>