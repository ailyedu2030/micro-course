<!--
  院系列表
  路由路径: /departments
  Phase 1
  Author: jackie
-->
<template>
  <div class="department-list">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item>{{ $t('departmentList.orgMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('departmentList.title') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('departmentList.name')">
          <el-input v-model="searchForm.name" :placeholder="$t('departmentList.namePlaceholder')" clearable class="search-input" />
        </el-form-item>
        <el-form-item :label="$t('departmentList.code')">
          <el-input v-model="searchForm.code" :placeholder="$t('departmentList.codePlaceholder')" clearable class="search-input" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('departmentList.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('departmentList.title') }}</span>
          <el-button type="primary" v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" @click="handleCreate">{{ $t('departmentList.create') }}</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-result v-else-if="error" icon="error" :title="$t('departmentList.loadFailed')" :sub-title="$t('departmentList.loadFailedSubtitle')">
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && tableData.length === 0" :description="$t('departmentList.noData')" :image-size="120" />
      <el-table
        v-loading="loading"
        v-else
        :data="tableData"
        stripe
        border
        class="data-table"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="name" :label="$t('departmentList.name')" min-width="150" />
        <el-table-column prop="code" :label="$t('departmentList.code')" width="120" />
        <el-table-column prop="sortOrder" :label="$t('course.sortOrder')" width="100" />
        <el-table-column prop="createdAt" :label="$t('departmentList.createdAt')" width="180" :formatter="$formatDateTime" />
        <el-table-column :label="$t('app.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
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
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </el-card>

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item :label="$t('departmentList.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('departmentList.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('departmentList.code')" prop="code">
          <el-input v-model="formData.code" :placeholder="$t('departmentList.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('course.sortOrder')" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getDepartments, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const { t } = useI18n()

const loading = ref(false)
const error = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  name: '',
  code: ''
})

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, searchForm, ['name', 'code'])

const dialogVisible = ref(false)
const dialogTitle = ref('departmentList.create')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  name: '',
  code: '',
  sortOrder: 0
})

const formRules = {
  name: [{ required: true, message: t('departmentList.nameRequired'), trigger: 'blur' }],
  code: [{ required: true, message: t('departmentList.codeRequired'), trigger: 'blur' }],
  sortOrder: [{ required: true, message: t('departmentList.sortOrderRequired'), trigger: 'blur' }]
}

const fetchData = async () => {
  loading.value = true
  error.value = false
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
  } catch {
    error.value = true
    ElMessage.error(t('departmentList.fetchListFailed'))
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
  dialogTitle.value = 'departmentList.create'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.code = ''
  formData.sortOrder = 0
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = 'departmentList.edit'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.code = row.code
  formData.sortOrder = row.sortOrder
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('departmentList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteDepartment(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (error) {
    const code = error.response?.data?.code
    if (code === 2002) {
      ElMessage.error(t('departmentList.deleteHasMajors'))
    } else if (error.response?.status === 400) {
      ElMessage.error(error.response?.data?.message || t('departmentList.deleteHasAssoc'))
    } else {
      ElMessage.error(error?.response?.data?.message || t('course.deleteFailed'))
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
        await updateDepartment(currentId.value, formData)
        ElMessage.success(t('departmentList.editSuccess'))
      } else {
        await createDepartment(formData)
        ElMessage.success(t('course.createSuccess'))
      }
      dialogVisible.value = false
      fetchData()
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || (isEdit.value ? t('departmentList.editFailed') : t('course.createFailed')))
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
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

.search-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.table-card :deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.search-input {
  width: 200px;
  border-radius: var(--radius-md);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.data-table :deep(.el-table__row) {
  transition: background var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__body tr) {
  background: var(--el-fill-color-blank);
}

.data-table :deep(.el-table__body tr:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.pagination-wrap {
  margin-top: var(--space-6);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.full-width {
  width: 100%;
}

/* 弹窗 border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}
:deep(.el-dialog__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:deep(.el-dialog__body) {
  padding: var(--space-5);
}
:deep(.el-dialog__footer) {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}
/* ===== 响应式适配（Mobile / Tablet ≤768px）===== */
@media (max-width: 768px) {
  .department-list {
    padding: var(--space-3);
  }
  .search-card :deep(.el-form--inline .el-form-item) {
    display: flex;
    width: 100%;
    margin-right: 0;
  }
  .search-input {
    width: 100%;
  }
  .card-header {
    flex-wrap: wrap;
    gap: var(--space-2);
  }
  .data-table {
    font-size: var(--text-sm);
  }
  .pagination-wrap {
    padding: var(--space-3);
    overflow-x: auto;
  }
}
</style>
