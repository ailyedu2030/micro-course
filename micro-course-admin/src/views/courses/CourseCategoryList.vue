<!--
  课程分类列表
  路由路径: /courses/categories
  Phase 1
  Author: jackie
-->
<template>
  <div class="category-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('course.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('route.CourseCategoryList') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">{{ $t('courseCategoryList.title') }}</span>
        <div class="toolbar-actions">
          <el-input
            v-model="searchForm.name"
            :placeholder="$t('courseCategoryList.searchPlaceholder')"
            clearable
            class="search-input"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">{{ $t('courseCategoryList.query') }}</el-button>
          <el-button type="primary" v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" @click="handleCreate">{{ $t('courseCategoryList.create') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('courseCategoryList.noData')" />
      <!-- P2-19: 大数据量时 default-expand-all 可能卡顿，设为 false 按需展开 -->
      <el-table v-else :data="tableData" stripe border class="data-table" row-key="id" :default-expand-all="false">
        <el-table-column prop="name" :label="$t('courseCategoryList.name')" min-width="180" />
        <el-table-column prop="sortOrder" :label="$t('course.sortOrder')" width="100" align="center" />
        <el-table-column :label="$t('app.operation')" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button type="success" link size="small" @click="handleAddChild(row)" v-if="(userRole === 'ADMIN' || userRole === 'ACADEMIC') && row.parentId === null">{{ $t('courseCategoryList.addChild') }}</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="!loading && tableData.length > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </el-card>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="$t(dialogTitle)" width="500px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item :label="$t('courseCategoryList.parent')" v-if="formData.parentId">
          <el-input :value="parentName" disabled />
        </el-form-item>
        <el-form-item :label="$t('courseCategoryList.nameLabel')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('courseCategoryList.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('courseCategoryList.code')" prop="code">
          <el-input v-model="formData.code" :placeholder="$t('courseCategoryList.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('course.sortOrder')" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item :label="$t('courseCategoryList.description')" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" :placeholder="$t('courseCategoryList.descriptionPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getCategories, createCategory, updateCategory, deleteCategory } from '@/api/course-category'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const { t } = useI18n()

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
const dialogTitle = ref('courseCategoryList.create')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)
const parentName = ref('')

// @deprecated level 字段已无用，保留父级ID但不再设置 level
const formData = reactive({
  parentId: null,
  name: '',
  code: '',
  sortOrder: 0
})

// P2-18: 表单中不存在 level 字段（formData.level 虽定义但无对应表单控件），移除无效校验规则
const formRules = {
  name: [{ required: true, message: t('courseCategoryList.nameRequired'), trigger: 'blur' }],
  code: [{ required: true, message: t('courseCategoryList.codeRequired'), trigger: 'blur' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value, name: searchForm.name || undefined }
    const { data } = await getCategories(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error(t('courseCategoryList.fetchListFailed'))
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
  dialogTitle.value = 'courseCategoryList.create'
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
  dialogTitle.value = 'courseCategoryList.edit'
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
  dialogTitle.value = 'courseCategoryList.addChild'
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
  // P1I-053: 子分类预检 — 有子分类时阻止删除
  if (row.children?.length > 0) {
    ElMessage.warning(t('courseCategoryList.deleteChildFirst'))
    return
  }
  try {
    await ElMessageBox.confirm(t('courseCategoryList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success(t('courseCategoryList.deleteSuccess'))
    fetchData()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) {
      ElMessage.error(t('courseCategoryList.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      if (isEdit.value) {
        await updateCategory(currentId.value, formData)
        ElMessage.success(t('courseCategoryList.editSuccess'))
      } else {
        await createCategory(formData)
        ElMessage.success(t('courseCategoryList.createSuccess'))
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? t('courseCategoryList.editFailed') : t('courseCategoryList.createFailed'))
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
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.toolbar-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.toolbar-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.search-input {
  width: 220px;
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

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

/* Tree table indent styling */
.data-table :deep(.el-table__indent) {
  padding-left: var(--space-4);
}

.full-width {
  width: 100%;
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .category-list-page {
    padding: var(--space-4);
  }

  .toolbar-card {
    margin-bottom: var(--space-4);
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>