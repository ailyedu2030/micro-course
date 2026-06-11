<!--
  课程分类列表
  路由路径: /courses/categories
  Phase 1
  Author: jackie
-->
<template>
  <div class="course-category-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="searchForm.name" placeholder="请输入分类名称" clearable class="search-input-w200" />
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
          <span>课程分类列表</span>
          <el-button type="primary" @click="handleCreate">新增分类</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="level" label="层级" width="100" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="层级" prop="level">
          <el-input-number v-model="formData.level" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="父分类" prop="parentId">
          <el-select v-model="formData.parentId" placeholder="请选择父分类" clearable class="full-width">
            <el-option label="无" :value="null" />
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategories, createCategory, updateCategory, deleteCategory } from '@/api/course-category'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  name: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)
const parentOptions = ref([])

const formData = reactive({
  name: '',
  level: 0,
  sortOrder: 0,
  parentId: null
})

const formRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  level: [{ required: true, message: '请输入层级', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      name: searchForm.name || undefined
    }
    const { data } = await getCategories(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取分类列表失败')
  } finally {
    loading.value = false
  }
}

const fetchParentOptions = async () => {
  try {
    const { data } = await getCategories({ page: 0, size: 1000 })
    parentOptions.value = data.items || []
  } catch {
    ElMessage.error('获取父分类列表失败')
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.name = ''
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
  dialogTitle.value = '新增分类'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.level = 0
  formData.sortOrder = 0
  formData.parentId = null
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑分类'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.level = row.level
  formData.sortOrder = row.sortOrder
  formData.parentId = row.parentId
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该分类?', '提示', { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    if (error !== 'cancel') {
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
        await updateCategory(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createCategory(formData)
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
  fetchParentOptions()
})
</script>

<style scoped>
.course-category-list {
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

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.data-table { width: 100%; }
.full-width { width: 100%; }
.search-input-w200 { width: 200px; }
</style>