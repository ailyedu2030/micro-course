<!--
  标签列表
  路由路径: /courses/tags
  Phase 1
  Author: jackie
-->
<template>
  <div class="tag-list-page">
    <!-- 顶栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">标签管理</span>
        <el-button type="primary" @click="handleCreate">新增标签</el-button>
      </div>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <el-empty description="暂无标签数据" />
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="name" label="标签名" min-width="150" />
        <el-table-column prop="color" label="颜色" width="120" align="center">
          <template #default="{ row }">
            <span class="color-swatch" :style="{ backgroundColor: row.color || '#409eff' }"></span>
            <span class="color-value">{{ row.color || '#409eff' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="usageCount" label="使用次数" width="120" align="center">
          <template #default="{ row }">
            {{ row.usageCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
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

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="标签名" prop="name">
          <el-input v-model="formData.name" placeholder="请输入标签名" />
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <div class="color-picker-row">
            <el-color-picker v-model="formData.color" />
            <el-input v-model="formData.color" placeholder="#409eff" class="color-input" />
          </div>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTags, createTag, updateTag, deleteTag } from '@/api/tag'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('新增标签')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  name: '',
  color: '#409eff',
  description: ''
})

const formRules = {
  name: [{ required: true, message: '请输入标签名', trigger: 'blur' }],
  color: [{ required: true, message: '请选择颜色', trigger: 'change' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    const { data } = await getTags(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取标签列表失败')
  } finally {
    loading.value = false
  }
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleCreate = () => {
  dialogTitle.value = '新增标签'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.color = '#409eff'
  formData.description = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑标签'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.color = row.color || '#409eff'
  formData.description = row.description || ''
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该标签?', '提示', { type: 'warning' })
    await deleteTag(row.id)
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
        await updateTag(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createTag(formData)
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
.tag-list-page {
  padding: var(--space-5);
}

.toolbar-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-md);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.table-card {
  border-radius: var(--radius-md);
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}

.data-table {
  width: 100%;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.data-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--color-bg-page);
}

.color-swatch {
  display: inline-block;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  vertical-align: middle;
  margin-right: var(--space-1);
}

.color-value {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  font-family: monospace;
}

.color-picker-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.color-input {
  width: 120px;
}

.full-width {
  width: 100%;
}

@media (max-width: 768px) {
  .tag-list-page {
    padding: var(--space-3);
  }

  .toolbar-card {
    margin-bottom: var(--space-3);
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>