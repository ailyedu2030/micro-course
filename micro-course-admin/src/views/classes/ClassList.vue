<!--
  班级列表
  路由路径: /classes
  Phase 1
  Author: jackie
-->
<template>
  <div class="class-list">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item>{{ $t('classList.orgMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('classList.title') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 搜索区 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('classList.name')">
          <el-input v-model="searchForm.name" :placeholder="$t('classList.namePlaceholder')" clearable class="search-input" />
        </el-form-item>
        <el-form-item :label="$t('classList.major')">
          <el-select v-model="searchForm.majorId" :placeholder="$t('classList.selectMajorPlaceholder')" clearable class="search-select">
            <el-option v-for="item in majorOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('classList.grade')">
          <el-input v-model="searchForm.grade" :placeholder="$t('classList.gradePlaceholder')" clearable class="search-input" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('classList.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('classList.title') }}</span>
          <el-button type="primary" v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" @click="handleCreate">{{ $t('classList.create') }}</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-result v-else-if="error" icon="error" :title="$t('classList.loadFailed')" :sub-title="$t('microSpecialtyManage.loadFailedSubtitle')">
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && tableData.length === 0" :description="$t('classList.noData')" :image-size="120" />
      <el-table v-loading="loading" v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="name" :label="$t('classList.name')" min-width="150" />
        <el-table-column prop="majorName" :label="$t('classList.belongMajor')" min-width="150" />
        <el-table-column prop="grade" :label="$t('classList.grade')" width="100" />
        <el-table-column prop="sortOrder" :label="$t('course.sortOrder')" width="100" />
        <el-table-column prop="createdAt" :label="$t('classList.createdAt')" width="180" :formatter="$formatDateTime" />
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
    <el-dialog v-model="dialogVisible" :title="$t(dialogTitle)" width="580px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item :label="$t('classList.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('classList.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('classList.belongMajor')" prop="majorId">
          <el-select v-model="formData.majorId" :placeholder="$t('classList.selectMajorPlaceholder')" class="full-width">
            <el-option v-for="item in majorOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('classList.grade')" prop="grade">
          <el-input v-model="formData.grade" :placeholder="$t('classList.gradePlaceholder')" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getClasses, createClass, updateClass, deleteClass } from '@/api/class'
import { getMajors } from '@/api/major'


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

// P2-14: URL 分页同步
import { useUrlPagination } from '@/composables/useUrlPagination'
const { bindToQuery } = useUrlPagination()

const majorOptions = ref([])
const searchForm = reactive({
  name: '',
  majorId: null,
  grade: ''
})

// P2-14: URL 分页同步
bindToQuery(page, size, searchForm, ['name', 'majorId', 'grade'])

const dialogVisible = ref(false)
const dialogTitle = ref('classList.create')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  name: '',
  majorId: null,
  grade: '',
  sortOrder: 0
})

const formRules = {
  name: [{ required: true, message: t('classList.nameRequired'), trigger: 'blur' }],
  majorId: [{ required: true, message: t('classList.selectMajorRequired'), trigger: 'change' }],
  grade: [{ required: true, message: t('classList.gradeRequired'), trigger: 'blur' }],
  sortOrder: [{ required: true, message: t('classList.sortOrderRequired'), trigger: 'blur' }]
}

const fetchMajors = async () => {
  try {
    const { data } = await getMajors({ page: 0, size: 100 })
    majorOptions.value = data.items || []
  } catch {
    ElMessage.error(t('classList.fetchMajorsFailed'))
  }
}

const fetchData = async () => {
  loading.value = true
  error.value = false
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
    error.value = true
    ElMessage.error(t('classList.fetchListFailed'))
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
  dialogTitle.value = 'classList.create'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.majorId = null
  formData.grade = ''
  formData.sortOrder = 0
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = 'classList.edit'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.majorId = row.majorId
  formData.grade = row.grade
  formData.sortOrder = row.sortOrder
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('classList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteClass(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (error) {
    const msg = error.response?.data?.message
    if (error.response?.data?.code === 4002) {
      ElMessage.error(msg || t('classList.deleteHasStudents'))
    } else if (error.response?.status === 409) {
      ElMessage.error(msg || t('classList.deleteHasAssoc'))
    } else {
      ElMessage.error(msg || t('course.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  // P1 幂等修复: validate 回调是异步的, 必须在 await 前置位 loading 防连点重复提交
  if (submitLoading.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      if (isEdit.value) {
        await updateClass(currentId.value, formData)
        ElMessage.success(t('classList.editSuccess'))
      } else {
        await createClass(formData)
        ElMessage.success(t('course.createSuccess'))
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? t('classList.editFailed') : t('course.createFailed'))
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
.class-list {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

.filter-card {
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
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.search-input {
  width: 200px;
  border-radius: var(--radius-md);
}

.search-select {
  width: 200px;
  border-radius: var(--radius-md);
}

.pagination-wrap {
  margin-top: var(--space-6);
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
  .class-list {
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
