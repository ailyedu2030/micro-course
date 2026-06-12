<!--
  课程分类列表
  路由路径: /courses/categories
  Phase 1
  Author: jackie
-->
<template>
  <div class="category-list-page">
    <!-- 顶栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">课程分类管理</span>
        <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增分类</el-button>
      </div>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table" row-key="id" default-expand-all>
        <template #empty>
          <el-empty description="暂无分类数据" />
        </template>
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="code" label="编码" width="150" />
        <el-table-column prop="sortOrder" label="排序" width="100" align="center" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleAddChild(row)" v-if="userRole !== 'ACADEMIC' && row.parentId === null">添加子分类</el-button>
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

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="上级分类" v-if="formData.parentId">
          <el-input :value="parentName" disabled />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="分类编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入分类编码" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getCategories, createCategory, updateCategory, deleteCategory } from '@/api/course-category'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)
const parentName = ref('')

const formData = reactive({
  parentId: null,
  name: '',
  code: '',
  sortOrder: 0,
  description: ''
})

const formRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入分类编码', trigger: 'blur' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    const { data } = await getCategories(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取分类列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
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
  formData.parentId = null
  formData.name = ''
  formData.code = ''
  formData.sortOrder = 0
  formData.description = ''
  parentName.value = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑分类'
  isEdit.value = true
  currentId.value = row.id
  formData.parentId = row.parentId
  formData.name = row.name
  formData.code = row.code
  formData.sortOrder = row.sortOrder || 0
  formData.description = row.description || ''
  parentName.value = row.parentId ? tableData.value.find(i => i.id === row.parentId)?.name || '' : ''
  dialogVisible.value = true
}

const handleAddChild = (row) => {
  dialogTitle.value = '添加子分类'
  isEdit.value = false
  currentId.value = null
  formData.parentId = row.id
  formData.name = ''
  formData.code = ''
  formData.sortOrder = 0
  formData.description = ''
  parentName.value = row.name
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该分类?', '提示', { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
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
})
</script>

<style scoped>
.category-list-page {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100%;
}

.toolbar-card {
  margin-bottom: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: none;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.table-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: none;
}

.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.data-table {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  background: #F8FAFC;
  font-weight: 600;
  color: #1E293B;
}

.data-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: #F1F5F9;
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

/* Tree table indent styling */
.data-table :deep(.el-table__indent) {
  padding-left: 16px;
}

.full-width {
  width: 100%;
}

:deep(.el-button) {
  border-radius: 8px;
}

:deep(.el-dialog) {
  border-radius: 12px;
}

@media (max-width: 768px) {
  .category-list-page {
    padding: 16px;
  }

  .toolbar-card {
    margin-bottom: 16px;
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>